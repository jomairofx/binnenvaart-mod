package nl.binnenvaart.net;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Netwerkberichten tussen speler en server. */
public final class ShipPayloads {
	private ShipPayloads() {}

	/** Bediening vanaf het roer: telegraafstand, roer, boegschroef en knoppen. */
	public record Control(int entityId, int throttle, int rudder, int thruster, boolean horn, boolean toggleHatches) implements CustomPayload {
		public static final Id<Control> ID = new Id<>(Identifier.of("binnenvaart", "control"));
		public static final PacketCodec<RegistryByteBuf, Control> CODEC = PacketCodec.of(Control::write, Control::read);

		private void write(RegistryByteBuf buf) {
			buf.writeVarInt(entityId);
			buf.writeByte(throttle);
			buf.writeByte(rudder);
			buf.writeByte(thruster);
			buf.writeBoolean(horn);
			buf.writeBoolean(toggleHatches);
		}

		private static Control read(RegistryByteBuf buf) {
			return new Control(buf.readVarInt(), buf.readByte(), buf.readByte(), buf.readByte(), buf.readBoolean(), buf.readBoolean());
		}

		@Override
		public Id<? extends CustomPayload> getId() {
			return ID;
		}
	}

	/** Klik op een plek op het schip (in sjablooncoördinaten, berekend door de speler). */
	public record Interact(int entityId, float x, float y, float z, boolean sneaking) implements CustomPayload {
		public static final Id<Interact> ID = new Id<>(Identifier.of("binnenvaart", "interact"));
		public static final PacketCodec<RegistryByteBuf, Interact> CODEC = PacketCodec.of(Interact::write, Interact::read);

		private void write(RegistryByteBuf buf) {
			buf.writeVarInt(entityId);
			buf.writeFloat(x);
			buf.writeFloat(y);
			buf.writeFloat(z);
			buf.writeBoolean(sneaking);
		}

		private static Interact read(RegistryByteBuf buf) {
			return new Interact(buf.readVarInt(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readBoolean());
		}

		@Override
		public Id<? extends CustomPayload> getId() {
			return ID;
		}
	}

	/** Gewijzigde blokken op het schip (deuren, luiken, hendels). */
	public record Blocks(int entityId, long[] positions, int[] states) implements CustomPayload {
		public static final Id<Blocks> ID = new Id<>(Identifier.of("binnenvaart", "blocks"));
		public static final PacketCodec<RegistryByteBuf, Blocks> CODEC = PacketCodec.of(Blocks::write, Blocks::read);

		private void write(RegistryByteBuf buf) {
			buf.writeVarInt(entityId);
			buf.writeVarInt(positions.length);
			for (int i = 0; i < positions.length; i++) {
				buf.writeLong(positions[i]);
				buf.writeVarInt(states[i]);
			}
		}

		private static Blocks read(RegistryByteBuf buf) {
			int id = buf.readVarInt();
			int n = buf.readVarInt();
			long[] p = new long[n];
			int[] s = new int[n];
			for (int i = 0; i < n; i++) {
				p[i] = buf.readLong();
				s[i] = buf.readVarInt();
			}
			return new Blocks(id, p, s);
		}

		@Override
		public Id<? extends CustomPayload> getId() {
			return ID;
		}
	}
}
