package nl.kempenaar.client;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import nl.kempenaar.net.CranePayloads;

/** Bedieningspaneel van de laadkraan: laden/lossen, go/stop en pauze. */
public class CraneScreen extends Screen {
	private final BlockPos pos;
	private boolean unloading, enabled, paused;

	public CraneScreen(CranePayloads.Panel p) {
		super(Text.literal("Cargo Crane Control"));
		this.pos = p.pos();
		update(p);
	}

	public BlockPos pos() {
		return pos;
	}

	public void update(CranePayloads.Panel p) {
		unloading = p.unloading();
		enabled = p.enabled();
		paused = p.paused();
		if (client != null) clearAndInit();
	}

	@Override
	protected void init() {
		int cx = width / 2, y = height / 2 - 40;
		addDrawableChild(ButtonWidget.builder(Text.literal((unloading ? "" : "> ") + "Load"), b -> send(0)).dimensions(cx - 102, y, 100, 20).build());
		addDrawableChild(ButtonWidget.builder(Text.literal((unloading ? "> " : "") + "Unload"), b -> send(1)).dimensions(cx + 2, y, 100, 20).build());
		addDrawableChild(ButtonWidget.builder(Text.literal(enabled ? "STOP" : "GO"), b -> send(2)).dimensions(cx - 102, y + 26, 100, 20).build());
		addDrawableChild(ButtonWidget.builder(Text.literal(paused ? "Resume" : "Pause"), b -> send(3)).dimensions(cx + 2, y + 26, 100, 20).build());
		addDrawableChild(ButtonWidget.builder(Text.literal("Close"), b -> close()).dimensions(cx - 50, y + 60, 100, 20).build());
	}

	private void send(int cmd) {
		ClientPlayNetworking.send(new CranePayloads.Command(pos, cmd));
	}

	@Override
	public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
		super.render(ctx, mouseX, mouseY, delta);
		int cx = width / 2, y = height / 2 - 72;
		ctx.drawCenteredTextWithShadow(textRenderer, title, cx, y, 0xFFFFFF);
		String state = (enabled ? (paused ? "PAUSED" : "RUNNING") : "STOPPED") + "  -  " + (unloading ? "unloading into the storage bins" : "loading into the ship");
		ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(state), cx, y + 14, enabled ? (paused ? 0xFFC040 : 0x60E060) : 0xFF6060);
	}

	@Override
	public boolean shouldPause() {
		return false;
	}
}
