package com.github.cerealklla.yconomics;

import com.github.cerealklla.yconomics.bag.client.LootBagRenderer;
import com.github.cerealklla.yconomics.registration.ModEntities;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

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
}
