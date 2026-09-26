package com.github.cerealklla.yconomics.bag;

import com.github.cerealklla.yconomics.registration.ModEntities;

import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * A dropped-item bag (design doc Section 4, see decisions.md) -- a world entity, not a block,
 * that collects a cluster of nearby drops instead of leaving them as loose {@code ItemEntity}s.
 * Right-click opens a real chest-style container ({@link ChestMenu#sixRows}, vanilla's own
 * generic 6-row menu/screen, reused wholesale -- no custom menu type or screen class needed since
 * this is exactly the same shape as a double chest).
 *
 * <p><b>54 slots, not literally unbounded</b> -- the design called for "no capacity cap ... if at
 * all technically feasible," but a truly unbounded inventory needs a custom scrolling screen,
 * real client-side work this pass doesn't build. 54 (a double chest's worth) is the largest size
 * vanilla's own generic container UI already supports with zero extra client code. The design's
 * own fallback -- "if there is no room they will be scattered on the ground by the loot bag" --
 * is exactly what happens once this fills up; see {@link LootBagListener#depositOrScatter}.
 */
public class LootBagEntity extends Entity implements MenuProvider {

    // Matches vanilla ItemEntity's own default despawn lifespan, so a bag's lifetime "feels" like
    // any other dropped item's, per the design doc's "same despawn timer as any other item" call.
    private static final int DESPAWN_TICKS = 6000;

    private final SimpleContainer items = new SimpleContainer(54);
    private int ticksUntilDespawn = DESPAWN_TICKS;

    public LootBagEntity(EntityType<? extends LootBagEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true; // Sits exactly where it's created -- no falling/pushing.

        // A floating "Loot Bag" nametag is the *entire* visual for this first version (see
        // bag.client.LootBagRenderer -- a bare-minimum EntityRenderer with no model of its own,
        // relying entirely on this to make the entity findable at all). Real client-rendered
        // work (a model/texture) is a follow-up, not built this pass.
        setCustomName(Component.literal("Loot Bag"));
        setCustomNameVisible(true);
    }

    public static LootBagEntity create(ServerLevel level, double x, double y, double z) {
        LootBagEntity bag = new LootBagEntity(ModEntities.LOOT_BAG.get(), level);
        bag.setPos(x, y, z);
        level.addFreshEntity(bag);
        return bag;
    }

    /** True if this bag actually held the item afterward (may be a partial merge -- check the returned remainder). */
    public ItemStack depositItem(ItemStack stack) {
        ItemStack remainder = stack;
        for (int slot = 0; slot < items.getContainerSize() && !remainder.isEmpty(); slot++) {
            remainder = tryMergeIntoSlot(slot, remainder);
        }
        if (remainder.getCount() != stack.getCount()) {
            ticksUntilDespawn = DESPAWN_TICKS;
        }
        return remainder;
    }

    private ItemStack tryMergeIntoSlot(int slot, ItemStack incoming) {
        ItemStack existing = items.getItem(slot);
        if (existing.isEmpty()) {
            int placed = Math.min(incoming.getCount(), incoming.getMaxStackSize());
            items.setItem(slot, incoming.split(placed));
            return incoming;
        }
        if (ItemStack.isSameItemSameComponents(existing, incoming)) {
            int room = existing.getMaxStackSize() - existing.getCount();
            if (room > 0) {
                int moved = Math.min(room, incoming.getCount());
                existing.grow(moved);
                incoming.shrink(moved);
            }
        }
        return incoming;
    }

    public boolean isEmpty() {
        return items.isEmpty();
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide()) {
            return;
        }
        if (items.isEmpty()) {
            discard();
            return;
        }
        if (--ticksUntilDespawn <= 0) {
            discard();
        }
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand, Vec3 location) {
        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
        }
        if (!level().isClientSide() && player instanceof ServerPlayer serverPlayer) {
            serverPlayer.openMenu(this);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return ChestMenu.sixRows(containerId, inventory, items);
    }

    @Override
    public Component getDisplayName() {
        return Component.literal("Loot Bag");
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        return false; // Immune -- no explosion/fire-destroys-the-bag-and-loses-everything edge case to handle.
    }

    // Entity#isPickable() defaults to false -- without this override, the entity is invisible to
    // the game's own crosshair/interaction raycast entirely (confirmed the hard way: a real
    // playtest report of "there's a nametag but nothing to actually click on," see decisions.md
    // 2026-09-25). This is what makes right-click targeting -- and so #interact -- reachable at all.
    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        // No synced data needed -- nothing about a bag's appearance depends on its contents, and
        // the menu's own slot-sync packets handle the container contents once it's opened.
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        ContainerHelper.loadAllItems(input, items.getItems());
        ticksUntilDespawn = input.getIntOr("TicksUntilDespawn", DESPAWN_TICKS);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        ContainerHelper.saveAllItems(output, items.getItems());
        output.putInt("TicksUntilDespawn", ticksUntilDespawn);
    }
}
