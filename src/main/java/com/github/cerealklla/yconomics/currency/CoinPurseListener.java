package com.github.cerealklla.yconomics.currency;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.github.cerealklla.yconomics.api.Yconomics;
import com.github.cerealklla.yconomics.bag.LootBagListener;
import com.github.cerealklla.yconomics.registration.ModItems;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.item.ItemTossEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * The Coin Purse mechanic (design doc Section 2/currency, see decisions.md 2026-09-25) -- every
 * player always carries exactly one {@link CoinPurseItem}, which silently absorbs loose {@link
 * Items#GOLD_NUGGET} at two trigger points (never continuously -- see below), pays out for
 * villager trading automatically, and can't be lost, hidden in the hotbar, or given away.
 *
 * <p><b>Why the sweep is trigger-based, not a continuous per-tick sweep</b>: the whole point of
 * letting a player pull nuggets out of the purse ({@link CoinPurseItem#use}) is so they sit loose
 * in the inventory long enough to be used in a crafting grid or dropped. A continuous sweep would
 * vacuum them back before the player could do anything with them. Instead, the sweep only runs on
 * ({@link #onItemPickup}) picking up a loose nugget, and ({@link #onContainerClose}) closing any
 * container (including the player's own inventory screen) -- exactly the two triggers the user
 * described ("automatically holds all your gold nuggets when you pick more up" / "when the
 * inventory closes all nuggets get moved back to the purse").
 *
 * <p><b>Vendor purchases</b>: villager/wandering-trader trading needs loose nuggets physically in
 * the trade-input slots (vanilla's {@code MerchantMenu} has no hook for pulling payment from an
 * alternate reserve, and its own internal "active offer" isn't exposed publicly enough to top up
 * exactly what one specific trade needs). The balance is meant to be spent from the purse's own
 * total, not physically handed over from the inventory, so this deliberately does NOT move the
 * whole balance into the inventory (a purse can hold thousands of nuggets at higher tiers -- far
 * more than 36 slots of 64-stacks could ever hold). Instead, while a {@code MerchantMenu} is open
 * ({@link #onPlayerTick}), it keeps just enough loose nuggets on hand to cover whatever the single
 * most expensive nugget-priced offer that merchant has costs -- topped up from the purse only as a
 * shortfall appears, never all at once -- and {@link #onContainerClose} sweeps every loose nugget
 * back into the purse the moment the screen closes, so nothing is ever left sitting loose in the
 * inventory. From the player's perspective the purse's total balance visibly funds any purchase.
 */
public final class CoinPurseListener {

    // Retained (not dropped) nugget count from a death, keyed by player UUID, bridging death to
    // respawn -- a plain in-memory map rather than a NeoForge attachment, since attachments do NOT
    // survive death by default either (see lyfe-context decisions.md, 2026-09-25, for the exact
    // same gotcha found independently there) and this is short-lived cross-respawn state anyway,
    // not something that needs to survive a server restart.
    private final Map<UUID, Integer> retainedOnDeath = new HashMap<>();

    // What fraction of a purse's contents stay on the body (as loose nuggets, deposited into the
    // dropped-item bag mechanic same as any other death drop) rather than being returned directly.
    private static final double RETAINED_ON_BODY_FRACTION = 0.10;

    // Inventory's own slot indexing: 0-8 are the hotbar, 9 is the main grid's row-0/col-0 slot --
    // the purse's one "home" position (a playtest request: never on the hotbar, always sorted to
    // (0,0), swapping with whatever's already there). Checked periodically, not on every single
    // inventory change (see #onPlayerTick) -- a full click-level intercept of the vanilla player
    // inventory menu would need far more invasive surgery than a small period of possibly seeing
    // the purse sit in the wrong slot for a few ticks is worth.
    private static final int PURSE_HOME_SLOT = 9;
    private static final int SLOT_CHECK_INTERVAL_TICKS = 4;

    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ensurePurse(player);
        }
    }

    @SubscribeEvent
    public void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        ensurePurse(player);

        Integer retained = retainedOnDeath.remove(player.getUUID());
        if (retained != null && retained > 0) {
            ItemStack purse = findPurse(player);
            if (purse != null) {
                purse.set(ModItems.COIN_PURSE_CONTENTS, CoinPurseContents.of(retained));
            }
        }
    }

    @SubscribeEvent
    public void onItemPickup(ItemEntityPickupEvent.Post event) {
        if (event.getOriginalStack().is(Items.GOLD_NUGGET) && event.getPlayer() instanceof ServerPlayer player) {
            sweepNuggetsIntoPurse(player);
        }
    }

    @SubscribeEvent
    public void onContainerClose(PlayerContainerEvent.Close event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            sweepNuggetsIntoPurse(player);
        }
    }

    /**
     * Every tick a player has a {@code MerchantMenu} open, tops up loose nuggets from the purse
     * up to whatever the single priciest nugget-costing offer at that merchant requires -- see the
     * class doc's "Vendor purchases" note for why this is bounded rather than moving the whole
     * balance. Checked every tick (not throttled, unlike {@link #enforcePurseHomeSlot}) since this
     * only runs at all while a trade screen is actually open, a short, rare state.
     */
    @SubscribeEvent
    public void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (player.tickCount % SLOT_CHECK_INTERVAL_TICKS == 0) {
            enforcePurseHomeSlot(player);
        }
        if (player.containerMenu instanceof MerchantMenu merchantMenu) {
            topUpForShopping(player, merchantMenu);
        }
    }

    /**
     * The purse can't actually be given away/lost by tossing it (Q or drag-out-of-inventory) --
     * {@link ItemTossEvent}'s own doc warns that cancelling it does NOT stop the item from being
     * removed from the inventory (only from entering the world), so simply cancelling would just
     * destroy it along with whatever it held. Instead: let the toss happen, immediately discard the
     * world entity it created, and hand the player back a fresh purse carrying the same balance.
     */
    @SubscribeEvent
    public void onItemToss(ItemTossEvent event) {
        if (!event.getEntity().getItem().is(ModItems.COIN_PURSE.get()) || !(event.getPlayer() instanceof ServerPlayer player)) {
            return;
        }
        CoinPurseContents contents = event.getEntity().getItem().getOrDefault(ModItems.COIN_PURSE_CONTENTS, CoinPurseContents.EMPTY);
        event.getEntity().discard();

        ItemStack replacement = new ItemStack(ModItems.COIN_PURSE.get());
        replacement.set(ModItems.COIN_PURSE_CONTENTS, contents);
        if (!player.getInventory().add(replacement)) {
            player.drop(replacement, false);
        }
    }

    /**
     * Strips the purse out of the player's inventory before vanilla's own death-drop logic runs
     * (this event fires at the very start of the death process), so it never becomes a normal,
     * lootable world drop. {@link #RETAINED_ON_BODY_FRACTION} of its contents drop as loose nuggets
     * at the death location instead (the "10% remain on the body" behavior, deposited into the
     * dropped-item bag mechanic via {@link LootBagListener#depositOrScatter} -- the same clustering
     * every other death drop goes through, not a raw untracked {@code ItemEntity}); the rest is
     * stashed in {@link #retainedOnDeath} and handed back on {@link #onPlayerRespawn}.
     */
    @SubscribeEvent
    public void onLivingDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        ItemStack purse = findPurse(player);
        if (purse == null) {
            return;
        }
        int total = purse.getOrDefault(ModItems.COIN_PURSE_CONTENTS, CoinPurseContents.EMPTY).totalCount();
        removePurseFromInventory(player, purse);

        if (total <= 0) {
            retainedOnDeath.put(player.getUUID(), 0);
            return;
        }

        int leftOnBody = (int) Math.round(total * RETAINED_ON_BODY_FRACTION);
        int returnedToPlayer = total - leftOnBody;
        retainedOnDeath.put(player.getUUID(), returnedToPlayer);

        if (leftOnBody > 0) {
            LootBagListener.depositOrScatter(serverLevel, player.position(), new ItemStack(Items.GOLD_NUGGET, leftOnBody));
        }
    }

    /** Adds a fresh, empty purse if the player doesn't already have one -- never touches an existing one. */
    private static void ensurePurse(ServerPlayer player) {
        if (findPurse(player) == null) {
            ItemStack fresh = new ItemStack(ModItems.COIN_PURSE.get());
            fresh.set(ModItems.COIN_PURSE_CONTENTS, CoinPurseContents.EMPTY);
            if (!player.getInventory().add(fresh)) {
                player.drop(fresh, false); // Best-effort: only reached with a completely full inventory.
            }
        }
    }

    private static ItemStack findPurse(Player player) {
        Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.is(ModItems.COIN_PURSE.get())) {
                return stack;
            }
        }
        return null;
    }

    private static void removePurseFromInventory(Player player, ItemStack purse) {
        Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            if (inventory.getItem(i) == purse) {
                inventory.setItem(i, ItemStack.EMPTY);
                return;
            }
        }
    }

    /** Never on the hotbar, always in {@link #PURSE_HOME_SLOT} -- swaps with whatever's currently there. */
    private static void enforcePurseHomeSlot(ServerPlayer player) {
        Inventory inventory = player.getInventory();
        int purseSlot = -1;
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            if (inventory.getItem(i).is(ModItems.COIN_PURSE.get())) {
                purseSlot = i;
                break;
            }
        }
        if (purseSlot == -1 || purseSlot == PURSE_HOME_SLOT) {
            return;
        }
        ItemStack purse = inventory.getItem(purseSlot);
        ItemStack displaced = inventory.getItem(PURSE_HOME_SLOT);
        inventory.setItem(PURSE_HOME_SLOT, purse);
        inventory.setItem(purseSlot, displaced);
    }

    /** Sweeps every loose {@link Items#GOLD_NUGGET} stack in the player's inventory (including equipment slots) into the purse, up to its capacity. */
    private static void sweepNuggetsIntoPurse(ServerPlayer player) {
        ItemStack purse = findPurse(player);
        if (purse == null) {
            return;
        }
        int capacity = Yconomics.coinPurseCapacity(Yconomics.getCoinPurseTier(player));
        CoinPurseContents contents = purse.getOrDefault(ModItems.COIN_PURSE_CONTENTS, CoinPurseContents.EMPTY);
        Inventory inventory = player.getInventory();
        boolean changed = false;
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.isEmpty() && stack.is(Items.GOLD_NUGGET)) {
                CoinPurseContents.InsertResult result = contents.insert(stack, capacity);
                contents = result.contents();
                changed |= result.inserted() > 0;
                // insert() shrinks `stack` in place by whatever fit -- any leftover (purse already
                // at capacity) is simply left sitting in this same slot, not lost.
                if (stack.isEmpty()) {
                    inventory.setItem(i, ItemStack.EMPTY);
                }
            }
        }
        if (changed) {
            purse.set(ModItems.COIN_PURSE_CONTENTS, contents);
        }
    }

    /**
     * Bounded auto-refill for shopping (see class doc) -- tops up loose nuggets only up to whatever
     * the single priciest nugget-costing offer at this merchant needs, withdrawing just the
     * shortfall from the purse each time (never the whole balance).
     */
    private static void topUpForShopping(ServerPlayer player, MerchantMenu merchantMenu) {
        int maxCost = maxSingleNuggetCost(merchantMenu.getOffers());
        if (maxCost <= 0) {
            return;
        }
        int loose = countLooseNuggets(player);
        if (loose >= maxCost) {
            return;
        }

        ItemStack purse = findPurse(player);
        if (purse == null) {
            return;
        }
        CoinPurseContents contents = purse.getOrDefault(ModItems.COIN_PURSE_CONTENTS, CoinPurseContents.EMPTY);
        CoinPurseContents.WithdrawResult result = contents.withdraw(maxCost - loose);
        if (result.withdrawn() <= 0) {
            return;
        }

        ItemStack nuggets = new ItemStack(Items.GOLD_NUGGET, result.withdrawn());
        if (player.getInventory().add(nuggets)) {
            purse.set(ModItems.COIN_PURSE_CONTENTS, result.contents());
        }
        // If it couldn't be placed anywhere (inventory completely full), leave the purse untouched
        // -- that trade just can't be completed right now, rather than losing the withdrawn amount.
    }

    private static int maxSingleNuggetCost(MerchantOffers offers) {
        int max = 0;
        for (MerchantOffer offer : offers) {
            int cost = nuggetAmount(offer.getItemCostA().itemStack());
            Optional<ItemCost> costB = offer.getItemCostB();
            if (costB.isPresent()) {
                cost += nuggetAmount(costB.get().itemStack());
            }
            max = Math.max(max, cost);
        }
        return max;
    }

    private static int nuggetAmount(ItemStack stack) {
        return stack.is(Items.GOLD_NUGGET) ? stack.getCount() : 0;
    }

    private static int countLooseNuggets(Player player) {
        Inventory inventory = player.getInventory();
        int total = 0;
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.is(Items.GOLD_NUGGET)) {
                total += stack.getCount();
            }
        }
        return total;
    }
}
