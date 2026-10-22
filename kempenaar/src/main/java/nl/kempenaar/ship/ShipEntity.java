package nl.kempenaar.ship;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.BedBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.LeverBlock;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.TrapdoorBlock;
import net.minecraft.block.enums.BedPart;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.nbt.NbtList;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.ItemScatterer;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import net.minecraft.world.explosion.Explosion;
import nl.kempenaar.Kempenaar;
import nl.kempenaar.mixin.EntityInvoker;
import nl.kempenaar.net.ShipPayloads;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Het varende kempenaar. De entiteitpositie is het midden van het schip (sjabloon x=48, z=5.5);
 * y is de onderkant van sjabloonlaag 0. Bij volle lading valt laag 0 samen met de bovenste waterlaag.
 */
public class ShipEntity extends Entity {
	// ---- constanten ----
	public static final int LENGTH = 70;
	public static final double EMPTY_RISE = 2.0;   // empty: 2 m draught
	public static final double LOADED_OFFSET = -0.111;   // fully loaded: 4 m draught          // zoveel blokken ligt een leeg schip hoger
	public static final double[] STEP_SPEED = {-0.14, -0.07, 0.0, 0.11, 0.22, 0.35}; // blokken per tick
	public static final String[] STEP_NAMES = {"Full astern", "Slow astern", "Stop", "Slow ahead", "Half ahead", "Full ahead"};
	public static final double V_FULL = 0.35;
	private static final double MAX_TURN = Math.toRadians(0.605);   // trager, zwaarder sturen
	private static final double THRUSTER_TURN = Math.toRadians(0.42);
	private static final double PIVOT_RUDDER = 8.0;      // draaipunt bij roeren (lokaal, t.o.v. midden)
	private static final double PIVOT_THRUSTER = -27.0;   // draaipunt bij boegschroef (bij de achtersteven)
	public static final int HOLD_SLOTS = 54;
	public static final int COVERS = 8;
	public static final float COVER_X0 = 19f, COVER_LEN = 4.75f, STACK_X = 52.25f;
	public static final float COVER_STACK = 0.26f;

