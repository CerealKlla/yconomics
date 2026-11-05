package com.github.cerealklla.yconomics.price;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import net.minecraft.resources.Identifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Real live bug report, 2026-11-05: "How on earth do you figure oak wood stairs are worth 108g?"
 * (Oak Stairs priced 108g buy/65g sell, two other plank goods in the same shop also absurdly priced
 * around 144g/86g). This test hand-builds the real vanilla oak-wood recipe graph (verified real
 * recipe shapes/quantities) directly as {@link RecipeEdge}s -- bypassing {@link
 * RecipeIndex#buildFor(net.minecraft.server.MinecraftServer)}, which needs a live server/RecipeManager
 * this project's test harness can't bootstrap (same reasoning the suite's other tests avoid
 * constructing a real {@code ItemStack}) -- and runs the exact real two-stage pricing pipeline
 * ({@link PriceDerivation#derive} for every item, then {@link PriceCommands#enforceBreaksDownIntoFloor},
 * exactly as {@link PriceCommands#runRecalculation} does) to get real, non-hand-simulated numbers.
 *
 * <p><b>Root cause confirmed by this test</b>: {@link PriceDerivation#derive}'s own "breaks down
 * into" direction is correctly cycle-safe (it values a raw material against its product's
 * {@link PriceDerivation} backward-only cost, never the product's full forward-inflated price -- see
 * that class's own extensive doc). But {@link PriceCommands#enforceBreaksDownIntoFloor} is a
 * *second*, separate pass (added 2026-10-11 for the real "a Log must never price below its Planks"
 * requirement) that raises a raw material's price using its product's **final, fully-forward-
 * inflated** price (`snapshot.get(edge.output())`, i.e. {@code derive()}'s real result, not a
 * backward-only one) -- exactly the "a direct relationship's two directions each apply their own
 * markup to the other's already-marked-up value" instability {@link PriceDerivation}'s own class doc
 * explains fixing for {@code derive()} itself, reopened here one level later. Oak Planks sits at the
 * root of many expensive-output, poor-yield recipes (a Chest alone needs 8 Planks for 1 output, a
 * genuinely bad per-unit ratio once marked up) -- {@code enforceBreaksDownIntoFloor} raises Oak
 * Planks' own stored price to match whichever one of those is highest, and because Oak Stairs is
 * *itself* one of Oak Planks' many "breaks down into" products, Oak Stairs' *own* enforcement pass
 * then raises ITS price again using Oak Planks' now-inflated-a-second-time value transitively through
 * further real items (Oak Boat, Crafting Table, etc. each independently push Planks up further via
 * the same mechanism) -- the result compounds across several real plank-sink items at once, which is
 * exactly why multiple unrelated plank goods in the same shop screenshot were all inflated together,
 * not just one.
 *
 * <p><b>The fix</b>: {@link PriceCommands#enforceBreaksDownIntoFloor} now reads each product's
 * backward-only cost (recomputed via a fresh {@link PriceDerivation#backwardOnly}-equivalent call,
 * exposed as {@link PriceDerivation#deriveBackwardOnly}) instead of its full forward-inclusive
 * {@code derive()} result -- the exact same cycle-safety fix {@code PriceDerivation} itself already
 * uses internally for this identical relationship, now applied consistently in the enforcement pass
 * too, instead of reopening the cycle with the other value.
 */
class PriceDerivationTest {

    private static final Identifier OAK_LOG = id("oak_log");
    private static final Identifier OAK_PLANKS = id("oak_planks");
    private static final Identifier STICK = id("stick");
    private static final Identifier OAK_STAIRS = id("oak_stairs");
    private static final Identifier OAK_SLAB = id("oak_slab");
    private static final Identifier OAK_DOOR = id("oak_door");
    private static final Identifier OAK_TRAPDOOR = id("oak_trapdoor");
    private static final Identifier CHEST = id("chest");
    private static final Identifier CRAFTING_TABLE = id("crafting_table");
    private static final Identifier BOWL = id("bowl");
    private static final Identifier OAK_BOAT = id("oak_boat");
    private static final Identifier OAK_BUTTON = id("oak_button");
    private static final Identifier OAK_PRESSURE_PLATE = id("oak_pressure_plate");
    private static final Identifier LADDER = id("ladder");
    private static final Identifier COMPOSTER = id("composter");

    private static final List<Identifier> UNIVERSE = List.of(
            OAK_LOG, OAK_PLANKS, STICK, OAK_STAIRS, OAK_SLAB, OAK_DOOR, OAK_TRAPDOOR, CHEST,
            CRAFTING_TABLE, BOWL, OAK_BOAT, OAK_BUTTON, OAK_PRESSURE_PLATE, LADDER, COMPOSTER);

    private static Identifier id(String path) {
        return Identifier.withDefaultNamespace(path);
    }

    /** Real vanilla oak-wood recipe shapes -- same real quantities confirmed against the game's actual recipes. */
    private static RecipeIndex buildOakWoodGraph() {
        RecipeIndex index = new RecipeIndex();
        index.addEdge(new RecipeEdge(OAK_PLANKS, 4, List.of(List.of(OAK_LOG))));
        index.addEdge(new RecipeEdge(STICK, 4, List.of(List.of(OAK_PLANKS), List.of(OAK_PLANKS))));
        index.addEdge(new RecipeEdge(OAK_STAIRS, 4, nCopies(OAK_PLANKS, 6)));
        index.addEdge(new RecipeEdge(OAK_SLAB, 6, nCopies(OAK_PLANKS, 3)));
        index.addEdge(new RecipeEdge(OAK_DOOR, 3, nCopies(OAK_PLANKS, 6)));
        index.addEdge(new RecipeEdge(OAK_TRAPDOOR, 2, nCopies(OAK_PLANKS, 6)));
        index.addEdge(new RecipeEdge(CHEST, 1, nCopies(OAK_PLANKS, 8)));
        index.addEdge(new RecipeEdge(CRAFTING_TABLE, 1, nCopies(OAK_PLANKS, 4)));
        index.addEdge(new RecipeEdge(BOWL, 4, nCopies(OAK_PLANKS, 3)));
        index.addEdge(new RecipeEdge(OAK_BOAT, 1, nCopies(OAK_PLANKS, 5)));
        index.addEdge(new RecipeEdge(OAK_BUTTON, 1, nCopies(OAK_PLANKS, 1)));
        index.addEdge(new RecipeEdge(OAK_PRESSURE_PLATE, 1, nCopies(OAK_PLANKS, 2)));
        index.addEdge(new RecipeEdge(LADDER, 3, nCopies(STICK, 7)));
        index.addEdge(new RecipeEdge(COMPOSTER, 1, nCopies(OAK_SLAB, 7)));
        return index;
    }

    private static List<List<Identifier>> nCopies(Identifier item, int count) {
        return java.util.stream.IntStream.range(0, count).mapToObj(i -> List.of(item)).toList();
    }

    private static Map<Identifier, Integer> deriveAll(PriceDerivation derivation) {
        Map<Identifier, Integer> prices = new HashMap<>();
        for (Identifier id : UNIVERSE) {
            prices.put(id, derivation.derive(id));
        }
        return prices;
    }

    @Test
    void oakStairsPricesSanelyAfterTheFullPipeline() {
        RecipeIndex index = buildOakWoodGraph();
        PriceDerivation derivation = new PriceDerivation(index);
        Map<Identifier, Integer> prices = deriveAll(derivation);

        int stairsBeforeEnforcement = prices.get(OAK_STAIRS);
        int planksBeforeEnforcement = prices.get(OAK_PLANKS);

        Set<Identifier> ceilingHit = new HashSet<>();
        PriceCommands.enforceBreaksDownIntoFloor(prices, index, derivation, ceilingHit);

        int stairsAfter = prices.get(OAK_STAIRS);
        int planksAfter = prices.get(OAK_PLANKS);
        int logAfter = prices.get(OAK_LOG);
        int planksBackwardOnly = derivation.deriveBackwardOnly(OAK_PLANKS);

        System.out.println("[PriceDerivationTest] oak_planks: before=" + planksBeforeEnforcement + " after=" + planksAfter);
        System.out.println("[PriceDerivationTest] oak_stairs: before=" + stairsBeforeEnforcement + " after=" + stairsAfter);
        System.out.println("[PriceDerivationTest] oak_log: after=" + logAfter);
        System.out.println("[PriceDerivationTest] full table after enforcement: " + prices);

        assertTrue(stairsAfter <= 15,
                "Oak Stairs should be a cheap, plank-derived item, not " + stairsAfter + " (real report: priced at 108g)");
        assertTrue(planksAfter <= 10,
                "Oak Planks should stay cheap -- it's the common root of many bulky, poor-yield recipes (Chest 8:1, "
                        + "etc.) that must not inflate it just because they individually price high, got " + planksAfter);
        // The log must still never price below what it refines into -- the literal 2026-10-11 user
        // requirement this whole enforcement pass exists for -- now checked against Planks' own
        // backward-only cost (what the fixed enforcement pass itself actually uses), not Planks'
        // enforced final price, which is a separate, independently-derived number.
        assertTrue(logAfter >= (int) Math.ceil(PriceDerivation.MARKUP * planksBackwardOnly * 4),
                "Oak Log must still price at least MARKUP * Planks-backward-only-value * 4 -- got log=" + logAfter
                        + " planksBackwardOnly=" + planksBackwardOnly);
    }

    @Test
    void goldIngotNuggetRefinementStillWorksAfterTheFix() {
        // Independent sanity check that the fix doesn't regress the feature's own original
        // motivating case (Ore -> Ingot -> Nugget/Block refinement chains).
        Identifier goldOre = id("raw_gold");
        Identifier goldIngot = id("gold_ingot");
        Identifier goldNugget = id("gold_nugget");
        Identifier goldBlock = id("gold_block");

        RecipeIndex index = new RecipeIndex();
        index.addEdge(new RecipeEdge(goldIngot, 1, List.of(List.of(goldOre)))); // smelting, 1:1
        index.addEdge(new RecipeEdge(goldNugget, 9, List.of(List.of(goldIngot)))); // 1 ingot -> 9 nuggets
        index.addEdge(new RecipeEdge(goldIngot, 1, nCopies(goldNugget, 9))); // 9 nuggets -> 1 ingot
        index.addEdge(new RecipeEdge(goldBlock, 1, nCopies(goldIngot, 9))); // 9 ingots -> 1 block
        index.addEdge(new RecipeEdge(goldIngot, 9, List.of(List.of(goldBlock)))); // 1 block -> 9 ingots

        PriceDerivation derivation = new PriceDerivation(index);
        Map<Identifier, Integer> prices = new HashMap<>();
        for (Identifier id : List.of(goldOre, goldIngot, goldNugget, goldBlock)) {
            prices.put(id, derivation.derive(id));
        }
        int ingotBefore = prices.get(goldIngot);

        Set<Identifier> ceilingHit = new HashSet<>();
        PriceCommands.enforceBreaksDownIntoFloor(prices, index, derivation, ceilingHit);

        System.out.println("[PriceDerivationTest] gold table after enforcement: " + prices);

        assertTrue(ceilingHit.isEmpty(), "Gold refinement chain should never hit the sanity ceiling: " + ceilingHit);
        assertTrue(prices.get(goldIngot) < 1000, "Gold Ingot should stay a sane value, got " + prices.get(goldIngot));
        assertEquals(ingotBefore, (int) prices.get(goldIngot),
                "Gold Ingot has no real 'breaks down into' consumer of its own other than itself via Block/Nugget "
                        + "round-trips (already cycle-guarded inside derive()) -- enforcement shouldn't move it further.");
    }
}
