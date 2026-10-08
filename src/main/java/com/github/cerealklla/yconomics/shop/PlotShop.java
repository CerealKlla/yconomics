package com.github.cerealklla.yconomics.shop;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.UUIDUtil;

/**
 * A real Shop backing a settlement plot (design doc Section 5c, added 2026-10-05) -- the system
 * Settlemynts' Plot Config Sign's "Enter Shop"/"Manage Shop" buttons have been waiting on since
 * 2026-09-29 (see that mod's own CLAUDE.md), and the pricing foundation the crafting-structure
 * upgrade-cost feature needs.
 *
 * <p>Deliberately holds no box references -- unlike {@code bills.RecurringBill}, which tracks its
 * own {@code BoxRef}s because its bills run unattended on a schedule, a Shop's stock/payment boxes
 * are resolved fresh by the caller (always Settlemynts, which already knows the plot's polygon) at
 * the moment of a transaction, via {@code Cartography.getBoxesAt}. This keeps Yconomics itself free
 * of any Cartographyr/geometry dependency, the same principle {@code RecurringBill}'s own class doc
 * already explains for {@code BoxRef}.
 *
 * @param ownerContext an opaque back-reference (e.g. a Settlemynts plot id) this mod never
 *                      interprets -- same convention as {@code RecurringBill#ownerContext}.
 */
public record PlotShop(UUID shopId, Optional<UUID> ownerContext, List<ShopListing> listings) {

    public static final Codec<PlotShop> CODEC = RecordCodecBuilder.create(i -> i.group(
            UUIDUtil.CODEC.fieldOf("shop_id").forGetter(PlotShop::shopId),
            UUIDUtil.CODEC.optionalFieldOf("owner_context").forGetter(PlotShop::ownerContext),
            Codec.list(ShopListing.CODEC).fieldOf("listings").forGetter(PlotShop::listings)
    ).apply(i, PlotShop::new));

    public PlotShop withListings(List<ShopListing> newListings) {
        return new PlotShop(shopId, ownerContext, List.copyOf(newListings));
    }
}
