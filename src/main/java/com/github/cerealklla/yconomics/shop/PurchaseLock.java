package com.github.cerealklla.yconomics.shop;

import java.util.Optional;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

/**
 * Whether a specific player may complete a purchase that's otherwise listed/visible in a shop --
 * distinct from who sells it or what it costs. A locked offer still shows up (so the player can see
 * it exists, and via the returned reason, roughly what would unlock it); it just can't be bought
 * yet. One mechanism covers every reason an offer might be temporarily unavailable to one specific
 * player -- a skill-level gate (see Lyfe's Merchant skill, {@code CoinPurseTierUnlocks}) and
 * "out of stock" are both just different {@link PurchaseLock} implementations, not separate
 * systems (design doc / decisions.md, 2026-09-26).
 *
 * <p>Not yet attached to any real vendor or trade -- added as the reusable hook point ahead of the
 * actual Coin Purse T6-T8 vendor, whose seller and price are still undecided.
 */
@FunctionalInterface
public interface PurchaseLock {

    /** Empty if {@code player} may purchase right now; otherwise the reason it's locked. */
    Optional<Component> checkLocked(Player player);

    static PurchaseLock unlocked() {
        return player -> Optional.empty();
    }
}
