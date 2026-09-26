package com.github.cerealklla.yconomics.currency;

import java.util.ArrayList;
import java.util.List;

import com.mojang.serialization.Codec;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The Coin Purse's actual storage (design doc Section 2/currency, see decisions.md 2026-09-25) --
 * a list of real {@link Items#GOLD_NUGGET} stacks (each capped at the vanilla max stack size, 64),
 * not a single flat count. Modeled on vanilla's own {@code BundleContents}/{@code
 * BundleContents.Mutable} split (immutable value + explicit "produce a new value" mutations,
 * written back via {@code ItemStack#set}), but simplified: no weight fraction, no arbitrary item
 * types, just "how many nuggets fit" against a capacity supplied by the caller (the player's
 * current tier -- see {@code api.Yconomics#getCoinPurseTier} -- decides that number, not this
 * class, since capacity is a per-player fact, not a per-item one).
 */
public record CoinPurseContents(List<ItemStack> stacks) {

    public static final CoinPurseContents EMPTY = new CoinPurseContents(List.of());

    public static final Codec<CoinPurseContents> CODEC = ItemStack.CODEC.listOf()
            .xmap(CoinPurseContents::new, CoinPurseContents::stacks);
    public static final StreamCodec<RegistryFriendlyByteBuf, CoinPurseContents> STREAM_CODEC = ItemStack.STREAM_CODEC
            .apply(ByteBufCodecs.list())
            .map(CoinPurseContents::new, CoinPurseContents::stacks);

    public CoinPurseContents {
        stacks = List.copyOf(stacks);
    }

    /** Builds fresh 64-nugget-or-fewer stacks totaling exactly {@code count} -- used to restore a death-retained balance on respawn. */
    public static CoinPurseContents of(int count) {
        List<ItemStack> stacks = new ArrayList<>();
        int remaining = count;
        while (remaining > 0) {
            int amount = Math.min(64, remaining);
            stacks.add(new ItemStack(Items.GOLD_NUGGET, amount));
            remaining -= amount;
        }
        return new CoinPurseContents(stacks);
    }

    public int totalCount() {
        int total = 0;
        for (ItemStack stack : stacks) {
            total += stack.getCount();
        }
        return total;
    }

    public boolean isEmpty() {
        return stacks.isEmpty();
    }

    /**
     * Inserts as much of {@code incoming} (assumed already validated as {@link Items#GOLD_NUGGET})
     * as fits under {@code capacity}, merging into existing under-64 stacks before starting new
     * ones. Mutates {@code incoming} in place (shrinks it by however much was actually taken),
     * matching {@code ItemStack#split}-style conventions used elsewhere in this project.
     */
    public InsertResult insert(ItemStack incoming, int capacity) {
        List<ItemStack> next = new ArrayList<>(stacks.size() + 1);
        for (ItemStack stack : stacks) {
            next.add(stack.copy());
        }

        int available = Math.max(0, capacity - totalCount());
        int toInsert = Math.min(incoming.getCount(), available);
        int remaining = toInsert;
        for (ItemStack stack : next) {
            if (remaining <= 0) {
                break;
            }
            int room = stack.getMaxStackSize() - stack.getCount();
            if (room > 0) {
                int add = Math.min(room, remaining);
                stack.grow(add);
                remaining -= add;
            }
        }
        while (remaining > 0) {
            int add = Math.min(64, remaining);
            next.add(new ItemStack(Items.GOLD_NUGGET, add));
            remaining -= add;
        }

        incoming.shrink(toInsert);
        return new InsertResult(new CoinPurseContents(next), toInsert);
    }

    /** Removes the most-recently-added stack whole (LIFO, matching vanilla's own Bundle#removeOne behavior). */
    public RemoveResult removeLast() {
        if (stacks.isEmpty()) {
            return new RemoveResult(this, ItemStack.EMPTY);
        }
        List<ItemStack> next = new ArrayList<>(stacks);
        ItemStack removed = next.remove(next.size() - 1);
        return new RemoveResult(new CoinPurseContents(next), removed);
    }

    public record InsertResult(CoinPurseContents contents, int inserted) {
    }

    public record RemoveResult(CoinPurseContents contents, ItemStack removed) {
    }
}
