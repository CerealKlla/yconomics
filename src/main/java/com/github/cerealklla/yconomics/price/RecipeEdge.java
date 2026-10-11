package com.github.cerealklla.yconomics.price;

import java.util.List;
import java.util.Map;

import net.minecraft.resources.Identifier;

/**
 * One normalized "these inputs produce this output" relationship -- the one shape every real recipe
 * source ({@link RecipeIndex}'s vanilla crafting/smelting scan, Lyfe's tool/weapon/armor recipes via
 * {@code bridge.LyfeRecipeCostBridge}) gets flattened into before {@link PriceDerivation} ever sees
 * it, so the solver itself doesn't need to know or care where an edge came from.
 *
 * <p>{@code inputs} maps each required ingredient "slot" to the real options that can fill it (a
 * plain vanilla {@code Ingredient}/generic component group can match several interchangeable items)
 * -- the solver always prices a slot at its cheapest resolvable option, same "always take the
 * cheapest path" precedent already established in {@code Settlemynts.zone.PlannedInventoryClearing}.
 * A slot with exactly one option is a specific/pinned ingredient. {@code outputCount} is how many
 * units of {@code output} one application of this recipe actually yields (e.g. Log->Planks is 1->4,
 * most recipes are N->1).
 */
public record RecipeEdge(Identifier output, int outputCount, List<List<Identifier>> inputs) {

    /**
     * True if every input slot is the exact same ingredient option-set -- a "refines into" shape
     * (Log -> Planks, Planks -> Sticks, Gold Ingot -> Gold Nuggets) as opposed to assembly (2 Sticks
     * + 3 Cobblestone -> Stone Pickaxe, several *distinct* ingredient types combining into something
     * new). See {@link PriceDerivation}'s own doc -- this is the real distinction between the two
     * directions: a refinement relationship genuinely links both items' values (either one can set a
     * floor for the other), but an assembly recipe only ever prices its *output* -- a component's own
     * value is never influenced by some unrelated recipe it happens to be used in.
     */
    public boolean isSingleIngredientType() {
        if (inputs.isEmpty()) {
            return false;
        }
        List<Identifier> first = inputs.get(0);
        for (List<Identifier> slot : inputs) {
            if (!slot.equals(first)) {
                return false;
            }
        }
        return true;
    }
}
