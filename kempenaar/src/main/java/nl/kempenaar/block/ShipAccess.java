package nl.kempenaar.block;

import net.minecraft.entity.Entity;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Laat de kraan werken met elk vrachtschip uit deze serie mods (JOHANNA, HENDRIKA, STELLA MARIS),
 * zonder dat die mods van elkaar afhangen: de schepen hebben dezelfde opbouw, dus we zoeken de
 * methoden op naam op. Schepen zonder luiken (de tanker) doen niet mee.
 */
public final class ShipAccess {
	private record Info(Method hold, Method toLocal, Method toWorld, Method hatchesClosed, Field hatchProgress, Field template,
						Field holds, Field holdZ0, Field holdZ1, Field coamingTop, Field name) {}

	private static final Map<Class<?>, Object> CACHE = new ConcurrentHashMap<>();
	private static final Object NONE = new Object();

	private final Entity entity;
	private final Info info;
	private final Object template;

	private ShipAccess(Entity e, Info info) throws ReflectiveOperationException {
		this.entity = e;
		this.info = info;
		this.template = info.template.get(e);
	}

	@Nullable
	public static ShipAccess of(Entity e) {
		Object o = CACHE.computeIfAbsent(e.getClass(), ShipAccess::inspect);
		if (o == NONE) return null;
		try {
			return new ShipAccess(e, (Info) o);
		} catch (ReflectiveOperationException ex) {
			return null;
		}
	}

	private static Object inspect(Class<?> c) {
		if (!c.getSimpleName().equals("ShipEntity")) return NONE;
		try {
			Field covers = c.getField("COVERS");
			if (covers.getInt(null) <= 0) return NONE; // geen luiken: tanker
			Method hold = c.getMethod("hold", int.class);
			Method toLocal = c.getMethod("toLocal", Vec3d.class);
			Method toWorld = c.getMethod("toWorld", double.class, double.class, double.class);
			Method hatches = c.getMethod("hatchesClosed");
			Field progress = c.getField("hatchProgress");
			Field template = c.getField("template");
			Class<?> t = template.getType();
			return new Info(hold, toLocal, toWorld, hatches, progress, template,
					t.getField("holds"), t.getField("holdZ0"), t.getField("holdZ1"), t.getField("coamingTopY"), t.getField("name"));
		} catch (ReflectiveOperationException | RuntimeException ex) {
			return NONE;
		}
	}

	public Entity entity() {
		return entity;
	}

	public int holdCount() {
		return holds().length;
	}

	public int[][] holds() {
		try {
			return (int[][]) info.holds.get(template);
		} catch (ReflectiveOperationException ex) {
			return new int[0][];
		}
	}

	public int holdZ0() {
		return getInt(info.holdZ0);
	}

	public int holdZ1() {
		return getInt(info.holdZ1);
	}

	public double coamingTopY() {
		try {
			return info.coamingTop.getDouble(template);
		} catch (ReflectiveOperationException ex) {
			return 3.5;
		}
	}

	public String name() {
		try {
			return String.valueOf(info.name.get(template));
		} catch (ReflectiveOperationException ex) {
			return "ship";
		}
	}

	private int getInt(Field f) {
		try {
			return f.getInt(template);
		} catch (ReflectiveOperationException ex) {
			return 0;
		}
	}

	public SimpleInventory hold(int i) {
		try {
			return (SimpleInventory) info.hold.invoke(entity, i);
		} catch (ReflectiveOperationException ex) {
			throw new IllegalStateException(ex);
		}
	}

	public Vec3d toLocal(Vec3d w) {
		try {
			return (Vec3d) info.toLocal.invoke(entity, w);
		} catch (ReflectiveOperationException ex) {
			throw new IllegalStateException(ex);
		}
	}

	public Vec3d toWorld(double x, double y, double z) {
		try {
			return (Vec3d) info.toWorld.invoke(entity, x, y, z);
		} catch (ReflectiveOperationException ex) {
			throw new IllegalStateException(ex);
		}
	}

	/** Luiken helemaal open? */
	public boolean hatchesOpen() {
		try {
			return !(boolean) info.hatchesClosed.invoke(entity) && info.hatchProgress.getFloat(entity) <= 0.001f;
		} catch (ReflectiveOperationException ex) {
			return false;
		}
	}
}
