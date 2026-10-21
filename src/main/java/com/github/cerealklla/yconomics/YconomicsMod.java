package com.github.cerealklla.yconomics;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import com.github.cerealklla.yconomics.bag.LootBagListener;
import com.github.cerealklla.yconomics.bills.BillProcessingTicker;
import com.github.cerealklla.yconomics.currency.CoinPurseListener;
import com.github.cerealklla.yconomics.debug.DebugCommands;
import com.github.cerealklla.yconomics.mob.HostileMobDrops;
import com.github.cerealklla.yconomics.price.PriceCommands;
import com.github.cerealklla.yconomics.registration.ModAttachments;
import com.github.cerealklla.yconomics.registration.ModEntities;
import com.github.cerealklla.yconomics.registration.ModItems;
import com.github.cerealklla.yconomics.registration.ModMenus;
import com.github.cerealklla.yconomics.registration.ModRecipes;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

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
        ModAttachments.ATTACHMENT_TYPES.register(modEventBus);

        NeoForge.EVENT_BUS.register(new CoinPurseListener());
        NeoForge.EVENT_BUS.register(new HostileMobDrops());
        NeoForge.EVENT_BUS.register(new LootBagListener());
        NeoForge.EVENT_BUS.register(new BillProcessingTicker());
        NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent event) -> DebugCommands.register(event.getDispatcher()));
        NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent event) -> PriceCommands.register(event.getDispatcher()));
        // Explicit user spec, 2026-10-20: "I don't want to ever have to run commands... something is
        // wired up to re-calculate the recipes, just add that on at the end of that method running."
        // ServerStartedEvent fires only once the full lifecycle (construct -> about-to-start ->
        // starting -> started) has completed for every mod -- including Lyfe's own ServerStartingEvent
        // listeners that load/generate its tool/food/runeword recipe sets -- so this is guaranteed to
        // see the real, complete recipe graph every single boot, with no new dependency on Lyfe and no
        // cross-mod event-ordering guesswork needed. See PriceCommands#runRecalculation's own doc for
        // the real gap this closes (a fresh world's derived prices were never actually computed until
        // someone remembered to run `/yconomics recalculateprices` by hand).
        NeoForge.EVENT_BUS.addListener((ServerStartedEvent event) -> {
            PriceCommands.Result result = PriceCommands.runRecalculation(event.getServer());
            LOGGER.info("[PriceDerivation] Automatic boot-time recalculation: {}", result.summary());
        });

        modEventBus.addListener(this::commonSetup);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        LOGGER.info("Yconomics common setup");
    }
}
