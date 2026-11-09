package com.powerfulhimchan.mapexit.core

import org.junit.Assert.*
import org.junit.Test

class HealthWarningTest {
    @Test fun edgeWarningBreathesSlowlyAndRespectsReducedMotion() {
        assertEquals(0f,HealthWarning.edgePulse(0),0f)
        assertEquals(1f,HealthWarning.edgePulse(1000),.0001f)
        for(clock in listOf(-1L,0L,500L,1000L,1999L)) {
            assertEquals(HealthWarning.edgePulse(clock),HealthWarning.edgePulse(clock+2000),.0001f)
            assertEquals(.45f,HealthWarning.edgePulse(clock,false),0f)
        }
        assertTrue(kotlin.math.abs(HealthWarning.edgePulse(1999)-HealthWarning.edgePulse(2000))<.001f)
    }
    @Test fun thresholdUsesEquippedMaximumAndExcludesDeathSurfaceAndInvalidValues() {
        assertTrue(HealthWarning.active(36.0, 360.0, 1))
        assertFalse(HealthWarning.active(36.001, 360.0, 1))
        assertTrue(HealthWarning.active(28.0, 360.0, 50))
        for (hp in listOf(0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY))
            assertFalse(HealthWarning.active(hp, 360.0, 1))
        for (maximum in listOf(0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY))
            assertFalse(HealthWarning.active(1.0, maximum, 1))
        assertFalse(HealthWarning.active(28.0, 360.0, 0))
        assertFalse(HealthWarning.active(28.0, 360.0, -1))
        assertFalse(HealthWarning.active(28.0, 200.0, 1))
    }

    @Test fun heartbeatHasTwoSoftPeaksIsPeriodicAndCanRemainStill() {
        val values = (0L until HealthWarning.PERIOD_MS).map { HealthWarning.pulse(it) }
        assertTrue(values.all { it.isFinite() && it in 0f..1f })
        assertTrue(HealthWarning.pulse(176) > HealthWarning.pulse(0) + .4f)
        assertTrue(HealthWarning.pulse(385) > HealthWarning.pulse(800) + .25f)
        for (clock in listOf(-1L, 0L, 176L, 385L, 1100L, Long.MAX_VALUE - 1100)) {
            assertEquals(HealthWarning.pulse(clock), HealthWarning.pulse(clock + 1100), .00001f)
            assertEquals(.45f, HealthWarning.pulse(clock, animate = false), 0f)
        }
        // A smooth envelope avoids a one-frame jump even when the clock wraps.
        assertTrue(kotlin.math.abs(HealthWarning.pulse(1099) - HealthWarning.pulse(1100)) < .01f)
    }
}
