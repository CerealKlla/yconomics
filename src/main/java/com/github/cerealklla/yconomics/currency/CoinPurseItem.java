package com.github.cerealklla.yconomics.currency;

import com.github.cerealklla.yconomics.registration.ModItems;

import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

/**
 * Every player's always-present nugget reserve (design doc Section 2/currency -- see decisions.md,
 * 2026-09-25). Unstackable ({@code stacksTo(1)}): each instance carries its own stored count via
 * {@link ModItems#NUGGET_COUNT}, so two purses can't be merged/confused the way a normal stackable
 * item could be.
 *
 * <p>Deposits happen automatically (see {@code CoinPurseListener} -- on pickup and on any container
 * close) and are not this class's job. This class only owns the one manual action a player takes
 * directly on the item: right-click to withdraw a stack's worth back into their inventory, so they
 * can drop it or use it in a crafting grid. Withdrawn nuggets aren't gone from the economy -- if
 * left loose in the inventory, {@code CoinPurseListener} sweeps them back in the next time any
 * container (including the player's own inventory screen) closes.
 */
public class CoinPurseItem extends Item {

    // Vanilla's own max stack size for Items.GOLD_NUGGET -- withdrawing more than this in one go
    // would create a stack the player couldn't actually hold as a single ItemStack.
    private static final int WITHDRAW_AMOUNT = 64;

    public CoinPurseItem(Properties properties) {
        super(properties);
    }

    @Override
    public Component getName(ItemStack itemStack) {
        int count = itemStack.getOrDefault(ModItems.NUGGET_COUNT, 0);
        return Component.literal("Coin Purse (" + count + ")");
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }

        ItemStack purse = player.getItemInHand(hand);
        int stored = purse.getOrDefault(ModItems.NUGGET_COUNT, 0);
        if (stored <= 0) {
            return InteractionResult.CONSUME;
        }

        int withdrawn = Math.min(stored, WITHDRAW_AMOUNT);
        ItemStack nuggets = new ItemStack(Items.GOLD_NUGGET, withdrawn);
        if (!player.getInventory().add(nuggets)) {
            player.drop(nuggets, false);
        }
        purse.set(ModItems.NUGGET_COUNT, stored - withdrawn);
        return InteractionResult.SUCCESS_SERVER;
    }
}
