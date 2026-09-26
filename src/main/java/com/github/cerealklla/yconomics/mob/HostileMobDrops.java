package com.github.cerealklla.yconomics.mob;

import java.util.Collection;
import java.util.Random;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;

/**
 * Every hostile mob (anything implementing vanilla's own {@link Enemy} marker interface --
 * zombies, skeletons, spiders, creepers, etc., the same "is this thing hostile toward players"
 * check vanilla itself uses) drops 1-3 {@link Items#GOLD_NUGGET} on death, so nugget currency has
 * a combat-side income source alongside trading/mining -- design doc Section 2/currency.
 */
public final class HostileMobDrops {

    private static final int MIN_NUGGETS = 1;
    private static final int MAX_NUGGETS = 3;
    private static final Random RANDOM = new Random();

    @SubscribeEvent
    public void onLivingDrops(LivingDropsEvent event) {
        LivingEntity entity = event.getEntity();
        if (!(entity instanceof Enemy) || !(entity.level() instanceof ServerLevel serverLevel)) {
            return;
        }

        int count = MIN_NUGGETS + RANDOM.nextInt(MAX_NUGGETS - MIN_NUGGETS + 1);
        Collection<ItemEntity> drops = event.getDrops();
        drops.add(new ItemEntity(serverLevel, entity.getX(), entity.getY(), entity.getZ(), new ItemStack(Items.GOLD_NUGGET, count)));
    }
}
