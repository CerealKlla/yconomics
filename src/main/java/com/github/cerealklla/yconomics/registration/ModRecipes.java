package com.github.cerealklla.yconomics.registration;

import java.util.function.Supplier;

import com.github.cerealklla.yconomics.YconomicsMod;
import com.github.cerealklla.yconomics.recipe.GamblingRecipe;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModRecipes {

    private ModRecipes() {
    }

    public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS =
            DeferredRegister.create(Registries.RECIPE_SERIALIZER, YconomicsMod.MODID);

    // No custom RecipeType needed -- GamblingRecipe is a CraftingRecipe (via CustomRecipe), whose
    // default getType() is the vanilla shared RecipeType.CRAFTING, same as vanilla's own special
    // crafting recipes (RepairItemRecipe, BannerDuplicateRecipe, etc.).
    public static final Supplier<RecipeSerializer<GamblingRecipe>> GAMBLING_SERIALIZER = RECIPE_SERIALIZERS.register(
            "gambling",
            () -> new RecipeSerializer<>(GamblingRecipe.MAP_CODEC, GamblingRecipe.STREAM_CODEC)
    );
}
