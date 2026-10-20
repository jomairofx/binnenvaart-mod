package nl.greatrivers.client;

import net.fabricmc.api.ClientModInitializer;

public class GreatRiversClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		ClientSelfTest.register();
	}
}
