package com.github.cerealklla.yconomics.price;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.github.cerealklla.yconomics.YconomicsMod;

import com.github.cerealklla.yconomics.shop.ShopPricing;

import net.minecraft.resources.Identifier;

/**
 * The recursive "what is this item actually worth" solver (explicit user spec, 2026-10-11: "if the
 * item either breaks down, or is crafted from, other items, then it cannot have the lowest cost. It
 * should instead be 10% more than the sum of its components"). For every item, checks both
 * directions and takes whichever is higher:
 *
 * <ul>
 *   <li><b>Crafted from</b> ({@link RecipeIndex#producing}) -- {@code 1.1 * sum(cost of each input
 *   slot's cheapest option) / outputCount} for the *cheapest* of every recipe that produces this
 *   item (any shape, including assembly) -- see {@link #cheapestProducingCost}'s own doc for why
 *   this takes the cheapest recipe across multiple producers, not the most expensive one. A Stone
 *   Pickaxe's floor comes from its own ingredient sum this way (2 Sticks + 3 Cobblestone).</li>
 *   <li><b>Breaks down into</b> ({@link RecipeIndex#refiningInto}) -- for every *single-ingredient-
 *   type* recipe that consumes this item (Log -> Planks, Gold Ingot -> Gold Nuggets -- a genuine
 *   refinement, not assembly), {@code 1.1 * cost(output) * outputCount / qty needed}. A Wood Log's
 *   floor comes from what it breaks down into (Planks) this way.</li>
 * </ul>
 *
 * <p><b>Why assembly recipes are excluded from the "breaks down into" direction</b> -- explicit user
 * correction, 2026-10-11, after a first version applied it to every consuming recipe regardless of
 * shape: "the value of a component is never influenced by its participation in a different recipe."
 * A component used alongside *other, different* ingredients to build something else (2 Sticks + 3
 * Cobblestone -> Stone Pickaxe) is assembly, not refinement -- the Pickaxe's own backward cost
 * already accounts for the Sticks/Cobblestone contribution; there's no reason their own prices should
 * *also* rise just because they happen to be used there. Only a recipe whose every input slot is the
 * exact same ingredient ({@link RecipeEdge#isSingleIngredientType}) represents a true "this converts
 * into that" relationship worth linking both items' values over.
 *
 * <p>A true leaf (no known recipe in either direction -- e.g. Sticks, Cobblestone, or any other
 * genuinely unrefinable/uncraftable item) falls back to {@link ShopPricing#MIN_SELL_PRICE}.
 *
 * <p><b>Real bugs found and fixed before this ever shipped</b> -- live-tested on the Dev Server per
 * explicit user instruction ("test it first, then we can deploy"), three times over. (1) The first
 * version applied the "breaks down into" direction to *every* consuming recipe (including assembly),
 * producing absurd results live (Stick/Planks/Log all converged to the identical 56,743,843 gold;
 * Cobblestone hit a raw {@code int} overflow) once a cheap item happened to be an ingredient
 * somewhere expensive -- fixed by the single-ingredient-type restriction above. (2) A second attempt
 * at fixing a minor ordering inconsistency (Oak Log computing cheaper than the Oak Planks it
 * produces, since whichever item in a refinement pair gets computed first can lock in a value from
 * while its counterpart was still cycle-guarded) fed each pass's results into the next pass's guard
 * fallback -- live-tested and found to be mathematically unstable, not just imprecise: a direct
 * two-item refinement pair's backward and forward directions are the same physical relationship
 * computed twice, each applying its own independent {@code MARKUP} to the other's already-marked-up
 * value, with no finite fixed point (confirmed live -- `/yconomics recalculateprices` correctly
 * reported "did NOT converge," values still climbing every pass: Log 9 -> 22 -> ... -> 119+). <b>The
 * real fix</b>: {@link #backwardOnly} -- the "breaks down into" direction values an item against its
 * output's *backward-only* cost ({@link RecipeIndex#producing} only, never {@link
 * RecipeIndex#refiningInto}), not the output's full {@link #derive} result. This structurally breaks
 * the two-item cycle (backward-only recursion is a genuine DAG for any sane recipe set -- nothing is
 * crafted from itself even indirectly), while an item's own *ingredient* costs within a backward
 * computation still use the real, full {@link #derive} price (so Stone Pickaxe's cost still reflects
 * Sticks' real market price, including Sticks' own forward value, if any -- only the direct two-item
 * refinement cycle is broken, not legitimate multi-item pricing). (3) {@link #MAX_DEPTH} and {@link
 * #MAX_REASONABLE_PRICE} remain as defensive backstops for whatever a *future* recipe addition might
 * still do that none of the above anticipated -- see {@link #MAX_REASONABLE_PRICE}'s own doc.
 * Multiple passes (see {@code PriceCommands}) are mostly now just a convergence sanity-check, not
 * load-bearing the way they were in the broken second attempt. Results are memoized per {@link
 * PriceDerivation} instance (i.e. per {@code /yconomics recalculateprices} run), not across runs.
 *
 * <p><b>Structural, standing safety net -- not just a one-time patch for this session's two bugs</b>
 * (explicit user request: "bake in a real fix so that future items/recipes don't silently break
 * behind the scenes"). {@link #MAX_DEPTH} and the single-ingredient-type restriction bound how a
 * *known-bad* shape (unrestricted forward chains, unbounded recursion) can recur, but neither one
 * guarantees a *future* recipe addition (a new mod, a Lyfe recipe regeneration, a datapack) can't
 * introduce some other pathological shape neither bug anticipated. {@link #MAX_REASONABLE_PRICE} is
 * the actual backstop: every computed value is hard-clamped to it, so nothing -- regardless of cause
 * -- can ever silently reach an absurd or overflow-prone number again, and {@link #itemsAtCeiling}/
 * {@link #depthGuardHitCount} surface *that something hit a guard at all* back to whoever runs
 * {@code /yconomics recalculateprices} (see that class's own doc for how the command reports and
 * logs this), so a future regression is loud and visible instead of a silently-wrong shop price
 * nobody notices until a player does.
 *
 * <p><b>Real bug found and fixed, 2026-11-05: "How on earth do you figure oak wood stairs are worth
 * 108g?"</b> (screenshot also showed Oak Door/Trapdoor-shaped goods at 144g/86g buy/sell). Root cause
 * confirmed by direct live instrumentation of the real recipe graph on a real suite server with Lyfe
 * loaded (several rounds of hand-tracing were tried first and each one turned out subtly wrong --
 * this recursive solver is genuinely too easy to mis-simulate by hand, confirmed the hard way):
 * {@link #cheapestProducingCost}'s forward ("crafted from") loop used to take {@code Math.max} across
 * every real recipe that produces the same item, reasoning the item's value should reflect whichever
 * recipe looks worst. Charcoal has two real producing recipes -- a cheap 1:1 smelting recipe from any
 * of 9 log species, and a Lyfe-added 64-Leaf-Litter-for-1-Charcoal disposal/recycling recipe for
 * excess leaf litter -- and taking the max meant Charcoal's own price was dominated by the
 * deliberately wasteful 64:1 recipe nobody would ever actually use to obtain it (Charcoal computed to
 * 141 gold), not the cheap smelting path everyone actually would. That inflated Charcoal price then
 * fed directly into Oak Log's own "breaks down into" direction (Log -> Charcoal is a real single-
 * ingredient refinement), pushing Oak Log to 234, which cascaded forward through Oak Planks (80) into
 * every plank-built good shown in the report -- Oak Stairs 108, Oak Door 144, Oak Trapdoor 215, Oak
 * Boat 358 -- explaining why several unrelated plank goods were all inflated together in the same
 * shop. A rational crafter always takes whichever real recipe is cheapest, so the cheapest one is the
 * item's genuine cost floor -- the same "always take the cheapest path" rule this solver already
 * applies *within* one recipe's own ingredient slots ({@link #cheapestOf}/{@link
 * #cheapestBackwardOnly}), now also applied *across* multiple recipes for the same output via the new
 * {@link #cheapestProducingCost} (shared by both {@link #derive} and {@link #backwardOnly}'s own
 * forward loops, replacing their separate near-identical {@code Math.max} loops). Confirmed live
 * after the fix: Oak Stairs 108 -> 9, Oak Planks 80 -> 5, Oak Log 234 -> 9 (matching this very class
 * doc's own long-standing "Log priced around 9" example exactly), Charcoal 141 -> 10, with every
 * legitimate late-game item (Netherite tools, Lyfe's own high-tier gear) still priced far higher, as
 * expected -- this narrows absurd/wrong prices without flattening genuine tiered pricing. A second,
 * independent, minor bug found and fixed the same investigation is documented in {@code
 * PriceCommands#enforceBreaksDownIntoFloor}'s own doc.
 */
