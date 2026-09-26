package com.github.cerealklla.yconomics.registration;

import com.github.cerealklla.yconomics.YconomicsMod;
import com.github.cerealklla.yconomics.currency.CoinPurseItem;

import com.mojang.serialization.Codec;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
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

    public static final DeferredItem<CoinPurseItem> COIN_PURSE = ITEMS.register(
            "coin_purse",
            id -> new CoinPurseItem(new Item.Properties().stacksTo(1).setId(ResourceKey.create(Registries.ITEM, id))));

    public static final DeferredRegister.DataComponents DATA_COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, YconomicsMod.MODID);

    // Persisted (survives save/load) and network-synchronized (client needs to render the purse's
    // displayed count correctly) -- same pattern as Lyfe's ModItems#KNOWLEDGE_REFERENCE.
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> NUGGET_COUNT =
            DATA_COMPONENTS.registerComponentType("nugget_count", builder -> builder
                    .persistent(Codec.INT)
                    .networkSynchronized(ByteBufCodecs.VAR_INT));
}
