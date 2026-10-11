package com.github.cerealklla.yconomics.price;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import com.github.cerealklla.yconomics.YconomicsMod;

import net.minecraft.resources.Identifier;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * Persisted store for the recipe-derived base price of every item {@link PriceDerivation} could
 * resolve -- computed once, by {@code price.PriceCommands}' {@code /yconomics recalculateprices}
 * command, never recomputed live on a per-listing basis (explicit user spec: "this calculation only
 * needs to happen one time"). Same world-level (save-wide) persistence shape as {@code
 * storage.BillSavedData}. This is NOT the intended integration point for other mods -- see {@code
 * api.Yconomics#getDerivedPrice}.
 */
public final class DerivedPriceTable extends SavedData {

    public static final SavedDataType<DerivedPriceTable> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(YconomicsMod.MODID, "derived_prices"),
            DerivedPriceTable::new,
            codec()
    );

    private Map<Identifier, Integer> prices;
    private long lastComputedGameTime;

    DerivedPriceTable() {
        this(new HashMap<>(), -1L);
    }

    private DerivedPriceTable(Map<Identifier, Integer> prices, long lastComputedGameTime) {
        this.prices = prices;
        this.lastComputedGameTime = lastComputedGameTime;
    }

    private static Codec<DerivedPriceTable> codec() {
        return RecordCodecBuilder.create(i -> i.group(
                Codec.unboundedMap(Identifier.CODEC, Codec.INT).fieldOf("prices").forGetter(d -> d.prices),
                Codec.LONG.optionalFieldOf("last_computed_game_time", -1L).forGetter(d -> d.lastComputedGameTime)
        ).apply(i, (prices, lastComputedGameTime) -> new DerivedPriceTable(new HashMap<>(prices), lastComputedGameTime)));
    }

    /** @apiNote Not the intended integration point — use {@code api.Yconomics#getDerivedPrice} instead. */
    public Optional<Integer> get(Identifier itemId) {
        return Optional.ofNullable(prices.get(itemId));
    }

    /** Wholesale replace -- called once per {@code /yconomics recalculateprices} run, never incrementally updated. */
    public void replaceAll(Map<Identifier, Integer> newPrices, long gameTime) {
        this.prices = new HashMap<>(newPrices);
        this.lastComputedGameTime = gameTime;
        setDirty();
    }

    public int size() {
        return prices.size();
    }

    public long lastComputedGameTime() {
        return lastComputedGameTime;
    }
}