public final class PriceDerivation {

    /** Package-visible so {@code PriceCommands}' own post-processing enforcement pass can reuse the exact same markup. */
    static final double MARKUP = 1.1;

    /** See class doc's "real bug found and fixed" note -- bounds how many hops of real recipe chain compound together before falling back to the flat floor. */
    private static final int MAX_DEPTH = 8;

    /**
     * Hard ceiling, see class doc's "structural, standing safety net" note -- generous by roughly
     * three orders of magnitude over every real item observed in live testing (the priciest, a
     * Diamond Sword, landed at 332 gold), while still fully preventing a future pathological recipe
     * shape from ever producing an overflow-prone or absurd value again.
     */
    public static final int MAX_REASONABLE_PRICE = 100_000;

    private final RecipeIndex index;
    private final Map<Identifier, Integer> memo = new HashMap<>();
    private final Map<Identifier, Integer> backwardMemo = new HashMap<>();
    private final Set<Identifier> ceilingHit = new HashSet<>();
    private int depthGuardHits = 0;

    /**
     * Deliberately stateless across runs -- see class doc's bug (2): an earlier version fed one
     * pass's results into the next pass's cycle-guard fallback to try to converge a mutual
     * refinement pair toward a "real" fixed point, and live-testing proved that mathematically
     * unstable (no finite fixed point exists when both directions of the same relationship each
     * apply their own independent markup to the other's already-marked-up value -- confirmed live,
     * values climbing every pass with no convergence). {@link #backwardOnly} is the real fix, and it
     * needs no outside state to work: every guard fallback here is always the flat {@link
     * ShopPricing#MIN_SELL_PRICE}, so a fresh {@link PriceDerivation} given the same {@link
     * RecipeIndex} always produces the exact same result, deterministically -- which is also exactly
     * what {@code PriceCommands}' two-pass determinism check relies on.
     */
    public PriceDerivation(RecipeIndex index) {
        this.index = index;
    }

