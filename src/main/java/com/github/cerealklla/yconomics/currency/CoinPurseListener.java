package com.github.cerealklla.yconomics.currency;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.github.cerealklla.yconomics.registration.ModItems;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.item.ItemTossEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * The Coin Purse mechanic (design doc Section 2/currency, see decisions.md 2026-09-25) -- every
 * player always carries exactly one {@link CoinPurseItem}, which silently absorbs loose {@link
 * Items#GOLD_NUGGET} at two trigger points (never continuously -- see below) and pays out for
 * villager trading automatically.
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
 * alternate reserve), which the sweep would otherwise make impossible by vacuuming everything into
 * the purse the moment it's picked up. Resolved by auto-withdrawing the full purse balance into the
 * player's inventory whenever a {@code MerchantMenu} opens ({@link #onContainerOpen}) -- shopping
 * "just works" without the player manually managing coins, and {@link #onContainerClose} (which
 * fires for every container, this one included) sweeps whatever's left back in afterward.
 */
public final class CoinPurseListener {

    // Retained (not dropped) nugget count from a death, keyed by player UUID, bridging death to
    // respawn -- a plain in-memory map rather than a NeoForge attachment, since attachments do NOT
    // survive death by default either (see lyfe-context decisions.md, 2026-09-25, for the exact
    // same gotcha found independently there) and this is short-lived cross-respawn state anyway,
    // not something that needs to survive a server restart.
    private final Map<UUID, Integer> retainedOnDeath = new HashMap<>();

    // What fraction of a purse's contents stay on the body (as loose nuggets, which fold into
    // whatever the dropped-item bag mechanic is once it exists) rather than being returned directly.
    private static final double RETAINED_ON_BODY_FRACTION = 0.10;

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
                purse.set(ModItems.NUGGET_COUNT, purse.getOrDefault(ModItems.NUGGET_COUNT, 0) + retained);
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

    @SubscribeEvent
    public void onContainerOpen(PlayerContainerEvent.Open event) {
        if (event.getContainer() instanceof MerchantMenu && event.getEntity() instanceof ServerPlayer player) {
            withdrawAllIntoInventory(player);
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
        int count = event.getEntity().getItem().getOrDefault(ModItems.NUGGET_COUNT, 0);
        event.getEntity().discard();

        ItemStack replacement = new ItemStack(ModItems.COIN_PURSE.get());
        replacement.set(ModItems.NUGGET_COUNT, count);
        if (!player.getInventory().add(replacement)) {
            player.drop(replacement, false);
        }
    }

    /**
     * Strips the purse out of the player's inventory before vanilla's own death-drop logic runs
     * (this event fires at the very start of the death process), so it never becomes a normal,
     * lootable world drop. {@link #RETAINED_ON_BODY_FRACTION} of its contents drop as loose nuggets
     * at the death location instead (the "10% remain on the body" behavior) -- the rest is stashed
     * in {@link #retainedOnDeath} and handed back on {@link #onPlayerRespawn}.
     */
    @SubscribeEvent
    public void onLivingDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        ItemStack purse = findPurse(player);
        if (purse == null) {
            return;
        }
        int total = purse.getOrDefault(ModItems.NUGGET_COUNT, 0);
        removePurseFromInventory(player, purse);

        if (total <= 0) {
            retainedOnDeath.put(player.getUUID(), 0);
            return;
        }

        int leftOnBody = (int) Math.round(total * RETAINED_ON_BODY_FRACTION);
        int returnedToPlayer = total - leftOnBody;
        retainedOnDeath.put(player.getUUID(), returnedToPlayer);

        if (leftOnBody > 0 && player.level() instanceof ServerLevel serverLevel) {
            ItemEntity drop = new ItemEntity(serverLevel, player.getX(), player.getY(), player.getZ(),
                    new ItemStack(Items.GOLD_NUGGET, leftOnBody));
            serverLevel.addFreshEntity(drop);
        }
    }

    /** Adds a fresh, empty purse if the player doesn't already have one -- never touches an existing one. */
    private static void ensurePurse(ServerPlayer player) {
        if (findPurse(player) == null) {
            ItemStack fresh = new ItemStack(ModItems.COIN_PURSE.get());
            fresh.set(ModItems.NUGGET_COUNT, 0);
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

    /** Removes every loose {@link Items#GOLD_NUGGET} stack from the player's inventory (including equipment slots) into the purse. */
    private static void sweepNuggetsIntoPurse(ServerPlayer player) {
        ItemStack purse = findPurse(player);
        if (purse == null) {
            return;
        }
        Inventory inventory = player.getInventory();
        int collected = 0;
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.is(Items.GOLD_NUGGET)) {
                collected += stack.getCount();
                inventory.setItem(i, ItemStack.EMPTY);
            }
        }
        if (collected > 0) {
            purse.set(ModItems.NUGGET_COUNT, purse.getOrDefault(ModItems.NUGGET_COUNT, 0) + collected);
        }
    }

    /** Auto-refill for shopping (see class doc) -- moves the purse's entire balance into the inventory as real nugget stacks. */
    private static void withdrawAllIntoInventory(ServerPlayer player) {
        ItemStack purse = findPurse(player);
        if (purse == null) {
            return;
        }
        int stored = purse.getOrDefault(ModItems.NUGGET_COUNT, 0);
        if (stored <= 0) {
            return;
        }
        int remaining = stored;
        while (remaining > 0) {
            int chunk = Math.min(remaining, 64);
            ItemStack nuggets = new ItemStack(Items.GOLD_NUGGET, chunk);
            if (!player.getInventory().add(nuggets)) {
                player.drop(nuggets, false);
            }
            remaining -= chunk;
        }
        purse.set(ModItems.NUGGET_COUNT, 0);
    }
}