	// ---- gesynchroniseerde gegevens ----
	private static final TrackedData<Float> YAW = DataTracker.registerData(ShipEntity.class, TrackedDataHandlerRegistry.FLOAT);
	private static final TrackedData<Float> WATER_Y = DataTracker.registerData(ShipEntity.class, TrackedDataHandlerRegistry.FLOAT);
	private static final TrackedData<Float> SPEED = DataTracker.registerData(ShipEntity.class, TrackedDataHandlerRegistry.FLOAT);
	private static final TrackedData<Float> RUDDER = DataTracker.registerData(ShipEntity.class, TrackedDataHandlerRegistry.FLOAT);
	private static final TrackedData<Integer> THROTTLE = DataTracker.registerData(ShipEntity.class, TrackedDataHandlerRegistry.INTEGER);
	private static final TrackedData<Integer> THRUSTER = DataTracker.registerData(ShipEntity.class, TrackedDataHandlerRegistry.INTEGER);
	private static final TrackedData<Integer> SEAT_AFT = DataTracker.registerData(ShipEntity.class, TrackedDataHandlerRegistry.INTEGER);
	private static final TrackedData<Integer> SEAT_BOW = DataTracker.registerData(ShipEntity.class, TrackedDataHandlerRegistry.INTEGER);
	private static final TrackedData<Boolean> HATCHES_CLOSED = DataTracker.registerData(ShipEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
	private static final TrackedData<Boolean> AGROUND = DataTracker.registerData(ShipEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
	private static final TrackedData<Float> LIFT = DataTracker.registerData(ShipEntity.class, TrackedDataHandlerRegistry.FLOAT);
	private static final TrackedData<Boolean> LIGHTS = DataTracker.registerData(ShipEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
	private static final TrackedData<Boolean> ENGINE = DataTracker.registerData(ShipEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
	@SuppressWarnings("unchecked")
	private static final TrackedData<Float>[] FILL = new TrackedData[4];
	@SuppressWarnings("unchecked")
	private static final TrackedData<Integer>[] MATERIAL = new TrackedData[4];
	@SuppressWarnings("unchecked")
	private static final TrackedData<Float>[] BOXED = new TrackedData[4];

	static {
		for (int i = 0; i < 4; i++) {
			FILL[i] = DataTracker.registerData(ShipEntity.class, TrackedDataHandlerRegistry.FLOAT);
			MATERIAL[i] = DataTracker.registerData(ShipEntity.class, TrackedDataHandlerRegistry.INTEGER);
			BOXED[i] = DataTracker.registerData(ShipEntity.class, TrackedDataHandlerRegistry.FLOAT);
		}
	}

	/** Haak voor de client (verzenden van pakketjes), ingesteld door KempenaarClient. */
	public static java.util.function.Consumer<net.minecraft.network.packet.CustomPayload> clientSender = p -> {};

	public final ShipTemplate template = ShipTemplate.get();
	public final ShipStructure structure = new ShipStructure(template);
	private final CargoInventory[] holds = new CargoInventory[4];

	// stand
	public double shipYaw, prevShipYaw;
	private boolean registered;
	private int waterLayerY;
	public float hatchProgress, prevHatchProgress;

	// server: besturing en fysica
	private double speed, yawRate, thrusterRate, rudder;
	private double courseYaw = Double.NaN;   // werkelijke vaarrichting: loopt achter op de koers (drift in de bocht)
	private int throttle, rudderInput, thrusterInput;
	private int lastControlTick = -1000, hornCooldown, bumpCooldown;
	private boolean aground;
	private final LongOpenHashSet displaced = new LongOpenHashSet();
	/** Zeewier en zeegras dat onder het schip moest wijken; komt terug als het schip verder vaart. */
	private final it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<BlockState> plants = new it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<>();
	private double dispX = Double.NaN, dispY, dispZ, dispYaw;
	private int lastDisplaceTick = -100;

	// client: interpolatie en weergave
	private double lerpX, lerpY, lerpZ, lerpYaw;
	private int lerpSteps, yawSteps;
	private boolean yawInit;
	public final float[] displayFill = new float[4];
	public final float[] prevDisplayFill = new float[4];
	public Object renderCache; // ShipMesh op de client
	/** Client: hoe hard de motor draait (0 = stationair, 1 = vol vermogen). */
	public float engineLoad;
	/** Alleen voor de automatische test: besturing zonder roerganger. */
	public boolean testMode;

	public ShipEntity(EntityType<? extends ShipEntity> type, World world) {
		super(type, world);
		for (int i = 0; i < 4; i++) holds[i] = new CargoInventory(this, i);
	}

	// =====================================================================
	// Coördinaten
	// =====================================================================

	/** Wereldpunt naar sjablooncoördinaten voor een gegeven stand van het schip. */
	public static Vec3d toLocal(double sx, double sy, double sz, double yaw, double wx, double wy, double wz) {
		double c = Math.cos(yaw), s = Math.sin(yaw);
		double dx = wx - sx, dz = wz - sz;
		double lx = dx * c + dz * s;
		double lz = -dx * s + dz * c;
		return new Vec3d(lx + ShipTemplate.CX, wy - sy, lz + ShipTemplate.CZ);
	}

	public static Vec3d toWorld(double sx, double sy, double sz, double yaw, double tx, double ty, double tz) {
		double c = Math.cos(yaw), s = Math.sin(yaw);
		double lx = tx - ShipTemplate.CX, lz = tz - ShipTemplate.CZ;
		return new Vec3d(sx + lx * c - lz * s, sy + ty, sz + lx * s + lz * c);
	}

	public Vec3d toLocal(Vec3d w) {
		return toLocal(getX(), getY(), getZ(), shipYaw, w.x, w.y, w.z);
	}

	public Vec3d toWorld(double tx, double ty, double tz) {
		return toWorld(getX(), getY(), getZ(), shipYaw, tx, ty, tz);
	}

	/** Lokale richting (alleen horizontaal draaien). */
	private Vec3d dirToLocal(Vec3d v) {
		double c = Math.cos(shipYaw), s = Math.sin(shipYaw);
		return new Vec3d(v.x * c + v.z * s, v.y, -v.x * s + v.z * c);
	}

	private Vec3d dirToWorld(Vec3d v) {
		double c = Math.cos(shipYaw), s = Math.sin(shipYaw);
		return new Vec3d(v.x * c - v.z * s, v.y, v.x * s + v.z * c);
	}

	@Override
	protected Box calculateBoundingBox() {
		double c = Math.cos(shipYaw), s = Math.sin(shipYaw);
		double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, minZ = Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
		double[][] corners = {{-1.5, -1.5}, {71.5, -1.5}, {-1.5, 10.5}, {71.5, 10.5}};
		for (double[] k : corners) {
			double lx = k[0] - ShipTemplate.CX, lz = k[1] - ShipTemplate.CZ;
			double wx = getX() + lx * c - lz * s, wz = getZ() + lx * s + lz * c;
			minX = Math.min(minX, wx);
			maxX = Math.max(maxX, wx);
			minZ = Math.min(minZ, wz);
			maxZ = Math.max(maxZ, wz);
		}
		return new Box(minX, getY() - 5, minZ, maxX, getY() + 15, maxZ);
	}

	// =====================================================================
	// Initialisatie en gegevens
	// =====================================================================

	@Override
	protected void initDataTracker(DataTracker.Builder builder) {
		builder.add(YAW, 0f);
		builder.add(WATER_Y, 62.9f);
		builder.add(SPEED, 0f);
		builder.add(RUDDER, 0f);
		builder.add(THROTTLE, 0);
		builder.add(THRUSTER, 0);
		builder.add(SEAT_AFT, -1);
		builder.add(SEAT_BOW, -1);
		builder.add(HATCHES_CLOSED, false);
		builder.add(AGROUND, false);
		builder.add(LIFT, 0f);
		builder.add(LIGHTS, true);
		builder.add(ENGINE, true);
		for (int i = 0; i < 4; i++) {
			builder.add(FILL[i], 0f);
			builder.add(MATERIAL[i], 0);
			builder.add(BOXED[i], 0f);
		}
	}

	/** Zet een nieuw schip neer (leeg, dus hoog op het water). */
	public void place(double x, int waterLayer, double z, double yaw) {
		this.waterLayerY = waterLayer;
		this.shipYaw = this.prevShipYaw = MathHelper.wrapDegrees(Math.toDegrees(yaw)) * Math.PI / 180.0;
		this.setPosition(x, waterLayer + LOADED_OFFSET + EMPTY_RISE, z);
		this.dataTracker.set(YAW, (float) shipYaw);
		this.dataTracker.set(WATER_Y, waterLayer + 0.889f);
		this.setYaw((float) Math.toDegrees(shipYaw) - 90f);
	}

	@Override
	public void onTrackedDataSet(TrackedData<?> data) {
		super.onTrackedDataSet(data);
		if (YAW.equals(data) && getWorld().isClient) {
			lerpYaw = dataTracker.get(YAW);
			if (!yawInit) {
				shipYaw = prevShipYaw = lerpYaw;
				yawInit = true;
				setPosition(getX(), getY(), getZ());
			} else {
				yawSteps = 3;
			}
		}
	}

	@Override
	public void updateTrackedPositionAndAngles(double x, double y, double z, float yaw, float pitch, int interpolationSteps) {
		this.lerpX = x;
		this.lerpY = y;
		this.lerpZ = z;
		this.lerpSteps = Math.max(1, interpolationSteps);
	}

	public float waterSurfaceY() {
		return dataTracker.get(WATER_Y);
	}

	public float engineTarget() {
		int t = syncedThrottle();
		return t > 0 ? t / 3f : -t / 2f * 0.85f;
	}

	// ---- hefbare stuurhut en verlichting ----
	private double liftTarget, liftCur, carriedLift;
	public float liftClient, prevLiftClient;

	/** Hoe ver de stuurhut nu omhoog staat (blokken). */
	public float liftOffset() {
		return getWorld().isClient ? liftClient : (float) liftCur;
	}

	public float liftLerp(float tickDelta) {
		return MathHelper.lerp(tickDelta, prevLiftClient, liftClient);
	}

	public boolean engineOn() {
		return dataTracker.get(ENGINE);
	}

	public void setEngine(boolean on) {
		dataTracker.set(ENGINE, on);
	}

	public boolean lightsOn() {
		return dataTracker.get(LIGHTS);
	}

	/** Punt in sjablooncoördinaten zoals je het ziet (met de stuurhut omhoog) terug naar het sjabloon. */
	public Vec3d unlift(Vec3d t) {
		double L = liftOffset();
		if (L <= 0.001 || template.liftRegion == null) return t;
		if (template.inLiftXZ(t.x, t.z) && t.y >= template.liftRegion[1] + L - 0.001) return new Vec3d(t.x, t.y - L, t.z);
		return t;
	}

	private double liftAt(double x, double y, double z) {
		return template.inLift(MathHelper.floor(x), MathHelper.floor(y), MathHelper.floor(z)) ? liftOffset() : 0;
	}

	/** Alleen voor de automatische test. */
	public void testSetLift(double target) {
		liftTarget = MathHelper.clamp(target, 0, template.liftMax);
	}

	public void setLights(boolean on) {
		if (getWorld().isClient || lightsOn() == on) return;
		dataTracker.set(LIGHTS, on);
		long[] lp = template.lightPositions;
		int[] st = new int[lp.length];
		for (int i = 0; i < lp.length; i++) {
			BlockPos p = BlockPos.fromLong(lp[i]);
			BlockState orig = template.getState(p.getX(), p.getY(), p.getZ());
			BlockState now = on ? orig : ShipTemplate.lightOff(orig);
			structure.set(p.getX(), p.getY(), p.getZ(), now);
			st[i] = Block.getRawIdFromState(now);
		}
		ShipPayloads.Blocks payload = new ShipPayloads.Blocks(getId(), lp, st);
		for (ServerPlayerEntity p : PlayerLookup.tracking(this)) ServerPlayNetworking.send(p, payload);
	}

	/** Een BlockView van alleen de stuurhut (onlyLift) of alles behalve de stuurhut. */
	private final class LiftView implements net.minecraft.world.BlockView {
		private final boolean onlyLift;

		LiftView(boolean onlyLift) {
			this.onlyLift = onlyLift;
		}

		@Override
		public BlockState getBlockState(BlockPos pos) {
			if (template.inLift(pos.getX(), pos.getY(), pos.getZ()) != onlyLift) return Blocks.AIR.getDefaultState();
			return structure.get(pos.getX(), pos.getY(), pos.getZ());
		}

		@Override
		public net.minecraft.fluid.FluidState getFluidState(BlockPos pos) {
			return net.minecraft.fluid.Fluids.EMPTY.getDefaultState();
		}

		@Nullable
		@Override
		public net.minecraft.block.entity.BlockEntity getBlockEntity(BlockPos pos) {
			return null;
		}

		@Override
		public int getHeight() {
			return structure.getHeight();
		}

		@Override
		public int getBottomY() {
			return structure.getBottomY();
		}
	}

	private final LiftView baseView = new LiftView(false), liftView = new LiftView(true);

	public float syncedSpeed() {
		return dataTracker.get(SPEED);
	}

	public float syncedRudder() {
		return dataTracker.get(RUDDER);
	}

	public int syncedThrottle() {
		return dataTracker.get(THROTTLE);
	}

	/** Huidige draaisnelheid door de boegschroef (alleen op de server bijgehouden). */
	public double thrusterTurnRate() {
		return thrusterRate;
	}

	public int syncedThruster() {
		return dataTracker.get(THRUSTER);
	}

	public boolean hatchesClosed() {
		return dataTracker.get(HATCHES_CLOSED);
	}

	public boolean isAground() {
		return dataTracker.get(AGROUND);
	}

	public float fill(int hold) {
		return dataTracker.get(FILL[hold]);
	}

	/** Deel van de lading in dit ruim dat in containers zit (0..1). */
	public float containerShare(int hold) {
		return dataTracker.get(BOXED[hold]);
	}

	public int material(int hold) {
		return dataTracker.get(MATERIAL[hold]);
	}

	public float load() {
		float t = 0;
		int n = template.holds.length;
		for (int i = 0; i < n; i++) t += fill(i);
		return t / n;
	}

	/** Diepgang in meters (blokken) onder de waterlijn. */
	public double draft() {
		return (waterSurfaceY()) - (getY() - 3);
	}

	// =====================================================================
	// Tick
	// =====================================================================

	@Override
	public void tick() {
		try {
			tickSafe();
		} catch (RuntimeException | StackOverflowError ex) {
			reportError("tick", ex);
		}
	}

	/** Fout in de scheepscode: in het logboek zetten (één keer per 10 s) in plaats van het spel te laten crashen. */
	public static void reportError(String where, Throwable ex) {
		long now = System.currentTimeMillis();
		if (now - lastError < 10_000) return;
		lastError = now;
		org.slf4j.LoggerFactory.getLogger("nl.kempenaar").error("Ship error in " + where + " (game keeps running) - please send this log", ex);
	}

	private static long lastError;

	/** Voert iets uit en vangt fouten af. */
	public static void safely(String where, Runnable r) {
		try {
			r.run();
		} catch (RuntimeException | StackOverflowError ex) {
			reportError(where, ex);
		}
	}

	private void tickSafe() {
		if (!registered) {
			ShipHolder.of(getWorld()).add(this);
			registered = true;
		}
		prevShipYaw = shipYaw;
		prevHatchProgress = hatchProgress;
		for (int i = 0; i < 4; i++) prevDisplayFill[i] = displayFill[i];
		double ox = getX(), oy = getY(), oz = getZ(), oyaw = shipYaw;

		if (getWorld().isClient) clientTick();
		else serverTick();

		float target = hatchesClosed() ? 1f : 0f;
		hatchProgress = approach(hatchProgress, target, 0.01f);

		carryEntities(ox, oy, oz, oyaw);
	}

	private void clientTick() {
		prevLiftClient = liftClient;
		float lt = dataTracker.get(LIFT);
		liftClient += (lt - liftClient) * 0.5f;
		if (Math.abs(lt - liftClient) < 0.002f) liftClient = lt;
		if (lerpSteps > 0) {
			double nx = getX() + (lerpX - getX()) / lerpSteps;
			double ny = getY() + (lerpY - getY()) / lerpSteps;
			double nz = getZ() + (lerpZ - getZ()) / lerpSteps;
			lerpSteps--;
			if (yawSteps > 0) {
				shipYaw += MathHelper.wrapDegrees(Math.toDegrees(lerpYaw - shipYaw)) * Math.PI / 180.0 / yawSteps;
				yawSteps--;
			}
			setPosition(nx, ny, nz);
		} else if (yawSteps > 0) {
			shipYaw += MathHelper.wrapDegrees(Math.toDegrees(lerpYaw - shipYaw)) * Math.PI / 180.0 / yawSteps;
			yawSteps--;
			setPosition(getX(), getY(), getZ());
		}
		clientEffects();
	}

	private void serverTick() {
		World world = getWorld();
		if (hornCooldown > 0) hornCooldown--;
		if (bumpCooldown > 0) bumpCooldown--;

		if (!testMode && (getHelmsman() == null || age - lastControlTick > 40)) {
			rudderInput = 0;
			thrusterInput = 0;
		}

		double load = load();
		double target = STEP_SPEED[throttle + 2] * (1.0 - 0.2 * load);
		boolean speedingUp = Math.abs(target) > Math.abs(speed) && (speed == 0 || Math.signum(target) == Math.signum(speed));
		double acc = (speedingUp ? 0.0018 : 0.0028) * (1.0 - 0.3 * load);
		speed = approach(speed, target, acc);
		if (Math.abs(speed) < 1e-4 && target == 0) speed = 0;

		// stuurmachine: het roer draait langzaam (ongeveer 5 s van midscheeps naar hard over)
		rudder = approach(rudder, rudderInput, 0.010);
		// schroefwater langs het roer: ook bij stilstand draait het schip een beetje
		double wash = throttle > 0 ? 0.3 : (throttle < 0 ? -0.15 : 0.15);
		double targetTurn = rudder * MAX_TURN * (speed / V_FULL + wash);
		// massatraagheid: het schip komt langzaam op gang in de bocht en draait na (tegenroer nodig)
		double yawResp = 0.02 * (1.0 - 0.35 * load);
		yawRate += (targetTurn - yawRate) * yawResp;
		// snelheidsverlies in de bocht
		speed *= 1.0 - Math.min(0.004, Math.abs(yawRate) * 0.15);
		double thrusterTarget = thrusterInput * THRUSTER_TURN * (1.0 - 0.5 * Math.min(1.0, Math.abs(speed) / 0.15));
		thrusterRate += (thrusterTarget - thrusterRate) * 0.03;
		if (Math.abs(yawRate) < 1e-6) yawRate = 0;
		if (Math.abs(thrusterRate) < 1e-6) thrusterRate = 0;

		double nyaw = shipYaw, nx = getX(), nz = getZ();
		double[] p = rotateAbout(nx, nz, nyaw, PIVOT_RUDDER, yawRate);
		nx = p[0];
		nz = p[1];
		nyaw += yawRate;
		p = rotateAbout(nx, nz, nyaw, PIVOT_THRUSTER, thrusterRate);
		nx = p[0];
		nz = p[1];
		nyaw += thrusterRate;
		// drift: de vaarrichting volgt de koers met vertraging, het schip glijdt wat uit in de bocht
		if (Double.isNaN(courseYaw)) courseYaw = shipYaw;
		double slip = MathHelper.wrapDegrees(Math.toDegrees(nyaw - courseYaw));
		slip = MathHelper.clamp(slip, -20, 20);
		double courseNew = nyaw - Math.toRadians(slip) * (1.0 - 0.07);
		nx += speed * Math.cos(courseNew);
		nz += speed * Math.sin(courseNew);

		double off = getY() - waterLayerY;
		double targetOff = LOADED_OFFSET + EMPTY_RISE * (1.0 - load);
		double ny = waterLayerY + approach(off, targetOff, 0.008);

		boolean moved = nx != getX() || nz != getZ() || nyaw != shipYaw;
		boolean vertical = ny != getY();
		boolean groundedNow = false;
		if (moved || vertical) {
			if (hullBlocked(nx, ny, nz, nyaw)) {
				if (moved && !hullBlocked(getX(), ny, getZ(), shipYaw)) {
					bump();
					nx = getX();
					nz = getZ();
					nyaw = shipYaw;
				} else if (!moved) {
					ny = getY();
					groundedNow = ny < waterLayerY + targetOff;
				} else {
					bump();
					nx = getX();
					nz = getZ();
					nyaw = shipYaw;
					ny = getY();
				}
			}
		}
		aground = groundedNow;

		courseYaw = (nx == getX() && nz == getZ() && nyaw == shipYaw) ? nyaw : courseNew;
		shipYaw = Math.toRadians(MathHelper.wrapDegrees(Math.toDegrees(nyaw)));
		if (Math.abs(shipYaw - nyaw) > 1) {
			// gewikkeld: niets aan de hand, alleen het getal is genormaliseerd
		}
		setPosition(nx, ny, nz);
		setYaw((float) Math.toDegrees(shipYaw) - 90f);

		dataTracker.set(YAW, (float) shipYaw);
		dataTracker.set(SPEED, (float) speed);
		dataTracker.set(RUDDER, (float) rudder);
		dataTracker.set(THROTTLE, throttle);
		dataTracker.set(THRUSTER, thrusterInput);
		dataTracker.set(AGROUND, aground);
		if (template.liftRegion != null) {
			double before = liftCur;
			liftCur = approach(liftCur, liftTarget, 0.025);
			dataTracker.set(LIFT, (float) liftCur);
			if (before != liftCur && age % 10 == 0) {
				Vec3d hp = toWorld(template.liftColumn[0] + 1, template.liftRegion[1], template.liftColumn[1] + 1);
				getWorld().playSound(null, hp.x, hp.y, hp.z, SoundEvents.BLOCK_PISTON_EXTEND, SoundCategory.NEUTRAL, 0.25f, 0.4f);
			}
		}
		dataTracker.set(WATER_Y, waterLayerY + 0.889f);

		// Water verplaatsen kost de spelers veel rekenwerk (elk gewijzigd blok laat de wereld opnieuw opbouwen).
		// Daarom pas bijwerken als het schip een merkbaar stuk is verschoven, of meteen als het stil komt te liggen.
		boolean still = !moved && !vertical;
		double shift = Double.isNaN(dispX) ? 99 : Math.max(Math.hypot(dispX - getX(), dispZ - getZ()), Math.abs(dispYaw - shipYaw) * 50);
		boolean fast = Math.abs(speed) > 0.12;
		boolean due = Double.isNaN(dispX) || shift > (fast ? 1.8 : 0.9) || Math.abs(dispY - getY()) > 0.3
				|| (still && (shift > 0.01 || Math.abs(dispY - getY()) > 0.01)) || age % 200 == 0;
		if (due && age - lastDisplaceTick >= (fast ? 8 : 3)) {
			updateDisplacement();
			lastDisplaceTick = age;
		}
	}

	private void bump() {
		// zonder roerganger zet de machine zich bij een aanvaring zelf op stop
		if (getHelmsman() == null && !testMode) throttle = 0;
		if (bumpCooldown == 0 && Math.abs(speed) > 0.02) {
			Vec3d bow = toWorld(speed > 0 ? 69 : 1, 0.5, ShipTemplate.CZ);
			getWorld().playSound(null, bow.x, bow.y, bow.z, SoundEvents.ENTITY_IRON_GOLEM_DAMAGE, SoundCategory.NEUTRAL, 1.5f, 0.5f);
			bumpCooldown = 20;
		}
		speed = -speed * 0.15;
		yawRate = 0;
		thrusterRate = 0;
	}

	private static double[] rotateAbout(double x, double z, double yaw, double pivotLocal, double d) {
		if (d == 0) return new double[]{x, z};
		double px = x + pivotLocal * Math.cos(yaw), pz = z + pivotLocal * Math.sin(yaw);
		double ny = yaw + d;
		return new double[]{px - pivotLocal * Math.cos(ny), pz - pivotLocal * Math.sin(ny)};
	}

	private static double approach(double v, double t, double step) {
		if (v < t) return Math.min(t, v + step);
		if (v > t) return Math.max(t, v - step);
		return v;
	}

	private static float approach(float v, float t, float step) {
		if (v < t) return Math.min(t, v + step);
		if (v > t) return Math.max(t, v - step);
		return v;
	}

	// =====================================================================
	// Meenemen van spelers en dieren die aan boord staan
	// =====================================================================

	private void carryEntities(double ox, double oy, double oz, double oyaw) {
		double lNow = liftOffset(), dl = lNow - carriedLift;
		carriedLift = lNow;
		if (ox == getX() && oy == getY() && oz == getZ() && oyaw == shipYaw && dl == 0) return;
		World world = getWorld();
		List<Entity> list = new ArrayList<>();
		if (world.isClient) {
			for (PlayerEntity p : world.getPlayers()) {
				if (p.isMainPlayer() && !p.hasVehicle() && !p.isSpectator()) list.add(p);
			}
		} else {
			list.addAll(world.getOtherEntities(this, getBoundingBox().expand(2, 4, 2),
					e -> !(e instanceof ShipEntity) && !(e instanceof PlayerEntity) && !e.hasVehicle()
							&& !(e instanceof net.minecraft.entity.ItemEntity && e.isTouchingWater())));
		}
		float dyaw = (float) Math.toDegrees(MathHelper.wrapDegrees(Math.toDegrees(shipYaw - oyaw)) * Math.PI / 180.0);
		for (Entity e : list) {
			Vec3d l = toLocal(ox, oy, oz, oyaw, e.getX(), e.getY(), e.getZ());
			if (!insideCarryVolume(l)) continue;
			if (dl != 0 && template.inLiftXZ(l.x, l.z) && l.y >= template.liftRegion[1] + (lNow - dl) - 0.6) l = l.add(0, dl, 0);
			Vec3d w = toWorld(getX(), getY(), getZ(), shipYaw, l.x, l.y, l.z);
			e.setPosition(w.x, w.y, w.z);
			if (dyaw != 0) {
				e.setYaw(e.getYaw() + dyaw);
				if (e instanceof LivingEntity le) {
					le.setHeadYaw(le.getHeadYaw() + dyaw);
					le.setBodyYaw(le.getBodyYaw() + dyaw);
				}
			}
		}
	}

	private boolean insideCarryVolume(Vec3d l) {
		int xi = MathHelper.clamp(MathHelper.floor(l.x), 0, LENGTH - 1);
		if (l.y < template.hullBottom[xi] - 0.5 || l.y > 24) return false;
		return template.insideOutline(l.x, l.z, 1.2);
	}

	/** Staat dit punt (wereld) binnen de romp of vlak ernaast? Voor de server-controles bij spelers. */
	public boolean isNear(Entity e, double margin) {
		if (!getBoundingBox().expand(margin + 2).contains(e.getPos())) return false;
		Vec3d l = toLocal(e.getPos());
		return l.y > -8 && l.y < 18 && template.insideOutline(l.x, l.z, margin);
	}

	public static boolean isNearAnyShip(Entity e) {
		for (ShipEntity s : ShipHolder.of(e.getWorld())) if (s.isNear(e, 3)) return true;
		return false;
	}

	// =====================================================================
	// Passagiers (roerganger)
	// =====================================================================

	@Override
	protected boolean canAddPassenger(Entity passenger) {
		return getPassengerList().size() < 2 && passenger instanceof PlayerEntity;
	}

	@Override
	protected void removePassenger(Entity passenger) {
		super.removePassenger(passenger);
		if (!getWorld().isClient) {
			if (throttle != 0 && getPassengerList().isEmpty() && passenger instanceof PlayerEntity pl)
				pl.sendMessage(Text.literal("The ship keeps sailing. Set the telegraph to Stop at the helm to halt it.").formatted(Formatting.GOLD), true);
			if (dataTracker.get(SEAT_AFT) == passenger.getId()) dataTracker.set(SEAT_AFT, -1);
			if (dataTracker.get(SEAT_BOW) == passenger.getId()) dataTracker.set(SEAT_BOW, -1);
		}
	}

	public boolean isAtBowHelm(Entity e) {
		return dataTracker.get(SEAT_BOW) == e.getId();
	}

	@Nullable
	public PlayerEntity getHelmsman() {
		for (Entity e : getPassengerList()) if (e instanceof PlayerEntity p) return p;
		return null;
	}

	@Override
	protected void updatePassengerPosition(Entity passenger, PositionUpdater positionUpdater) {
		if (!hasPassenger(passenger)) return;
		double[] seat = isAtBowHelm(passenger) ? template.seatBow : template.seatAft;
		Vec3d w = toWorld(seat[0], seat[1] + liftAt(seat[0], seat[1], seat[2]), seat[2]);
		positionUpdater.accept(passenger, w.x, w.y, w.z);
		float dyaw = (float) Math.toDegrees(MathHelper.wrapDegrees(Math.toDegrees(shipYaw - prevShipYaw)) * Math.PI / 180.0);
		if (dyaw != 0) {
			passenger.setYaw(passenger.getYaw() + dyaw);
			if (passenger instanceof LivingEntity le) {
				le.setHeadYaw(le.getHeadYaw() + dyaw);
				le.setBodyYaw(le.getBodyYaw() + dyaw);
			}
		}
	}

	@Override
	public Vec3d updatePassengerForDismount(LivingEntity passenger) {
		boolean bow = isAtBowHelm(passenger);
		return bow ? toWorld(61.5, 2.0, 4.5) : toWorld(13.5, 5.5 + liftOffset(), 3.5);
	}

	@Override
	public LivingEntity getControllingPassenger() {
		return null;
	}

	// =====================================================================
	// Bediening
	// =====================================================================

	public void handleControl(ServerPlayerEntity player, ShipPayloads.Control c) {
		if (!hasPassenger(player)) return;
		lastControlTick = age;
		throttle = MathHelper.clamp(c.throttle(), -2, 3);
		if (c.toggleEngine()) {
			boolean on = !engineOn();
			dataTracker.set(ENGINE, on);
			Vec3d ep = getPos();
			getWorld().playSound(null, ep.x, ep.y, ep.z, on ? SoundEvents.BLOCK_PISTON_EXTEND : SoundEvents.BLOCK_PISTON_CONTRACT, SoundCategory.NEUTRAL, 1.2f, 0.4f);
			player.sendMessage(Text.literal(on ? "Engine started" : "Engine stopped").formatted(Formatting.GRAY), true);
		}
		if (!engineOn()) throttle = 0;
		rudderInput = MathHelper.clamp(c.rudder(), -1, 1);
		thrusterInput = MathHelper.clamp(c.thruster(), -1, 1);
		if (template.liftRegion != null && c.lift() != 0)
			liftTarget = MathHelper.clamp(liftTarget + Math.signum(c.lift()) * 0.06, 0, template.liftMax);
		if (c.toggleLights()) {
			setLights(!lightsOn());
			Vec3d lp = getPos();
			getWorld().playSound(null, lp.x, lp.y, lp.z, SoundEvents.BLOCK_LEVER_CLICK, SoundCategory.BLOCKS, 0.6f, lightsOn() ? 0.6f : 0.5f);
			player.sendMessage(Text.literal(lightsOn() ? "Lights on" : "Lights off").formatted(Formatting.GRAY), true);
		}
		if (c.horn() && hornCooldown == 0) {
			Vec3d h = toWorld(13.5, 9.5 + liftOffset(), 4.5);
			getWorld().playSound(null, h.x, h.y, h.z, SoundEvents.EVENT_RAID_HORN, SoundCategory.NEUTRAL, 8.0f, 0.75f);
			hornCooldown = 60;
		}
		if (c.toggleHatches()) {
			if (hatchProgress > 0.001f && hatchProgress < 0.999f) return;
			boolean closed = !hatchesClosed();
			if (closed && containersStickOut()) {
				player.sendMessage(Text.literal("Containers stick out above the hold: the hatches cannot close").formatted(Formatting.RED), true);
				return;
			}
			dataTracker.set(HATCHES_CLOSED, closed);
			Vec3d h = toWorld(52, 4, 4.5);
			getWorld().playSound(null, h.x, h.y, h.z, SoundEvents.BLOCK_PISTON_EXTEND, SoundCategory.NEUTRAL, 1.5f, 0.5f);
			player.sendMessage(Text.literal(closed ? "Hatches closing" : "Hatches opening").formatted(Formatting.GRAY), true);
		}
	}

	/** Steken er containers boven het ruim uit (meer dan er onder de luiken passen)? */
	public boolean containersStickOut() {
		if (COVERS == 0) return false;
		int fit = MathHelper.clamp((int) ((CV_Y - template.holdFloorY - 0.05) / 2.6), 0, 3);
		for (int h = 0; h < template.holds.length; h++) {
			float f = fill(h);
			if (f < 0.002f) continue;
			float share = material(h) > 0 ? containerShare(h) : 1f;
			if (f * share > fit / 3f + 0.005f) return true;
		}
		return false;
	}

	public void testControl(int throttle, int rudder, int thruster) {
		dataTracker.set(ENGINE, true);
		this.testMode = true;
		this.throttle = throttle;
		this.rudderInput = rudder;
		this.thrusterInput = thruster;
	}

	public void testSetHatches(boolean closed) {
		dataTracker.set(HATCHES_CLOSED, closed);
	}

	public SimpleInventory hold(int i) {
		return holds[i];
	}

	public int displacedCount() {
		return displaced.size();
	}

	public double currentSpeed() {
		return speed;
	}

	public enum Action { NONE, HELM_AFT, HELM_BOW, HOLD, TOGGLE, BED, STORAGE }

	/** Working chests and cupboards on board (every chest and barrel in the ship): 54 slots each. */
	private final java.util.Map<Long, net.minecraft.inventory.SimpleInventory> lockers = new java.util.HashMap<>();

	/** Both halves of a double chest share one inventory: the key is the left half. */
	private long lockerKey(int x, int y, int z) {
		BlockState s = structure.get(x, y, z);
		if (s.getBlock() instanceof net.minecraft.block.ChestBlock && s.get(net.minecraft.block.ChestBlock.CHEST_TYPE) == net.minecraft.block.enums.ChestType.RIGHT) {
			Direction d = net.minecraft.block.ChestBlock.getFacing(s);
			return BlockPos.asLong(x + d.getOffsetX(), y, z + d.getOffsetZ());
		}
		return BlockPos.asLong(x, y, z);
	}

	private net.minecraft.inventory.SimpleInventory locker(long key) {
		return lockers.computeIfAbsent(key, k -> new net.minecraft.inventory.SimpleInventory(54));
	}

	private void openLocker(ServerPlayerEntity player, int x, int y, int z, Vec3d w) {
		BlockState s = structure.get(x, y, z);
		net.minecraft.inventory.SimpleInventory inv = locker(lockerKey(x, y, z));
		Text title = Text.literal(s.getBlock() instanceof net.minecraft.block.ChestBlock ? "Ship's chest" : "Cupboard");
		player.openHandledScreen(new net.minecraft.screen.SimpleNamedScreenHandlerFactory(
				(id, pinv, p) -> net.minecraft.screen.GenericContainerScreenHandler.createGeneric9x6(id, pinv, inv), title));
		getWorld().playSound(null, w.x, w.y, w.z, s.getBlock() instanceof net.minecraft.block.ChestBlock ? SoundEvents.BLOCK_CHEST_OPEN : SoundEvents.BLOCK_BARREL_OPEN,
				SoundCategory.BLOCKS, 0.6f, 1f);
	}

	private static boolean inZone(int[] z, int x, int y, int zz) {
		return x >= z[0] && x <= z[3] && y >= z[1] && y <= z[4] && zz >= z[2] && zz <= z[5];
	}

	/** Welke ruim hoort bij dit punt (sjablooncoördinaten), of -1. */
	public int holdAt(Vec3d t) {
		if (t.z < template.holdZ0 - 1 || t.z > template.holdZ1 + 2 || t.y < -3.5 || t.y > 9.5) return -1;
		for (int i = 0; i < template.holds.length; i++) {
			int a = template.holds[i][0], b = template.holds[i][1];
			if (t.x >= a - 1 && t.x < b + 2) return i;
		}
		return -1;
	}

	public Action actionAt(Vec3d t) {
		int bx = MathHelper.floor(t.x), by = MathHelper.floor(t.y), bz = MathHelper.floor(t.z);
		if (inZone(template.helmAft, bx, by, bz)) return Action.HELM_AFT;
		if (inZone(template.helmBow, bx, by, bz)) return Action.HELM_BOW;
		BlockState s = structure.get(bx, by, bz);
		Block b = s.getBlock();
		if (b instanceof BedBlock) return Action.BED;
		if (b instanceof DoorBlock || (b instanceof TrapdoorBlock && !s.isOf(Blocks.IRON_TRAPDOOR)) || b instanceof LeverBlock)
			return Action.TOGGLE;
		if (b instanceof net.minecraft.block.ChestBlock || b instanceof net.minecraft.block.BarrelBlock) return Action.STORAGE;
		if (holdAt(t) >= 0) return Action.HOLD;
		return Action.NONE;
	}

	@Override
	public ActionResult interactAt(PlayerEntity player, Vec3d hitPos, Hand hand) {
		if (hand != Hand.MAIN_HAND) return ActionResult.PASS;
		Vec3d w = getPos().add(hitPos).add(player.getRotationVec(1f).multiply(0.05));
		Vec3d t = unlift(toLocal(w));
		Action a = actionAt(t);
		if (a == Action.NONE) return ActionResult.PASS;
		if (getWorld().isClient) {
			clientSender.accept(new ShipPayloads.Interact(getId(), (float) t.x, (float) t.y, (float) t.z, player.isSneaking()));
		}
		return ActionResult.SUCCESS;
	}

	@Override
	public ActionResult interact(PlayerEntity player, Hand hand) {
		return ActionResult.PASS;
	}

	public void handleInteract(ServerPlayerEntity player, Vec3d t, boolean sneaking) {
		Vec3d w = toWorld(t.x, t.y, t.z);
		if (player.getEyePos().squaredDistanceTo(w) > Math.pow(player.getBlockInteractionRange() + 3.0, 2)) return;
		Action a = actionAt(t);
		switch (a) {
			case HELM_AFT, HELM_BOW -> {
				if (sneaking && player.getMainHandStack().isEmpty() && a == Action.HELM_AFT) {
					tryPickUp(player);
					return;
				}
				TrackedData<Integer> seat = a == Action.HELM_AFT ? SEAT_AFT : SEAT_BOW;
				if (hasPassenger(player)) return;
				if (dataTracker.get(seat) != -1) {
					player.sendMessage(Text.literal("This helm is taken").formatted(Formatting.RED), true);
					return;
				}
				dataTracker.set(seat, player.getId());
				if (!player.startRiding(this, true)) {
					dataTracker.set(seat, -1);
				} else {
					player.sendMessage(Text.literal(a == Action.HELM_AFT ? "You are at the helm in the wheelhouse" : "You are at the bow steering position").formatted(Formatting.AQUA), true);
				}
			}
			case BED -> useBed(player, MathHelper.floor(t.x), MathHelper.floor(t.y), MathHelper.floor(t.z));
			case TOGGLE -> toggleBlock(MathHelper.floor(t.x), MathHelper.floor(t.y), MathHelper.floor(t.z), w);
			case STORAGE -> openLocker(player, MathHelper.floor(t.x), MathHelper.floor(t.y), MathHelper.floor(t.z), w);
			case HOLD -> {
				int h = holdAt(t);
				if (h >= 0) openHold(player, h);
			}
			default -> {}
		}
	}

	private void toggleBlock(int x, int y, int z, Vec3d w) {
		BlockState s = structure.get(x, y, z);
		List<long[]> changed = new ArrayList<>();
		SoundEvent sound;
		if (s.getBlock() instanceof DoorBlock) {
			boolean open = !s.get(Properties.OPEN);
			int oy = s.get(Properties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.LOWER ? y + 1 : y - 1;
			structure.set(x, y, z, s.with(Properties.OPEN, open));
			BlockState o = structure.get(x, oy, z);
			if (o.getBlock() instanceof DoorBlock) structure.set(x, oy, z, o.with(Properties.OPEN, open));
			changed.add(new long[]{x, y, z});
			changed.add(new long[]{x, oy, z});
			sound = open ? SoundEvents.BLOCK_WOODEN_DOOR_OPEN : SoundEvents.BLOCK_WOODEN_DOOR_CLOSE;
		} else if (s.getBlock() instanceof TrapdoorBlock) {
			boolean open = !s.get(Properties.OPEN);
			structure.set(x, y, z, s.with(Properties.OPEN, open));
			changed.add(new long[]{x, y, z});
			sound = open ? SoundEvents.BLOCK_WOODEN_TRAPDOOR_OPEN : SoundEvents.BLOCK_WOODEN_TRAPDOOR_CLOSE;
		} else if (s.getBlock() instanceof LeverBlock) {
			structure.set(x, y, z, s.cycle(Properties.POWERED));
			changed.add(new long[]{x, y, z});
			sound = SoundEvents.BLOCK_LEVER_CLICK;
		} else {
			return;
		}
		getWorld().playSound(null, w.x, w.y, w.z, sound, SoundCategory.BLOCKS, 1f, 1f);
		long[] pos = new long[changed.size()];
		int[] st = new int[changed.size()];
		for (int i = 0; i < pos.length; i++) {
			long[] c = changed.get(i);
			pos[i] = BlockPos.asLong((int) c[0], (int) c[1], (int) c[2]);
			st[i] = Block.getRawIdFromState(structure.get((int) c[0], (int) c[1], (int) c[2]));
		}
		ShipPayloads.Blocks payload = new ShipPayloads.Blocks(getId(), pos, st);
		for (ServerPlayerEntity p : PlayerLookup.tracking(this)) ServerPlayNetworking.send(p, payload);
	}

	/** Client: blokwijzigingen toepassen. */
	public void applyBlockChanges(long[] positions, int[] states) {
		for (int i = 0; i < positions.length; i++) {
			BlockPos p = BlockPos.fromLong(positions[i]);
			structure.set(p.getX(), p.getY(), p.getZ(), Block.getStateFromRawId(states[i]));
		}
	}

	@Override
	public void onStartedTrackingBy(ServerPlayerEntity player) {
		super.onStartedTrackingBy(player);
		if (structure.changes.isEmpty()) return;
		long[] pos = new long[structure.changes.size()];
		int[] st = new int[pos.length];
		int i = 0;
		for (Long2ObjectMap.Entry<BlockState> e : structure.changes.long2ObjectEntrySet()) {
			pos[i] = e.getLongKey();
			st[i] = Block.getRawIdFromState(e.getValue());
			i++;
		}
		ServerPlayNetworking.send(player, new ShipPayloads.Blocks(getId(), pos, st));
	}

	// =====================================================================
	// Bedden: echt slapen aan boord
	// =====================================================================

	private List<int[]> bedHeads;

	/** Alle hoofdeinden van bedden in het schip (sjablooncoördinaten). */
	public List<int[]> bedHeads() {
		if (bedHeads == null) {
			List<int[]> list = new ArrayList<>();
			for (int x = ShipTemplate.MIN_X; x < ShipTemplate.MIN_X + ShipTemplate.SIZE_X; x++)
				for (int y = ShipTemplate.MIN_Y; y < ShipTemplate.MIN_Y + ShipTemplate.SIZE_Y; y++)
					for (int z = ShipTemplate.MIN_Z; z < ShipTemplate.MIN_Z + ShipTemplate.SIZE_Z; z++) {
						BlockState s = structure.get(x, y, z);
						if (s.getBlock() instanceof BedBlock && s.get(BedBlock.PART) == BedPart.HEAD) list.add(new int[]{x, y, z});
					}
			bedHeads = list;
		}
		return bedHeads;
	}

	@Nullable
	private int[] bedHead(int x, int y, int z) {
		BlockState s = structure.get(x, y, z);
		if (!(s.getBlock() instanceof BedBlock)) return null;
		if (s.get(BedBlock.PART) == BedPart.HEAD) return new int[]{x, y, z};
		Direction f = s.get(BedBlock.FACING);
		return new int[]{x + f.getOffsetX(), y, z + f.getOffsetZ()};
	}

	/** Waar het hoofd van een slapende speler ligt (wereld). */
	public Vec3d bedSpot(int[] h) {
		return toWorld(h[0] + 0.5, h[1] + 0.6875, h[2] + 0.5);
	}

	/** Kijkrichting van het bed in de wereld (van voeteneind naar hoofdeinde). */
	public Direction bedDirection(int[] h) {
		Direction f = structure.get(h[0], h[1], h[2]).get(BedBlock.FACING);
		Vec3d d = dirToWorld(new Vec3d(f.getOffsetX(), 0, f.getOffsetZ()));
		return Direction.getFacing(d.x, 0, d.z);
	}

	/** Een vrije plek naast het bed om op op te staan (wereld). */
	public Vec3d bedWakeSpot(int[] h) {
		Direction f = structure.get(h[0], h[1], h[2]).get(BedBlock.FACING);
		int fx = h[0] - f.getOffsetX(), fz = h[2] - f.getOffsetZ();
		BlockPos.Mutable m = new BlockPos.Mutable();
		int[] best = null;
		double bestD = Double.MAX_VALUE;
		for (int dy = 0; dy <= 1; dy++)
			for (int dx = -2; dx <= 2; dx++)
				for (int dz = -2; dz <= 2; dz++) {
					int x = fx + dx, z = fz + dz, y = h[1] + dy;
					if (!free(m, x, y, z) || !free(m, x, y + 1, z)) continue;
					BlockState below = structure.get(x, y - 1, z);
					if (below.isAir() || below.getBlock() instanceof BedBlock) continue;
					m.set(x, y - 1, z);
					if (below.getCollisionShape(structure, m).isEmpty()) continue;
					// room around it too, so the player (0.6 wide) is not pushed into a wall when the ship lies at an angle
					int open = 0;
					for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) if (free(m, x + d[0], y, z + d[1]) && free(m, x + d[0], y + 1, z + d[1])) open++;
					double dd = dx * dx + dz * dz + dy * 3 - open * 0.6;
					if (dd < bestD) {
						bestD = dd;
						best = new int[]{x, y, z};
					}
				}
		if (best != null) {
			double top = structure.get(best[0], best[1] - 1, best[2]).getCollisionShape(structure, m.set(best[0], best[1] - 1, best[2])).getMax(Direction.Axis.Y);
			return toWorld(best[0] + 0.5, best[1] - 1 + top + 0.02, best[2] + 0.5);
		}
		return toWorld(h[0] + 0.5, h[1] + 0.6, h[2] + 0.5);
	}

	/** Who was asleep in a ship bed last tick: when they wake up they are put beside the bed, not inside it. */
	private static final java.util.Map<java.util.UUID, BedRef> SLEEPERS = new java.util.HashMap<>();

	private boolean free(BlockPos.Mutable m, int x, int y, int z) {
		BlockState s = structure.get(x, y, z);
		m.set(x, y, z);
		return !(s.getBlock() instanceof BedBlock) && s.getCollisionShape(structure, m).isEmpty();
	}

	public record BedRef(ShipEntity ship, int[] head) {}

	/** Hoort deze slaappositie (wereldblok) bij een bed op een schip? */
	@Nullable
	public static BedRef findBed(World world, BlockPos sleepPos) {
		BedRef best = null;
		double bestD = 3.5 * 3.5;
		Vec3d c = new Vec3d(sleepPos.getX() + 0.5, sleepPos.getY() + 0.6875, sleepPos.getZ() + 0.5);
		for (ShipEntity s : ShipHolder.of(world)) {
			if (!s.getBoundingBox().expand(3).contains(c)) continue;
			for (int[] h : s.bedHeads()) {
				Vec3d p = s.bedSpot(h);
				if (Math.abs(p.y - c.y) > 2.5) continue;
				double d = (p.x - c.x) * (p.x - c.x) + (p.z - c.z) * (p.z - c.z);
				if (d < bestD) {
					bestD = d;
					best = new BedRef(s, h);
				}
			}
		}
		return best;
	}

	/** Slapers in een scheepsbed: slaappositie en lichaam bewegen mee met het schip, zodat je niet uit bed valt. */
	public static void keepSleepers(net.minecraft.server.MinecraftServer server) {
		for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
			try {
				if (!p.isSleeping()) {
					BedRef was = SLEEPERS.remove(p.getUuid());
					if (was != null && !was.ship().isRemoved()) {
						// just woke up: stand up next to the bed, on the floor of the cabin
						Vec3d w = was.ship().bedWakeSpot(was.head());
						p.networkHandler.requestTeleport(w.x, w.y, w.z, p.getYaw(), p.getPitch());
					}
					continue;
				}
				var sp = p.getSleepingPosition();
				if (sp.isEmpty() || p.getWorld().getBlockState(sp.get()).getBlock() instanceof BedBlock) continue;
				BedRef ref = findBed(p.getWorld(), sp.get());
				if (ref == null) ref = findBed(p.getWorld(), BlockPos.ofFloored(p.getX(), p.getY() - 0.5, p.getZ()));
				if (ref == null) continue;
				SLEEPERS.put(p.getUuid(), ref);
				Vec3d spot = ref.ship().bedSpot(ref.head());
				BlockPos bp = BlockPos.ofFloored(spot.x, spot.y - 0.6875 + 0.01, spot.z);
				if (!bp.equals(sp.get())) p.setSleepingPosition(bp);
				double d = p.getPos().squaredDistanceTo(spot);
				if (d > 1e-4) p.setPosition(spot.x, spot.y, spot.z);
			} catch (RuntimeException | StackOverflowError ex) {
				LOGGER_SAFE.warn("ship bed keeper", ex);
			}
		}
	}

	private static final org.slf4j.Logger LOGGER_SAFE = org.slf4j.LoggerFactory.getLogger("ship-beds");

	private void useBed(ServerPlayerEntity player, int x, int y, int z) {
		int[] h = bedHead(x, y, z);
		if (h == null) return;
		World w = getWorld();
		// slaapplaats onthouden: na doodgaan word je weer aan boord wakker
		NbtCompound tag = new NbtCompound();
		tag.putUuid("Ship", getUuid());
		tag.putIntArray("Bed", h);
		Vec3d spot = bedSpot(h);
		tag.putDouble("X", spot.x);
		tag.putDouble("Y", spot.y);
		tag.putDouble("Z", spot.z);
		tag.putString("Dim", w.getRegistryKey().getValue().toString());
		NbtCompound old = player.getAttached(Kempenaar.SHIP_BED);
		player.setAttached(Kempenaar.SHIP_BED, tag);
		if (old == null || !old.getUuid("Ship").equals(getUuid()))
			player.sendMessage(Text.literal("Your bed is now on board the " + template.name).formatted(Formatting.AQUA), false);
		if (player.isSleeping()) return;
		if (w.isDay() && !w.isThundering()) {
			player.sendMessage(Text.translatable("block.minecraft.bed.no_sleep"), true);
			return;
		}
		if (player.hasVehicle()) player.stopRiding();
		BlockPos bp = BlockPos.ofFloored(spot.x, spot.y - 0.6875 + 0.01, spot.z);
		player.sleep(bp);
		((nl.kempenaar.mixin.PlayerEntityAccessor) player).kempenaar$setSleepTimer(0);
		player.setPosition(spot.x, spot.y, spot.z);
		player.networkHandler.requestTeleport(spot.x, spot.y, spot.z, player.getYaw(), player.getPitch());
		if (w instanceof ServerWorld sw) sw.updateSleepingPlayers();
	}

	private void openHold(ServerPlayerEntity player, int h) {
		CargoInventory inv = holds[h];
		int pct = Math.round(fill(h) * 100);
		player.openHandledScreen(new SimpleNamedScreenHandlerFactory(
				(syncId, playerInv, p) -> GenericContainerScreenHandler.createGeneric9x6(syncId, playerInv, inv),
				Text.literal("Hold " + (h + 1) + " (" + pct + "% full)")));
	}

	private void tryPickUp(ServerPlayerEntity player) {
		for (CargoInventory inv : holds) {
			if (!inv.isEmpty()) {
				player.sendMessage(Text.literal("Empty all cargo holds before picking up the ship").formatted(Formatting.RED), true);
				return;
			}
		}
		if (hasPassengers()) return;
		if (!player.getAbilities().creativeMode) player.giveItemStack(new ItemStack(Kempenaar.SHIP_ITEM));
		player.sendMessage(Text.literal("Ship picked up").formatted(Formatting.GRAY), true);
		discard();
	}

	/** Server: lading veranderd. */
	void onCargoChanged(int hold) {
		if (getWorld().isClient) return;
		dataTracker.set(FILL[hold], Cargo.fill(holds[hold]));
		dataTracker.set(MATERIAL[hold], Cargo.material(holds[hold]));
		dataTracker.set(BOXED[hold], Cargo.containerShare(holds[hold]));
	}

	// =====================================================================
	// Waterverplaatsing
	// =====================================================================

	private boolean isDisplaceable(World world, BlockPos pos, BlockState state) {
		if (!state.getFluidState().isIn(FluidTags.WATER)) return false;
		return state.isOf(Blocks.WATER) || state.getCollisionShape(world, pos).isEmpty();
	}

	private void updateDisplacement() {
		World world = getWorld();
		dispX = getX();
		dispY = getY();
		dispZ = getZ();
		dispYaw = shipYaw;
		LongOpenHashSet want = new LongOpenHashSet();
		Box bb = getBoundingBox();
		int x0 = MathHelper.floor(bb.minX), x1 = MathHelper.floor(bb.maxX);
		int z0 = MathHelper.floor(bb.minZ), z1 = MathHelper.floor(bb.maxZ);
		double c = Math.cos(shipYaw), s = Math.sin(shipYaw);
		for (int wx = x0; wx <= x1; wx++) {
			for (int wz = z0; wz <= z1; wz++) {
				double dx = wx + 0.5 - getX(), dz = wz + 0.5 - getZ();
				double tx = dx * c + dz * s + ShipTemplate.CX;
				double tz = -dx * s + dz * c + ShipTemplate.CZ;
				if (!template.insideOutline(tx, tz, 0)) continue;
				int xi = MathHelper.clamp(MathHelper.floor(tx), 0, LENGTH - 1);
				double bottom = getY() + template.hullBottom[xi];
				int yMin = MathHelper.floor(bottom - 0.5) + 1;
				for (int wy = yMin; wy <= waterLayerY; wy++) want.add(BlockPos.asLong(wx, wy, wz));
			}
		}
		BlockPos.Mutable m = new BlockPos.Mutable();
		BlockState marker = Kempenaar.DISPLACED_WATER.getDefaultState();
		int flags = Block.NOTIFY_LISTENERS | Block.FORCE_STATE | Block.SKIP_DROPS;
		LongIterator it = displaced.iterator();
		while (it.hasNext()) {
			long p = it.nextLong();
			if (want.contains(p)) continue;
			m.set(p);
			if (!world.isChunkLoaded(m.getX() >> 4, m.getZ() >> 4)) continue;
			{ BlockState back = plants.remove(m.asLong()); if (world.getBlockState(m).isOf(Kempenaar.DISPLACED_WATER)) world.setBlockState(m, back != null ? back : Blocks.WATER.getDefaultState(), flags); }
			it.remove();
		}
		LongIterator wi = want.iterator();
		while (wi.hasNext()) {
			long p = wi.nextLong();
			if (displaced.contains(p)) continue;
			m.set(p);
			if (!world.isChunkLoaded(m.getX() >> 4, m.getZ() >> 4)) continue;
			BlockState st = world.getBlockState(m);
			if (st.isOf(Kempenaar.DISPLACED_WATER)) {
				displaced.add(p);
			} else if (isDisplaceable(world, m, st)) {
				if (!st.isOf(Blocks.WATER)) plants.put(p, st);
				world.setBlockState(m, marker, flags);
				displaced.add(p);
			}
		}
	}

	private void restoreAllWater() {
		World world = getWorld();
		BlockPos.Mutable m = new BlockPos.Mutable();
		int flags = Block.NOTIFY_LISTENERS | Block.FORCE_STATE | Block.SKIP_DROPS;
		LongIterator it = displaced.iterator();
		while (it.hasNext()) {
			m.set(it.nextLong());
			{ BlockState back = plants.remove(m.asLong()); if (world.getBlockState(m).isOf(Kempenaar.DISPLACED_WATER)) world.setBlockState(m, back != null ? back : Blocks.WATER.getDefaultState(), flags); }
		}
		displaced.clear();
		plants.clear();
	}

	// =====================================================================
	// Botsen met de wal en andere schepen
	// =====================================================================

	public boolean hullBlocked(double sx, double sy, double sz, double yaw) {
		World world = getWorld();
		BlockPos.Mutable m = new BlockPos.Mutable();
		List<ShipEntity> others = ShipHolder.of(world);
		for (int i = 0; i < LENGTH; i++) {
			double tx = i + 0.5;
			double[] zs = {template.hullZ0[i] + 0.15, template.hullZ1[i] + 0.85};
			for (double tz : zs) {
				for (double ty = template.hullBottom[i] + 0.25; ty < 2.0; ty += 0.85) {
					if (pointBlocked(world, m, others, sx, sy, sz, yaw, tx, ty, tz)) return true;
				}
			}
			if (i % 3 == 0 && pointBlocked(world, m, others, sx, sy, sz, yaw, tx, template.hullBottom[i] + 0.2, ShipTemplate.CZ)) return true;
		}
		for (int tz = template.hullZ0[0]; tz <= template.hullZ1[0]; tz++) {
			for (double ty = template.hullBottom[0] + 0.25; ty < 2.0; ty += 0.85)
				if (pointBlocked(world, m, others, sx, sy, sz, yaw, 0.15, ty, tz + 0.5)) return true;
		}
		for (int tz = template.hullZ0[LENGTH - 1]; tz <= template.hullZ1[LENGTH - 1]; tz++) {
			for (double ty = template.hullBottom[LENGTH - 1] + 0.25; ty < 3.0; ty += 0.85)
				if (pointBlocked(world, m, others, sx, sy, sz, yaw, LENGTH - 0.15, ty, tz + 0.5)) return true;
		}
		return false;
	}

	private boolean pointBlocked(World world, BlockPos.Mutable m, List<ShipEntity> others, double sx, double sy, double sz, double yaw,
								 double tx, double ty, double tz) {
		Vec3d w = toWorld(sx, sy, sz, yaw, tx, ty, tz);
		m.set(MathHelper.floor(w.x), MathHelper.floor(w.y), MathHelper.floor(w.z));
		if (!world.isChunkLoaded(m.getX() >> 4, m.getZ() >> 4)) return true;
		BlockState st = world.getBlockState(m);
		if (!st.isAir() && !st.isOf(Kempenaar.DISPLACED_WATER)) {
			if (!(st.getFluidState().isIn(FluidTags.WATER) && st.getCollisionShape(world, m).isEmpty())) {
				if (!st.getCollisionShape(world, m).isEmpty()) return true;
			}
		}
		for (ShipEntity o : others) {
			if (o == this) continue;
			if (!o.getBoundingBox().contains(w)) continue;
			Vec3d l = o.toLocal(w);
			int xi = MathHelper.clamp(MathHelper.floor(l.x), 0, LENGTH - 1);
			if (o.template.insideOutline(l.x, l.z, 0) && l.y > o.template.hullBottom[xi] && l.y < 3) return true;
		}
		return false;
	}

	// =====================================================================
	// Botsing van spelers/dieren met het schip (aangeroepen vanuit de mixin)
	// =====================================================================

	/** Luikvakken in sjablooncoördinaten (alleen als ze stilstaan). */
	// ---- luiken: maten in sjablooncoördinaten ----
	public static final float CV_X0 = 19f, CV_LEN = 4.75f, CV_Y = 3.5f, CV_Z0 = 0.95f, CV_Z1 = 8.05f, CV_IN1 = 1.05f, CV_IN2 = 2.25f;
	/** Hoogte per luik op de stapel en dikte van een luik. */
	public static final float CV_STACK = 0.36f, CV_THICK = 0.34f;
	private static int[] coverGroupEnd;

	/** Het voorste luik van de groep (per ruim) waar luik i bij hoort: daar komt de stapel. */
	public static int coverGroupEnd(int i) {
		if (coverGroupEnd == null) {
			int[][] h = ShipTemplate.get().holds;
			int[] grp = new int[COVERS];
			for (int k = 0; k < COVERS; k++) {
				float c = CV_X0 + CV_LEN * (k + 0.5f);
				int g = h.length - 1;
				for (int j = 0; j < h.length; j++) if (c < h[j][1] + 1.5f) { g = j; break; }
				grp[k] = g;
			}
			int[] end = new int[COVERS];
			for (int k = COVERS - 1; k >= 0; k--) end[k] = (k == COVERS - 1 || grp[k + 1] != grp[k]) ? k : end[k + 1];
			coverGroupEnd = end;
		}
		return coverGroupEnd[i];
	}

	/** Waar luik i ligt als het ruim open is. */
	public static float stackX(int i) {
		return CV_X0 + CV_LEN * coverGroupEnd(i);
	}

	/** Luikvakken in sjablooncoördinaten (alleen als ze stilstaan). */
	public List<Box> coverBoxes() {
		List<Box> list = new ArrayList<>();
		if (hatchProgress >= 0.999f) {
			for (int i = 0; i < COVERS; i++) {
				double x0 = CV_X0 + CV_LEN * i;
				list.add(new Box(x0, CV_Y, CV_Z0, x0 + CV_LEN, CV_Y + 0.14, CV_Z1));
				list.add(new Box(x0, CV_Y + 0.14, CV_Z0 + CV_IN1, x0 + CV_LEN, CV_Y + CV_THICK, CV_Z1 - CV_IN1));
			}
		} else if (hatchProgress <= 0.001f) {
			int start = 0;
			for (int i = 0; i < COVERS; i++) {
				if (coverGroupEnd(i) != i) continue;
				double sx = stackX(i);
				list.add(new Box(sx, CV_Y, CV_Z0, sx + CV_LEN, CV_Y + CV_STACK * (i - start) + CV_THICK, CV_Z1));
				start = i + 1;
			}
		}
		return list;
	}

	private void gatherShapes(Entity e, Box localQuery, List<VoxelShape> out) {
		int x0 = MathHelper.floor(localQuery.minX) - 1, x1 = MathHelper.floor(localQuery.maxX) + 1;
		int y0 = MathHelper.floor(localQuery.minY) - 1, y1 = MathHelper.floor(localQuery.maxY) + 1;
		int z0 = MathHelper.floor(localQuery.minZ) - 1, z1 = MathHelper.floor(localQuery.maxZ) + 1;
		ShapeContext ctx = ShapeContext.of(e);
		BlockPos.Mutable m = new BlockPos.Mutable();
		for (int x = x0; x <= x1; x++)
			for (int y = y0; y <= y1; y++)
				for (int z = z0; z <= z1; z++) {
					BlockState s = structure.get(x, y, z);
					if (s.isAir()) continue;
					if (template.liftRegion != null && template.inLift(x, y, z)) continue;
					m.set(x, y, z);
					VoxelShape shape = s.getCollisionShape(structure, m, ctx);
					if (!shape.isEmpty()) out.add(shape.offset(x, y, z));
				}
		if (template.liftRegion != null) {
			double L = liftOffset();
			int ly0 = MathHelper.floor(localQuery.minY - L) - 1, ly1 = MathHelper.floor(localQuery.maxY - L) + 1;
			int[] r = template.liftRegion;
			for (int x = Math.max(x0, r[0]); x <= Math.min(x1, r[3]); x++)
				for (int y = Math.max(ly0, r[1]); y <= Math.min(ly1, r[4]); y++)
					for (int z = Math.max(z0, r[2]); z <= Math.min(z1, r[5]); z++) {
						BlockState s = structure.get(x, y, z);
						if (s.isAir()) continue;
						if (!template.inLift(x, y, z)) continue;   // columns kept out of the lift (ladders) do not rise with it
						m.set(x, y, z);
						VoxelShape shape = s.getCollisionShape(structure, m, ctx);
						if (!shape.isEmpty()) out.add(shape.offset(x, y + L, z));
					}
			if (L > 0.01) {
				int[] c = template.liftColumn;
				Box col = new Box(c[0], r[1], c[1], c[2] + 1, r[1] + L, c[3] + 1);
				if (col.intersects(localQuery)) out.add(VoxelShapes.cuboid(col));
			}
		}
		for (Box b : coverBoxes()) if (b.intersects(localQuery)) out.add(VoxelShapes.cuboid(b));
	}

	public Vec3d collide(Entity e, Vec3d mv) {
		Box wb = e.getBoundingBox();
		double cx = (wb.minX + wb.maxX) / 2, cz = (wb.minZ + wb.maxZ) / 2;
		Vec3d lc = toLocal(new Vec3d(cx, wb.minY, cz));
		double hw = (wb.maxX - wb.minX) / 2, h = wb.maxY - wb.minY;
		Box lb = new Box(lc.x - hw, lc.y, lc.z - hw, lc.x + hw, lc.y + h, lc.z + hw);
		Vec3d lm = dirToLocal(mv);
		double step = e.getStepHeight();
		List<VoxelShape> shapes = new ArrayList<>();
		gatherShapes(e, lb.stretch(lm).expand(0.1, step + 0.1, 0.1), shapes);
		if (shapes.isEmpty()) return mv;
		Vec3d lm0 = lm;
		// Staat iemand een klein stukje ín een vloer (na teleport, uitstappen of omhoogkomend schip)? Dan omhoog duwen.
		double lift = 0;
		Box feet = lb.contract(0.05, 0, 0.05);
		for (VoxelShape sh : shapes) {
			for (Box bx : sh.getBoundingBoxes()) {
				double d = bx.maxY - lb.minY;
				if (d > 1e-6 && d <= 0.55 && bx.intersects(feet)) lift = Math.max(lift, d);
			}
		}
		if (lift > 0 && lm.y < lift) lm = new Vec3d(lm.x, lift, lm.z);
		Vec3d res = EntityInvoker.kempenaar$adjust(lm, lb, shapes);
		boolean hColl = res.x != lm.x || res.z != lm.z;
		boolean ground = e.isOnGround() || (lm.y < 0 && res.y != lm.y);
		if (step > 0 && hColl && ground) {
			Vec3d s1 = EntityInvoker.kempenaar$adjust(new Vec3d(lm.x, step, lm.z), lb, shapes);
			Vec3d s2 = EntityInvoker.kempenaar$adjust(new Vec3d(0, step, 0), lb.stretch(lm.x, 0, lm.z), shapes);
			if (s2.y < step) {
				Vec3d s3 = EntityInvoker.kempenaar$adjust(new Vec3d(lm.x, 0, lm.z), lb.offset(s2), shapes).add(s2);
				if (s3.horizontalLengthSquared() > s1.horizontalLengthSquared()) s1 = s3;
			}
			if (s1.horizontalLengthSquared() > res.horizontalLengthSquared()) {
				res = s1.add(EntityInvoker.kempenaar$adjust(new Vec3d(0, -s1.y + lm.y, 0), lb.offset(s1), shapes));
			}
		}
		if (res.equals(lm0)) return mv;
		if (res.x == lm0.x && res.z == lm0.z) return new Vec3d(mv.x, res.y, mv.z);
		Vec3d wres = dirToWorld(res);
		return new Vec3d(wres.x, res.y, wres.z);
	}

	public static Vec3d collideAll(Entity e, Vec3d mv) {
		try {
			return collideAllSafe(e, mv);
		} catch (RuntimeException | StackOverflowError ex) {
			reportError("collision", ex);
			return mv;
		}
	}

	private static Vec3d collideAllSafe(Entity e, Vec3d mv) {
		if (e instanceof ShipEntity || e.noClip) return mv;
		List<ShipEntity> ships = ShipHolder.of(e.getWorld());
		if (ships.isEmpty()) return mv;
		Box q = e.getBoundingBox().stretch(mv).expand(1.0);
		for (ShipEntity s : ships) {
			if (e.getVehicle() == s) continue;
			if (!s.getBoundingBox().intersects(q)) continue;
			mv = s.collide(e, mv);
		}
		return mv;
	}

	/** Client: hoogte waar de regen in deze wereldkolom ophoudt, rekening houdend met schepen. */
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
		} catch (RuntimeException | StackOverflowError ex) {
			reportError("rain", ex);
		}
		return top;
	}

	public static int rainChecks;

	public static boolean isClimbing(Entity e) {
		try {
			return isClimbingSafe(e);
		} catch (RuntimeException | StackOverflowError ex) {
			reportError("ladder", ex);
			return false;
		}
	}

	private static boolean isClimbingSafe(Entity e) {
		for (ShipEntity s : ShipHolder.of(e.getWorld())) {
			if (!s.getBoundingBox().contains(e.getPos())) continue;
			Vec3d l = s.unlift(s.toLocal(e.getPos()));
			BlockState st = s.structure.get(MathHelper.floor(l.x), MathHelper.floor(l.y), MathHelper.floor(l.z));
			if (st.isIn(BlockTags.CLIMBABLE)) return true;
			// open luik boven een ladder telt ook als ladder (zoals in vanilla)
			if (st.getBlock() instanceof TrapdoorBlock && st.get(Properties.OPEN)
					&& s.structure.get(MathHelper.floor(l.x), MathHelper.floor(l.y) - 1, MathHelper.floor(l.z)).isIn(BlockTags.CLIMBABLE)) return true;
		}
		return false;
	}

	/** Straal door het schip; geeft het wereldpunt van de treffer of null. */
	@Nullable
	public Vec3d raycast(Vec3d startW, Vec3d endW, Entity e) {
		Vec3d a = toLocal(startW), b = toLocal(endW);
		Vec3d best;
		double bestD;
		if (template.liftRegion == null) {
			BlockHitResult r = structure.raycast(new RaycastContext(a, b, RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, e));
			best = r.getType() == HitResult.Type.MISS ? null : r.getPos();
		} else {
			double L = liftOffset();
			BlockHitResult r1 = baseView.raycast(new RaycastContext(a, b, RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, e));
			BlockHitResult r2 = liftView.raycast(new RaycastContext(a.add(0, -L, 0), b.add(0, -L, 0), RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, e));
			Vec3d p1 = r1.getType() == HitResult.Type.MISS ? null : r1.getPos();
			Vec3d p2 = r2.getType() == HitResult.Type.MISS ? null : r2.getPos().add(0, L, 0);
			best = p1 == null ? p2 : p2 == null ? p1 : (p1.squaredDistanceTo(a) <= p2.squaredDistanceTo(a) ? p1 : p2);
		}
		bestD = best == null ? Double.MAX_VALUE : best.squaredDistanceTo(a);
		for (Box box : coverBoxes()) {
			var h = box.raycast(a, b);
			if (h.isPresent() && h.get().squaredDistanceTo(a) < bestD) {
				best = h.get();
				bestD = best.squaredDistanceTo(a);
			}
		}
		if (best == null) return null;
		return toWorld(best.x, best.y, best.z);
	}

	// =====================================================================
	// Client-effecten
	// =====================================================================

	private void clientEffects() {
		World world = getWorld();
		float spd = syncedSpeed();
		int thr = syncedThrottle();
		// motortoerental loopt in een paar seconden op of af
		engineLoad += (engineTarget() - engineLoad) * (engineTarget() > engineLoad ? 0.02f : 0.035f);
		double wy = waterSurfaceY();
		var r = this.random;
		if (Math.abs(spd) > 0.03) {
			int n = Math.abs(spd) > 0.15 ? 2 : 1;
			for (int i = 0; i < n; i++) {
				for (double tz : new double[]{0.6, 8.4}) {
					Vec3d p = toWorld(spd > 0 ? 65 + r.nextDouble() * 4 : 1 + r.nextDouble() * 2, 0, tz);
					Vec3d side = dirToWorld(new Vec3d(0, 0, tz < 5 ? -0.12 : 0.12));
					world.addParticle(ParticleTypes.SPLASH, p.x, wy + 0.05, p.z, side.x, 0.1, side.z);
				}
			}
		}
		if (thr != 0 && (age & 1) == 0) {
			for (double tz : new double[]{3.0, 6.0}) {
				Vec3d p = toWorld(-0.5 - r.nextDouble() * 2, 0, tz + r.nextDouble() - 0.5);
				world.addParticle(ParticleTypes.BUBBLE, p.x, wy - 0.6, p.z, 0, 0.05, 0);
				if (r.nextInt(3) == 0) world.addParticle(ParticleTypes.SPLASH, p.x, wy + 0.05, p.z, 0, 0.05, 0);
			}
		}
		int smokeEvery = Math.max(2, 8 - Math.abs(thr) * 2);
		if (thr != 0 && age % smokeEvery == 0) {
			Vec3d c = toWorld(10.5, 9.1, 7.5);
			world.addParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, true, c.x, c.y, c.z, 0, 0.06, 0);
			if (Math.abs(thr) >= 2) world.addParticle(ParticleTypes.LARGE_SMOKE, c.x, c.y, c.z, 0, 0.08, 0);
		}
		// laad-/losanimatie: de zichtbare lading groeit langzaam mee met de echte lading
		for (int i = 0; i < template.holds.length; i++) {
			float target = fill(i);
			float before = displayFill[i];
			displayFill[i] = approach(displayFill[i], target, 0.006f);
			if (displayFill[i] > before + 1e-4) loadingEffects(i);
		}
	}

