package com.github.cerealklla.yconomics.bridge;

import java.util.List;
import java.util.Map;

import com.github.cerealklla.lyfe.cook.GeneratedFoodRecipe;
import com.github.cerealklla.lyfe.cook.ServerFoodRecipeStore;
import com.github.cerealklla.lyfe.craft.ComponentGroups;
import com.github.cerealklla.lyfe.craft.GeneratedRecipe;
import com.github.cerealklla.lyfe.craft.ServerRecipeStore;

import net.minecraft.resources.Identifier;
import net.neoforged.fml.ModList;

/**
 * Feeds Lyfe's server-generated recipes into the price-derivation engine ({@code
 * price.RecipeIndex}) -- two independent sets, both needed for the identical reason: Lyfe strips the
 * real vanilla recipe outright and replaces it with its own generated one, so on a server with Lyfe
 * loaded, {@code RecipeManager} alone has nothing left to read for either:
 *
 * <ul>
 *   <li>Tool/weapon/armor ({@code craft.GeneratedRecipe}/{@code craft.ServerRecipeStore}) --
 *   stripped by {@code craft.VanillaRecipeStripper}.</li>
 *   <li>Food ({@code cook.GeneratedFoodRecipe}/{@code cook.ServerFoodRecipeStore}, added
 *   2026-10-11, real gap found via direct user question before this feature's final testing: "is
 *   this including cooking recipe values as well right?") -- stripped by {@code
 *   cook.VanillaFoodRecipeStripper} (both the crafting-table recipe, e.g. Bread, and the smelting/
 *   smoking/campfire trio for raw-to-cooked meat/fish). Always a single result and no generic-
 *   component-group feature (that system's own deliberate scope simplification -- see Lyfe's own
 *   decisions.md), so {@link #getAllFoodRecipes} is a plainer shape than {@link #getAllRecipes}.</li>
 * </ul>
 *
 * <p>Optional soft dependency, same isolation convention as every other bridge in this suite (e.g.
 * Settlemynts' own {@code bridge.LyfeCraftingBridge}, which already proves this exact recipe shape
 * is safely readable this way) -- every call site elsewhere in this mod must check {@link
 * #isLoaded()} first.
 */
public final class LyfeRecipeCostBridge {

    private LyfeRecipeCostBridge() {
    }

    public static boolean isLoaded() {
        return ModList.get().isLoaded("lyfe");
    }

    /** Local mirror of {@code craft.GeneratedRecipe} -- a Lyfe type never appears in a signature outside this bridge, same precedent as every other bridge in the suite. */
    public record RecipeInfo(Identifier resultId, Map<Identifier, Integer> specificComponents, Map<String, Integer> genericComponents) {
    }

    public static List<RecipeInfo> getAllRecipes() {
        return ServerRecipeStore.all().values().stream()
                .map(r -> new RecipeInfo(r.resultId(), r.specificComponents(), r.genericComponents()))
                .toList();
    }

    public static List<Identifier> componentGroupMembers(String groupName) {
        return ComponentGroups.membersOf(groupName);
    }

    /** Local mirror of {@code cook.GeneratedFoodRecipe} -- see class doc's "Food" bullet. */
    public record FoodRecipeInfo(Identifier resultId, Map<Identifier, Integer> components) {
    }

    public static List<FoodRecipeInfo> getAllFoodRecipes() {
        return ServerFoodRecipeStore.all().values().stream()
                .map(r -> new FoodRecipeInfo(r.resultId(), r.components()))
                .toList();
    }
}
