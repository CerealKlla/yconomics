package com.github.cerealklla.yconomics.storage;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import com.github.cerealklla.yconomics.YconomicsMod;
import com.github.cerealklla.yconomics.shop.PlotShop;
import com.github.cerealklla.yconomics.shop.ShopListing;
import com.github.cerealklla.yconomics.shop.ShopResource;

import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * World-level persistent store for Shops -- same shape as {@link BillSavedData}. This is NOT the
 * intended integration point for other mods -- see {@code api.Yconomics}.
 */
public final class ShopSavedData extends SavedData {

    public static final SavedDataType<ShopSavedData> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(YconomicsMod.MODID, "shops"),
            ShopSavedData::new,
            codec()
    );

    private final Map<UUID, PlotShop> shops;

    ShopSavedData() {
        this(new HashMap<>());
    }

    private ShopSavedData(Map<UUID, PlotShop> shops) {
        this.shops = shops;
    }

    private static Codec<ShopSavedData> codec() {
        return RecordCodecBuilder.create(i -> i.group(
                Codec.unboundedMap(UUIDUtil.STRING_CODEC, PlotShop.CODEC).fieldOf("shops").forGetter(d -> d.shops)
        ).apply(i, shops -> new ShopSavedData(new HashMap<>(shops))));
    }

    /** @apiNote Not the intended integration point — use {@code Yconomics.registerPlotShop} instead. */
    public PlotShop addShop(Optional<UUID> ownerContext) {
        UUID shopId = UUID.randomUUID();
        PlotShop shop = new PlotShop(shopId, ownerContext, List.of());
        shops.put(shopId, shop);
        setDirty();
        return shop;
    }

    /** @apiNote Not the intended integration point — use {@code Yconomics.getPlotShop} instead. */
    public Optional<PlotShop> getShop(UUID shopId) {
        return Optional.ofNullable(shops.get(shopId));
    }

    /** @apiNote Not the intended integration point — use {@code Yconomics.getPlotShopFor} instead. */
    public Optional<PlotShop> getShopFor(UUID ownerContext) {
        for (PlotShop shop : shops.values()) {
            if (shop.ownerContext().equals(Optional.of(ownerContext))) {
                return Optional.of(shop);
            }
        }
        return Optional.empty();
    }

    /** @apiNote Not the intended integration point — use {@code Yconomics.setListingPrice} instead. */
    public Optional<PlotShop> setListingPrice(UUID shopId, ShopResource resource, int pricePerUnit) {
        PlotShop current = shops.get(shopId);
        if (current == null) {
            return Optional.empty();
        }
        List<ShopListing> newListings = new java.util.ArrayList<>();
        boolean replaced = false;
        for (ShopListing listing : current.listings()) {
            if (listing.resource().key().equals(resource.key())) {
                newListings.add(new ShopListing(resource, pricePerUnit));
                replaced = true;
            } else {
                newListings.add(listing);
            }
        }
        if (!replaced) {
            newListings.add(new ShopListing(resource, pricePerUnit));
        }
        PlotShop updated = current.withListings(newListings);
        shops.put(shopId, updated);
        setDirty();
        return Optional.of(updated);
    }

    /** @apiNote Not the intended integration point — use {@code Yconomics.removeListing} instead. */
    public Optional<PlotShop> removeListing(UUID shopId, ShopResource resource) {
        PlotShop current = shops.get(shopId);
        if (current == null) {
            return Optional.empty();
        }
        List<ShopListing> newListings = current.listings().stream()
                .filter(listing -> !listing.resource().key().equals(resource.key()))
                .toList();
        PlotShop updated = current.withListings(newListings);
        shops.put(shopId, updated);
        setDirty();
        return Optional.of(updated);
    }
}
