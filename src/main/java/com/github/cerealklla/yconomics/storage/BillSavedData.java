package com.github.cerealklla.yconomics.storage;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import com.github.cerealklla.yconomics.YconomicsMod;
import com.github.cerealklla.yconomics.bills.BillTransferEngine;
import com.github.cerealklla.yconomics.bills.RecurringBill;
import com.github.cerealklla.yconomics.bills.RecurringBill.BoxRef;

import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * World-level (save-wide, not per-dimension) persistent store for recurring bills -- same shape
 * and same {@code MinecraftServer}-level resolution rule as Cartographyr's own {@code
 * CartographySavedData} (bills aren't dimension-scoped; their boxes can even span dimensions).
 * This is NOT the intended integration point for other mods -- see {@code api.Yconomics}.
 */
public final class BillSavedData extends SavedData {

    public static final SavedDataType<BillSavedData> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(YconomicsMod.MODID, "recurring_bills"),
            BillSavedData::new,
            codec()
    );

    private final Map<UUID, RecurringBill> bills;

    // Package-private (not private) so the test suite in this same package can construct a fresh
    // instance directly without going through the SavedDataType machinery.
    BillSavedData() {
        this(new HashMap<>());
    }

    private BillSavedData(Map<UUID, RecurringBill> bills) {
        this.bills = bills;
    }

    private static Codec<BillSavedData> codec() {
        return RecordCodecBuilder.create(i -> i.group(
                Codec.unboundedMap(UUIDUtil.STRING_CODEC, RecurringBill.CODEC).fieldOf("bills").forGetter(d -> d.bills)
        ).apply(i, bills -> new BillSavedData(new HashMap<>(bills))));
    }

    /** @apiNote Not the intended integration point — use {@code Yconomics.registerRecurringBill} instead. */
    public RecurringBill addBill(List<BoxRef> sourceBoxes, List<BoxRef> destinationBoxes,
                                  Map<Identifier, Integer> cost, long periodDays, Optional<UUID> ownerContext) {
        UUID billId = UUID.randomUUID();
        RecurringBill bill = new RecurringBill(billId, List.copyOf(sourceBoxes), List.copyOf(destinationBoxes),
                Map.copyOf(cost), periodDays, 0L, true, ownerContext, Optional.empty());
        bills.put(billId, bill);
        setDirty();
        return bill;
    }

    /** @apiNote Not the intended integration point — use {@code Yconomics.getRecurringBill} instead. */
    public Optional<RecurringBill> getBill(UUID billId) {
        return Optional.ofNullable(bills.get(billId));
    }

    /** @apiNote Not the intended integration point — use {@code Yconomics.getAllActiveBills} instead (bills package only). */
    public Set<RecurringBill> getAllActiveBills() {
        Set<RecurringBill> result = new HashSet<>();
        for (RecurringBill bill : bills.values()) {
            if (bill.active()) {
                result.add(bill);
            }
        }
        return result;
    }

    /** @apiNote Not the intended integration point — use {@code Yconomics.getRecurringBillsFor} instead. */
    public Set<RecurringBill> getBillsFor(UUID ownerContext) {
        Set<RecurringBill> result = new HashSet<>();
        for (RecurringBill bill : bills.values()) {
            if (bill.ownerContext().equals(Optional.of(ownerContext))) {
                result.add(bill);
            }
        }
        return result;
    }

    /** @apiNote Not the intended integration point — use {@code Yconomics.setRecurringBillActive} instead. */
    public Optional<RecurringBill> setActive(UUID billId, boolean active) {
        RecurringBill current = bills.get(billId);
        if (current == null) {
            return Optional.empty();
        }
        RecurringBill updated = current.withActive(active);
        bills.put(billId, updated);
        setDirty();
        return Optional.of(updated);
    }

    /** @apiNote Not the intended integration point — use {@code bills.BillProcessor} instead. */
    public Optional<RecurringBill> markProcessed(UUID billId, long day, BillTransferEngine.BillOutcome outcome) {
        RecurringBill current = bills.get(billId);
        if (current == null) {
            return Optional.empty();
        }
        RecurringBill updated = current.withProcessingResult(day, outcome);
        bills.put(billId, updated);
        setDirty();
        return Optional.of(updated);
    }

    /** @apiNote Not the intended integration point — use {@code Yconomics.setRecurringBillBoxes} instead. */
    public Optional<RecurringBill> setBoxes(UUID billId, List<BoxRef> sourceBoxes, List<BoxRef> destinationBoxes) {
        RecurringBill current = bills.get(billId);
        if (current == null) {
            return Optional.empty();
        }
        RecurringBill updated = current.withBoxes(sourceBoxes, destinationBoxes);
        bills.put(billId, updated);
        setDirty();
        return Optional.of(updated);
    }

    /** @apiNote Not the intended integration point — use {@code Yconomics.removeRecurringBill} instead. */
    public boolean removeBill(UUID billId) {
        boolean removed = bills.remove(billId) != null;
        if (removed) {
            setDirty();
        }
        return removed;
    }
}
