package com.github.cerealklla.yconomics.api;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.github.cerealklla.yconomics.bills.BillTransferEngine;
import com.github.cerealklla.yconomics.bills.RecurringBill;
import com.github.cerealklla.yconomics.bills.RecurringBill.BoxRef;
import com.github.cerealklla.yconomics.currency.CoinPurseContents;
import com.github.cerealklla.yconomics.registration.ModAttachments;
import com.github.cerealklla.yconomics.registration.ModItems;
import com.github.cerealklla.yconomics.shop.PlotShop;
import com.github.cerealklla.yconomics.shop.ShopListing;
import com.github.cerealklla.yconomics.shop.ShopPricing;
import com.github.cerealklla.yconomics.shop.ShopResource;
import com.github.cerealklla.yconomics.shop.ShopTransferEngine;
import com.github.cerealklla.yconomics.storage.BillSavedData;
import com.github.cerealklla.yconomics.storage.ShopSavedData;

import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The stable public entry point for other mods to integrate with Yconomics' economy -- currently
 * just the Coin Purse's tier (design doc Section 2/currency, decided 2026-09-25: "Yconomics has an
 * API which allows other mods to change the tier of the coin purse for an individual player").
 * Same "stable facade, don't reach into internals" pattern as Cartographyr's {@code Cartography}
 * and Lyfe's {@code api.Lyfe}.
 */
public final class Yconomics {

    /** Inclusive upper bound -- Coin Purse (T0) 1x1 through (T10) 11x11, see {@link #coinPurseCapacity}. */
    public static final int MAX_COIN_PURSE_TIER = 10;

    private Yconomics() {
    }

    /** The player's current Coin Purse tier, always in {@code [0, MAX_COIN_PURSE_TIER]}. */
    public static int getCoinPurseTier(Player player) {
        return player.getData(ModAttachments.COIN_PURSE_TIER);
    }

    /** Sets the player's Coin Purse tier directly, clamped to {@code [0, MAX_COIN_PURSE_TIER]}. Last write wins. */
    public static void setCoinPurseTier(Player player, int tier) {
        player.setData(ModAttachments.COIN_PURSE_TIER, Mth.clamp(tier, 0, MAX_COIN_PURSE_TIER));
    }

    /**
     * Raises the player's Coin Purse tier to at least {@code minimumTier}, never lowering it --
     * the convenience most integrations actually want (e.g. Lyfe's Merchant skill calling this on
     * every level-up), since a plain {@link #setCoinPurseTier} would let a lower-priority caller
     * accidentally undo a higher tier some other source already granted.
     */
    public static void increaseCoinPurseTierTo(Player player, int minimumTier) {
        if (getCoinPurseTier(player) < minimumTier) {
            setCoinPurseTier(player, minimumTier);
        }
    }

    /**
     * The raw nugget capacity for a given tier: an {@code (tier + 1)} x {@code (tier + 1)} grid of
     * 64-nugget stacks -- Coin Purse (T0) is a 1x1 grid (64), (T1) is 2x2 (256), ...,
     * (T{@value #MAX_COIN_PURSE_TIER}) is 11x11 (7744).
     */
    public static int coinPurseCapacity(int tier) {
        int side = tier + 1;
        return side * side * 64;
    }

    // Recurring bills (design doc Section 5a, added 2026-10-05, see decisions.md) -- a generic,
    // item/currency-agnostic periodic transfer between two lists of storage boxes. Deliberately
    // MinecraftServer-level, not per-dimension (bills can reference boxes in different
    // dimensions), same rule as Cartographyr's own CartographySavedData.

    private static BillSavedData billData(MinecraftServer server) {
        return server.getDataStorage().computeIfAbsent(BillSavedData.TYPE);
    }

    public static UUID registerRecurringBill(MinecraftServer server, List<BoxRef> sourceBoxes, List<BoxRef> destinationBoxes,
                                              Map<Identifier, Integer> cost, long periodDays, Optional<UUID> ownerContext) {
        return billData(server).addBill(sourceBoxes, destinationBoxes, cost, periodDays, ownerContext).billId();
    }

    public static UUID registerRecurringBill(ServerLevel level, List<BoxRef> sourceBoxes, List<BoxRef> destinationBoxes,
                                              Map<Identifier, Integer> cost, long periodDays, Optional<UUID> ownerContext) {
        return registerRecurringBill(level.getServer(), sourceBoxes, destinationBoxes, cost, periodDays, ownerContext);
    }

