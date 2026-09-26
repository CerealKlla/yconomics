package com.github.cerealklla.yconomics.bag.client;

import com.github.cerealklla.yconomics.bag.LootBagMenu;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

/**
 * {@link LootBagMenu}'s screen -- a copy of vanilla's own generic {@code ContainerScreen} (which
 * can't be reused directly: it's hardcoded to {@code AbstractContainerScreen<ChestMenu>}, and Java
 * generics are invariant, so a screen for the {@code LootBagMenu} subclass can't just extend it)
 * plus one addition: a "Take All" button (design doc Section 4, see decisions.md 2026-09-25) that
 * asks the server to run {@link LootBagMenu#clickMenuButton} via the same generic
 * inventory-button-click packet vanilla's own furnace/enchanting-table buttons use.
 */
public final class LootBagScreen extends AbstractContainerScreen<LootBagMenu> {

    private static final Identifier CONTAINER_BACKGROUND = Identifier.withDefaultNamespace("textures/gui/container/generic_54.png");
    private final int containerRows;

    public LootBagScreen(LootBagMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 176, 114 + menu.getRowCount() * 18);
        this.containerRows = menu.getRowCount();
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    protected void init() {
        super.init();
        int buttonWidth = 80;
        addRenderableWidget(Button.builder(Component.literal("Take All"), button ->
                        Minecraft.getInstance().gameMode.handleInventoryButtonClick(menu.containerId, LootBagMenu.TAKE_ALL_BUTTON_ID))
                .bounds(leftPos + (imageWidth - buttonWidth) / 2, topPos - 24, buttonWidth, 20)
                .build());
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        int xo = (this.width - this.imageWidth) / 2;
        int yo = (this.height - this.imageHeight) / 2;
        graphics.blit(RenderPipelines.GUI_TEXTURED, CONTAINER_BACKGROUND, xo, yo, 0.0F, 0.0F, this.imageWidth, this.containerRows * 18 + 17, 256, 256);
        graphics.blit(RenderPipelines.GUI_TEXTURED, CONTAINER_BACKGROUND, xo, yo + this.containerRows * 18 + 17, 0.0F, 126.0F, this.imageWidth, 96, 256, 256);
    }
}
