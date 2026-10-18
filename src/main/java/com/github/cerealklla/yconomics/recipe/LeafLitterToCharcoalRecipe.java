package com.github.cerealklla.yconomics.recipe;

import com.mojang.serialization.MapCodec;

import com.github.cerealklla.yconomics.registration.ModRecipes;

import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

/**
 * Explicit user request: "a shapeless recipe that converts a stack of 64 leaf litter into 10
 * charcoal." A real data-driven shaped/shapeless recipe can't do this -- vanilla's crafting grid
 * is capped at 9 slots, consuming exactly 1 item per occupied slot per craft, so there's no way to
 * express "consume a full 64-stack in one slot" through that codec at all. A {@link CustomRecipe}
 * (same shape as {@link GamblingRecipe}, confirmed against the decompiled {@code RepairItemRecipe}
 * as the real precedent for a singleton, no-JSON-fields recipe) can inspect a slot's real stack
 * size directly, which is the only way to match "a full stack" as the actual trigger condition
 * rather than approximating it with a smaller, vanilla-shaped batch.
 *
 * <p>Requires EXACTLY 64 Leaf Litter in one slot, nothing else in the grid -- not "64 or more" (a
 * single slot can never hold more than a stack anyway) and not "any amount," since the user's own
 * spec was specifically "a stack of 64," not a partial one.
 */
public class LeafLitterToCharcoalRecipe extends CustomRecipe {

    static final int CHARCOAL_OUTPUT = 10;
    private static final int REQUIRED_COUNT = 64;

    public static final LeafLitterToCharcoalRecipe INSTANCE = new LeafLitterToCharcoalRecipe();
    public static final MapCodec<LeafLitterToCharcoalRecipe> MAP_CODEC = MapCodec.unit(INSTANCE);
    public static final StreamCodec<RegistryFriendlyByteBuf, LeafLitterToCharcoalRecipe> STREAM_CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public boolean matches(CraftingInput input, Level level) {
        return findSoleLeafLitterStack(input) != null;
    }

    @Override
    public ItemStack assemble(CraftingInput input) {
        ItemStack stack = findSoleLeafLitterStack(input);
        if (stack == null) {
            return ItemStack.EMPTY;
        }
        return new ItemStack(Items.CHARCOAL, CHARCOAL_OUTPUT);
    }

    /**
     * Consumes the entire matched slot outright rather than vanilla's own default (decrement every
     * occupied slot by exactly 1, {@code CraftingRecipe#defaultCraftingReminder}) -- that default is
     * what {@link GamblingRecipe} relies on, correct there since it requires exactly 1 Gold Ingot,
     * but it would leave 63 Leaf Litter behind here, defeating the entire point of matching on a
     * full 64-stack in the first place.
     */
    @Override
    public NonNullList<ItemStack> getRemainingItems(CraftingInput input) {
        return NonNullList.withSize(input.size(), ItemStack.EMPTY);
    }

    /** Non-null only when the grid holds exactly one non-empty slot, a full 64-stack of {@link Items#LEAF_LITTER}. */
    private static ItemStack findSoleLeafLitterStack(CraftingInput input) {
        if (input.ingredientCount() != 1) {
            return null;
        }
        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getItem(i);
            if (!stack.isEmpty()) {
                return stack.is(Items.LEAF_LITTER) && stack.getCount() == REQUIRED_COUNT ? stack : null;
            }
        }
        return null;
    }

    @Override
    public RecipeSerializer<LeafLitterToCharcoalRecipe> getSerializer() {
        return ModRecipes.LEAF_LITTER_TO_CHARCOAL_SERIALIZER.get();
    }
}
