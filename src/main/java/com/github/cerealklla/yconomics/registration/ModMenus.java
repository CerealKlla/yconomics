package com.github.cerealklla.yconomics.registration;

import com.github.cerealklla.yconomics.YconomicsMod;
import com.github.cerealklla.yconomics.bag.LootBagMenu;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Menu types for the dropped-item bag mechanic (design doc Section 4). */
public final class ModMenus {

    private ModMenus() {
    }

    public static final DeferredRegister<MenuType<?>> MENU_TYPES = DeferredRegister.create(Registries.MENU, YconomicsMod.MODID);

    // A custom MenuType (rather than reusing vanilla's own MenuType.GENERIC_9x6, which ChestMenu.sixRows
    // would otherwise use) so the client wires this up to LootBagScreen -- with its "Take All" button --
    // instead of vanilla's own plain ContainerScreen. No extra client data needed: both sides build an
    // identical 6-row LootBagMenu (the client's own container starts empty -- contents sync through the
    // menu's own slot packets as usual, same as any vanilla container).
    // menuType is @Nullable on AbstractContainerMenu's own constructor -- the client-side
    // reconstruction here doesn't need it (only server-side menu-button dispatch cares), so this
    // passes null rather than self-referencing LOOT_BAG from within its own initializer (illegal).
    public static final DeferredHolder<MenuType<?>, MenuType<LootBagMenu>> LOOT_BAG = MENU_TYPES.register(
            "loot_bag",
            () -> IMenuTypeExtension.create((windowId, inventory, extraData) ->
                    new LootBagMenu(null, windowId, inventory, new SimpleContainer(LootBagMenu.SLOT_COUNT))));
}
