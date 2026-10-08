package com.github.cerealklla.yconomics.shop;

import java.util.List;

import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

/**
 * Tag-aware sibling of {@code bills.BillTransferEngine} (design doc Section 5c, added 2026-10-05) --
 * that engine only ever matches an exact item {@code Identifier}, but a Shop's stock needs to match
 * a whole {@link ShopResource} category (e.g. "any log"), so counting/draining is reimplemented here
 * against {@link ShopResource#matches} instead. Single-resource-at-a-time by design (a purchase is
 * always for one listing), so there's no need for {@code BillTransferEngine}'s multi-item
 * per-container capacity bookkeeping -- a straightforward list-order drain is enough.
 *
 * <p>Payment (nugget) deposit is deliberately <b>not</b> handled here -- {@code
 * BillTransferEngine#deposit} already does exact-item deposit correctly (nuggets are never matched
 * by tag), so callers use that directly for the payment side of a purchase.
 */
public final class ShopTransferEngine {

    private ShopTransferEngine() {
    }

    /** How many units of {@code resource} are available across all of {@code containers}, in total. */
    public static int countAvailable(List<Container> containers, ShopResource resource) {
        int total = 0;
        for (Container container : containers) {
            for (int slot = 0; slot < container.getContainerSize(); slot++) {
                ItemStack stack = container.getItem(slot);
                if (resource.matches(stack)) {
                    total += stack.getCount();
                }
            }
        }
        return total;
    }

    /**
     * Removes up to {@code amount} units of {@code resource} from {@code containers} (list order,
     * searching every container for matching stacks before moving to the next requested resource --
     * irrelevant here since this only ever drains one resource at a time, unlike
     * {@code BillTransferEngine#drain}'s multi-item map). Never removes more than is actually
     * available. Returns the actual stacks removed (preserving each one's real item/components,
     * since a tag-based resource like "any log" can match several different concrete items) --
     * **not just a count** (fixed 2026-10-06, real report: a Settlement Shop purchase took the
     * buyer's gold and drained the shop's stock, but never actually gave the buyer anything, because
     * the old int-only return threw away which real item(s) were taken the moment this returned).
     * Callers needing just the total can sum {@code ItemStack#getCount()} over the result.
     */
    public static List<ItemStack> drain(List<Container> containers, ShopResource resource, int amount) {
        List<ItemStack> taken = new java.util.ArrayList<>();
        int remaining = amount;
        for (Container container : containers) {
            if (remaining <= 0) {
                break;
            }
            for (int slot = 0; slot < container.getContainerSize() && remaining > 0; slot++) {
                ItemStack stack = container.getItem(slot);
                if (!resource.matches(stack)) {
                    continue;
                }
                int take = Math.min(remaining, stack.getCount());
                taken.add(stack.copyWithCount(take));
                stack.shrink(take);
                remaining -= take;
            }
        }
        return taken;
    }
}
