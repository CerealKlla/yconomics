package com.github.cerealklla.yconomics;

import com.github.cerealklla.yconomics.bag.client.LootBagRenderer;
import com.github.cerealklla.yconomics.bag.client.LootBagScreen;
import com.github.cerealklla.yconomics.currency.client.CoinPurseDecorator;
import com.github.cerealklla.yconomics.registration.ModEntities;
import com.github.cerealklla.yconomics.registration.ModItems;
import com.github.cerealklla.yconomics.registration.ModMenus;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterItemDecorationsEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

// This class will not load on dedicated servers. Accessing client side code from here is safe.
@Mod(value = YconomicsMod.MODID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = YconomicsMod.MODID, value = Dist.CLIENT)
public class YconomicsModClient {
    public YconomicsModClient(ModContainer container) {
    }

    @SubscribeEvent
    static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.LOOT_BAG.get(), LootBagRenderer::new);
    }

    @SubscribeEvent
    static void onRegisterMenuScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenus.LOOT_BAG.get(), LootBagScreen::new);
    }

    @SubscribeEvent
    static void onRegisterItemDecorations(RegisterItemDecorationsEvent event) {
        event.register(ModItems.COIN_PURSE.get(), new CoinPurseDecorator());
    }
}
