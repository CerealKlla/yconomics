package com.github.cerealklla.yconomics.shop;

/**
 * The one shared pricing rule every Gold-Nugget-priced sale in this suite derives from (2026-10-08
 * user spec): no listing may ever sell for less than {@link #MIN_SELL_PRICE}, and buying the same
 * item back always costs {@link #BUY_PRICE_FRACTION} of its sell price, never equal to it. This is
 * the single source of truth both Settlement Shops ({@code api.Yconomics#sellToShop}/{@code
 * #setListingPrice}) and the vanilla NPC trade files (regenerated once from this exact formula, see
 * decisions.md) derive from -- nothing else re-implements this math.
 *
 * <p><b>{@link #MIN_SELL_PRICE} is derived, not a hardcoded business rule</b> (2026-10-08 follow-up:
 * "the 2 gold floor was intended to prevent sell and buy from being equal... if that isn't relevant
 * anymore you can remove that hardcoded limit, and instead put something in that mathematically
 * makes better sense"). It's computed once, from {@link #BUY_PRICE_FRACTION} itself, as the smallest
 * sell price whose derived buy-back price is still a positive integer strictly below it -- at
 * {@code BUY_PRICE_FRACTION = 0.6} that happens to be 2 (matching the old hardcoded value exactly),
 * but it's no longer a number that can silently drift out of sync if the fraction is ever retuned;
 * it's always whatever the formula itself actually requires. {@link #deriveBuyPrice}'s own
 * nudge-down-by-1 still stays in place as a belt-and-suspenders guard, not dead code -- this
 * derivation just means it should never actually need to fire for any sell price at or above the
 * (also-derived) floor.
 *
 * <p><b>Skill-bonus-aware effective prices, added same day</b> (real user concern: "make sure this
 * clamp takes place after player skill %s modify them... should never be possible that buy = sell
 * cost, or you could just farm xp infinitely") -- confirmed as a genuine bug, not a hypothetical:
 * at sell price 3 (buy-back 2) and Lyfe's max Merchant bonus (20%), the naive math was
 * {@code effectiveBuyCost = 3 - round(3*0.2) = 2} and {@code effectiveSellPayout = 2 + round(2*0.2)
 * = 2} -- an exact tie, a real zero-cost infinite buy/sell XP loop purely from integer rounding.
 * {@link #effectiveBuyCost}/{@link #effectiveSellPayout} are the fix: {@code effectiveSellPayout}
 * is computed, then clamped strictly below whatever {@code effectiveBuyCost} comes out to for the
 * *same* sell price and *same* bonus fraction, so the invariant holds for every combination, not
 * just the unmodified base case. Callers (Settlemynts' Settlement Shop buy/sell handlers) must use
 * these instead of applying Lyfe's bonus fraction to {@link #deriveBuyPrice}'s result independently.
 */
public final class ShopPricing {

    public static final double BUY_PRICE_FRACTION = 0.6;

    /**
     * The smallest sell price for which {@link #deriveBuyPrice} (before this floor is applied)
     * produces a positive integer strictly less than the sell price itself -- see this class's own
     * doc for why this replaced a hardcoded {@code 2}. Computed once at class-init, not re-derived
     * per call.
     */
    public static final int MIN_SELL_PRICE = computeMinSellPrice();

    private ShopPricing() {
    }

    private static int computeMinSellPrice() {
        for (int sell = 1; sell < 1000; sell++) {
            int buy = Math.max(1, Math.round((float) (sell * BUY_PRICE_FRACTION)));
            if (buy < sell) {
                return sell;
            }
        }
        throw new IllegalStateException("No valid sell price found a buy-back price for BUY_PRICE_FRACTION=" + BUY_PRICE_FRACTION);
    }

    /** Raises {@code price} up to {@link #MIN_SELL_PRICE} if it's below the floor; otherwise unchanged. */
    public static int clampSellPrice(int price) {
        return Math.max(MIN_SELL_PRICE, price);
    }

    /**
     * What buying {@code sellPrice}'s item back from the player costs, as a baseline before any
     * per-player skill modifier -- {@link #BUY_PRICE_FRACTION} of the (floor-clamped) sell price,
     * rounded, and nudged down by 1 if rounding would otherwise make it equal the sell price (the
     * "never equal" guarantee holds by construction, not by re-checking at each call site).
     */
    public static int deriveBuyPrice(int sellPrice) {
        int sell = clampSellPrice(sellPrice);
        int buy = Math.max(1, Math.round((float) (sell * BUY_PRICE_FRACTION)));
        return buy >= sell ? sell - 1 : buy;
    }

    /**
     * What a player with Lyfe's Merchant skill bonus {@code bonusFraction} (0.0-0.20) actually pays
     * to buy one unit of {@code sellPrice}'s item -- the (floor-clamped) sell price minus the
     * discount, floored at 1 (a discount can shrink the price, never make an item free).
     */
    public static int effectiveBuyCost(int sellPrice, double bonusFraction) {
        int sell = clampSellPrice(sellPrice);
        int discount = (int) Math.round(sell * bonusFraction);
        return Math.max(1, sell - discount);
    }

    /**
     * What that same player actually receives selling one unit of {@code sellPrice}'s item back --
     * {@link #deriveBuyPrice}'s result plus the bonus, but clamped strictly below {@link
     * #effectiveBuyCost} for this exact {@code sellPrice}/{@code bonusFraction} pair, so a skilled
     * Merchant can never buy and immediately sell back at break-even or a profit (see this class's
     * own doc for the real rounding case this guards against).
     */
    public static int effectiveSellPayout(int sellPrice, double bonusFraction) {
        int baseBuy = deriveBuyPrice(sellPrice);
        int bonus = (int) Math.round(baseBuy * bonusFraction);
        int payout = baseBuy + bonus;
        int cost = effectiveBuyCost(sellPrice, bonusFraction);
        return Math.max(0, Math.min(payout, cost - 1));
    }
}
