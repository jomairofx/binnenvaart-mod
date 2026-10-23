package nl.greatrivers;

/** For the client tests: where the special blocks of a town are. */
public final class VillageBuilderAccess {
	private VillageBuilderAccess() {
	}

	public static int[] special(Villages.Town t, int n) {
		return VillageBuilder.special(t, n);
	}
}
