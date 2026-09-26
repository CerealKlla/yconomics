package com.github.cerealklla.yconomics.bag.client;

import com.github.cerealklla.yconomics.bag.LootBagEntity;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * {@link LootBagEntity}'s client renderer. No dedicated model/texture -- renders vanilla's own
 * {@link Items#BUNDLE} icon (a real, existing item, thematically the closest vanilla equivalent to
 * "a bag") floating in place, plus the entity's own "Loot Bag" nametag (handled automatically by
 * the inherited default {@code submit}/{@code extractRenderState}, since neither is overridden for
 * that part -- see {@code EntityRenderer}'s own name-tag logic). Good enough to actually see where
 * a bag is (a real playtest request, see decisions.md 2026-09-25) without needing to author new art.
 *
 * <p>Also what makes this entity exist client-side at all without crashing in the first place --
 * every custom {@code Entity} needs *some* registered renderer or the game throws a
 * {@code NullPointerException} the moment one is spawned in view (a real bug this project hit; see
 * decisions.md).
 */
public final class LootBagRenderer extends EntityRenderer<LootBagEntity, LootBagRenderState> {

    private final ItemModelResolver itemModelResolver;

    public LootBagRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.itemModelResolver = context.getItemModelResolver();
    }

    @Override
    public LootBagRenderState createRenderState() {
        return new LootBagRenderState();
    }

    @Override
    public void extractRenderState(LootBagEntity entity, LootBagRenderState state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        // Built here, not as a static field -- constructing an ItemStack too early (at class-load
        // time, before the game's registries/components finish bootstrapping) crashed resource pack
        // loading entirely ("Components not bound yet", see decisions.md 2026-09-25). Cheap enough
        // to build fresh each call.
        itemModelResolver.updateForNonLiving(state.icon, new ItemStack(Items.BUNDLE), ItemDisplayContext.GROUND, entity);
    }

    @Override
    public void submit(LootBagRenderState state, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState camera) {
        poseStack.pushPose();
        poseStack.translate(0.0, 0.25, 0.0);
        state.icon.submit(poseStack, submitNodeCollector, state.lightCoords, OverlayTexture.NO_OVERLAY, EntityRenderState.NO_OUTLINE);
        poseStack.popPose();
        super.submit(state, poseStack, submitNodeCollector, camera);
    }
}
