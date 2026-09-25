package com.github.cerealklla.yconomics.recipe;

import org.junit.jupiter.api.Test;

import net.minecraft.util.RandomSource;

import static org.junit.jupiter.api.Assertions.assertTrue;

class GamblingRecipeTest {

    @Test
    void rollNuggetCountStaysWithinInclusiveBounds() {
        RandomSource random = RandomSource.create(1234L);
        for (int i = 0; i < 1000; i++) {
            int count = GamblingRecipe.rollNuggetCount(random);
            assertTrue(count >= GamblingRecipe.MIN_NUGGETS && count <= GamblingRecipe.MAX_NUGGETS,
                    "Expected [" + GamblingRecipe.MIN_NUGGETS + ", " + GamblingRecipe.MAX_NUGGETS + "], got " + count);
        }
    }

    @Test
    void rollNuggetCountCanReachBothEnds() {
        RandomSource random = RandomSource.create(1234L);
        boolean sawMin = false;
        boolean sawMax = false;
        for (int i = 0; i < 1000; i++) {
            int count = GamblingRecipe.rollNuggetCount(random);
            sawMin |= count == GamblingRecipe.MIN_NUGGETS;
            sawMax |= count == GamblingRecipe.MAX_NUGGETS;
        }
        assertTrue(sawMin, "Never rolled the minimum in 1000 tries");
        assertTrue(sawMax, "Never rolled the maximum in 1000 tries");
    }
}
