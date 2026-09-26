package com.github.cerealklla.yconomics.registration;

import com.github.cerealklla.yconomics.YconomicsMod;

import com.mojang.serialization.Codec;

import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/** Per-player data attachments -- currently just the Coin Purse tier (design doc Section 2/currency). */
public final class ModAttachments {

    private ModAttachments() {
    }

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, YconomicsMod.MODID);

    // copyOnDeath() -- a character stat, not something death should ever reset (see Lyfe's own
    // decisions.md, 2026-09-25, for the exact bug this avoids: NeoForge attachments do NOT survive
    // death by default, a real playtest-found bug there). Synced so a future client-side tooltip
    // could show a player's own current tier; persisted so it survives save/reload.
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Integer>> COIN_PURSE_TIER = ATTACHMENT_TYPES.register(
            "coin_purse_tier",
            () -> AttachmentType.builder(() -> 0)
                    .serialize(Codec.INT.fieldOf("value"))
                    .sync(ByteBufCodecs.VAR_INT)
                    .copyOnDeath()
                    .build());
}
