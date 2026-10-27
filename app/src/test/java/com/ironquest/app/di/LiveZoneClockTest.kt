package com.ironquest.app.di

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId
import java.util.TimeZone

class LiveZoneClockTest {
    @Test
    fun `zone follows the default time zone without a restart`() {
        val original = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Tokyo"))
            assertEquals(ZoneId.of("Asia/Tokyo"), LiveZoneClock.zone)
            TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"))
            assertEquals(ZoneId.of("America/New_York"), LiveZoneClock.zone)
        } finally {
            TimeZone.setDefault(original)
        }
    }
}