    public int derive(Identifier itemId) {
        return derive(itemId, Set.of(), 0);
    }

    /**
     * Public entry point for {@link #backwardOnly} -- added 2026-11-05 for {@code
     * PriceCommands#enforceBreaksDownIntoFloor}'s own real bug fix (see that method's doc): it used
     * to read an output's full {@link #derive} price to raise a raw material's floor, which reopens
     * the exact same-relationship-markups-itself-twice instability {@link #backwardOnly} exists to
     * avoid in the first place -- just one level later, and silently bounded instead of diverging, so
     * it was never caught by the determinism/ceiling checks. Shares this instance's own {@link
     * #backwardMemo}, so calling this after a full {@link #derive} pass over the same items is cheap
     * (almost every lookup is already memoized from {@code derive}'s own internal {@link
     * #backwardOnly} calls).
     */
    public int deriveBackwardOnly(Identifier itemId) {
        return backwardOnly(itemId, Set.of(), 0);
    }

    private int derive(Identifier itemId, Set<Identifier> visiting, int depth) {
        Integer cached = memo.get(itemId);
        if (cached != null) {
            return cached;
        }
        if (visiting.contains(itemId) || depth >= MAX_DEPTH) {
            // Cycle/depth guard -- deliberately not memoized, always the flat floor (see constructor
            // doc for why this is stateless/deterministic rather than pass-dependent).
            depthGuardHits++;
            return ShopPricing.MIN_SELL_PRICE;
        }
        Set<Identifier> nextVisiting = withAdded(visiting, itemId);
        int nextDepth = depth + 1;

        int best = -1;
        int cheapestProducing = cheapestProducingCost(index.producing(itemId), nextVisiting, nextDepth, false);
        if (cheapestProducing >= 0) {
            best = cheapestProducing;
        }
        for (RecipeEdge edge : index.refiningInto(itemId)) {
            long qtyNeeded = edge.inputs().stream().filter(slot -> slot.contains(itemId)).count();
            if (qtyNeeded == 0) {
                continue;
            }
            // backwardOnly, not the full derive() -- see class doc's bug (2): using the output's full
            // (potentially forward-inflated) price here is exactly what made a direct refinement pair
            // mathematically unstable. The output's own *backward* cost is all that's needed to value
            // "what this item breaks down into."
            int outputCost = backwardOnly(edge.output(), nextVisiting, nextDepth);
            double perUnit = (double) outputCost * edge.outputCount() / qtyNeeded;
            int candidate = (int) Math.ceil(MARKUP * perUnit);
            best = Math.max(best, candidate);
        }

        int result = Math.max(best > 0 ? best : ShopPricing.MIN_SELL_PRICE, ShopPricing.MIN_SELL_PRICE);
        if (result > MAX_REASONABLE_PRICE) {
            YconomicsMod.LOGGER.warn("[PriceDerivation] {} computed at {} gold, clamped to the {} ceiling -- "
                    + "likely a new recipe shape this engine wasn't designed for; worth investigating.",
                    itemId, result, MAX_REASONABLE_PRICE);
            ceilingHit.add(itemId);
            result = MAX_REASONABLE_PRICE;
        }
        memo.put(itemId, result);
        return result;
    }

