package com.github.cerealklla.yconomics.shop;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** One resource a {@link PlotShop} currently sells, and its nugget price per single unit. */
public record ShopListing(ShopResource resource, int pricePerUnit) {

    public static final Codec<ShopListing> CODEC = RecordCodecBuilder.create(i -> i.group(
            ShopResource.CODEC.fieldOf("resource").forGetter(ShopListing::resource),
            Codec.INT.fieldOf("price_per_unit").forGetter(ShopListing::pricePerUnit)
    ).apply(i, ShopListing::new));
}
