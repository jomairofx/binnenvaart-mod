"""Faster ship mesh drawing without losing anything visible:
- faces turned away from the camera are skipped on the CPU (the GPU would cull them anyway),
- one vertex call per vertex with positions transformed here."""
import sys
for f in sys.argv[1:]:
    s = open(f).read()
    a = "	private static final int STRIDE = 7; // x y z u v kleur licht\n"
    assert a in s, f
    s = s.replace(a, "	private static final int STRIDE = 7; // x y z u v kleur licht\n	/** Per vlak: normaal (3) en afstand (1), daarna 4 hoekpunten. */\n	private static final int QUAD = 4 + 4 * STRIDE;\n")
    i = s.index("	public void render(MatrixStack matrices, VertexConsumerProvider consumers")
    j = s.index("	// -------------------------------------------------------------------------", i)
    s = s[:i] + """	public void render(MatrixStack matrices, VertexConsumerProvider consumers, @Nullable Frustum frustum, Vec3d shipPos, double yaw, Vec3d cam, int minLevelAll) {
		MatrixStack.Entry entry = matrices.peek();
		Matrix4f m = entry.getPositionMatrix();
		boolean[] visible = new boolean[sections];
		int[] level = new int[sections];
		double c = Math.cos(yaw), s = Math.sin(yaw);
		for (int k = 0; k < sections; k++) {
			double x0 = ShipTemplate.MIN_X + k * SEC - ShipTemplate.CX, x1 = x0 + SEC;
			double z0 = ShipTemplate.MIN_Z - ShipTemplate.CZ, z1 = z0 + ShipTemplate.SIZE_Z;
			double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, minZ = Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
			for (double lx : new double[]{x0, x1})
				for (double lz : new double[]{z0, z1}) {
					double wx = shipPos.x + lx * c - lz * s, wz = shipPos.z + lx * s + lz * c;
					minX = Math.min(minX, wx);
					maxX = Math.max(maxX, wx);
					minZ = Math.min(minZ, wz);
					maxZ = Math.max(maxZ, wz);
				}
			Box b = new Box(minX, shipPos.y + ShipTemplate.MIN_Y, minZ, maxX, shipPos.y + ShipTemplate.MIN_Y + ShipTemplate.SIZE_Y, maxZ);
			visible[k] = frustum == null || frustum.isVisible(b);
			double dx = Math.max(0, Math.max(b.minX - cam.x, cam.x - b.maxX));
			double dy = Math.max(0, Math.max(b.minY - cam.y, cam.y - b.maxY));
			double dz = Math.max(0, Math.max(b.minZ - cam.z, cam.z - b.maxZ));
			double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
			level[k] = Math.max(minLevelAll, d < NEAR ? 0 : d < MID ? 1 : 2);
		}
		// camera in sjablooncoördinaten, om afgewende vlakken over te slaan
		double rx = cam.x - shipPos.x, rz = cam.z - shipPos.z;
		float cx = (float) (rx * c + rz * s + ShipTemplate.CX), cy = (float) (cam.y - shipPos.y), cz = (float) (-rx * s + rz * c + ShipTemplate.CZ);
		float m00 = m.m00(), m01 = m.m01(), m02 = m.m02(), m10 = m.m10(), m11 = m.m11(), m12 = m.m12();
		float m20 = m.m20(), m21 = m.m21(), m22 = m.m22(), m30 = m.m30(), m31 = m.m31(), m32 = m.m32();
		org.joml.Vector3f nrm = entry.getNormalMatrix().transform(new org.joml.Vector3f(0f, 1f, 0f));
		float nx = nrm.x, ny = nrm.y, nz = nrm.z;
		int overlay = OverlayTexture.DEFAULT_UV;
		for (int l = 0; l < layers.size(); l++) {
			float[][] parts = data.get(l);
			VertexConsumer vc = null;
			for (int k = 0; k < sections; k++) {
				if (!visible[k]) continue;
				for (int lev = level[k]; lev < 3; lev++) {
					float[] d = parts[k * 3 + lev];
					if (d == null || d.length == 0) continue;
					if (vc == null) vc = consumers.getBuffer(layers.get(l));
					for (int q = 0; q < d.length; q += QUAD) {
						// achterkant naar de camera: wordt toch niet getekend
						if (d[q] * cx + d[q + 1] * cy + d[q + 2] * cz - d[q + 3] < -0.6f) continue;
						for (int i = q + 4, e = q + QUAD; i < e; i += STRIDE) {
							float x = d[i], y = d[i + 1], z = d[i + 2];
							vc.vertex(m00 * x + m10 * y + m20 * z + m30, m01 * x + m11 * y + m21 * z + m31, m02 * x + m12 * y + m22 * z + m32,
									Float.floatToRawIntBits(d[i + 5]), d[i + 3], d[i + 4], overlay, Float.floatToRawIntBits(d[i + 6]), nx, ny, nz);
						}
					}
				}
			}
		}
	}

""" + s[j:]
    a = "		int light = LightmapTextureManager.pack(blkL, skyL);\n		for (int i = 0; i < 4; i++) {\n"
    assert a in s, f
    s = s.replace(a, """		int light = LightmapTextureManager.pack(blkL, skyL);
		// echte normaal van het vlak (uit de hoekpunten), voor het overslaan van afgewende vlakken
		float ax = Float.intBitsToFloat(v[0]) + x, ay = Float.intBitsToFloat(v[1]) + y, az = Float.intBitsToFloat(v[2]) + z;
		float ux = Float.intBitsToFloat(v[stride]) + x - ax, uy = Float.intBitsToFloat(v[stride + 1]) + y - ay, uz = Float.intBitsToFloat(v[stride + 2]) + z - az;
		float wx = Float.intBitsToFloat(v[2 * stride]) + x - ax, wy = Float.intBitsToFloat(v[2 * stride + 1]) + y - ay, wz = Float.intBitsToFloat(v[2 * stride + 2]) + z - az;
		float qx = uy * wz - uz * wy, qy = uz * wx - ux * wz, qz = ux * wy - uy * wx;
		float ql = (float) Math.sqrt(qx * qx + qy * qy + qz * qz);
		if (ql < 1e-6f) {
			// ontaard vlak: nooit overslaan
			buf.add(0); buf.add(0); buf.add(0); buf.add(-1000f);
		} else {
			qx /= ql; qy /= ql; qz /= ql;
			if (qx * face.getOffsetX() + qy * face.getOffsetY() + qz * face.getOffsetZ() < 0) { qx = -qx; qy = -qy; qz = -qz; }
			buf.add(qx); buf.add(qy); buf.add(qz); buf.add(qx * ax + qy * ay + qz * az);
		}
		for (int i = 0; i < 4; i++) {
""")
    open(f, "w").write(s)
    print("ok", f)