	private void loadingEffects(int hold) {
		World world = getWorld();
		int mat = material(hold);
		int a = template.holds[hold][0], b = template.holds[hold][1];
		var r = this.random;
		Vec3d center = toWorld((a + b + 1) / 2.0, 4, ShipTemplate.CZ);
		if (mat > 0) {
			BlockState st = Registries.BLOCK.get(mat - 1).getDefaultState();
			for (int k = 0; k < 4; k++) {
				Vec3d p = toWorld(a + 3 + r.nextDouble() * (b - a - 5), 6 + r.nextDouble() * 2, 2.5 + r.nextDouble() * 3);
				world.addParticle(new BlockStateParticleEffect(ParticleTypes.FALLING_DUST, st), p.x, p.y, p.z, 0, 0, 0);
				world.addParticle(new BlockStateParticleEffect(ParticleTypes.BLOCK, st), p.x, p.y - 2, p.z, 0, -0.5, 0);
			}
			if (age % 6 == 0)
				world.playSound(center.x, center.y, center.z, st.getSoundGroup().getPlaceSound(), SoundCategory.BLOCKS, 0.6f, 0.7f + r.nextFloat() * 0.2f, false);
		} else if (mat < 0) {
			float slots = displayFill[hold] * 8;
			float frac = slots - (float) Math.floor(slots);
			if (frac > 0.9f && age % 4 == 0)
				world.playSound(center.x, center.y, center.z, SoundEvents.BLOCK_CHAIN_PLACE, SoundCategory.BLOCKS, 1f, 0.6f, false);
		}
	}

