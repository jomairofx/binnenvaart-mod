package com.inlandbarge.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.MovingSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Vec3d;
import com.inlandbarge.ship.ShipEntity;

/** Het dreunen van de scheepsdiesel; harder en hoger bij meer vermogen. */
public class ShipEngineSound extends MovingSoundInstance {
	private final ShipEntity ship;

	public ShipEngineSound(ShipEntity ship) {
		super(SoundEvents.BLOCK_BEACON_AMBIENT, SoundCategory.NEUTRAL, SoundInstance.createRandom());
		this.ship = ship;
		this.repeat = true;
		this.repeatDelay = 0;
		this.volume = 0.2f;
		this.pitch = 0.5f;
		updatePos();
	}

	private void updatePos() {
		Vec3d p = ship.toWorld(10, -1, 5.5);
		this.x = p.x;
		this.y = p.y;
		this.z = p.z;
	}

	@Override
	public void tick() {
		var player = MinecraftClient.getInstance().player;
		if (ship.isRemoved() || player == null || ship.squaredDistanceTo(player) > 120 * 120) {
			setDone();
			return;
		}
		updatePos();
		float thr = Math.abs(ship.syncedThrottle()) / 3f;
		float targetVol = 0.25f + 0.55f * thr;
		float targetPitch = 0.5f + 0.25f * thr;
		this.volume += (targetVol - this.volume) * 0.05f;
		this.pitch += (targetPitch - this.pitch) * 0.05f;
	}
}
