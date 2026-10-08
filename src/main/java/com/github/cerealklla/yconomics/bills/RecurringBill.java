package com.github.cerealklla.yconomics.bills;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.GlobalPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;

/**
 * A generic, periodic item transfer (design doc Section 5a, 2026-10-05, see decisions.md) --
 * originally motivated by Settlemynts plot rent, but item/currency-agnostic by construction: a
 * bill's {@code cost} is just an item-id-to-quantity map, and real gold nuggets sitting in a
 * tracked box are simply one more entry in it (no separate ledger/account concept, per the
 * confirmed "nuggets-in-a-box" decision).
 *
 * <p><b>No dependency on Cartographyr anywhere in this type</b> (revised 2026-10-05, see
 * decisions.md) -- {@link BoxRef} carries both a box's identity and its last-known position
 * directly, so {@code bills.BillProcessor} resolves a live {@link net.minecraft.world.Container}
 * via plain vanilla {@code ServerLevel#getBlockEntity(pos)}, never asking Cartographyr "where is
 * this UUID." Whoever registers/maintains a bill (e.g. Settlemynts, which already depends on
 * Cartographyr to discover boxes) is responsible for keeping each {@link BoxRef}'s position fresh
 * when box membership changes -- the same "update the bill when a box is created/destroyed"
 * responsibility already assigned to that caller, just now also covering position refresh, not a
 * new burden. A box that moves without the registering mod updating the bill simply goes stale
 * (a {@code MISSING_BOX} outcome at processing time) -- consistent with the already-accepted "a
 * box's UUID doesn't migrate; that's the player's/registering mod's own responsibility" stance.
 *
 * @param ownerContext an opaque back-reference (e.g. a Settlemynts plot id) this mod never
 *                      interprets -- keeps Yconomics fully decoupled from whatever registered the bill.
 */
public record RecurringBill(
        UUID billId,
        List<BoxRef> sourceBoxes,
        List<BoxRef> destinationBoxes,
        Map<Identifier, Integer> cost,
        long periodDays,
        long lastProcessedDay,
        boolean active,
        Optional<UUID> ownerContext,
        Optional<BillTransferEngine.BillOutcome> lastOutcome
) {
    private static final Codec<BillTransferEngine.BillOutcome> OUTCOME_CODEC =
            Codec.STRING.xmap(BillTransferEngine.BillOutcome::valueOf, Enum::name);

    public static final Codec<RecurringBill> CODEC = RecordCodecBuilder.create(i -> i.group(
            UUIDUtil.CODEC.fieldOf("bill_id").forGetter(RecurringBill::billId),
            Codec.list(BoxRef.CODEC).fieldOf("source_boxes").forGetter(RecurringBill::sourceBoxes),
            Codec.list(BoxRef.CODEC).fieldOf("destination_boxes").forGetter(RecurringBill::destinationBoxes),
            Codec.unboundedMap(Identifier.CODEC, Codec.INT).fieldOf("cost").forGetter(RecurringBill::cost),
            Codec.LONG.fieldOf("period_days").forGetter(RecurringBill::periodDays),
            Codec.LONG.fieldOf("last_processed_day").forGetter(RecurringBill::lastProcessedDay),
            Codec.BOOL.fieldOf("active").forGetter(RecurringBill::active),
            UUIDUtil.CODEC.optionalFieldOf("owner_context").forGetter(RecurringBill::ownerContext),
            OUTCOME_CODEC.optionalFieldOf("last_outcome").forGetter(RecurringBill::lastOutcome)
    ).apply(i, RecurringBill::new));

    public RecurringBill withProcessingResult(long day, BillTransferEngine.BillOutcome outcome) {
        return new RecurringBill(billId, sourceBoxes, destinationBoxes, cost, periodDays, day, active, ownerContext, Optional.of(outcome));
    }

    public RecurringBill withActive(boolean newActive) {
        return new RecurringBill(billId, sourceBoxes, destinationBoxes, cost, periodDays, lastProcessedDay, newActive, ownerContext, lastOutcome);
    }

    /** Refreshes both box lists in place -- see this record's own class doc on why the registering mod owns keeping these fresh. */
    public RecurringBill withBoxes(List<BoxRef> newSourceBoxes, List<BoxRef> newDestinationBoxes) {
        return new RecurringBill(billId, List.copyOf(newSourceBoxes), List.copyOf(newDestinationBoxes), cost, periodDays, lastProcessedDay, active, ownerContext, lastOutcome);
    }

    /** A box's stable identity plus its last-known position -- see this record's own class doc for why both are needed. */
    public record BoxRef(UUID id, GlobalPos pos) {
        public static final Codec<BoxRef> CODEC = RecordCodecBuilder.create(i -> i.group(
                UUIDUtil.CODEC.fieldOf("id").forGetter(BoxRef::id),
                GlobalPos.CODEC.fieldOf("pos").forGetter(BoxRef::pos)
        ).apply(i, BoxRef::new));
    }
}
