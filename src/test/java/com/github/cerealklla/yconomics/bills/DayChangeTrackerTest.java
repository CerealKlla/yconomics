package com.github.cerealklla.yconomics.bills;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DayChangeTrackerTest {

    @Test
    void dayNumberAtExactBoundaries() {
        assertEquals(0, DayChangeTracker.dayNumber(0));
        assertEquals(0, DayChangeTracker.dayNumber(23999));
        assertEquals(1, DayChangeTracker.dayNumber(24000));
        assertEquals(1, DayChangeTracker.dayNumber(47999));
        assertEquals(2, DayChangeTracker.dayNumber(48000));
    }

    @Test
    void hasCrossedDayBoundaryWithinSameDayIsFalse() {
        assertFalse(DayChangeTracker.hasCrossedDayBoundary(100, 23999));
    }

    @Test
    void hasCrossedDayBoundaryAcrossExactBoundaryIsTrue() {
        assertTrue(DayChangeTracker.hasCrossedDayBoundary(23999, 24000));
    }

    @Test
    void hasCrossedDayBoundaryAcrossMultipleDaysIsTrue() {
        assertTrue(DayChangeTracker.hasCrossedDayBoundary(0, 48500));
    }
}
