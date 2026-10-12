package com.github.cerealklla.yconomics.bills;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import net.minecraft.core.GlobalPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;

/**
 * Open, trust-based listener registry for "a recurring bill just got processed" -- added 2026-10-12
 * so a mod that registers a bill (so far only Settlemynts, for plot rent) can react to a real
 * payment without this mod needing to know anything about what a bill is *for* (ledgers, UI
 * notifications, whatever). Deliberately uses only primitive/common types in the callback signature
 * (positions, an item-id-to-quantity map, the outcome's own {@code name()}) rather than this mod's
 * internal {@link RecurringBill}/{@link BillTransferEngine.BillOutcome} types, so a listener never
 * needs a hard compile dependency on this mod's internals -- same boundary discipline {@code
 * api.Yconomics} itself already follows.
 */
public final class BillEvents {

    private static final List<BillProcessedListener> LISTENERS = new CopyOnWriteArrayList<>();

    private BillEvents() {
    }

    public interface BillProcessedListener {
        void onBillProcessed(MinecraftServer server, UUID billId, Optional<UUID> ownerContext,
                              List<GlobalPos> sourceBoxPositions, List<GlobalPos> destinationBoxPositions,
                              Map<Identifier, Integer> cost, String outcomeName);
    }

    public static void register(BillProcessedListener listener) {
        LISTENERS.add(listener);
    }

    /** Called once per bill processed (success or failure) -- see {@code BillProcessor#processAndMark}. */
    static void fire(MinecraftServer server, RecurringBill bill, BillTransferEngine.BillOutcome outcome) {
        if (LISTENERS.isEmpty()) {
            return;
        }
        List<GlobalPos> sourcePositions = bill.sourceBoxes().stream().map(RecurringBill.BoxRef::pos).toList();
        List<GlobalPos> destinationPositions = bill.destinationBoxes().stream().map(RecurringBill.BoxRef::pos).toList();
        for (BillProcessedListener listener : LISTENERS) {
            listener.onBillProcessed(server, bill.billId(), bill.ownerContext(), sourcePositions, destinationPositions, bill.cost(), outcome.name());
        }
    }
}
