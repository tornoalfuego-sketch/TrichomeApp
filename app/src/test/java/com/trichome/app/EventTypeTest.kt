package com.trichome.app

import com.trichome.app.model.EventType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** All 16 journal event types resolve consistently by key and index. */
class EventTypeTest {

    @Test
    fun `exactly sixteen types are defined`() {
        assertEquals(16, EventType.entries.size)
    }

    @Test
    fun `every storage key round-trips`() {
        EventType.entries.forEach { type ->
            assertEquals(type, EventType.fromKey(type.storageKey))
        }
    }

    @Test
    fun `unknown key falls back to diagnosis`() {
        assertEquals(EventType.DIAGNOSIS, EventType.fromKey("NOT_A_TYPE"))
    }

    @Test
    fun `index mapping stays in range`() {
        assertEquals(EventType.IRRIGATION, EventType.fromIndex(0))
        assertEquals(EventType.DIAGNOSIS, EventType.fromIndex(15))
        // Out-of-range indexes clamp instead of crashing.
        assertTrue(EventType.fromIndex(999) in EventType.entries)
    }

    @Test
    fun `metric types for charts are the quantitative series`() {
        val metrics = EventType.metricTypes
        assertTrue(metrics.contains(EventType.TEMPERATURE_HUMIDITY))
        assertTrue(metrics.contains(EventType.HEIGHT))
        assertTrue(metrics.contains(EventType.IRRIGATION))
        assertTrue(metrics.contains(EventType.FERTILIZATION))
        assertTrue(metrics.contains(EventType.VPD))
        assertTrue(!metrics.contains(EventType.PRUNING))
    }
}