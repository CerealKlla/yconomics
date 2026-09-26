package com.github.cerealklla.yconomics.currency.client;

import com.github.cerealklla.yconomics.api.Yconomics;
import com.github.cerealklla.yconomics.currency.CoinPurseContents;
import com.github.cerealklla.yconomics.registration.ModItems;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.IItemDecorator;

/**
 * Draws the Coin Purse's tier and nugget total directly on its inventory-slot icon, with no
 * hover required -- a real playtest request ("I don't want to have to mouse over it"). Registered
 * via {@code RegisterItemDecorationsEvent} in {@code YconomicsModClient}, the intended NeoForge
 * extension point for exactly this (the same mechanism vanilla's own durability bar uses, but for
 * arbitrary rendering rather than a colored bar -- confirmed against the decompiled source: there
 * is no hook to override the vanilla stack-count *text* itself, only this "draw something extra
 * after decorations render" one, see decisions.md 2026-09-26).
 *
 * <p>The purse is {@code stacksTo(1)}, so vanilla's own count text never draws in the first place
 * (it only draws when {@code ItemStack#getCount() != 1}) -- there's nothing to visually replace,
 * just empty space to use. Tier goes top-left, nugget total bottom-right (the same corner vanilla's
 * count would otherwise occupy).
 *
 * <p><b>Tier is read from the local client player, not the stack</b> -- it's a per-player fact
 * ({@code api.Yconomics#getCoinPurseTier}), synced to the client via {@code
 * registration.ModAttachments#COIN_PURSE_TIER}'s existing {@code .sync(...)}, not stored on the
 * item itself. This is correct for the overwhelmingly common case (a player looking at their own
 * inventory) and a reasonable simplification for the rare case of viewing another entity's
 * inventory (e.g. a debug/admin screen) -- it would show the viewer's own tier in that case, not
 * the other entity's. Not expected to matter in practice; flagged here rather than silently assumed.
 */
public final class CoinPurseDecorator implements IItemDecorator {

    private static final int TIER_COLOR = 0xFFD700; // gold
    private static final int NUGGET_COLOR = 0xFFFFFF; // white, matches vanilla's own count color

    @Override
    public boolean render(GuiGraphicsExtractor guiGraphics, Font font, ItemStack stack, int xOffset, int yOffset) {
        Player player = Minecraft.getInstance().player;
        if (player != null) {
            String tierText = "T" + Yconomics.getCoinPurseTier(player);
            guiGraphics.text(font, tierText, xOffset + 1, yOffset + 1, TIER_COLOR, true);
        }

        int total = stack.getOrDefault(ModItems.COIN_PURSE_CONTENTS, CoinPurseContents.EMPTY).totalCount();
        String nuggetText = String.valueOf(total);
        // Same corner/offset formula vanilla's own itemCount uses for the stack-size number --
        // consistent placement, safe to reuse since the purse's real stack count never draws here.
        int textX = xOffset + 19 - 2 - font.width(nuggetText);
        int textY = yOffset + 6 + 3;
        guiGraphics.text(font, nuggetText, textX, textY, NUGGET_COLOR, true);

        return false;
    }
}