	// =====================================================================
	// Opslaan en laden
	// =====================================================================

	@Override
	protected void readCustomDataFromNbt(NbtCompound nbt) {
		liftTarget = nbt.getDouble("LiftTarget");
		liftCur = nbt.getDouble("Lift");
		dataTracker.set(LIFT, (float) liftCur);
		dataTracker.set(LIGHTS, !nbt.contains("Lights") || nbt.getBoolean("Lights"));
		dataTracker.set(ENGINE, !nbt.contains("EngineOn") || nbt.getBoolean("EngineOn"));
		shipYaw = prevShipYaw = nbt.getDouble("ShipYaw");
		waterLayerY = nbt.getInt("WaterLayer");
		throttle = MathHelper.clamp(nbt.getInt("Throttle"), -2, 3);
		dataTracker.set(YAW, (float) shipYaw);
		dataTracker.set(WATER_Y, waterLayerY + 0.889f);
		dataTracker.set(HATCHES_CLOSED, nbt.getBoolean("HatchesClosed"));
		hatchProgress = prevHatchProgress = nbt.getBoolean("HatchesClosed") ? 1f : 0f;
		NbtList hl = nbt.getList("Holds", NbtElement.LIST_TYPE);
		for (int i = 0; i < 4 && i < hl.size(); i++) {
			holds[i].readNbtList(hl.getList(i), getRegistryManager());
		}
		for (int i = 0; i < 4; i++) {
			dataTracker.set(FILL[i], Cargo.fill(holds[i]));
			dataTracker.set(MATERIAL[i], Cargo.material(holds[i]));
			dataTracker.set(BOXED[i], Cargo.containerShare(holds[i]));
		}
		lockers.clear();
		NbtList lk = nbt.getList("Lockers", NbtElement.COMPOUND_TYPE);
		for (int i = 0; i < lk.size(); i++) {
			NbtCompound c = lk.getCompound(i);
			locker(c.getLong("P")).readNbtList(c.getList("Items", NbtElement.COMPOUND_TYPE), getRegistryManager());
		}
		displaced.clear();
		for (long p : nbt.getLongArray("Displaced")) displaced.add(p);
		NbtList ch = nbt.getList("Changes", NbtElement.COMPOUND_TYPE);
		for (int i = 0; i < ch.size(); i++) {
			NbtCompound c = ch.getCompound(i);
			BlockPos p = BlockPos.fromLong(c.getLong("P"));
			BlockState st = NbtHelper.toBlockState(Registries.BLOCK.getReadOnlyWrapper(), c.getCompound("S"));
			structure.set(p.getX(), p.getY(), p.getZ(), st);
		}
		setPosition(getX(), getY(), getZ());
	}

