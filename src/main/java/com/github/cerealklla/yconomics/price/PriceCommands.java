package com.github.cerealklla.yconomics.price;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;

import com.github.cerealklla.yconomics.YconomicsMod;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;

/**
 * {@code /yconomics recalculateprices} -- runs the full recipe-derived pricing pass ({@link
 * RecipeIndex}/{@link PriceDerivation}) over every registered item and replaces {@link
 * DerivedPriceTable}'s contents. Explicit user spec: "this calculation only needs to happen one time
 * when the world is generated after the recipes are generated... let's add an in-game [command] to
 * recalculate it to backfill" -- meant to be run once after setup (or again any time recipes change,
 * e.g. a new Lyfe recipe set is generated), not on any regular schedule. Op-gated (permission level
 * 2) -- unlike this mod's existing {@code debug.DebugCommands} (deliberately ungated for quick
 * local testing), this walks every registered item and writes real persisted state other systems
 * then rely on, closer to a real admin action than a debug toggle.
 *
 * <p><b>Runs the derivation twice and checks the two results match exactly</b> (added 2026-10-11,
 * see {@link PriceDerivation}'s own doc for the full live-testing story behind this) -- a first
 * attempt at fixing a real ordering inconsistency tried feeding each pass's results into the next
 * pass's cycle-guard fallback, which live-testing proved mathematically unstable (never converged,
 * values climbed indefinitely). The real fix landed in {@link PriceDerivation} itself
 * ({@code backwardOnly}, structurally breaking the cycle); once an individual derivation run is
 * genuinely stateless and deterministic, two independent runs against the same {@link RecipeIndex}
 * *must* produce byte-identical results -- so running it twice is now a cheap, standing determinism
 * check rather than a real iterative solver. If a future change ever reintroduces order-dependence
 * (e.g. a new recipe shape nobody anticipated), the two runs disagreeing is itself the loud signal
 * that something regressed -- see this method's own logging.
 *
 * <p><b>Final enforcement pass, added 2026-10-11</b> -- live-testing still showed Oak Log (9) priced
 * below Oak Planks (11) even after the determinism fix above, for a genuinely different reason:
 * {@link PriceDerivation}'s "breaks down into" direction deliberately values Log against Planks'
 * bare backward-only cost (not Planks' real final price), specifically to avoid reopening the cycle
 * the determinism fix just closed -- so Log never inherited Planks' *own* independent forward value
 * (Planks -> Crafting Table is a separate real refinement). {@link #enforceBreaksDownIntoFloor} is
 * the real fix for the user's literal requirement ("a log must never be priced below what it
 * produces") -- raises an input's price to {@code MARKUP * the output's real final price * ratio}
 * if it's currently lower, using every item's already-settled {@link PriceDerivation} result.
 *
 * <p><b>Deliberately exactly one pass, not iterated to convergence</b> -- a first attempt ran this
 * repeatedly (feeding each round's updated prices into the next) and live-testing proved that's the
 * same unbounded-growth problem all over again (Gold Ingot climbing to 387, the pass hitting its
 * round cap without ever settling): confirmed algebraically, same as {@link PriceDerivation}'s own
 * doc explains for the original cycle -- there is genuinely no finite fixed point for a relationship
 * where both directions keep referencing each other's *already-updated* value. A single pass avoids
 * this entirely by only ever reading the one frozen, pre-enforcement snapshot -- correctly fixing the
 * *direct* relationship actually reported (Log vs. Planks), which is what the user's own spec
 * describes, without chasing a multi-hop transitive reconciliation that has no general solution.
 */
public final class PriceCommands {

