package com.github.cerealklla.yconomics.bag.client;

import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;

/** Render state for {@link com.github.cerealklla.yconomics.bag.LootBagEntity} -- see {@link LootBagRenderer}. */
public class LootBagRenderState extends EntityRenderState {
    public final ItemStackRenderState icon = new ItemStackRenderState();
}
