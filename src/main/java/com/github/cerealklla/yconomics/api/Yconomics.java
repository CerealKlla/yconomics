package com.github.cerealklla.yconomics.api;

import com.github.cerealklla.yconomics.registration.ModAttachments;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

/**
 * The stable public entry point for other mods to integrate with Yconomics' economy -- currently
 * just the Coin Purse's tier (design doc Section 2/currency, decided 2026-09-25: "Yconomics has an
 * API which allows other mods to change the tier of the coin purse for an individual player").
 * Same "stable facade, don't reach into internals" pattern as Cartographyr's {@code Cartography}
 * and Lyfe's {@code api.Lyfe}.
 */
public final class Yconomics {

    /** Inclusive upper bound -- Coin Purse (T0) 1x1 through (T10) 11x11, see {@link #coinPurseCapacity}. */
    public static final int MAX_COIN_PURSE_TIER = 10;

    private Yconomics() {
    }

    /** The player's current Coin Purse tier, always in {@code [0, MAX_COIN_PURSE_TIER]}. */
    public static int getCoinPurseTier(Player player) {
        return player.getData(ModAttachments.COIN_PURSE_TIER);
    }

    /** Sets the player's Coin Purse tier directly, clamped to {@code [0, MAX_COIN_PURSE_TIER]}. Last write wins. */
    public static void setCoinPurseTier(Player player, int tier) {
        player.setData(ModAttachments.COIN_PURSE_TIER, Mth.clamp(tier, 0, MAX_COIN_PURSE_TIER));
    }

    /**
     * Raises the player's Coin Purse tier to at least {@code minimumTier}, never lowering it --
     * the convenience most integrations actually want (e.g. Lyfe's Merchant skill calling this on
     * every level-up), since a plain {@link #setCoinPurseTier} would let a lower-priority caller
     * accidentally undo a higher tier some other source already granted.
     */
    public static void increaseCoinPurseTierTo(Player player, int minimumTier) {
        if (getCoinPurseTier(player) < minimumTier) {
            setCoinPurseTier(player, minimumTier);
        }
    }

    /**
     * The raw nugget capacity for a given tier: an {@code (tier + 1)} x {@code (tier + 1)} grid of
     * 64-nugget stacks -- Coin Purse (T0) is a 1x1 grid (64), (T1) is 2x2 (256), ...,
     * (T{@value #MAX_COIN_PURSE_TIER}) is 11x11 (7744).
     */
    public static int coinPurseCapacity(int tier) {
        int side = tier + 1;
        return side * side * 64;
    }
}
