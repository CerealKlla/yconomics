package com.github.cerealklla.yconomics.bag.client;

import com.github.cerealklla.yconomics.bag.LootBagEntity;

import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;

/**
 * The bare minimum renderer {@link LootBagEntity} needs to exist client-side without crashing --
 * NeoForge/vanilla requires every spawned {@code Entity} subtype to have a registered {@code
 * EntityRenderer}, even if (like here) it draws no model of its own. A real crash this project hit
 * the first time a bag was created in view (see decisions.md, 2026-09-25): {@code runServer}
 * boot-smoke-testing can never catch a missing-renderer bug, since a dedicated server never
 * renders anything -- this needed a real {@code runClient} session to surface at all.
 *
 * <p>No model/texture is drawn -- the entity's own floating "Loot Bag" nametag (set in {@code
 * LootBagEntity}'s constructor via {@code setCustomName}/{@code setCustomNameVisible}) is the
 * entire visual for this first version, handled automatically by {@link EntityRenderer}'s own
 * default {@code submit}/{@code extractRenderState} (neither overridden here) once a name is set.
 * A real model is a follow-up, not built this pass.
 */
public final class LootBagRenderer extends EntityRenderer<LootBagEntity, EntityRenderState> {

    public LootBagRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public EntityRenderState createRenderState() {
        return new EntityRenderState();
    }
}
