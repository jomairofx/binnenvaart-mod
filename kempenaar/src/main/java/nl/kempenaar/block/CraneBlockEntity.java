package nl.kempenaar.block;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventories;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.ItemScatterer;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import nl.kempenaar.Kempenaar;
import net.minecraft.entity.Entity;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * De laadkraan: een grote havenkraan (torenkraan) met loopkat en grijper, 42 blokken bereik.
 * Eén werkgang duurt ruim 11 seconden en verplaatst twee stapels; een volle kraan (54 stapels) is zo'n 5 minuten bezig.
 * Werkt met alle vrachtschepen met luiken uit deze serie mods (via {@link ShipAccess}).
 */
public class CraneBlockEntity extends BlockEntity implements Inventory, NamedScreenHandlerFactory {
	public static final int SLOTS = 54, CYCLE = 230, AT_SHIP = 115, STACKS_PER_RUN = 2;
	public static final double REACH = 42, HOME_R = 2.0, HOOK_TOP = 20.0, HOME_LOW = 2.3, TOWER = 22;

	private final DefaultedList<ItemStack> items = DefaultedList.ofSize(SLOTS, ItemStack.EMPTY);
	private final DefaultedList<ItemStack> carried = DefaultedList.ofSize(STACKS_PER_RUN, ItemStack.EMPTY);
	public boolean unloading;

	// werkgang (gesynchroniseerd met de client voor de animatie)
	public boolean running;
	/** Go/Stop: alleen als de kraan aan staat begint hij aan een nieuwe slag. */
	public boolean enabled;
	/** Pauze: de kraan bevriest midden in de slag. */
	public boolean paused;
	public long pausedAt;
	public long cycleStart;
	public float targetYaw, targetR = (float) HOME_R, targetLow = (float) HOME_LOW;
	public int carryMat; // 0 = leeg, >0 = blok (raw state id + 1), -1 = kist met stukgoed
	/** 0 = a working stroke (pile -> ship -> pile), 1 = just moving the grab from a to b (to the pile, or home to rest). */
	public int animKind;
	/** Where the stroke starts (a) and ends (b); where the grab is while the crane stands still (rest). NaN = at home. */
	public float aYaw, aR, aLow, bYaw, bR, bLow;
	public float restYaw = Float.NaN, restR, restLow;
	public static final int MOVE = 90;
	@Nullable
	private Vec3d pilePoint;

	@Nullable
	private UUID shipId;
	private int holdIndex = -1, idle, waitMsg, spot = 1;

	public CraneBlockEntity(BlockPos pos, BlockState state) {
		super(Kempenaar.CRANE_BE, pos, state);
	}

	// ------------------------------------------------------------------ stand van de kraan

	public float homeYaw() {
		Direction f = getCachedState().contains(CraneBlock.FACING) ? getCachedState().get(CraneBlock.FACING) : Direction.NORTH;
		return (float) Math.atan2(f.getOffsetZ(), f.getOffsetX());
	}

	public record Pose(float yaw, float radius, float hookY) {}

	private static float smooth(double x) {
		x = MathHelper.clamp(x, 0, 1);
		return (float) (x * x * (3 - 2 * x));
	}

	/** Stand van giek, loopkat en grijper op een (gebroken) tijdstip, plus hoe ver de grijper openstaat (0..1). */
	public Pose pose(double time) {
		if (paused) time = pausedAt;
		Pose rest = restPose();
		if (!running) return rest;
		double t = time - cycleStart;
		float top = (float) HOOK_TOP;
		if (animKind == 1) {
			if (t < 20) return new Pose(aYaw, aR, MathHelper.lerp(smooth(t / 20), aLow, top));
			if (t < 65) {
				float s = smooth((t - 20) / 45);
				return new Pose(aYaw + turn(aYaw, bYaw) * s, MathHelper.lerp(s, aR, bR), top);
			}
			return new Pose(bYaw, bR, MathHelper.lerp(smooth((t - 65) / 25), top, bLow));
		}
		if (t < 25) return new Pose(aYaw, aR, MathHelper.lerp(smooth(t / 25), aLow, top));
		if (t < 70) {
			float s = smooth((t - 25) / 45);
			return new Pose(aYaw + turn(aYaw, targetYaw) * s, MathHelper.lerp(s, aR, targetR), top);
		}
		if (t < AT_SHIP) return new Pose(targetYaw, targetR, MathHelper.lerp(smooth((t - 70) / 45), top, targetLow));
		if (t < 140) return new Pose(targetYaw, targetR, MathHelper.lerp(smooth((t - AT_SHIP) / 25), targetLow, top));
		if (t < 185) {
			float s = smooth((t - 140) / 45);
			return new Pose(targetYaw + turn(targetYaw, bYaw) * s, MathHelper.lerp(s, targetR, bR), top);
		}
		return new Pose(bYaw, bR, MathHelper.lerp(smooth((t - 185) / 45), top, bLow));
	}

