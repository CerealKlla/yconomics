package com.github.cerealklla.yconomics.bills;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Covers {@code BillTransferEngine}'s pure list-order greedy-allocation math only -- the
 * real-container glue (reading/writing actual {@code ItemStack}s) can't be unit tested in this
 * project (constructing a real {@code ItemStack} throws "Components not bound yet" outside a real
 * client/server boot, confirmed empirically; see {@code BillTransferEngine}'s own class doc) and
 * is instead verified by the live playtest script in the implementation plan.
 */
class BillTransferEngineTest {

    @Test
    void allocatesPartiallyFromEachBucketInListOrderUntilSatisfied() {
        List<Integer> taken = BillTransferEngine.greedyAllocate(List.of(4, 10), 10);
        assertEquals(List.of(4, 6), taken);
    }

    @Test
    void allocateNeverTakesMoreThanABucketsOwnCapacity() {
        List<Integer> taken = BillTransferEngine.greedyAllocate(List.of(4, 3), 10);
        assertEquals(List.of(4, 3), taken);
        assertEquals(7, BillTransferEngine.sum(taken));
    }

    @Test
    void allocateAcrossThreeBucketsSkipsAnEmptyOneInTheMiddle() {
        List<Integer> taken = BillTransferEngine.greedyAllocate(List.of(5, 0, 8), 10);
        assertEquals(List.of(5, 0, 5), taken);
    }

    @Test
    void allocateOfZeroAmountTakesNothing() {
        List<Integer> taken = BillTransferEngine.greedyAllocate(List.of(5, 5), 0);
        assertEquals(List.of(0, 0), taken);
    }

    @Test
    void allocateFromNoBucketsReturnsEmptyList() {
        assertEquals(List.of(), BillTransferEngine.greedyAllocate(List.of(), 10));
    }

    @Test
    void sumAddsEveryBucket() {
        assertEquals(15, BillTransferEngine.sum(List.of(4, 6, 5)));
    }
}