    public static Optional<RecurringBill> getRecurringBill(MinecraftServer server, UUID billId) {
        return billData(server).getBill(billId);
    }

    public static Optional<RecurringBill> getRecurringBill(ServerLevel level, UUID billId) {
        return getRecurringBill(level.getServer(), billId);
    }

    /** Every bill registered against {@code ownerContext} (e.g. a Settlemynts plot id) -- for a future plot-bills UI. */
    public static Set<RecurringBill> getRecurringBillsFor(MinecraftServer server, UUID ownerContext) {
        return billData(server).getBillsFor(ownerContext);
    }

    public static Set<RecurringBill> getRecurringBillsFor(ServerLevel level, UUID ownerContext) {
        return getRecurringBillsFor(level.getServer(), ownerContext);
    }

    public static boolean setRecurringBillActive(MinecraftServer server, UUID billId, boolean active) {
        return billData(server).setActive(billId, active).isPresent();
    }

    public static boolean setRecurringBillActive(ServerLevel level, UUID billId, boolean active) {
        return setRecurringBillActive(level.getServer(), billId, active);
    }

    public static boolean removeRecurringBill(MinecraftServer server, UUID billId) {
        return billData(server).removeBill(billId);
    }

    public static boolean removeRecurringBill(ServerLevel level, UUID billId) {
        return removeRecurringBill(level.getServer(), billId);
    }

    /**
     * Refreshes a bill's source/destination box lists -- for a caller (e.g. Settlemynts) that keeps
     * discovering which real containers currently occupy the areas a bill cares about, since this
     * mod never polls Cartographyr itself (see {@code RecurringBill}'s own class doc).
     */
    public static boolean setRecurringBillBoxes(MinecraftServer server, UUID billId, List<BoxRef> sourceBoxes, List<BoxRef> destinationBoxes) {
        return billData(server).setBoxes(billId, sourceBoxes, destinationBoxes).isPresent();
    }

    public static boolean setRecurringBillBoxes(ServerLevel level, UUID billId, List<BoxRef> sourceBoxes, List<BoxRef> destinationBoxes) {
        return setRecurringBillBoxes(level.getServer(), billId, sourceBoxes, destinationBoxes);
    }

    // Shops (design doc Section 5c, added 2026-10-05, see decisions.md) -- the real system
    // Settlemynts' Plot Config Sign "Enter Shop"/"Manage Shop" buttons have been waiting on since
    // 2026-09-29, and the pricing foundation the crafting-structure upgrade-cost feature needs.
    // Yconomics itself stays geography-blind: a shop's stock/payment boxes are supplied fresh by
    // the caller at transaction time (see PlotShop's own class doc), never stored here.

    private static final Identifier GOLD_NUGGET_ID = Identifier.fromNamespaceAndPath("minecraft", "gold_nugget");

    private static ShopSavedData shopData(MinecraftServer server) {
        return server.getDataStorage().computeIfAbsent(ShopSavedData.TYPE);
    }

    public static UUID registerPlotShop(MinecraftServer server, Optional<UUID> ownerContext) {
        return shopData(server).addShop(ownerContext).shopId();
    }

    public static UUID registerPlotShop(ServerLevel level, Optional<UUID> ownerContext) {
        return registerPlotShop(level.getServer(), ownerContext);
    }

    public static Optional<PlotShop> getPlotShop(MinecraftServer server, UUID shopId) {
        return shopData(server).getShop(shopId);
    }

    public static Optional<PlotShop> getPlotShop(ServerLevel level, UUID shopId) {
        return getPlotShop(level.getServer(), shopId);
    }

    /** The one Shop (if any) registered against {@code ownerContext} (e.g. a Settlemynts plot id). */
    public static Optional<PlotShop> getPlotShopFor(MinecraftServer server, UUID ownerContext) {
        return shopData(server).getShopFor(ownerContext);
    }

    public static Optional<PlotShop> getPlotShopFor(ServerLevel level, UUID ownerContext) {
        return getPlotShopFor(level.getServer(), ownerContext);
    }

    /**
     * Sets (replacing any existing listing for the same resource) or adds a listing's price --
     * {@code pricePerUnit} is clamped up to {@link ShopPricing#MIN_SELL_PRICE} if set any lower
     * (2026-10-08 user spec: "no shop should ever sell an item for less than 2 gold").
     */
    public static Optional<PlotShop> setListingPrice(MinecraftServer server, UUID shopId, ShopResource resource, int pricePerUnit) {
        return shopData(server).setListingPrice(shopId, resource, ShopPricing.clampSellPrice(pricePerUnit));
    }

