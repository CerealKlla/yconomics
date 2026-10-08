package com.github.cerealklla.yconomics.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.mojang.serialization.DataResult;

import com.github.cerealklla.yconomics.bills.BillTransferEngine;
import com.github.cerealklla.yconomics.bills.RecurringBill;
import com.github.cerealklla.yconomics.bills.RecurringBill.BoxRef;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;

class BillSavedDataTest {

    private static final Identifier GOLD_NUGGET = Identifier.withDefaultNamespace("gold_nugget");

    @Test
    void addGetAndRemoveBill() {
        BillSavedData data = new BillSavedData();
        BoxRef source = new BoxRef(UUID.randomUUID(), GlobalPos.of(Level.OVERWORLD, new BlockPos(0, 64, 0)));
        BoxRef dest = new BoxRef(UUID.randomUUID(), GlobalPos.of(Level.OVERWORLD, new BlockPos(5, 64, 0)));

        RecurringBill created = data.addBill(List.of(source), List.of(dest), java.util.Map.of(GOLD_NUGGET, 10), 1L, Optional.empty());

        assertTrue(data.getBill(created.billId()).isPresent());
        assertEquals(0L, created.lastProcessedDay());
        assertTrue(created.active());

        assertTrue(data.removeBill(created.billId()));
        assertTrue(data.getBill(created.billId()).isEmpty());
        assertFalse(data.removeBill(created.billId()));
    }

    @Test
    void markProcessedUpdatesLastProcessedDay() {
        BillSavedData data = new BillSavedData();
        BoxRef ref = new BoxRef(UUID.randomUUID(), GlobalPos.of(Level.OVERWORLD, new BlockPos(0, 64, 0)));
        RecurringBill created = data.addBill(List.of(ref), List.of(ref), java.util.Map.of(GOLD_NUGGET, 1), 1L, Optional.empty());

        Optional<RecurringBill> updated = data.markProcessed(created.billId(), 7L, BillTransferEngine.BillOutcome.PAID);
        assertTrue(updated.isPresent());
        assertEquals(7L, updated.get().lastProcessedDay());
        assertEquals(7L, data.getBill(created.billId()).get().lastProcessedDay());
        assertEquals(Optional.of(BillTransferEngine.BillOutcome.PAID), updated.get().lastOutcome());
    }

    @Test
    void setActiveTogglesAndGetAllActiveBillsFiltersCorrectly() {
        BillSavedData data = new BillSavedData();
        BoxRef ref = new BoxRef(UUID.randomUUID(), GlobalPos.of(Level.OVERWORLD, new BlockPos(0, 64, 0)));
        RecurringBill bill = data.addBill(List.of(ref), List.of(ref), java.util.Map.of(GOLD_NUGGET, 1), 1L, Optional.empty());

        assertEquals(Set.of(bill), data.getAllActiveBills());

        data.setActive(bill.billId(), false);
        assertEquals(Set.of(), data.getAllActiveBills());
    }

    @Test
    void getBillsForFiltersByOwnerContext() {
        BillSavedData data = new BillSavedData();
        BoxRef ref = new BoxRef(UUID.randomUUID(), GlobalPos.of(Level.OVERWORLD, new BlockPos(0, 64, 0)));
        UUID plotId = UUID.randomUUID();
        RecurringBill owned = data.addBill(List.of(ref), List.of(ref), java.util.Map.of(GOLD_NUGGET, 1), 1L, Optional.of(plotId));
        data.addBill(List.of(ref), List.of(ref), java.util.Map.of(GOLD_NUGGET, 1), 1L, Optional.empty());

        assertEquals(Set.of(owned), data.getBillsFor(plotId));
        assertEquals(Set.of(), data.getBillsFor(UUID.randomUUID()));
    }

    @Test
    void billSurvivesEncodeDecodeRoundTrip() {
        BillSavedData data = new BillSavedData();
        BoxRef source = new BoxRef(UUID.randomUUID(), GlobalPos.of(Level.OVERWORLD, new BlockPos(0, 64, 0)));
        BoxRef dest = new BoxRef(UUID.randomUUID(), GlobalPos.of(Level.NETHER, new BlockPos(5, 64, 0)));
        RecurringBill created = data.addBill(List.of(source), List.of(dest), java.util.Map.of(GOLD_NUGGET, 10), 3L, Optional.of(UUID.randomUUID()));
        data.markProcessed(created.billId(), 5L, BillTransferEngine.BillOutcome.INSUFFICIENT_FUNDS);

        BillSavedData reloaded = simulateReload(data);
        Optional<RecurringBill> reloadedBill = reloaded.getBill(created.billId());
        assertTrue(reloadedBill.isPresent());
        assertEquals(List.of(source), reloadedBill.get().sourceBoxes());
        assertEquals(List.of(dest), reloadedBill.get().destinationBoxes());
        assertEquals(5L, reloadedBill.get().lastProcessedDay());
        assertEquals(created.ownerContext(), reloadedBill.get().ownerContext());
        assertEquals(Optional.of(BillTransferEngine.BillOutcome.INSUFFICIENT_FUNDS), reloadedBill.get().lastOutcome());
    }

    private static BillSavedData simulateReload(BillSavedData data) {
        var codec = BillSavedData.TYPE.codecFactory().create(null);

        DataResult<Tag> encodeResult = codec.encodeStart(NbtOps.INSTANCE, data);
        Tag encoded = encodeResult.result().orElseThrow(() -> new AssertionError("Encode failed: " + encodeResult.error()));

        DataResult<BillSavedData> decodeResult = codec.parse(NbtOps.INSTANCE, encoded);
        return decodeResult.result().orElseThrow(() -> new AssertionError("Decode failed: " + decodeResult.error()));
    }
}