	@Override
	protected void writeCustomDataToNbt(NbtCompound nbt) {
		nbt.putDouble("LiftTarget", liftTarget);
		nbt.putDouble("Lift", liftCur);
		nbt.putBoolean("Lights", lightsOn());
		nbt.putBoolean("EngineOn", engineOn());
		nbt.putDouble("ShipYaw", shipYaw);
		nbt.putInt("WaterLayer", waterLayerY);
		nbt.putInt("Throttle", throttle);
		nbt.putBoolean("HatchesClosed", hatchesClosed());
		NbtList hl = new NbtList();
		for (CargoInventory h : holds) hl.add(h.toNbtList(getRegistryManager()));
		nbt.put("Holds", hl);
		NbtList lk = new NbtList();
		for (java.util.Map.Entry<Long, net.minecraft.inventory.SimpleInventory> e : lockers.entrySet()) {
			if (e.getValue().isEmpty()) continue;
			NbtCompound c = new NbtCompound();
			c.putLong("P", e.getKey());
			c.put("Items", e.getValue().toNbtList(getRegistryManager()));
			lk.add(c);
		}
		nbt.put("Lockers", lk);
		nbt.putLongArray("Displaced", new LongArrayList(displaced).toLongArray());
		NbtList ch = new NbtList();
		for (Long2ObjectMap.Entry<BlockState> e : structure.changes.long2ObjectEntrySet()) {
			NbtCompound c = new NbtCompound();
			c.putLong("P", e.getLongKey());
			c.put("S", NbtHelper.fromBlockState(e.getValue()));
			ch.add(c);
		}
		nbt.put("Changes", ch);
	}