    /**
     * The "crafted from" direction only, ignoring "breaks down into" entirely -- see class doc's
     * real fix. Explicit user correction, 2026-10-11, after live-testing still showed Oak Log (9)
     * priced below Oak Planks (11): "this needs to be fixed correctly; future items/recipes may
     * enflame this issue further," plus a worked proposal (find every item with no "crafted from"
     * relationship first, then propagate outward wave by wave) that's exactly a topological sort --
     * provably order-independent, unlike the previous version. The previous version's bug: it was
     * *conceptually* backward-only, but its own ingredient costs still called the full, forward-aware
     * {@link #derive} -- so Planks' backward cost could still transitively reach Log's *forward*
     * value (which itself depends on Planks), reopening the exact registry-order dependency this was
     * supposed to eliminate. Fixed by making every ingredient lookup here call {@link #backwardOnly}
     * too (via {@link #cheapestBackwardOnly}), never {@link #derive}, anywhere, even transitively --
     * this makes {@code backwardOnly} a genuinely self-contained, pure DAG (nothing is crafted from
     * itself even indirectly in any sane recipe set), computable in any order with an identical
     * result -- exactly the "leaves first, then whatever they craft into, then iterate outward"
     * process the user described. The {@code visiting}/depth guard remains purely as a defensive
     * backstop for a future recipe set that somehow isn't a sane DAG, not something this is expected
     * to actually rely on anymore.
     *
     * <p><b>One accepted, documented simplification</b>: an assembly item's (e.g. Stone Pickaxe)
     * backward cost is computed from its ingredients' backward-only prices, not their full (forward-
     * value-included) final prices -- so if Sticks ever had a meaningful forward bonus of its own,
     * Pickaxe's price wouldn't reflect it. This never produces an incorrect/out-of-order result (the
     * direction this fix is actually for), only an occasional slight underestimate for an assembly
     * item specifically -- a deliberate, bounded tradeoff for keeping this phase genuinely
     * order-independent, not an oversight.
     */
    private int backwardOnly(Identifier itemId, Set<Identifier> visiting, int depth) {
        Integer cached = backwardMemo.get(itemId);
        if (cached != null) {
            return cached;
        }
        if (visiting.contains(itemId) || depth >= MAX_DEPTH) {
            return ShopPricing.MIN_SELL_PRICE;
        }
        Set<Identifier> nextVisiting = withAdded(visiting, itemId);
        int nextDepth = depth + 1;

        int best = cheapestProducingCost(index.producing(itemId), nextVisiting, nextDepth, true);

        int result = Math.max(best > 0 ? best : ShopPricing.MIN_SELL_PRICE, ShopPricing.MIN_SELL_PRICE);
        result = Math.min(result, MAX_REASONABLE_PRICE); // Silent clamp -- the public derive() path already logs/tracks ceiling hits once per item.
        backwardMemo.put(itemId, result);
        return result;
    }

