package com.speedboat.client;

/** Afstand van de derdepersoonscamera (F5) als je aan het roer zit; in te stellen met het muiswiel. */
public final class ShipCamera {
	public static final float DEFAULT = 14f, MIN = 5f, MAX = 60f;
	public static float distance = DEFAULT;

	private ShipCamera() {
	}
}
