"""Keep players asleep in ship beds while the ship moves, rises or settles."""
import sys, re
root, pkg, main = sys.argv[1:4]
P = pkg.replace(".", "/")
f = f"{root}/main/java/{P}/ship/ShipEntity.java"
s = open(f).read()
s = s.replace("double bestD = 2.5 * 2.5;", "double bestD = 3.5 * 3.5;")
s = s.replace("if (Math.abs(p.y - c.y) > 1.6) continue;", "if (Math.abs(p.y - c.y) > 2.5) continue;")
a = "	private void useBed(ServerPlayerEntity player, int x, int y, int z) {"
assert s.count(a) == 1
s = s.replace(a, """	/** Slapers in een scheepsbed: slaappositie en lichaam bewegen mee met het schip, zodat je niet uit bed valt. */
	public static void keepSleepers(net.minecraft.server.MinecraftServer server) {
		for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
			try {
				if (!p.isSleeping()) continue;
				var sp = p.getSleepingPosition();
				if (sp.isEmpty() || p.getWorld().getBlockState(sp.get()).getBlock() instanceof BedBlock) continue;
				BedRef ref = findBed(p.getWorld(), sp.get());
				if (ref == null) ref = findBed(p.getWorld(), BlockPos.ofFloored(p.getX(), p.getY() - 0.5, p.getZ()));
				if (ref == null) continue;
				Vec3d spot = ref.ship().bedSpot(ref.head());
				BlockPos bp = BlockPos.ofFloored(spot.x, spot.y - 0.6875 + 0.01, spot.z);
				if (!bp.equals(sp.get())) p.setSleepingPosition(bp);
				double d = p.getPos().squaredDistanceTo(spot);
				if (d > 1e-4) {
					p.setPosition(spot.x, spot.y, spot.z);
					if (d > 0.04) p.networkHandler.requestTeleport(spot.x, spot.y, spot.z, p.getYaw(), p.getPitch());
				}
			} catch (RuntimeException ex) {
				LOGGER_SAFE.warn("ship bed keeper", ex);
			}
		}
	}

	private static final org.slf4j.Logger LOGGER_SAFE = org.slf4j.LoggerFactory.getLogger("ship-beds");

""" + a)
open(f, "w").write(s)
f = f"{root}/main/java/{P}/{main}.java"
s = open(f).read()
s2, n = re.subn(r"(		ServerTickEvents\.END_SERVER_TICK\.register\(\w+::respawnTick\);\n)",
                r"\1		ServerTickEvents.END_SERVER_TICK.register(com_ship_beds -> " + pkg + ".ship.ShipEntity.keepSleepers(com_ship_beds));\n", s)
assert n == 1
open(f, "w").write(s2)
print("ok", root)
