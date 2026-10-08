package com.github.cerealklla.yconomics.bills;

import java.util.ArrayDeque;
import java.util.Deque;

import com.github.cerealklla.yconomics.storage.BillSavedData;

import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Processes recurring bills once a day boundary is crossed (design doc Section 5a), spread across
 * several real ticks rather than all in one -- a small fixed batch per tick, so registering many
 * bills never causes a single-frame chunk-load/transfer spike (the user's own stated concern,
 * 2026-10-05, see decisions.md: "iterate slowly only waking one portion up... and then slept
 * again"). Resolving one box's container is already a single synchronous call (see {@code
 * BillProcessor}); staggering happens here, at the batch level, not inside that call.
 */
public final class BillProcessingTicker {

    private static final int BILLS_PER_TICK = 5;

    private long lastKnownDay = -1;
    private final Deque<java.util.UUID> pendingBillsToday = new ArrayDeque<>();

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        BillSavedData data = server.getDataStorage().computeIfAbsent(BillSavedData.TYPE);

        long currentDay = DayChangeTracker.dayNumber(server.overworld().getOverworldClockTime());
        if (lastKnownDay < 0) {
            // First tick since this listener instance was created (server start) -- don't treat
            // this as a boundary crossing, just establish the baseline.
            lastKnownDay = currentDay;
        } else if (currentDay > lastKnownDay) {
            for (var bill : data.getAllActiveBills()) {
                if (bill.lastProcessedDay() < currentDay) {
                    pendingBillsToday.add(bill.billId());
                }
            }
            lastKnownDay = currentDay;
        }

        int processedThisTick = 0;
        while (processedThisTick < BILLS_PER_TICK && !pendingBillsToday.isEmpty()) {
            var billId = pendingBillsToday.poll();
            data.getBill(billId).ifPresent(bill -> BillProcessor.processAndMark(server, data, bill, currentDay));
            processedThisTick++;
        }
    }
}
