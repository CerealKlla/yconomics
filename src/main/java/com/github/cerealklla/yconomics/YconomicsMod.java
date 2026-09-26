package com.github.cerealklla.yconomics;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import com.github.cerealklla.yconomics.bag.LootBagListener;
import com.github.cerealklla.yconomics.currency.CoinPurseListener;
import com.github.cerealklla.yconomics.mob.HostileMobDrops;
import com.github.cerealklla.yconomics.registration.ModEntities;
import com.github.cerealklla.yconomics.registration.ModItems;
import com.github.cerealklla.yconomics.registration.ModMenus;
import com.github.cerealklla.yconomics.registration.ModRecipes;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;

// The value here must match the modId entry in META-INF/neoforge.mods.toml (sourced from mod_id in gradle.properties)
@Mod(YconomicsMod.MODID)
public class YconomicsMod {
    public static final String MODID = "yconomics";
    public static final Logger LOGGER = LogUtils.getLogger();

    public YconomicsMod(IEventBus modEventBus, ModContainer modContainer) {
        ModRecipes.RECIPE_SERIALIZERS.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        ModItems.DATA_COMPONENTS.register(modEventBus);
        ModEntities.ENTITIES.register(modEventBus);
        ModMenus.MENU_TYPES.register(modEventBus);

        NeoForge.EVENT_BUS.register(new CoinPurseListener());
        NeoForge.EVENT_BUS.register(new HostileMobDrops());
        NeoForge.EVENT_BUS.register(new LootBagListener());

        modEventBus.addListener(this::commonSetup);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        LOGGER.info("Yconomics common setup");
    }
}