	/** Shortest turn from one yaw to another (radians). */
	private static float turn(float from, float to) {
		return MathHelper.wrapDegrees((float) Math.toDegrees(to - from)) * MathHelper.RADIANS_PER_DEGREE;
	}

	/** Where the grab hangs while the crane does nothing: low at home, or low over the pile it works from. */
	public Pose restPose() {
		if (Float.isNaN(restYaw)) return new Pose(homeYaw(), (float) HOME_R, (float) HOME_LOW);
		return new Pose(restYaw, restR, restLow);
	}

	private boolean atHome() {
		return Float.isNaN(restYaw);
	}

	/** Opening van de grijper: bij laden open boven het ruim, bij lossen open boven de kade (en tijdens het zakken in het ruim). */
	public float grabOpen(double time) {
		if (paused) time = pausedAt;
		if (!running) return 0.6f;
		double t = time - cycleStart;
		if (animKind == 1) return 0.6f;
		if (!unloading) {
			if (t > AT_SHIP - 8 && t < AT_SHIP + 18) return smooth((t - (AT_SHIP - 8)) / 8) * (1 - smooth((t - (AT_SHIP + 10)) / 8));
			if (t < 10) return 0.6f * (1 - smooth(t / 10));
			if (t > CYCLE - 12) return 0.6f * smooth((t - (CYCLE - 12)) / 12);
			return 0f;
		}
		if (t < 12) return 0.6f * (1 - smooth(t / 12));
		if (t > 90 && t < AT_SHIP + 8) return smooth((t - 90) / 10) * (1 - smooth((t - AT_SHIP) / 8));
		if (t > CYCLE - 14) return smooth((t - (CYCLE - 14)) / 8);
		return 0f;
	}

	// ------------------------------------------------------------------ server

	public static void serverTick(World world, BlockPos pos, BlockState state, CraneBlockEntity be) {
		if (world instanceof ServerWorld sw) nl.kempenaar.ship.ShipEntity.safely("crane", () -> be.tickServer(sw));
	}

