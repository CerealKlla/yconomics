package com.github.cerealklla.yconomics.bills;

/**
 * Pure day-boundary math over {@code Level#getOverworldClockTime()} -- this MC version replaced
 * the old flat {@code getDayTime()} tick counter with a data-driven {@code Timeline}/{@code
 * ClockManager} system (confirmed via the real decompiled source, 2026-10-05); {@code
 * data/minecraft/timeline/day.json}'s {@code period_ticks} is still {@value #TICKS_PER_DAY} by
 * default, so the day-length math here is unchanged numerically, just sourced from the new
 * method. Deliberately doesn't read the Timeline registry to discover a server's actual
 * configured period -- a datapack-customized day length would need this revisited, flagged as a
 * simplification rather than assumed impossible.
 */
public final class DayChangeTracker {

    public static final long TICKS_PER_DAY = 24000L;

    private DayChangeTracker() {
    }

    public static long dayNumber(long dayTime) {
        return Math.floorDiv(dayTime, TICKS_PER_DAY);
    }

    public static boolean hasCrossedDayBoundary(long previousDayTime, long currentDayTime) {
        return dayNumber(currentDayTime) > dayNumber(previousDayTime);
    }
}
