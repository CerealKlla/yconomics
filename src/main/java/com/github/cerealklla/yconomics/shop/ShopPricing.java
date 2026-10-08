package com.github.cerealklla.yconomics.shop;

/**
 * The one shared pricing rule every Gold-Nugget-priced sale in this suite derives from (2026-10-08
 * user spec): no listing may ever sell for less than {@link #MIN_SELL_PRICE}, and buying the same
 * item back always costs {@link #BUY_PRICE_FRACTION} of its sell price, never equal to it. This is
 * the single source of truth both Settlement Shops ({@code api.Yconomics#sellToShop}/{@code
 * #setListingPrice}) and the vanilla NPC trade files (regenerated once from this exact formula, see
 * decisions.md) derive from -- nothing else re-implements this math.
 *
 * <p>Deliberately excludes Lyfe's Merchant-skill price bonus -- that's a separate, per-player runtime
 * modifier applied on top of whatever this class returns, never baked into the shared/global price
 * itself (explicit user requirement: "merchant skill can augment the transactions on a per/player
 * basis separately").
 */
public final class ShopPricing {

    public static final int MIN_SELL_PRICE = 2;
    public static final double BUY_PRICE_FRACTION = 0.6;

    private ShopPricing() {
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
}
