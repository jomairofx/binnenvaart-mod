package com.speedboat;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import com.speedboat.ship.ShipEntity;
import com.speedboat.ship.ShipTemplate;

/** Rechtsklik op water: het schip verschijnt met de achtersteven bij de plek waar je klikt en de boeg in je kijkrichting. */
public class ShipItem extends Item {
	public ShipItem(Settings settings) {
		super(settings);
	}

	@Override
	public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
		ItemStack stack = user.getStackInHand(hand);
		BlockHitResult hit = raycast(world, user, RaycastContext.FluidHandling.SOURCE_ONLY);
		if (hit.getType() != HitResult.Type.BLOCK) return TypedActionResult.pass(stack);
		BlockPos pos = hit.getBlockPos();
		if (!world.getFluidState(pos).isIn(FluidTags.WATER)) return TypedActionResult.pass(stack);
		if (world.isClient) return TypedActionResult.success(stack);

		BlockPos.Mutable top = pos.mutableCopy();
		while (world.getFluidState(top.up()).isIn(FluidTags.WATER) && top.getY() < pos.getY() + 32) top.move(0, 1, 0);
		double yaw = Math.toRadians(user.getYaw() + 90.0);
		double half = ShipTemplate.CX;
		double x = pos.getX() + 0.5 + Math.cos(yaw) * half;
		double z = pos.getZ() + 0.5 + Math.sin(yaw) * half;

		ShipEntity ship = new ShipEntity(Speedboat.SHIP, world);
		ship.place(x, top.getY(), z, yaw);
		if (ship.hullBlocked(ship.getX(), ship.getY(), ship.getZ(), ship.shipYaw)) {
			user.sendMessage(Text.literal("The speedboat does not fit here: it is 15 blocks long and 4 wide. Look out over open water.").formatted(Formatting.RED), false);
			return TypedActionResult.fail(stack);
		}
		world.spawnEntity(ship);
		stack.decrementUnlessCreative(1, user);
		user.sendMessage(Text.literal("The " + ship.template.name + " is ready. Click the driver's seat to take the wheel.").formatted(Formatting.AQUA), true);
		return TypedActionResult.success(stack);
	}
}
