package com.megatanker.client;

/** Afstand van de derdepersoonscamera (F5) als je aan het roer zit; in te stellen met het muiswiel. */
public final class ShipCamera {
	public static final float DEFAULT = 70f, MIN = 6f, MAX = 140f;
	public static float distance = DEFAULT;

	private ShipCamera() {
	}
}
