package nl.kempenaar.block;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventories;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.text.Text;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;

/** 54 vakken, net als een dubbele kist; trechters kunnen erin en eruit. */
public class CraneStorageBlockEntity extends BlockEntity implements Inventory, NamedScreenHandlerFactory {
	public static final int SLOTS = 54;
	private final DefaultedList<ItemStack> items = DefaultedList.ofSize(SLOTS, ItemStack.EMPTY);

	public CraneStorageBlockEntity(BlockPos pos, BlockState state) {
		super(nl.kempenaar.Kempenaar.CRANE_STORAGE_BE, pos, state);
	}

	/** Zoveel mogelijk van de stapel opslaan; geeft de rest terug. */
	public ItemStack insert(ItemStack st) {
		for (int k = 0; k < SLOTS && !st.isEmpty(); k++) {
			ItemStack slot = items.get(k);
			if (!slot.isEmpty() && ItemStack.areItemsAndComponentsEqual(slot, st)) {
				int n = Math.min(st.getCount(), slot.getMaxCount() - slot.getCount());
				slot.increment(n);
				st.decrement(n);
			}
		}
		for (int k = 0; k < SLOTS && !st.isEmpty(); k++) {
			if (items.get(k).isEmpty()) {
				items.set(k, st);
				st = ItemStack.EMPTY;
			}
		}
		markDirty();
		return st;
	}

	public boolean hasEmptySlot() {
		for (ItemStack s : items) if (s.isEmpty()) return true;
		return false;
	}

	public int countItems() {
		int n = 0;
		for (ItemStack s : items) n += s.getCount();
		return n;
	}

	// voor de weergave: hoe vol en met welk blok (raw state id + 1), bijgehouden op de client
	public float shownFill;
	public int shownMat;
	private int syncCooldown;

	@Override
	public void markDirty() {
		super.markDirty();
		if (world != null && !world.isClient && syncCooldown <= 0) {
			syncCooldown = 10;
			world.updateListeners(pos, getCachedState(), getCachedState(), net.minecraft.block.Block.NOTIFY_LISTENERS);
		}
	}

	public static void tick(net.minecraft.world.World world, BlockPos pos, BlockState state, CraneStorageBlockEntity be) {
		if (be.syncCooldown > 0 && --be.syncCooldown == 0) world.updateListeners(pos, state, state, net.minecraft.block.Block.NOTIFY_LISTENERS);
	}

	private float fillFraction() {
		int used = 0;
		for (ItemStack s : items) if (!s.isEmpty()) used++;
		return used / (float) SLOTS;
	}

	private int material() {
		for (ItemStack s : items)
			if (s.getItem() instanceof net.minecraft.item.BlockItem bi) return net.minecraft.block.Block.getRawIdFromState(bi.getBlock().getDefaultState()) + 1;
		return 0;
	}

	@Override
	public net.minecraft.network.packet.Packet<net.minecraft.network.listener.ClientPlayPacketListener> toUpdatePacket() {
		return net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket.create(this);
	}

	@Override
	public NbtCompound toInitialChunkDataNbt(RegistryWrapper.WrapperLookup registries) {
		NbtCompound n = new NbtCompound();
		n.putFloat("ShownFill", fillFraction());
		n.putInt("ShownMat", material());
		return n;
	}

	@Override
	protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
		super.writeNbt(nbt, registries);
		Inventories.writeNbt(nbt, items, registries);
	}

	@Override
	protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
		super.readNbt(nbt, registries);
		if (nbt.contains("ShownFill")) {
			shownFill = nbt.getFloat("ShownFill");
			shownMat = nbt.getInt("ShownMat");
		}
		if (nbt.contains("Items")) {
			for (int i = 0; i < SLOTS; i++) items.set(i, ItemStack.EMPTY);
			Inventories.readNbt(nbt, items, registries);
		}
	}

	@Override public int size() { return SLOTS; }

	@Override
	public boolean isEmpty() {
		for (ItemStack s : items) if (!s.isEmpty()) return false;
		return true;
	}

	@Override public ItemStack getStack(int slot) { return items.get(slot); }

	@Override
	public ItemStack removeStack(int slot, int amount) {
		ItemStack r = Inventories.splitStack(items, slot, amount);
		if (!r.isEmpty()) markDirty();
		return r;
	}

	@Override public ItemStack removeStack(int slot) { return Inventories.removeStack(items, slot); }

	@Override
	public void setStack(int slot, ItemStack stack) {
		items.set(slot, stack);
		stack.capCount(getMaxCount(stack));
		markDirty();
	}

	@Override public boolean canPlayerUse(PlayerEntity player) { return Inventory.canPlayerUse(this, player); }

	@Override public void clear() { items.clear(); }

	@Override
	public Text getDisplayName() {
		return Text.literal("Crane Storage " + getCachedState().get(CraneStorageBlock.BIN));
	}

	@Override
	public ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity player) {
		return GenericContainerScreenHandler.createGeneric9x6(syncId, playerInventory, this);
	}
}
