package com.github.cerealklla.yconomics.price;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.github.cerealklla.yconomics.bridge.LyfeRecipeCostBridge;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplay;

/**
 * Builds the full {@link RecipeEdge} graph once per {@code /yconomics recalculateprices} run (see
 * {@code price.PriceCommands}) -- two sources, both read fresh at call time rather than cached
 * across runs, so a server's actual current recipe set (including any datapack overrides) is always
 * what gets priced:
 *
 * <ul>
 *   <li><b>Vanilla crafting/smelting recipes</b> ({@link MinecraftServer#getRecipeManager()}) --
 *   {@code CraftingRecipe} (covers Shaped/Shapeless/any other real crafting recipe) and
 *   {@code AbstractCookingRecipe} (Smelting/Blasting/Smoking/Campfire -- ore/raw-material -> ingot
 *   chains). Every other recipe type (stonecutting, smithing, brewing) is deliberately out of scope
 *   this pass. Inputs come from {@code Recipe#placementInfo().ingredients()} (uniform across every
 *   recipe type, one entry per required slot -- a repeated ingredient naturally represents "needs 2
 *   of this," no separate quantity field needed); the output comes from {@code Recipe#display()}'s
 *   {@code SlotDisplay.ItemStackSlotDisplay} (both {@code ShapedRecipe}/{@code ShapelessRecipe} and
 *   {@code AbstractCookingRecipe} construct their result display this exact way -- confirmed against
 *   the decompiled source, since neither exposes a public result accessor directly).</li>
 *   <li><b>Lyfe's generated tool/weapon/armor recipes</b>, via {@link LyfeRecipeCostBridge} when
 *   Lyfe is loaded -- needed because Lyfe strips vanilla's own tool/weapon recipes outright, so
 *   {@code RecipeManager} alone has nothing left to read for those items on such a server.</li>
 * </ul>
 */
public final class RecipeIndex {

    private final List<RecipeEdge> edges = new ArrayList<>();
    private final Map<Identifier, List<RecipeEdge>> consumedBy = new HashMap<>();

    public static RecipeIndex buildFor(MinecraftServer server) {
        RecipeIndex index = new RecipeIndex();
        index.addVanillaRecipes(server);
        if (LyfeRecipeCostBridge.isLoaded()) {
            index.addLyfeRecipes();
            index.addLyfeFoodRecipes();
        }
        return index;
    }

    private void addVanillaRecipes(MinecraftServer server) {
        for (RecipeHolder<?> holder : server.getRecipeManager().getRecipes()) {
            Recipe<?> recipe = holder.value();
            if (!(recipe instanceof CraftingRecipe) && !(recipe instanceof AbstractCookingRecipe)) {
                continue;
            }
            ItemStack result = resultOf(recipe);
            if (result == null || result.isEmpty()) {
                continue;
            }
            List<List<Identifier>> inputs = new ArrayList<>();
            for (Ingredient ingredient : recipe.placementInfo().ingredients()) {
                List<Identifier> options = ingredient.items().map(this::idOf).toList();
                if (!options.isEmpty()) {
                    inputs.add(options);
                }
            }
            if (inputs.isEmpty()) {
                continue; // No real ingredient cost to derive from (e.g. a special/empty-input recipe).
            }
            addEdge(new RecipeEdge(idOf(result.getItem()), result.getCount(), inputs));
        }
    }

    private void addLyfeRecipes() {
        for (LyfeRecipeCostBridge.RecipeInfo recipe : LyfeRecipeCostBridge.getAllRecipes()) {
            List<List<Identifier>> inputs = new ArrayList<>();
            for (Map.Entry<Identifier, Integer> entry : recipe.specificComponents().entrySet()) {
                for (int i = 0; i < entry.getValue(); i++) {
                    inputs.add(List.of(entry.getKey()));
                }
            }
            for (Map.Entry<String, Integer> entry : recipe.genericComponents().entrySet()) {
                List<Identifier> members = LyfeRecipeCostBridge.componentGroupMembers(entry.getKey());
                if (members.isEmpty()) {
                    continue;
                }
                for (int i = 0; i < entry.getValue(); i++) {
                    inputs.add(members);
                }
            }
            if (inputs.isEmpty()) {
                continue;
            }
            addEdge(new RecipeEdge(recipe.resultId(), 1, inputs));
        }
    }

    /** Lyfe's generated food recipes (Bread, cooked meats, etc.) -- see {@link LyfeRecipeCostBridge}'s own "Food" doc for why these are a separate source from {@link #addLyfeRecipes}. */
    private void addLyfeFoodRecipes() {
        for (LyfeRecipeCostBridge.FoodRecipeInfo recipe : LyfeRecipeCostBridge.getAllFoodRecipes()) {
            List<List<Identifier>> inputs = new ArrayList<>();
            for (Map.Entry<Identifier, Integer> entry : recipe.components().entrySet()) {
                for (int i = 0; i < entry.getValue(); i++) {
                    inputs.add(List.of(entry.getKey()));
                }
            }
            if (inputs.isEmpty()) {
                continue;
            }
            addEdge(new RecipeEdge(recipe.resultId(), 1, inputs));
        }
    }

    private void addEdge(RecipeEdge edge) {
        edges.add(edge);
        if (!edge.isSingleIngredientType()) {
            return; // Assembly (multiple distinct ingredient types) only ever prices its output -- see RecipeEdge's own doc.
        }
        for (Identifier option : edge.inputs().get(0)) {
            consumedBy.computeIfAbsent(option, k -> new ArrayList<>()).add(edge);
        }
    }

    /** Every recipe that produces {@code itemId} -- the "crafted/refined from" direction, applies to every recipe regardless of shape. */
    public List<RecipeEdge> producing(Identifier itemId) {
        List<RecipeEdge> result = new ArrayList<>();
        for (RecipeEdge edge : edges) {
            if (edge.output().equals(itemId)) {
                result.add(edge);
            }
        }
        return result;
    }

    /**
     * Every single-ingredient-type ("refines into") recipe that consumes {@code itemId} -- the
     * "breaks down into" direction. Deliberately excludes assembly recipes (multiple distinct
     * ingredient types) -- see {@link RecipeEdge#isSingleIngredientType}'s own doc for why a
     * component's value must never be influenced by an unrelated recipe it happens to be used in.
     */
    public List<RecipeEdge> refiningInto(Identifier itemId) {
        return consumedBy.getOrDefault(itemId, List.of());
    }

    public int edgeCount() {
        return edges.size();
    }

    private static ItemStack resultOf(Recipe<?> recipe) {
        for (RecipeDisplay display : recipe.display()) {
            if (display.result() instanceof SlotDisplay.ItemStackSlotDisplay itemStackSlot) {
                return itemStackSlot.stack().create();
            }
        }
        return null;
    }

    private Identifier idOf(Holder<Item> item) {
        return idOf(item.value());
    }

    private Identifier idOf(Item item) {
        return BuiltInRegistries.ITEM.getKey(item);
    }
}
