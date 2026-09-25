package com.github.cerealklla.yconomics;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import com.github.cerealklla.yconomics.registration.ModRecipes;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;

// The value here must match the modId entry in META-INF/neoforge.mods.toml (sourced from mod_id in gradle.properties)
@Mod(YconomicsMod.MODID)
public class YconomicsMod {
    public static final String MODID = "yconomics";
    public static final Logger LOGGER = LogUtils.getLogger();

    public YconomicsMod(IEventBus modEventBus, ModContainer modContainer) {
        ModRecipes.RECIPE_SERIALIZERS.register(modEventBus);

        modEventBus.addListener(this::commonSetup);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        LOGGER.info("Yconomics common setup");
    }
}