    /** Every item whose computed value hit {@link #MAX_REASONABLE_PRICE} this pass -- a real signal something's off, see class doc. */
    public Set<Identifier> itemsAtCeiling() {
        return ceilingHit;
    }

    /** How many times the cycle/depth guard fired this pass -- informational, not itself alarming (real refinement pairs legitimately trigger it), but a sharp jump between passes is worth a second look. */
    public int depthGuardHitCount() {
        return depthGuardHits;
    }

    /**
     * The real cost of the *cheapest* way to craft {@code itemId}, across every recipe that produces
     * it -- {@code -1} if there's no real recipe at all. Added 2026-11-05, real bug fix: this used to
     * take the <i>highest</i>-costing recipe across every producer ({@code Math.max}), on the
     * reasoning that the item's value should reflect whichever recipe happens to look worst -- but
     * real report "How on earth do you figure oak wood stairs are worth 108g?" traced to exactly
     * this: Charcoal has two real producing recipes (a 1:1 smelting recipe from any of 9 log species,
     * and a Lyfe-added 64-Leaf-Litter-for-1-Charcoal disposal/recycling recipe for excess leaf
     * litter) -- taking the max meant Charcoal's own computed price was dominated by the deliberately
     * wasteful 64:1 disposal recipe nobody would actually use to obtain it, not the cheap smelting
     * recipe everyone actually would, and that one inflated price then cascaded through Oak Log (one
     * of Charcoal's own "breaks down into" sources) into Oak Planks and every plank-based good built
     * from it. A rational crafter always takes the cheapest available recipe, so the cheapest one is
     * the item's real cost floor -- matching every other "always take the cheapest path" precedent
     * already used *within* a single recipe's own ingredient slots ({@link #cheapestOf}/{@link
     * #cheapestBackwardOnly}), just now also applied *across* multiple recipes for the same output.
     */
    private int cheapestProducingCost(List<RecipeEdge> edges, Set<Identifier> visiting, int depth, boolean backward) {
        int best = -1;
        for (RecipeEdge edge : edges) {
            long inputCost = 0;
            for (List<Identifier> slot : edge.inputs()) {
                inputCost += backward ? cheapestBackwardOnly(slot, visiting, depth) : cheapestOf(slot, visiting, depth);
            }
            int candidate = (int) Math.ceil(MARKUP * inputCost / edge.outputCount());
            best = best < 0 ? candidate : Math.min(best, candidate);
        }
        return best;
    }

    private int cheapestOf(List<Identifier> slot, Set<Identifier> visiting, int depth) {
        int best = Integer.MAX_VALUE;
        for (Identifier option : slot) {
            best = Math.min(best, derive(option, visiting, depth));
        }
        return best;
    }

    /** {@link #cheapestOf}'s sibling for {@link #backwardOnly} -- calls {@link #backwardOnly}, never {@link #derive}, keeping the whole backward-only phase self-contained (see that method's own doc). */
    private int cheapestBackwardOnly(List<Identifier> slot, Set<Identifier> visiting, int depth) {
        int best = Integer.MAX_VALUE;
        for (Identifier option : slot) {
            best = Math.min(best, backwardOnly(option, visiting, depth));
        }
        return best;
    }

    private static Set<Identifier> withAdded(Set<Identifier> set, Identifier id) {
        Set<Identifier> copy = new HashSet<>(set);
        copy.add(id);
        return copy;
    }
}