    public static Optional<PlotShop> setListingPrice(ServerLevel level, UUID shopId, ShopResource resource, int pricePerUnit) {
        return setListingPrice(level.getServer(), shopId, resource, pricePerUnit);
    }

    public static Optional<PlotShop> removeListing(MinecraftServer server, UUID shopId, ShopResource resource) {
        return shopData(server).removeListing(shopId, resource);
    }

    public static Optional<PlotShop> removeListing(ServerLevel level, UUID shopId, ShopResource resource) {
        return removeListing(level.getServer(), shopId, resource);
    }

    /**
     * {@code itemsReceived} is what the buyer must actually be given (fixed 2026-10-06, real report:
     * a purchase took the buyer's gold and drained the shop's stock, but never gave the buyer
     * anything at all -- this facade used to return just a {@code filled} count, discarding which
     * real item(s) were drained the moment {@code ShopTransferEngine#drain} returned, with nothing
     * downstream ever delivering anything to the buyer). A tag-based listing ("any log") can drain
     * several different concrete items in one purchase, so this is a list of real stacks, not one.
     */
    public record PurchaseResult(List<ItemStack> itemsReceived, int nuggetsCharged) {
        public int filled() {
            return itemsReceived.stream().mapToInt(ItemStack::getCount).sum();
        }
    }

    /**
     * Buys up to {@code quantity} units of {@code resource} from {@code shopId}'s current listing,
     * draining matching items from {@code stockBoxes} (list order, see {@code shop.ShopTransferEngine#drain})
     * and depositing the charged nuggets into {@code paymentBoxes} (list order, overflow becomes the
     * shop owner's own storage problem, same accepted behavior as {@code bills.BillTransferEngine#deposit}).
     * Fills as much as actual stock allows -- a partial fill is not an error, just a smaller {@link
     * PurchaseResult#filled}; {@code 0} means either no listing exists for this resource or the shop
     * has none in stock. Charges only for what was actually filled. **The caller is responsible for
     * actually giving {@link PurchaseResult#itemsReceived} to the buyer** -- this facade has no
     * {@code Player} reference to do so itself.
     */
    public static PurchaseResult purchaseFromShop(ServerLevel level, UUID shopId, ShopResource resource, int quantity,
                                                   List<Container> stockBoxes, List<Container> paymentBoxes) {
        Optional<PlotShop> shop = getPlotShop(level, shopId);
        if (shop.isEmpty()) {
            return new PurchaseResult(List.of(), 0);
        }
        Optional<ShopListing> listing = shop.get().listings().stream()
                .filter(l -> l.resource().coversSameListingAs(resource))
                .findFirst();
        if (listing.isEmpty()) {
            return new PurchaseResult(List.of(), 0);
        }
        List<ItemStack> itemsReceived = ShopTransferEngine.drain(stockBoxes, resource, quantity);
        int filled = itemsReceived.stream().mapToInt(ItemStack::getCount).sum();
        int nuggetsCharged = filled * listing.get().pricePerUnit();
        if (nuggetsCharged > 0) {
            BillTransferEngine.deposit(paymentBoxes, Map.of(GOLD_NUGGET_ID, nuggetsCharged));
        }
        return new PurchaseResult(itemsReceived, nuggetsCharged);
    }

    /**
     * Mirror of {@link PurchaseResult} for the reverse direction (2026-10-08, "I want to implement
     * them buying items as well") -- {@code itemsSold} is how many units were actually taken (0 if
     * the shop couldn't afford it or no listing covers {@code itemId}), {@code nuggetsReceived} is
     * what the seller is owed, before any per-player Merchant-skill bonus (applied by the caller --
     * see {@code ShopPricing}'s own doc on why that stays separate). {@code sellPricePerUnit} is the
     * listing's own sell price this was derived from -- the caller needs it (not just the already-
     * derived buy price baked into {@code nuggetsReceived}) to compute {@link
     * ShopPricing#effectiveSellPayout}, which takes the sell price itself, not the buy price.
     */
    public record SellResult(int itemsSold, int nuggetsReceived, int sellPricePerUnit) {
    }