    private PriceCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("yconomics")
                .then(Commands.literal("recalculateprices")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .executes(ctx -> recalculate(ctx.getSource())))
                .then(Commands.literal("derivedprice")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.argument("itemId", StringArgumentType.greedyString())
                                .executes(ctx -> lookup(ctx.getSource(), StringArgumentType.getString(ctx, "itemId"))))));
    }

    /** DEBUG ONLY -- spot-checks one item's currently-stored derived price without rerunning the whole pass. Removed once this feature is confirmed live. */
    private static int lookup(CommandSourceStack source, String itemIdRaw) {
        Identifier itemId = Identifier.tryParse(itemIdRaw);
        if (itemId == null) {
            source.sendFailure(Component.literal("Not a valid item id: " + itemIdRaw));
            return 0;
        }
        Optional<Integer> price = com.github.cerealklla.yconomics.api.Yconomics.getDerivedPrice(source.getServer(), itemId);
        if (price.isEmpty()) {
            source.sendFailure(Component.literal("No derived price for " + itemId + " (run recalculateprices first, or it's a true leaf with no recipe either direction)."));
            return 0;
        }
        source.sendSuccess(() -> Component.literal(itemId + " -> " + price.get() + " gold"), false);
        return price.get();
    }

    private static int recalculate(CommandSourceStack source) {
        MinecraftServer server = source.getServer();
        long start = System.nanoTime();

        RecipeIndex index = RecipeIndex.buildFor(server);
        Map<Identifier, Integer> prices = deriveAll(new PriceDerivation(index));
        PriceDerivation checkRun = new PriceDerivation(index);
        Map<Identifier, Integer> check = deriveAll(checkRun);
        boolean deterministic = prices.equals(check);

        Set<Identifier> ceilingHit = new java.util.HashSet<>(checkRun.itemsAtCeiling());
        int depthGuardHits = checkRun.depthGuardHitCount();

        Set<Identifier> enforcedUpward = enforceBreaksDownIntoFloor(prices, index, ceilingHit);

        // Structural safety net, not just this session's own fix -- explicit user request: "bake in
        // a real fix so that future items/recipes don't silently break behind the scenes." Logged
        // regardless of whether anyone's watching chat, so a future regression (a new mod's recipe, a
        // Lyfe recipe regeneration) leaves a permanent, findable record instead of a silently-wrong
        // shop price nobody notices until a player does.
        if (!deterministic) {
            YconomicsMod.LOGGER.warn("[PriceDerivation] Two independent runs produced different results -- "
                    + "this should be impossible and means something regressed (likely order-dependent state "
                    + "crept back in). Worth investigating before trusting these prices.");
        }
        if (!ceilingHit.isEmpty()) {
            YconomicsMod.LOGGER.warn("[PriceDerivation] {} item(s) hit the sanity ceiling this run: {}", ceilingHit.size(), ceilingHit);
        }

        DerivedPriceTable table = server.getDataStorage().computeIfAbsent(DerivedPriceTable.TYPE);
        table.replaceAll(prices, server.overworld().getGameTime());

        double ms = (System.nanoTime() - start) / 1_000_000.0;
        int itemCount = prices.size();
        int edgeCount = index.edgeCount();
        String summary = String.format(
                "Recalculated derived prices for %d item(s) from %d recipe(s) in %.1fms (%s, %d item(s) raised by the breaks-down-into enforcement pass).%s",
                itemCount, edgeCount, ms, deterministic ? "deterministic" : "NOT deterministic -- check logs", enforcedUpward.size(),
                ceilingHit.isEmpty() ? "" : " WARNING: " + ceilingHit.size() + " item(s) hit the sanity ceiling -- check logs.");
        source.sendSuccess(() -> Component.literal(summary), true);
        if (depthGuardHits > 0) {
            String guardNote = "(Cycle/depth guard fired " + depthGuardHits + " time(s) -- normal for real refinement chains, informational only.)";
            source.sendSuccess(() -> Component.literal(guardNote), false);
        }
        return itemCount;
    }

    private static Map<Identifier, Integer> deriveAll(PriceDerivation derivation) {
        Map<Identifier, Integer> prices = new HashMap<>();
        for (var item : BuiltInRegistries.ITEM) {
            Identifier id = BuiltInRegistries.ITEM.getKey(item);
            prices.put(id, derivation.derive(id));
        }
        return prices;
    }

    /**
     * Post-processing fix for a real gap {@link PriceDerivation}'s own recursive computation can't
     * close without reopening the cycle it was built to avoid -- see this class's own doc. Mutates
     * {@code prices} in place; returns the set of items actually raised. Reads every output price
     * from a frozen snapshot of the pre-enforcement results (never the map being written to), so this
     * is a single, non-recursive, non-iterated pass by construction -- see this class's own doc for
     * why iterating it further reopens the exact divergence it exists to avoid.
     */
    private static Set<Identifier> enforceBreaksDownIntoFloor(Map<Identifier, Integer> prices, RecipeIndex index, Set<Identifier> ceilingHit) {
        Map<Identifier, Integer> snapshot = Map.copyOf(prices);
        Set<Identifier> raised = new java.util.HashSet<>();
        for (Identifier itemId : snapshot.keySet()) {
            int current = snapshot.get(itemId);
            int best = current;
            for (RecipeEdge edge : index.refiningInto(itemId)) {
                long qtyNeeded = edge.inputs().stream().filter(slot -> slot.contains(itemId)).count();
                if (qtyNeeded == 0) {
                    continue;
                }
                Integer outputPrice = snapshot.get(edge.output());
                if (outputPrice == null) {
                    continue;
                }
                double perUnit = (double) outputPrice * edge.outputCount() / qtyNeeded;
                int candidate = (int) Math.ceil(PriceDerivation.MARKUP * perUnit);
                best = Math.max(best, candidate);
            }
            if (best > PriceDerivation.MAX_REASONABLE_PRICE) {
                ceilingHit.add(itemId);
                best = PriceDerivation.MAX_REASONABLE_PRICE;
            }
            if (best != current) {
                prices.put(itemId, best);
                raised.add(itemId);
            }
        }
        return raised;
    }
}