	private void tickServer(ServerWorld w) {
		if (paused) return;
		if (running) {
			long t = w.getTime() - cycleStart;
			if (t == 25 || t == 140) sound(w, Vec3d.ofCenter(pos).add(0, TOWER, 0), SoundEvents.BLOCK_PISTON_CONTRACT, 0.5f, 0.5f);
			if (t == 70 || t == 185) sound(w, Vec3d.ofCenter(pos).add(0, TOWER, 0), SoundEvents.BLOCK_CHAIN_STEP, 0.8f, 0.5f);
			if (animKind == 1) {
				if (t >= MOVE || t < 0) {
					running = false;
					setRest(bYaw, bR, bLow);
					markDirty();
					sync();
				}
				return;
			}
			if (t == 6 && !unloading) pileDust(w, false);
			if (t == AT_SHIP) atShip(w);
			if (t == CYCLE - 8 && unloading) pileDust(w, true);
			if (t >= CYCLE || t < 0) {
				running = false;
				setRest(bYaw, bR, bLow);
				depositCarried();
				carryMat = 0;
				markDirty();
				sync();
			}
			return;
		}
		if (++idle < 10) return;
		idle = 0;
		if (!enabled) {
			// switched off: the grab comes back from the pile and is lowered to the ground at the foot of the crane
			if (!atHome()) {
				Pose r = restPose();
				startMove(w, r.yaw(), r.radius(), r.hookY(), homeYaw(), (float) HOME_R, (float) HOME_LOW);
				pilePoint = null;
			}
			return;
		}
		if (!isCarriedEmpty()) {
			depositCarried();
			if (!isCarriedEmpty()) return;
		}
		Target tg = findTarget(w);
		if (tg == null) return;
		// first to the pile of goods (or the storage bins): the grab scoops there, swings to the ship and comes back
		ItemStack goods = unloading ? lastIn(tg.ship.hold(tg.hold)) : firstGoods();
		PilePose pp = pileFor(w, goods);
		Pose rest = restPose();
		if (farApart(rest, pp)) {
			startMove(w, rest.yaw(), rest.radius(), rest.hookY(), pp.yaw, pp.r, pp.low);
			pilePoint = pp.point;
			return;
		}
		if (!unloading) {
			int taken = 0;
			for (int i = 0; i < SLOTS && taken < STACKS_PER_RUN; i++) {
				ItemStack s = items.get(i);
				if (s.isEmpty()) continue;
				carried.set(taken++, s);
				items.set(i, ItemStack.EMPTY);
			}
			for (CraneStorageBlockEntity bin : bins()) {
				for (int i = 0; i < bin.size() && taken < STACKS_PER_RUN; i++) {
					ItemStack s = bin.getStack(i);
					if (s.isEmpty()) continue;
					carried.set(taken++, s);
					bin.setStack(i, ItemStack.EMPTY);
				}
			}
			if (taken == 0) return;
			carryMat = matOf(carried.get(0));
		} else {
			carryMat = 0;
		}
		animKind = 0;
		aYaw = rest.yaw();
		aR = rest.radius();
		aLow = rest.hookY();
		bYaw = pp.yaw;
		bR = pp.r;
		bLow = pp.low;
		pilePoint = pp.point;
		shipId = tg.ship.entity().getUuid();
		spot++;
		holdIndex = tg.hold;
		targetYaw = tg.yaw;
		targetR = tg.radius;
		targetLow = tg.low;
		running = true;
		cycleStart = w.getTime();
		sound(w, Vec3d.ofCenter(pos).add(0, 1, 0), SoundEvents.BLOCK_PISTON_EXTEND, 0.4f, 0.5f);
		markDirty();
		sync();
	}

	private record Target(ShipAccess ship, int hold, float yaw, float radius, float low, Vec3d point) {}

	private record PilePose(float yaw, float r, float low, @Nullable Vec3d point) {}

	private void setRest(float yaw, float r, float low) {
		Pose h = new Pose(homeYaw(), (float) HOME_R, (float) HOME_LOW);
		if (!farApart(h, new PilePose(yaw, r, low, null)) && Math.abs(low - HOME_LOW) < 0.01f) restYaw = Float.NaN;
		else {
			restYaw = yaw;
			restR = r;
			restLow = low;
		}
	}

	private void startMove(ServerWorld w, float ay, float ar, float al, float by, float br, float bl) {
		animKind = 1;
		aYaw = ay;
		aR = ar;
		aLow = al;
		bYaw = by;
		bR = br;
		bLow = bl;
		carryMat = 0;
		running = true;
		cycleStart = w.getTime();
		sound(w, Vec3d.ofCenter(pos).add(0, TOWER, 0), SoundEvents.BLOCK_CHAIN_STEP, 0.6f, 0.5f);
		markDirty();
		sync();
	}

	private static boolean farApart(Pose p, PilePose q) {
		double x1 = Math.cos(p.yaw()) * p.radius(), z1 = Math.sin(p.yaw()) * p.radius();
		double x2 = Math.cos(q.yaw) * q.r, z2 = Math.sin(q.yaw) * q.r;
		return Math.hypot(x1 - x2, z1 - z2) > 5 || Math.abs(p.hookY() - q.low) > 4;
	}

	private ItemStack firstGoods() {
		for (ItemStack st : items) if (!st.isEmpty()) return st;
		for (CraneStorageBlockEntity b : bins())
			for (int i = 0; i < b.size(); i++) if (!b.getStack(i).isEmpty()) return b.getStack(i);
		return ItemStack.EMPTY;
	}

	private static ItemStack lastIn(SimpleInventory inv) {
		for (int i = inv.size() - 1; i >= 0; i--) if (!inv.getStack(i).isEmpty()) return inv.getStack(i);
		return ItemStack.EMPTY;
	}

	private static int surface(ServerWorld w, int x, int z) {
		return w.getTopY(net.minecraft.world.Heightmap.Type.WORLD_SURFACE, x, z) - 1;
	}

