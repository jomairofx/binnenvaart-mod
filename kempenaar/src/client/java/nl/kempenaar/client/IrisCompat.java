package nl.kempenaar.client;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/** Vraagt (zonder harde afhankelijkheid) aan Iris of nu de schaduwen getekend worden. */
final class IrisCompat {
	private static MethodHandle shadowPass;
	private static Object api;
	private static boolean tried;

	private IrisCompat() {
	}

	static boolean isShadowPass() {
		if (!tried) {
			tried = true;
			try {
				Class<?> c = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
				api = c.getMethod("getInstance").invoke(null);
				shadowPass = MethodHandles.publicLookup().findVirtual(c, "isRenderingShadowPass", MethodType.methodType(boolean.class));
			} catch (Throwable t) {
				shadowPass = null;
			}
		}
		if (shadowPass == null) return false;
		try {
			return (boolean) shadowPass.invoke(api);
		} catch (Throwable t) {
			return false;
		}
	}
}
