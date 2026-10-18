package com.inlandbarge.client;

/** Afstand van de derdepersoonscamera (F5) als je aan het roer zit; in te stellen met het muiswiel. */
public final class ShipCamera {
	public static final float DEFAULT = 40f, MIN = 6f, MAX = 80f;
	public static float distance = DEFAULT;

	private ShipCamera() {
	}
}