	/**
	 * Where the grab works on land: the top of the nearest heap of the same goods in the yard (a pile of gravel, sand,
	 * coal ...), otherwise over the storage bins at the foot of the crane.
	 */
	private PilePose pileFor(ServerWorld w, ItemStack goods) {
		Vec3d c = Vec3d.ofCenter(pos);
		Block want = bulkBlock(goods);
		if (want != null && !(want instanceof net.minecraft.block.BlockEntityProvider)) {
			BlockPos.Mutable m = new BlockPos.Mutable();
			int R = 32, bx = 0, bz = 0, by = 0;
			double bd = Double.MAX_VALUE;
			for (int dx = -R; dx <= R; dx++)
				for (int dz = -R; dz <= R; dz++) {
					double d = Math.sqrt(dx * dx + dz * dz);
					if (d > R || d < 4 || d >= bd) continue;
					int x = pos.getX() + dx, z = pos.getZ() + dz;
					if (!w.isChunkLoaded(x >> 4, z >> 4)) continue;
					int top = surface(w, x, z);
					if (top < pos.getY() - 4 || top > pos.getY() + 16) continue;
					if (!w.getBlockState(m.set(x, top, z)).isOf(want)) continue;
					bd = d;
					bx = x;
					bz = z;
					by = top;
				}
			if (bd < Double.MAX_VALUE) {
				// climb to the top of that heap, then take a spot near the top (a little different each time)
				int cx = bx, cz = bz, cy = by;
				for (int dx = -7; dx <= 7; dx++)
					for (int dz = -7; dz <= 7; dz++) {
						int x = bx + dx, z = bz + dz;
						if (!w.isChunkLoaded(x >> 4, z >> 4)) continue;
						int top = surface(w, x, z);
						if (top > cy && top <= pos.getY() + 16 && w.getBlockState(m.set(x, top, z)).isOf(want)) {
							cx = x;
							cz = z;
							cy = top;
						}
					}
				int tx = cx + (spot % 3) - 1, tz = cz + ((spot / 3) % 3) - 1;
				if (w.isChunkLoaded(tx >> 4, tz >> 4)) {
					int tt = surface(w, tx, tz);
					if (tt >= cy - 1 && w.getBlockState(m.set(tx, tt, tz)).isOf(want)) {
						cx = tx;
						cz = tz;
						cy = tt;
					}
				}
				double px = cx + 0.5, pz = cz + 0.5, dx = px - c.x, dz = pz - c.z, r = Math.sqrt(dx * dx + dz * dz);
				if (r <= REACH && r >= 2.5) {
					float low = (float) MathHelper.clamp(cy + 1 - 0.35 - pos.getY(), -14, HOOK_TOP - 1);
					return new PilePose((float) Math.atan2(dz, dx), (float) r, low, new Vec3d(px, cy + 1, pz));
				}
			}
		}
		// over a storage bin
		for (CraneStorageBlockEntity b : bins()) {
			if (unloading ? !b.hasEmptySlot() : b.isEmpty()) continue;
			BlockPos bp = b.getPos();
			double px = bp.getX() + 0.5, pz = bp.getZ() + 0.5, dx = px - c.x, dz = pz - c.z;
			double r = Math.max(HOME_R, Math.sqrt(dx * dx + dz * dz));
			var shape = w.getBlockState(bp).getCollisionShape(w, bp);
			double topY = bp.getY() + (shape.isEmpty() ? 1 : shape.getMax(Direction.Axis.Y));
			float low = (float) Math.max(HOME_LOW, topY + 0.1 - pos.getY());
			return new PilePose((float) Math.atan2(dz, dx), (float) r, low, new Vec3d(px, topY, pz));
		}
		return new PilePose(homeYaw(), (float) HOME_R, (float) HOME_LOW, c.add(0, HOME_LOW - 0.5, 0));
	}

