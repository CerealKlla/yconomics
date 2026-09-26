package com.github.cerealklla.yconomics.bag;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.github.cerealklla.yconomics.registration.ModItems;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.item.ItemTossEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * The dropped-item bag mechanic itself (design doc Section 4, see decisions.md) -- clusters a
 * player's drops (voluntary tosses and death drops alike) into {@link LootBagEntity}s instead of
 * leaving them as loose {@code ItemEntity}s.
 *
 * <p><b>Two different capture paths, matching the design's capture-timing decision</b>: a
 * voluntary drop still visibly falls and lands like any other item ({@link #onItemToss} just
 * starts watching it; {@link #onServerTick} captures it once it's actually touched down), while a
 * death drop skips that animation entirely and goes straight into a bag ({@link #onLivingDeath}
 * captures the whole inventory directly, before vanilla's own death-drop logic even runs).
 */
public final class LootBagListener {

    // 3 blocks, per the design's "join a bag within 3 tiles, otherwise start a new one."
    private static final double CLUSTER_RADIUS = 3.0;

    // Items tossed by a player, waiting to land before being swept into a bag -- see the class doc's
    // two-path explanation for why this can't just happen immediately on toss.
    private final List<ItemEntity> pendingLanding = new ArrayList<>();

    @SubscribeEvent
    public void onItemToss(ItemTossEvent event) {
        // The Coin Purse is never bagged -- CoinPurseListener's own onItemToss handler discards the
        // world entity and reissues the purse directly, so it would never actually land anyway.
        if (!event.getEntity().getItem().is(ModItems.COIN_PURSE.get())) {
            pendingLanding.add(event.getEntity());
        }
    }

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        if (pendingLanding.isEmpty()) {
            return;
        }
        Iterator<ItemEntity> iterator = pendingLanding.iterator();
        while (iterator.hasNext()) {
            ItemEntity itemEntity = iterator.next();
            if (itemEntity.isRemoved()) {
                iterator.remove();
            } else if (itemEntity.onGround() && itemEntity.level() instanceof ServerLevel serverLevel) {
                depositOrScatter(serverLevel, itemEntity.position(), itemEntity.getItem());
                itemEntity.discard();
                iterator.remove();
            }
        }
    }

    /**
     * Captures the player's entire inventory directly into a bag at the moment of death, before
     * vanilla's own death-drop logic runs (this event fires at the very start of the death
     * process) -- so nothing from a dying player's inventory ever becomes a normal, scattered
     * death-drop pile. The Coin Purse is skipped here regardless of listener-registration order
     * with {@code CoinPurseListener}'s own death handling, which removes it separately.
     */
    @SubscribeEvent
    public void onLivingDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        Inventory inventory = player.getInventory();
        Vec3 pos = player.position();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.isEmpty() || stack.is(ModItems.COIN_PURSE.get())) {
                continue;
            }
            inventory.setItem(i, ItemStack.EMPTY);
            depositOrScatter(serverLevel, pos, stack);
        }
    }

    /**
     * Deposits into a nearby-or-new bag; whatever doesn't fit (the bag is full) scatters as a
     * normal item, per the design's own fallback. Public (widened from {@code private} 2026-09-25,
     * see decisions.md) so {@code currency.CoinPurseListener} can route a Coin Purse's death-retained
     * loose nuggets through the same clustering logic as any other death drop, instead of spawning a
     * raw {@code ItemEntity} that the rest of this class would never actually see or bag.
     */
    public static void depositOrScatter(ServerLevel level, Vec3 pos, ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        LootBagEntity bag = findOrCreateBag(level, pos);
        ItemStack remainder = bag.depositItem(stack);
        if (!remainder.isEmpty()) {
            level.addFreshEntity(new ItemEntity(level, pos.x, pos.y, pos.z, remainder));
        }
    }

    private static LootBagEntity findOrCreateBag(ServerLevel level, Vec3 pos) {
        List<LootBagEntity> nearby = level.getEntitiesOfClass(LootBagEntity.class, new AABB(pos, pos).inflate(CLUSTER_RADIUS));
        return nearby.isEmpty() ? LootBagEntity.create(level, pos.x, pos.y, pos.z) : nearby.get(0);
    }
}
