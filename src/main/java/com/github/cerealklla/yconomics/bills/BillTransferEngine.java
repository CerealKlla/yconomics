package com.github.cerealklla.yconomics.bills;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

/**
 * Generalizes {@code bag.LootBagEntity#depositItem}'s single-container slot-fill/overflow pattern
 * across a *list* of containers (design decision, 2026-10-05 -- see decisions.md): pulling drains
 * partially from each source box in list order until the required quantity is met (search across
 * all, don't drain everything from one); depositing fills each destination box in list order,
 * overflow moving to the next box, same as normal inventory stacking.
 *
 * <p>The actual list-order greedy-fill math is factored out into {@link #greedyAllocate}, which
 * takes and returns plain {@code int}s -- real {@link Container}/{@link ItemStack} access (reading
 * existing slot counts, constructing new stacks to deposit) can't be unit tested in this project
 * (constructing a real {@code ItemStack} throws "Components not bound yet" outside a real client/
 * server boot -- confirmed empirically, same limitation this whole suite already works around
 * elsewhere). {@link #greedyAllocate} itself has no such dependency and is fully unit tested;
 * the real-container glue around it is verified by the live playtest script instead.
 */
public final class BillTransferEngine {

    private BillTransferEngine() {
    }

    public enum BillOutcome {
        PAID,
        INSUFFICIENT_FUNDS,
        MISSING_BOX
    }

    /**
     * Greedily takes from each bucket in {@code capacities} (list order) until {@code amount} is
     * reached or every bucket is exhausted. Never takes more than a bucket's own capacity. Returns
     * a same-size list of how much was taken from each bucket; the total taken may be less than
     * {@code amount} if every bucket together can't cover it -- callers that need an all-or-nothing
     * guarantee should check the total against {@code amount} (or against {@link #sum}) themselves,
     * same as {@link #canSatisfy} does before calling {@link #drain}.
     */
    static List<Integer> greedyAllocate(List<Integer> capacities, int amount) {
        List<Integer> taken = new ArrayList<>(capacities.size());
        int remaining = amount;
        for (int capacity : capacities) {
            int take = Math.max(0, Math.min(remaining, capacity));
            taken.add(take);
            remaining -= take;
        }
        return taken;
    }

    static int sum(List<Integer> values) {
        int total = 0;
        for (int v : values) {
            total += v;
        }
        return total;
    }

    /**
     * {@code true} only if {@code sources} together hold at least {@code required} of every item --
     * a non-mutating check, so a short bill never partially drains before failing.
     */
    public static boolean canSatisfy(List<Container> sources, Map<Identifier, Integer> required) {
        for (Map.Entry<Identifier, Integer> entry : required.entrySet()) {
            List<Integer> available = countPerContainer(sources, entry.getKey());
            if (sum(greedyAllocate(available, entry.getValue())) < entry.getValue()) {
                return false;
            }
        }
        return true;
    }

    private static List<Integer> countPerContainer(List<Container> containers, Identifier itemId) {
        List<Integer> counts = new ArrayList<>(containers.size());
        for (Container container : containers) {
            int total = 0;
            for (int slot = 0; slot < container.getContainerSize(); slot++) {
                ItemStack stack = container.getItem(slot);
                if (!stack.isEmpty() && BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(itemId)) {
                    total += stack.getCount();
                }
            }
            counts.add(total);
        }
        return counts;
    }

    /**
     * Removes exactly {@code required} of each item from {@code sources}, in list order (searching
     * every box for a given item before moving to the next item, never draining a box of anything
     * not actually required). Callers must call {@link #canSatisfy} first -- this always mutates,
     * assuming satisfiability has already been confirmed.
     */
    public static void drain(List<Container> sources, Map<Identifier, Integer> required) {
        for (Map.Entry<Identifier, Integer> entry : required.entrySet()) {
            List<Integer> available = countPerContainer(sources, entry.getKey());
            List<Integer> toTake = greedyAllocate(available, entry.getValue());
            for (int i = 0; i < sources.size(); i++) {
                shrinkFrom(sources.get(i), entry.getKey(), toTake.get(i));
            }
        }
    }

    private static void shrinkFrom(Container container, Identifier itemId, int amount) {
        int remaining = amount;
        for (int slot = 0; slot < container.getContainerSize() && remaining > 0; slot++) {
            ItemStack stack = container.getItem(slot);
            if (stack.isEmpty() || !BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(itemId)) {
                continue;
            }
            int take = Math.min(remaining, stack.getCount());
            stack.shrink(take);
            remaining -= take;
        }
    }

    /**
     * Fills {@code destinations} in list order with {@code toDeposit}, overflow moving to the next
     * box -- same merge-then-new-stack idiom as {@code bag.LootBagEntity#tryMergeIntoSlot}. Returns
     * whatever didn't fit anywhere (every destination full).
     */
    public static Map<Identifier, Integer> deposit(List<Container> destinations, Map<Identifier, Integer> toDeposit) {
        Map<Identifier, Integer> remainder = new HashMap<>();
        for (Map.Entry<Identifier, Integer> entry : toDeposit.entrySet()) {
            ItemStack toPlace = new ItemStack(BuiltInRegistries.ITEM.getValue(entry.getKey()), entry.getValue());
            for (Container container : destinations) {
                if (toPlace.isEmpty()) {
                    break;
                }
                toPlace = depositIntoOne(container, toPlace);
            }
            if (!toPlace.isEmpty()) {
                remainder.put(entry.getKey(), toPlace.getCount());
            }
        }
        return remainder;
    }

    private static ItemStack depositIntoOne(Container container, ItemStack incoming) {
        ItemStack remainder = incoming;
        for (int slot = 0; slot < container.getContainerSize() && !remainder.isEmpty(); slot++) {
            remainder = tryMergeIntoSlot(container, slot, remainder);
        }
        return remainder;
    }

    private static ItemStack tryMergeIntoSlot(Container container, int slot, ItemStack incoming) {
        ItemStack existing = container.getItem(slot);
        if (existing.isEmpty()) {
            int placed = Math.min(incoming.getCount(), incoming.getMaxStackSize());
            container.setItem(slot, incoming.split(placed));
            return incoming;
        }
        if (ItemStack.isSameItemSameComponents(existing, incoming)) {
            int room = existing.getMaxStackSize() - existing.getCount();
            if (room > 0) {
                int moved = Math.min(room, incoming.getCount());
                existing.grow(moved);
                incoming.shrink(moved);
            }
        }
        return incoming;
    }

    /**
     * Full orchestration: fails cleanly ({@code INSUFFICIENT_FUNDS}) with no mutation if the
     * sources can't together cover {@code cost}; otherwise drains then deposits. A deposit
     * remainder (every destination full) is intentionally not itself a failure -- the bill was
     * still paid, the overflow is the destination owner's own storage problem, not the payer's.
     */
    public static BillOutcome processBill(List<Container> sources, List<Container> destinations, Map<Identifier, Integer> cost) {
        if (!canSatisfy(sources, cost)) {
            return BillOutcome.INSUFFICIENT_FUNDS;
        }
        drain(sources, cost);
        deposit(destinations, cost);
        return BillOutcome.PAID;
    }
}
