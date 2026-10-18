package nl.kempenaar.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.List;

@Mixin(Entity.class)
public interface EntityInvoker {
	@Invoker("adjustMovementForCollisions")
	static Vec3d kempenaar$adjust(Vec3d movement, Box entityBoundingBox, List<VoxelShape> collisions) {
		throw new AssertionError();
	}
}
