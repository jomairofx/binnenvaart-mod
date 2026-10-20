"""Rain stops on the ship (roofs, decks, closed hatches) instead of falling through it."""
import sys, json
root, pkg, ns = sys.argv[1:4]
P = pkg.replace(".", "/")

f = f"{root}/main/java/{P}/ship/ShipTemplate.java"
s = open(f).read()
a = "	public BlockState getState(int x, int y, int z) {"
assert s.count(a) == 1
s = s.replace(a, """	private int[] topCache;

	/** Hoogste blok dat regen tegenhoudt in kolom (x, z), of Integer.MIN_VALUE als er niets staat. */
	public int topAt(int x, int z) {
		int ix = x - MIN_X, iz = z - MIN_Z;
		if (ix < 0 || iz < 0 || ix >= SIZE_X || iz >= SIZE_Z) return Integer.MIN_VALUE;
		int[] c = topCache;
		if (c == null) {
			c = new int[SIZE_X * SIZE_Z];
			for (int cx = 0; cx < SIZE_X; cx++)
				for (int cz = 0; cz < SIZE_Z; cz++) {
					int top = Integer.MIN_VALUE;
					for (int cy = SIZE_Y - 1; cy >= 0; cy--) {
						BlockState st = states[(cy * SIZE_Z + cz) * SIZE_X + cx];
						if (st != null && !st.isAir() && (st.blocksMovement() || !st.getFluidState().isEmpty())) {
							top = cy + MIN_Y;
							break;
						}
					}
					c[cz * SIZE_X + cx] = top;
				}
			topCache = c;
		}
		return c[iz * SIZE_X + ix];
	}

""" + a)
open(f, "w").write(s)

f = f"{root}/main/java/{P}/ship/ShipEntity.java"
s = open(f).read()
a = "	public static boolean isClimbing(Entity e) {"
assert s.count(a) == 1
s = s.replace(a, """	/** Client: hoogte waar de regen in deze wereldkolom ophoudt, rekening houdend met schepen. */
	public static int rainTop(World w, int x, int z, int top) {
		try {
			for (ShipEntity s : ShipHolder.of(w)) {
				Box bb = s.getBoundingBox();
				if (x + 1 < bb.minX || x > bb.maxX || z + 1 < bb.minZ || z > bb.maxZ) continue;
				Vec3d l = s.toLocal(new Vec3d(x + 0.5, s.getY(), z + 0.5));
				int lx = MathHelper.floor(l.x), lz = MathHelper.floor(l.z);
				int t = s.template.topAt(lx, lz);
				if (t == Integer.MIN_VALUE) continue;
				double y = t + 1 + (s.template.inLiftXZ(l.x, l.z) ? s.liftOffset() : 0);
				// dichte luiken houden de regen ook tegen
				if (COVERS > 0 && s.hatchProgress > 0.5f && l.z >= s.template.holdZ0 - 1 && l.z < s.template.holdZ1 + 2) {
					int[][] h = s.template.holds;
					if (h.length > 0 && l.x >= h[0][0] - 1 && l.x < h[h.length - 1][1] + 2) y = Math.max(y, s.template.coamingTopY + 0.5);
				}
				int wy = MathHelper.ceil(s.getY() + y);
				if (wy > top) top = wy;
			}
		} catch (RuntimeException ex) {
			reportError("rain", ex);
		}
		return top;
	}

	public static int rainChecks;

""" + a)
open(f, "w").write(s)

mixin = f"""package {pkg}.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.util.math.BlockPos;
import {pkg}.ship.ShipEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/** Regen en spatten stoppen op het dak en dek van het schip, in plaats van erdoorheen te vallen. */
@Mixin(WorldRenderer.class)
public abstract class WeatherMixin {{
	@Shadow
	@Final
	private MinecraftClient client;

	@ModifyExpressionValue(method = "renderWeather", require = 0, at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/World;getTopY(Lnet/minecraft/world/Heightmap$Type;II)I"))
	private int {ns}$rainOnShip(int top, @com.llamalad7.mixinextras.sugar.Local(ordinal = 0, argsOnly = false) int unused) {{
		return top;
	}}
}}
"""
# simpler, robust version: capture x/z through the call arguments with WrapOperation
mixin = f"""package {pkg}.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;
import net.minecraft.world.WorldView;
import {pkg}.ship.ShipEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Regen en spatten stoppen op het dak en dek van het schip, in plaats van erdoorheen te vallen. */
@Mixin(WorldRenderer.class)
public abstract class WeatherMixin {{
	@WrapOperation(method = "renderWeather", require = 0, at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/World;getTopY(Lnet/minecraft/world/Heightmap$Type;II)I"))
	private int {ns}$rainOnShip(World world, Heightmap.Type type, int x, int z, Operation<Integer> op) {{
		ShipEntity.rainChecks++;
		return ShipEntity.rainTop(world, x, z, op.call(world, type, x, z));
	}}

	@WrapOperation(method = "tickRainSplashing", require = 0, at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/WorldView;getTopPosition(Lnet/minecraft/world/Heightmap$Type;Lnet/minecraft/util/math/BlockPos;)Lnet/minecraft/util/math/BlockPos;"))
	private BlockPos {ns}$splashOnShip(WorldView view, Heightmap.Type type, BlockPos pos, Operation<BlockPos> op) {{
		BlockPos p = op.call(view, type, pos);
		if (!(view instanceof World w)) return p;
		int y = ShipEntity.rainTop(w, p.getX(), p.getZ(), p.getY());
		return y == p.getY() ? p : new BlockPos(p.getX(), y, p.getZ());
	}}
}}
"""
open(f"{root}/client/java/{P}/client/mixin/WeatherMixin.java", "w").write(mixin)
jf = f"{root}/client/resources/{ns}.client.mixins.json"
j = json.load(open(jf))
if "WeatherMixin" not in j["client"]: j["client"].append("WeatherMixin")
json.dump(j, open(jf, "w"), indent=2)
print("ok", root)
