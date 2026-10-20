package nl.kempenaar.net;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

/** Bedieningspaneel van de laadkraan. */
public final class CranePayloads {
	private CranePayloads() {}

	/** Server -> speler: open het paneel met de huidige stand. */
	public record Panel(BlockPos pos, boolean unloading, boolean enabled, boolean paused) implements CustomPayload {
		public static final Id<Panel> ID = new Id<>(Identifier.of("kempenaar", "crane_panel"));
		public static final PacketCodec<RegistryByteBuf, Panel> CODEC = PacketCodec.of(Panel::write, Panel::read);

		private void write(RegistryByteBuf buf) {
			buf.writeBlockPos(pos);
			buf.writeBoolean(unloading);
			buf.writeBoolean(enabled);
			buf.writeBoolean(paused);
		}

		private static Panel read(RegistryByteBuf buf) {
			return new Panel(buf.readBlockPos(), buf.readBoolean(), buf.readBoolean(), buf.readBoolean());
		}

		@Override
		public Id<? extends CustomPayload> getId() {
			return ID;
		}
	}

	/** Speler -> server: knop ingedrukt (0 laden, 1 lossen, 2 go/stop, 3 pauze). */
	public record Command(BlockPos pos, int cmd) implements CustomPayload {
		public static final Id<Command> ID = new Id<>(Identifier.of("kempenaar", "crane_command"));
		public static final PacketCodec<RegistryByteBuf, Command> CODEC = PacketCodec.of(Command::write, Command::read);

		private void write(RegistryByteBuf buf) {
			buf.writeBlockPos(pos);
			buf.writeByte(cmd);
		}

		private static Command read(RegistryByteBuf buf) {
			return new Command(buf.readBlockPos(), buf.readByte());
		}

		@Override
		public Id<? extends CustomPayload> getId() {
			return ID;
		}
	}
}
