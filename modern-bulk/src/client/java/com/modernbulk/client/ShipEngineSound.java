package com.modernbulk.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.MovingSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import com.modernbulk.ship.ShipEntity;

/**
 * Scheepsdiesel in twee lagen: stationair gebrom en het zware geluid onder belasting.
 * Het toerental (pitch) loopt langzaam op als je gas geeft, net als bij een echte motor.
 */
public class ShipEngineSound extends MovingSoundInstance {
	static final SoundEvent IDLE = SoundEvent.of(Identifier.of("modernbulk", "engine_idle"), 40f);
	static final SoundEvent LOAD = SoundEvent.of(Identifier.of("modernbulk", "engine_load"), 48f);
	private static final float PITCH_FACTOR = 1.0f;
	private final ShipEntity ship;
	private final boolean loadLayer;

	public ShipEngineSound(ShipEntity ship, boolean loadLayer) {
		super(loadLayer ? LOAD : IDLE, SoundCategory.NEUTRAL, SoundInstance.createRandom());
		this.ship = ship;
		this.loadLayer = loadLayer;
		this.repeat = true;
		this.repeatDelay = 0;
		this.volume = 0.01f;
		this.pitch = 0.85f * PITCH_FACTOR;
		updatePos();
	}

	private void updatePos() {
		Vec3d p = ship.toWorld(14, -2, 8.5);
		this.x = p.x;
		this.y = p.y;
		this.z = p.z;
	}

	@Override
	public boolean shouldAlwaysPlay() {
		return true;
	}

	@Override
	public void tick() {
		var player = MinecraftClient.getInstance().player;
		if (ship.isRemoved() || player == null || ship.squaredDistanceTo(player) > 120 * 120) {
			setDone();
			return;
		}
		updatePos();
		float l = ship.engineLoad;
		boolean running = ship.syncedThrottle() != 0;
		float push = Math.max(0f, ship.engineTarget() - l); // gas geven: even wat harder
		// telegraaf op Stop = motor uit; anders zwaar en vrij zacht, zodat hij niet overheerst
		float targetVol = !running ? 0f : loadLayer ? 0.02f + 0.5f * l + 0.18f * push : 0.3f * (1f - 0.5f * l);
		float targetPitch = (0.64f + 0.42f * l + 0.04f * push) * PITCH_FACTOR;
		this.volume += (targetVol - this.volume) * (running ? 0.08f : 0.035f);
		if (!running) targetPitch = this.pitch + (0.55f * PITCH_FACTOR - this.pitch) * 0.04f; // uitlopen
		this.pitch = targetPitch;
	}
}
