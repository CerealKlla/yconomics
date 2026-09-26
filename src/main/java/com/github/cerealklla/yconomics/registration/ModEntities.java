package com.github.cerealklla.yconomics.registration;

import com.github.cerealklla.yconomics.YconomicsMod;
import com.github.cerealklla.yconomics.bag.LootBagEntity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Entity types for the dropped-item bag mechanic (design doc Section 4). */
public final class ModEntities {

    private ModEntities() {
    }

    public static final DeferredRegister.Entities ENTITIES = DeferredRegister.createEntities(YconomicsMod.MODID);

    public static final DeferredHolder<EntityType<?>, EntityType<LootBagEntity>> LOOT_BAG = ENTITIES.registerEntityType(
            "loot_bag",
            LootBagEntity::new,
            MobCategory.MISC,
            builder -> builder.sized(0.75f, 0.75f));
}