    /**
     * Sells up to {@code quantity} units of {@code itemId} to {@code shopId} -- the reverse of
     * {@link #purchaseFromShop}, reusing the exact same listing lookup. {@code resource} identifies
     * which existing listing this item falls under (a shop only ever buys back something it already
     * sells, per the user's own spec -- this is not an open "sell anything" mechanic); {@code itemId}
     * is the concrete item actually being deposited (a tag-based listing like "any log" can cover
     * several different real items, so the caller -- which has the player's actual held stack --
     * resolves this, same division of responsibility as {@code purchaseFromShop}'s {@code
     * itemsReceived}). Price per unit is {@link ShopPricing#deriveBuyPrice} of the listing's own sell
     * price -- never separately configurable, so it can never drift out of the required 60%
     * relationship. All-or-nothing: {@code 0} if the shop's {@code paymentBoxes} can't cover the full
     * {@code quantity} at once (no partial-fill support, unlike the buy direction -- a shop simply
     * isn't open for business on an item it can't currently afford).
     */
    public static SellResult sellToShop(ServerLevel level, UUID shopId, ShopResource resource, Identifier itemId, int quantity,
                                         List<Container> stockBoxes, List<Container> paymentBoxes) {
        Optional<PlotShop> shop = getPlotShop(level, shopId);
        if (shop.isEmpty()) {
            return new SellResult(0, 0, 0);
        }
        Optional<ShopListing> listing = shop.get().listings().stream()
                .filter(l -> l.resource().coversSameListingAs(resource))
                .findFirst();
        if (listing.isEmpty() || quantity <= 0) {
            return new SellResult(0, 0, 0);
        }
        int sellPricePerUnit = listing.get().pricePerUnit();
        int nuggetsOwed = quantity * ShopPricing.deriveBuyPrice(sellPricePerUnit);
        if (!BillTransferEngine.canSatisfy(paymentBoxes, Map.of(GOLD_NUGGET_ID, nuggetsOwed))) {
            return new SellResult(0, 0, 0);
        }
        BillTransferEngine.drain(paymentBoxes, Map.of(GOLD_NUGGET_ID, nuggetsOwed));
        BillTransferEngine.deposit(stockBoxes, Map.of(itemId, quantity));
        return new SellResult(quantity, nuggetsOwed, sellPricePerUnit);
    }

    // Generic currency debit (added 2026-10-05 for the Shop purchase flow above) -- the facade's
    // first general "does this player have N nuggets, take them" method; previously the only way to
    // move currency was the Coin Purse's own click-to-insert/withdraw UI or a Recurring Bill's
    // box-to-box transfer, neither of which fits "a player buys something and pays immediately."
    // Mirrors currency.CoinPurseListener's own loose-inventory-then-purse withdrawal order (small,
    // deliberate duplication of that private logic rather than exposing it directly -- same
    // precedent as this suite's copied-not-shared small ghost-rendering helpers).

    /** Loose Gold Nuggets in the player's inventory, plus whatever's in their Coin Purse. */
    public static int getNuggetBalance(Player player) {
        return countLooseNuggets(player) + purseContents(player).totalCount();
    }

    /**
     * Removes exactly {@code amount} nuggets, loose inventory stacks first, then the Coin Purse --
     * an all-or-nothing operation: returns {@code false} (no mutation at all) if the player's total
     * balance is less than {@code amount}.
     */
    public static boolean withdrawNuggets(Player player, int amount) {
        if (amount <= 0) {
            return true;
        }
        if (getNuggetBalance(player) < amount) {
            return false;
        }
        Inventory inventory = player.getInventory();
        int remaining = amount;
        for (int i = 0; i < inventory.getContainerSize() && remaining > 0; i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.is(Items.GOLD_NUGGET)) {
                int take = Math.min(remaining, stack.getCount());
                stack.shrink(take);
                remaining -= take;
            }
        }
        if (remaining > 0) {
            ItemStack purse = findPurse(player);
            if (purse != null) {
                CoinPurseContents.WithdrawResult result = purseContents(player).withdraw(remaining);
                purse.set(ModItems.COIN_PURSE_CONTENTS, result.contents());
                remaining -= result.withdrawn();
            }
        }
        return remaining <= 0;
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

    private static CoinPurseContents purseContents(Player player) {
        ItemStack purse = findPurse(player);
        return purse == null ? CoinPurseContents.EMPTY : purse.getOrDefault(ModItems.COIN_PURSE_CONTENTS, CoinPurseContents.EMPTY);
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
}
