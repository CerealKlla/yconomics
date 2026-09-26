package com.github.cerealklla.yconomics.currency;

import com.github.cerealklla.yconomics.api.Yconomics;
import com.github.cerealklla.yconomics.registration.ModItems;

import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

/**
 * Every player's always-present nugget reserve (design doc Section 2/currency -- see decisions.md,
 * 2026-09-25). Unstackable ({@code stacksTo(1)}): each instance carries its own stored contents
 * via {@link ModItems#COIN_PURSE_CONTENTS} (a list of real Gold Nugget stacks, see {@link
 * CoinPurseContents}), capped by the owning player's current tier (see {@link
 * Yconomics#getCoinPurseTier}) -- capacity is a per-player fact, not a per-item one, so a purse's
 * effective limit changes immediately if the player's tier changes, with no item migration needed.
 *
 * <p><b>Interacts like a vanilla Bundle</b> (a real playtest request, "the same way you can with a
 * Bundle, but the only thing that can go into it is Gold Nuggets") -- {@link
 * #overrideStackedOnOther}/{@link #overrideOtherStackedOnMe} are the exact same interception
 * points vanilla's own {@code BundleItem} uses (any inventory-slot click involving the bundle as
 * either the carried or clicked stack routes through these, in the player's own vanilla inventory
 * screen, no custom menu needed), just restricted to Gold Nuggets and backed by {@link
 * CoinPurseContents} instead of {@code BundleContents}. Deliberately not a full copy of vanilla's
 * behavior, though: plain {@link #use} pops exactly one stack (up to 64) instead of vanilla
 * Bundle's "hold right-click to progressively dump everything" -- explicitly requested, since
 * dropping money for another player one stack at a time is the actual use case, not dumping an
 * entire purse on the ground.
 */
public class CoinPurseItem extends Item {

    public CoinPurseItem(Properties properties) {
        super(properties);
    }

    private static CoinPurseContents contentsOf(ItemStack stack) {
        return stack.getOrDefault(ModItems.COIN_PURSE_CONTENTS, CoinPurseContents.EMPTY);
    }

    @Override
    public Component getName(ItemStack itemStack) {
        CoinPurseContents contents = contentsOf(itemStack);
        return Component.literal("Coin Purse (" + contents.totalCount() + ")");
    }

    /**
     * The carried stack (this purse) is clicked onto {@code slot}'s stack -- try to stuff that
     * stack in. Uses {@code slot.safeTake}, the same "properly remove respecting modification
     * rules" primitive vanilla's own {@code BundleContents.Mutable#tryTransfer} uses, rather than
     * mutating {@code slot.getItem()} directly.
     */
    @Override
    public boolean overrideStackedOnOther(ItemStack self, Slot slot, ClickAction clickAction, Player player) {
        if (clickAction != ClickAction.PRIMARY) {
            return false;
        }
        ItemStack clicked = slot.getItem();
        if (clicked.isEmpty() || !clicked.is(Items.GOLD_NUGGET)) {
            return false;
        }
        int capacity = Yconomics.coinPurseCapacity(Yconomics.getCoinPurseTier(player));
        int available = Math.max(0, capacity - contentsOf(self).totalCount());
        ItemStack taken = slot.safeTake(clicked.getCount(), available, player);
        depositAndReturnLeftover(self, taken, capacity, slot, player);
        return true;
    }

    /** Some other stack is clicked onto the purse (the purse is {@code slot}'s own item, {@code other} is carried). */
    @Override
    public boolean overrideOtherStackedOnMe(ItemStack self, ItemStack other, Slot slot, ClickAction clickAction, Player player, SlotAccess carriedItem) {
        if (clickAction != ClickAction.PRIMARY || other.isEmpty() || !other.is(Items.GOLD_NUGGET) || !slot.allowModification(player)) {
            return false;
        }
        int capacity = Yconomics.coinPurseCapacity(Yconomics.getCoinPurseTier(player));
        CoinPurseContents.InsertResult result = contentsOf(self).insert(other, capacity);
        self.set(ModItems.COIN_PURSE_CONTENTS, result.contents());
        broadcastChanges(player);
        return true;
    }

    /** Inserts as much of {@code taken} as fits, putting any leftover (the purse is already full) back into the slot. */
    private static void depositAndReturnLeftover(ItemStack self, ItemStack taken, int capacity, Slot slot, Player player) {
        CoinPurseContents.InsertResult result = contentsOf(self).insert(taken, capacity);
        self.set(ModItems.COIN_PURSE_CONTENTS, result.contents());
        if (!taken.isEmpty()) {
            ItemStack remainder = slot.safeInsert(taken);
            if (!remainder.isEmpty()) {
                // Truly no room anywhere (slot and purse both full) -- shouldn't normally happen
                // since `available` already bounded how much safeTake removed, but fall back to
                // handing it back to the player rather than silently destroying it.
                if (!player.getInventory().add(remainder)) {
                    player.drop(remainder, false);
                }
            }
        }
        broadcastChanges(player);
    }

    private static void broadcastChanges(Player player) {
        if (player.containerMenu != null) {
            player.containerMenu.slotsChanged(player.getInventory());
        }
    }

    /** Pops exactly one stack (up to 64) into the player's inventory/hand, or drops it if the inventory is full. */
    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }

        ItemStack purse = player.getItemInHand(hand);
        CoinPurseContents.RemoveResult result = contentsOf(purse).removeLast();
        if (result.removed().isEmpty()) {
            return InteractionResult.CONSUME;
        }
        purse.set(ModItems.COIN_PURSE_CONTENTS, result.contents());

        if (!player.getInventory().add(result.removed())) {
            player.drop(result.removed(), false);
        }
        return InteractionResult.SUCCESS_SERVER;
    }
}
