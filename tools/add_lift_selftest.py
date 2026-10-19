import sys
f, x, y, z = sys.argv[1], sys.argv[2], sys.argv[3], sys.argv[4]
lift = len(sys.argv) < 6
s = open(f).read()
a = '''				check("stilstand", Math.abs(ship.currentSpeed()) < 0.01, String.format("snelheid %.4f", ship.currentSpeed()));
				int restored = 0;'''
assert a in s, f
p = f"{x}, {y}, {z}"
mid = (f'''				check("stuurhut omhoog", ship.liftOffset() > {sys.argv[5] if not lift else "2.9"}, String.format("stuurhut staat %.2f blok omhoog", ship.liftOffset()));
''' if lift else "")
s = s.replace(a, '''				check("stilstand", Math.abs(ship.currentSpeed()) < 0.01, String.format("snelheid %.4f", ship.currentSpeed()));
''' + ('				ship.testSetLift(3.0);\n' if lift else '') + '''			}
			if (tick == 1180) {
''' + mid + f'''				ship.setLights(false);
				var lamp = ship.structure.get({p});
				check("licht uit", !ship.lightsOn() && !lamp.isOf(net.minecraft.block.Blocks.SEA_LANTERN), "lamp nu " + lamp);
				ship.setLights(true);
				check("licht aan", ship.lightsOn() && ship.structure.get({p}).isOf(net.minecraft.block.Blocks.SEA_LANTERN), "lamp weer " + ship.structure.get({p}));
''' + ('				ship.testSetLift(0);\n' if lift else '') + '''			}
			if (tick == 1200) {
				int restored = 0;''')
open(f, "w").write(s)