	// =====================================================================
	// Diversen
	// =====================================================================

	@Override
	public void remove(RemovalReason reason) {
		if (!getWorld().isClient && reason.shouldDestroy()) {
			restoreAllWater();
			if (reason == RemovalReason.KILLED) {
				for (CargoInventory inv : holds) ItemScatterer.spawn(getWorld(), getBlockPos().up(3), inv);
				for (net.minecraft.inventory.SimpleInventory inv : lockers.values()) ItemScatterer.spawn(getWorld(), getBlockPos().up(3), inv);
			}
		}
		super.remove(reason);
	}

	@Override
	public boolean damage(DamageSource source, float amount) {
		return false;
	}

	@Override
	public boolean handleAttack(Entity attacker) {
		return true;
	}

	@Override
	public boolean isAttackable() {
		return false;
	}

	@Override
	public boolean canHit() {
		return false;
	}

	@Override
	public boolean isCollidable() {
		return false;
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	public boolean isImmuneToExplosion(Explosion explosion) {
		return true;
	}

	@Override
	public ItemStack getPickBlockStack() {
		return new ItemStack(Kempenaar.SHIP_ITEM);
	}

	@Override
	public boolean shouldRender(double distance) {
		return distance < 512 * 512;
	}

	/** Ruiminventaris met terugkoppeling naar het schip. */
	public static class CargoInventory extends SimpleInventory {
		private final ShipEntity ship;
		private final int index;

		CargoInventory(ShipEntity ship, int index) {
			super(HOLD_SLOTS);
			this.ship = ship;
			this.index = index;
		}

		@Override
		public void markDirty() {
			super.markDirty();
			ship.onCargoChanged(index);
		}

		@Override
		public boolean canPlayerUse(PlayerEntity player) {
			return !ship.isRemoved() && ship.isNear(player, 8);
		}
	}
}
