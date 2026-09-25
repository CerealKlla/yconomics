package com.github.cerealklla.yconomics.recipe;

import com.mojang.serialization.MapCodec;

import com.github.cerealklla.yconomics.registration.ModRecipes;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

/**
 * "Gambling" recipe: a single {@link Items#GOLD_INGOT} anywhere in the crafting grid, nothing
 * else, breaks down into a randomized {@link #MIN_NUGGETS}-{@link #MAX_NUGGETS} {@link
 * Items#GOLD_NUGGET}s (5-12, average 8.5) -- expected-value-negative against the vanilla
 * 9-nuggets-to-1-ingot recipe it's the inverse of, but can occasionally pay off. Not the exact
 * inverse recipe (9 in, 9 out every time) on purpose -- that would just be a shape-only recipe
 * with no actual economic content.
 *
 * <p>A {@link CustomRecipe} rather than a data-driven shaped/shapeless recipe because the output
 * isn't fixed -- vanilla's shaped/shapeless recipe codecs have no concept of a randomized result
 * count, only {@code CustomRecipe#assemble} can compute one per craft. Modeled directly on
 * vanilla's own {@code RepairItemRecipe} (same "singleton recipe, {@code MapCodec.unit}, no JSON
 * fields" shape) -- confirmed against the decompiled source rather than assumed. Declared in a
 * datapack JSON ({@code data/yconomics/recipe/gambling.json}) with just {@code {"type":
 * "yconomics:gambling"}}, same as any other recipe, despite having no configurable fields.
 */
public class GamblingRecipe extends CustomRecipe {

    static final int MIN_NUGGETS = 5;
    static final int MAX_NUGGETS = 12;

    public static final GamblingRecipe INSTANCE = new GamblingRecipe();
    public static final MapCodec<GamblingRecipe> MAP_CODEC = MapCodec.unit(INSTANCE);
    public static final StreamCodec<RegistryFriendlyByteBuf, GamblingRecipe> STREAM_CODEC = StreamCodec.unit(INSTANCE);

    private final RandomSource random = RandomSource.create();

    @Override
    public boolean matches(CraftingInput input, Level level) {
        return findSoleIngot(input) != null;
    }

    @Override
    public ItemStack assemble(CraftingInput input) {
        ItemStack ingot = findSoleIngot(input);
        if (ingot == null) {
            return ItemStack.EMPTY;
        }
        return new ItemStack(Items.GOLD_NUGGET, rollNuggetCount(random));
    }

    /** Non-null only when the grid holds exactly one non-empty slot, a single {@link Items#GOLD_INGOT}. */
    private static ItemStack findSoleIngot(CraftingInput input) {
        if (input.ingredientCount() != 1) {
            return null;
        }
        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getItem(i);
            if (!stack.isEmpty()) {
                return stack.is(Items.GOLD_INGOT) && stack.getCount() == 1 ? stack : null;
            }
        }
        return null;
    }

    /** Inclusive [{@link #MIN_NUGGETS}, {@link #MAX_NUGGETS}]. Package-visible for {@code GamblingRecipeTest}. */
    static int rollNuggetCount(RandomSource random) {
        return MIN_NUGGETS + random.nextInt(MAX_NUGGETS - MIN_NUGGETS + 1);
    }

    @Override
    public RecipeSerializer<GamblingRecipe> getSerializer() {
        return ModRecipes.GAMBLING_SERIALIZER.get();
    }
}