	/** Dust and sound where the grab scoops from (or drops onto) the heap. */
	private void pileDust(ServerWorld w, boolean drop) {
		if (pilePoint == null || carryMat <= 0) return;
		BlockState st = Block.getStateFromRawId(carryMat - 1);
		w.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, st), pilePoint.x, pilePoint.y + 0.2, pilePoint.z, 30, 0.6, 0.3, 0.6, 0.1);
		sound(w, pilePoint, drop ? st.getSoundGroup().getPlaceSound() : st.getSoundGroup().getBreakSound(), 1.0f, 0.6f);
	}

	/** Dichtstbijzijnde laadruim binnen bereik waar nu iets te doen is. */
	@Nullable
	private Target findTarget(ServerWorld w) {
		Vec3d c = Vec3d.ofCenter(pos);
		Target best = null;
		ShipAccess closedShip = null;
		for (Entity e : w.getOtherEntities(null, new Box(pos).expand(REACH + 60, 30, REACH + 60), e -> !e.isRemoved())) {
			ShipAccess s = ShipAccess.of(e);
			if (s == null) continue;
			for (int i = 0; i < s.holdCount(); i++) {
				Target t = targetFor(s, i, c);
				if (t == null || !work(s, i)) continue;
				if (!s.hatchesOpen()) {
					closedShip = s;
					continue;
				}
				if (best == null || t.radius < best.radius) best = t;
			}
		}
		if (best == null && closedShip != null && ++waitMsg % 10 == 1) {
			Text msg = Text.literal("Cargo crane waiting: the hatches of the " + closedShip.name() + " are closed (press L at the helm)").formatted(Formatting.GOLD);
			for (ServerPlayerEntity p : w.getPlayers(p -> p.squaredDistanceTo(c) < 48 * 48)) p.sendMessage(msg, true);
		}
		return best;
	}

	/** Is er in dit ruim iets te doen (ruimte bij laden, lading bij lossen)? */
	private boolean work(ShipAccess s, int hold) {
		SimpleInventory inv = s.hold(hold);
		if (unloading) return !inv.isEmpty() && (hasEmptySlot() || binsHaveRoom());
		ItemStack first = ItemStack.EMPTY;
		for (ItemStack st : items) if (!st.isEmpty()) {
			first = st;
			break;
		}
		if (first.isEmpty())
			for (CraneStorageBlockEntity b : bins())
				for (int i = 0; i < b.size() && first.isEmpty(); i++) if (!b.getStack(i).isEmpty()) first = b.getStack(i);
		if (first.isEmpty()) return false;
		for (int i = 0; i < inv.size(); i++) {
			ItemStack h = inv.getStack(i);
			if (h.isEmpty() || (ItemStack.areItemsAndComponentsEqual(h, first) && h.getCount() < h.getMaxCount())) return true;
		}
		return false;
	}

	@Nullable
	private Target targetFor(ShipAccess s, int hold, Vec3d c) {
		Vec3d l = s.toLocal(c);
		int[] hb = s.holds()[hold];
		int a = hb[0], b = hb[1];
		// altijd op de hartlijn van het ruim, afwisselend voor, midden en achter, zodat de stort mooi verdeeld wordt
		double len = b - a + 1, mid = (a + b + 1) / 2.0;
		double tx = mid + new double[]{-0.28, -0.05, 0.16}[spot % 3] * len;   // stays clear of the cover stack at the front of the hold
		double tz = (s.holdZ0() + s.holdZ1() + 1) / 2.0;
		Vec3d p = s.toWorld(tx, s.coamingTopY(), tz);
		double dx = p.x - c.x, dz = p.z - c.z, d = Math.sqrt(dx * dx + dz * dz);
		if (d > REACH || d < 2.5) return null;
		float low = (float) MathHelper.clamp(p.y - 1.6 - pos.getY(), -14, HOOK_TOP - 1);
		return new Target(s, hold, (float) Math.atan2(dz, dx), (float) d, low, p);
	}

	private void atShip(ServerWorld w) {
		Entity e = shipId != null ? w.getEntity(shipId) : null;
		ShipAccess ship = e != null ? ShipAccess.of(e) : null;
		if (ship == null || holdIndex < 0 || holdIndex >= ship.holdCount()) return;
		Target t = targetFor(ship, holdIndex, Vec3d.ofCenter(pos));
		if (t == null || !ship.hatchesOpen()) return; // schip is weggevaren of luiken dicht: lading gaat terug
		SimpleInventory inv = ship.hold(holdIndex);
		if (!unloading) {
			BlockState mat = carryMat > 0 ? Block.getStateFromRawId(carryMat - 1) : null;
			for (int i = 0; i < carried.size(); i++) {
				ItemStack st = carried.get(i);
				if (st.isEmpty()) continue;
				carried.set(i, inv.addStack(st));
			}
			inv.markDirty();
			if (mat != null) {
				w.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, mat), t.point.x, t.point.y - 0.5, t.point.z, 40, 0.6, 0.4, 0.6, 0.1);
				sound(w, t.point, mat.getSoundGroup().getPlaceSound(), 1.2f, 0.6f);
				sound(w, t.point, SoundEvents.BLOCK_GRAVEL_BREAK, 1.0f, 0.6f);
			} else {
				sound(w, t.point, SoundEvents.BLOCK_CHAIN_PLACE, 1.2f, 0.6f);
			}
			carryMat = isCarriedEmpty() ? 0 : matOf(firstCarried());
		} else {
			int taken = 0;
			for (int i = inv.size() - 1; i >= 0 && taken < STACKS_PER_RUN; i--) {
				if (inv.getStack(i).isEmpty()) continue;
				carried.set(taken++, inv.removeStack(i));
			}
			inv.markDirty();
			carryMat = isCarriedEmpty() ? 0 : matOf(firstCarried());
			sound(w, t.point, SoundEvents.BLOCK_GRAVEL_BREAK, 1.0f, 0.5f);
		}
		markDirty();
		sync();
	}

	private ItemStack firstCarried() {
		for (ItemStack s : carried) if (!s.isEmpty()) return s;
		return ItemStack.EMPTY;
	}

	private boolean isCarriedEmpty() {
		for (ItemStack s : carried) if (!s.isEmpty()) return false;
		return true;
	}

	private boolean hasEmptySlot() {
		for (ItemStack s : items) if (s.isEmpty()) return true;
		return false;
	}

	/** De vier opslagbunkers rond de voet. */
	private java.util.List<CraneStorageBlockEntity> bins() {
		java.util.List<CraneStorageBlockEntity> list = new java.util.ArrayList<>();
		if (world == null) return list;
		for (Direction d : new Direction[]{Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST})
			for (int dist = 1; dist <= 2; dist++)
				if (world.getBlockEntity(pos.offset(d, dist)) instanceof CraneStorageBlockEntity b) list.add(b);
		return list;
	}

	private boolean binsHaveRoom() {
		for (CraneStorageBlockEntity b : bins()) if (b.hasEmptySlot()) return true;
		return false;
	}

	/** Gedragen lading wegzetten: bij lossen eerst in de opslagbunkers, anders (of als die vol zijn) in de kraan zelf. */
	private void depositCarried() {
		if (unloading) {
			for (int i = 0; i < carried.size(); i++) {
				ItemStack st = carried.get(i);
				for (CraneStorageBlockEntity b : bins()) {
					if (st.isEmpty()) break;
					st = b.insert(st);
				}
				carried.set(i, st);
			}
		}
		for (int i = 0; i < carried.size(); i++) {
			ItemStack st = carried.get(i);
			if (st.isEmpty()) continue;
			for (int k = 0; k < SLOTS && !st.isEmpty(); k++) {
				ItemStack slot = items.get(k);
				if (!slot.isEmpty() && ItemStack.areItemsAndComponentsEqual(slot, st)) {
					int n = Math.min(st.getCount(), slot.getMaxCount() - slot.getCount());
					slot.increment(n);
					st.decrement(n);
				}
			}
			for (int k = 0; k < SLOTS && !st.isEmpty(); k++) {
				if (items.get(k).isEmpty()) {
					items.set(k, st);
					st = ItemStack.EMPTY;
				}
			}
			carried.set(i, st);
		}
		markDirty();
	}

	public void dropCarried() {
		if (world == null) return;
		for (int i = 0; i < carried.size(); i++) {
			ItemScatterer.spawn(world, pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, carried.get(i));
			carried.set(i, ItemStack.EMPTY);
		}
	}

	private static int matOf(ItemStack s) {
		Block b = bulkBlock(s);
		if (b != null) return Block.getRawIdFromState(b.getDefaultState()) + 1;
		return -1;
	}

	/** The block that bulk goods look like in a heap or in the grab (coal -> coal, raw iron -> iron ore ...). */
	@Nullable
	private static Block bulkBlock(ItemStack s) {
		if (s.getItem() instanceof BlockItem bi) return bi.getBlock();
		if (s.isOf(net.minecraft.item.Items.COAL) || s.isOf(net.minecraft.item.Items.CHARCOAL)) return net.minecraft.block.Blocks.COAL_BLOCK;
		if (s.isOf(net.minecraft.item.Items.RAW_IRON)) return net.minecraft.block.Blocks.RAW_IRON_BLOCK;
		if (s.isOf(net.minecraft.item.Items.RAW_COPPER)) return net.minecraft.block.Blocks.RAW_COPPER_BLOCK;
		if (s.isOf(net.minecraft.item.Items.CLAY_BALL)) return net.minecraft.block.Blocks.CLAY;
		return null;
	}

	private static void sound(ServerWorld w, Vec3d p, SoundEvent e, float vol, float pitch) {
		w.playSound(null, p.x, p.y, p.z, e, SoundCategory.BLOCKS, vol, pitch);
	}

	public void toggleMode(PlayerEntity player) {
		if (running) {
			player.sendMessage(Text.literal("Please wait, the crane is busy").formatted(Formatting.GRAY), true);
			return;
		}
		unloading = !unloading;
		markDirty();
		sync();
		if (bins().isEmpty() && world != null && CraneBlock.placeStorage(world, pos) > 0)
			player.sendMessage(Text.literal("Storage bins placed around the crane").formatted(Formatting.GRAY), false);
		player.sendMessage(Text.literal(unloading ? "Cargo crane: UNLOADING — from the hold into the 4 storage bins" : "Cargo crane: LOADING — from the crane (and its bins) into the hold").formatted(Formatting.AQUA), true);
	}

	/** Bediening vanaf het bedieningspaneel: 0 = laden, 1 = lossen, 2 = go/stop, 3 = pauze/verder. */
	public void command(PlayerEntity player, int cmd) {
		if (world == null) return;
		switch (cmd) {
			case 0, 1 -> {
				boolean u = cmd == 1;
				if (u != unloading) {
					if (running) {
						player.sendMessage(Text.literal("Please wait, the crane is busy").formatted(Formatting.GRAY), true);
						break;
					}
					unloading = u;
					if (bins().isEmpty()) CraneBlock.placeStorage(world, pos);
				}
				player.sendMessage(Text.literal(unloading ? "Cargo crane: UNLOADING (hold -> storage bins)" : "Cargo crane: LOADING (crane and bins -> hold)").formatted(Formatting.AQUA), true);
			}
			case 2 -> {
				enabled = !enabled;
				if (!enabled && paused) resume();
				player.sendMessage(Text.literal(enabled ? "Cargo crane: GO" : "Cargo crane: STOP (finishes the current move)").formatted(enabled ? Formatting.GREEN : Formatting.RED), true);
			}
			case 3 -> {
				if (paused) resume();
				else {
					paused = true;
					pausedAt = world.getTime();
				}
				player.sendMessage(Text.literal(paused ? "Cargo crane: PAUSED" : "Cargo crane: running again").formatted(Formatting.GOLD), true);
			}
			default -> {}
		}
		markDirty();
		sync();
	}

	private void resume() {
		if (world != null && running) cycleStart += world.getTime() - pausedAt;
		paused = false;
	}

	/** Alleen voor de automatische test. */
	public void testSetUnloading(boolean u) {
		unloading = u;
	}

	private void sync() {
		if (world != null) world.updateListeners(pos, getCachedState(), getCachedState(), Block.NOTIFY_LISTENERS);
	}

	// ------------------------------------------------------------------ opslaan / synchroniseren

	private void writeAnim(NbtCompound nbt) {
		nbt.putBoolean("Running", running);
		nbt.putLong("CycleStart", cycleStart);
		nbt.putFloat("TargetYaw", targetYaw);
		nbt.putFloat("TargetR", targetR);
		nbt.putFloat("TargetLow", targetLow);
		nbt.putInt("CarryMat", carryMat);
		nbt.putBoolean("Unloading", unloading);
		nbt.putBoolean("Enabled", enabled);
		nbt.putBoolean("Paused", paused);
		nbt.putLong("PausedAt", pausedAt);
		nbt.putInt("AnimKind", animKind);
		nbt.putFloat("AYaw", aYaw);
		nbt.putFloat("AR", aR);
		nbt.putFloat("ALow", aLow);
		nbt.putFloat("BYaw", bYaw);
		nbt.putFloat("BR", bR);
		nbt.putFloat("BLow", bLow);
		nbt.putBoolean("AtHome", Float.isNaN(restYaw));
		nbt.putFloat("RestYaw", Float.isNaN(restYaw) ? 0 : restYaw);
		nbt.putFloat("RestR", restR);
		nbt.putFloat("RestLow", restLow);
	}

	@Override
	protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
		super.writeNbt(nbt, registries);
		Inventories.writeNbt(nbt, items, registries);
		NbtCompound c = new NbtCompound();
		Inventories.writeNbt(c, carried, registries);
		nbt.put("Carried", c);
		nbt.putBoolean("Unloading", unloading);
		nbt.putBoolean("Enabled", enabled);
		nbt.putBoolean("Paused", paused);
		nbt.putLong("PausedAt", pausedAt);
		writeAnim(nbt);
	}

	@Override
	protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
		super.readNbt(nbt, registries);
		if (nbt.contains("Items")) {
			for (int i = 0; i < SLOTS; i++) items.set(i, ItemStack.EMPTY);
			Inventories.readNbt(nbt, items, registries);
		}
		if (nbt.contains("Carried")) {
			for (int i = 0; i < carried.size(); i++) carried.set(i, ItemStack.EMPTY);
			Inventories.readNbt(nbt.getCompound("Carried"), carried, registries);
		}
		unloading = nbt.getBoolean("Unloading");
		enabled = nbt.getBoolean("Enabled");
		paused = nbt.getBoolean("Paused");
		pausedAt = nbt.getLong("PausedAt");
		if (nbt.contains("Running")) {
			running = nbt.getBoolean("Running");
			cycleStart = nbt.getLong("CycleStart");
			targetYaw = nbt.getFloat("TargetYaw");
			targetR = nbt.getFloat("TargetR");
			targetLow = nbt.getFloat("TargetLow");
			carryMat = nbt.getInt("CarryMat");
		}
		if (nbt.contains("AtHome")) {
			animKind = nbt.getInt("AnimKind");
			aYaw = nbt.getFloat("AYaw");
			aR = nbt.getFloat("AR");
			aLow = nbt.getFloat("ALow");
			bYaw = nbt.getFloat("BYaw");
			bR = nbt.getFloat("BR");
			bLow = nbt.getFloat("BLow");
			restYaw = nbt.getBoolean("AtHome") ? Float.NaN : nbt.getFloat("RestYaw");
			restR = nbt.getFloat("RestR");
			restLow = nbt.getFloat("RestLow");
		} else if (nbt.contains("Running")) {
			// old save: a stroke from home back to home
			animKind = 0;
			aYaw = bYaw = homeYaw();
			aR = bR = (float) HOME_R;
			aLow = bLow = (float) HOME_LOW;
		}
	}

	@Nullable
	@Override
	public Packet<ClientPlayPacketListener> toUpdatePacket() {
		return BlockEntityUpdateS2CPacket.create(this);
	}

	@Override
	public NbtCompound toInitialChunkDataNbt(RegistryWrapper.WrapperLookup registries) {
		NbtCompound n = new NbtCompound();
		writeAnim(n);
		return n;
	}

	// ------------------------------------------------------------------ inventaris

	@Override
	public int size() {
		return SLOTS;
	}

	@Override
	public boolean isEmpty() {
		for (ItemStack s : items) if (!s.isEmpty()) return false;
		return true;
	}

	@Override
	public ItemStack getStack(int slot) {
		return items.get(slot);
	}

	@Override
	public ItemStack removeStack(int slot, int amount) {
		ItemStack r = Inventories.splitStack(items, slot, amount);
		if (!r.isEmpty()) markDirty();
		return r;
	}

	@Override
	public ItemStack removeStack(int slot) {
		return Inventories.removeStack(items, slot);
	}

	@Override
	public void setStack(int slot, ItemStack stack) {
		items.set(slot, stack);
		stack.capCount(getMaxCount(stack));
		markDirty();
	}

	@Override
	public boolean canPlayerUse(PlayerEntity player) {
		return Inventory.canPlayerUse(this, player);
	}

	@Override
	public void clear() {
		items.clear();
	}

	public int countItems() {
		int n = 0;
		for (ItemStack s : items) n += s.getCount();
		return n;
	}

	public int countBinItems() {
		int n = 0;
		for (CraneStorageBlockEntity b : bins()) n += b.countItems();
		return n;
	}

	@Override
	public Text getDisplayName() {
		return Text.literal("Cargo Crane – " + (unloading ? "unloading" : "loading"));
	}

	@Override
	public ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity player) {
		return GenericContainerScreenHandler.createGeneric9x6(syncId, playerInventory, this);
	}
}
