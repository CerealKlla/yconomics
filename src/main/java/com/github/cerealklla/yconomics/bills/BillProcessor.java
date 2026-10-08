package com.github.cerealklla.yconomics.bills;

import java.util.ArrayList;
import java.util.List;

import com.github.cerealklla.yconomics.YconomicsMod;
import com.github.cerealklla.yconomics.bills.BillTransferEngine.BillOutcome;
import com.github.cerealklla.yconomics.bills.RecurringBill.BoxRef;
import com.github.cerealklla.yconomics.storage.BillSavedData;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;

/**
 * Resolves a bill's box references to live containers via plain vanilla {@code
 * ServerLevel#getBlockEntity(pos)} -- no Cartographyr dependency (revised 2026-10-05, see
 * decisions.md and {@code RecurringBill}'s own class doc). A box reference whose position no
 * longer holds a {@link Container} (block changed/removed underneath it) is treated as {@code
 * MISSING_BOX}, not retried or auto-corrected -- refreshing a stale position is the registering
 * mod's job (e.g. Settlemynts updating the bill when its own box membership changes), not this
 * processor's.
 */
public final class BillProcessor {

    private BillProcessor() {
    }

    public static BillOutcome process(MinecraftServer server, RecurringBill bill) {
        List<Container> sources = resolveAll(server, bill.sourceBoxes());
        if (sources == null) {
            return BillOutcome.MISSING_BOX;
        }
        List<Container> destinations = resolveAll(server, bill.destinationBoxes());
        if (destinations == null) {
            return BillOutcome.MISSING_BOX;
        }
        return BillTransferEngine.processBill(sources, destinations, bill.cost());
    }

    /** {@code null} if any single box reference fails to resolve -- a bill never half-processes against a missing box. */
    private static List<Container> resolveAll(MinecraftServer server, List<BoxRef> refs) {
        List<Container> containers = new ArrayList<>(refs.size());
        for (BoxRef ref : refs) {
            ServerLevel level = server.getLevel(ref.pos().dimension());
            if (level == null) {
                return null;
            }
            if (!(level.getBlockEntity(ref.pos().pos()) instanceof Container container)) {
                return null;
            }
            containers.add(container);
        }
        return containers;
    }

    /** Called once per bill processed (success or failure) -- see {@code BillProcessingTicker}. */
    public static void processAndMark(MinecraftServer server, BillSavedData data, RecurringBill bill, long day) {
        BillOutcome outcome = process(server, bill);
        data.markProcessed(bill.billId(), day, outcome);
        if (outcome != BillOutcome.PAID) {
            YconomicsMod.LOGGER.info("Recurring bill {} did not pay (outcome: {})", bill.billId(), outcome);
        }
    }
}
