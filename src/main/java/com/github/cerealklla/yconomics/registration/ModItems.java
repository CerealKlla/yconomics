package com.github.cerealklla.yconomics.registration;

import com.github.cerealklla.yconomics.YconomicsMod;
import com.github.cerealklla.yconomics.currency.CoinPurseContents;
import com.github.cerealklla.yconomics.currency.CoinPurseItem;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Items and data components for the Coin Purse mechanic (design doc Section 2/currency). */
public final class ModItems {

    private ModItems() {
    }

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(YconomicsMod.MODID);

    // ENCHANTMENT_GLINT_OVERRIDE forces vanilla's own shimmer effect regardless of actual
    // enchantments -- a playtest request ("make it glow purple like it's enchanted") that turned
    // out to already be a real, no-art-needed vanilla component rather than something to build.
    public static final DeferredItem<CoinPurseItem> COIN_PURSE = ITEMS.register(
            "coin_purse",
            id -> new CoinPurseItem(new Item.Properties()
                    .stacksTo(1)
                    .setId(ResourceKey.create(Registries.ITEM, id))
                    .component(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true)));

    public static final DeferredRegister.DataComponents DATA_COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, YconomicsMod.MODID);

    // Persisted (survives save/load) and network-synchronized (client needs the contents to render
    // correctly, e.g. a future tooltip) -- same pattern as Lyfe's ModItems#KNOWLEDGE_REFERENCE.
    // Replaced a flat Integer count (2026-09-25, see decisions.md) once the purse became a real
    // multi-stack container instead of a single scaling number.
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<CoinPurseContents>> COIN_PURSE_CONTENTS =
            DATA_COMPONENTS.registerComponentType("coin_purse_contents", builder -> builder
                    .persistent(CoinPurseContents.CODEC)
                    .networkSynchronized(CoinPurseContents.STREAM_CODEC));
}
