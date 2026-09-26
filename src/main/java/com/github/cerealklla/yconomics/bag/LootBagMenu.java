package com.github.cerealklla.yconomics.bag;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * A 6-row {@link ChestMenu} with one addition: button id {@link #TAKE_ALL_BUTTON_ID} ("Take All",
 * see {@code client.LootBagScreen}) moves every item in the bag's slots into the viewing player's
 * inventory in one action, added after a playtest request (design doc Section 4, see decisions.md
 * 2026-09-25).
 */
public class LootBagMenu extends ChestMenu {

    public static final int SLOT_COUNT = 54;
    public static final int TAKE_ALL_BUTTON_ID = 0;

    public LootBagMenu(MenuType<?> menuType, int containerId, Inventory playerInventory, Container container) {
        super(menuType, containerId, playerInventory, container, 6);
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id != TAKE_ALL_BUTTON_ID) {
            return super.clickMenuButton(player, id);
        }
        // Slots 0..SLOT_COUNT-1 are the bag's own container; the rest belong to the player's own
        // inventory/hotbar (ChestMenu's own slot layout convention) and must not be touched here.
        for (int i = 0; i < SLOT_COUNT; i++) {
            Slot slot = getSlot(i);
            ItemStack stack = slot.getItem();
            if (stack.isEmpty()) {
                continue;
            }
            if (!player.getInventory().add(stack)) {
                player.drop(stack, false);
            }
            slot.set(ItemStack.EMPTY);
        }
        broadcastChanges();
        return true;
    }
}
