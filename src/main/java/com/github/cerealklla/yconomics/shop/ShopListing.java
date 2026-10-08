package com.github.cerealklla.yconomics.shop;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** One resource a {@link PlotShop} currently sells, and its nugget price per single unit. */
public record ShopListing(ShopResource resource, int pricePerUnit) {

    /**
     * Clamps {@code pricePerUnit} through {@link ShopPricing#clampSellPrice} in the canonical
     * constructor itself (2026-10-08, real report: an "oak planks" listing seeded before this
     * feature existed showed "Buy 1 / Sell 1" -- {@code api.Yconomics#setListingPrice} only clamps
     * at the moment it's *called*, so an already-stored sub-floor price was never retroactively
     * touched, and {@code ShopPricing#deriveBuyPrice(1)} happens to round to 1 too, landing on the
     * exact same number). Putting the clamp here instead makes it structurally impossible to ever
     * hold an under-floor listing, through any construction path -- including {@link #CODEC}
     * decoding already-saved NBT, which retroactively fixes stale data the next time it loads, with
     * no separate migration needed.
     */
    public ShopListing {
        pricePerUnit = ShopPricing.clampSellPrice(pricePerUnit);
    }

    public static final Codec<ShopListing> CODEC = RecordCodecBuilder.create(i -> i.group(
            ShopResource.CODEC.fieldOf("resource").forGetter(ShopListing::resource),
            Codec.INT.fieldOf("price_per_unit").forGetter(ShopListing::pricePerUnit)
    ).apply(i, ShopListing::new));
}
