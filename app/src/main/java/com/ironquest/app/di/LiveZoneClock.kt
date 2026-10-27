package com.ironquest.app.di

import java.time.Clock
import java.time.Instant
import java.time.ZoneId

/**
 * System clock whose zone follows the device setting. `Clock.systemDefaultZone()` captures
 * the zone once, so after TIMEZONE_CHANGED "today" and reminder times would stay in the old
 * zone until the process restarts. Android resets the process default TimeZone on a change.
 */
object LiveZoneClock : Clock() {
    override fun getZone(): ZoneId = ZoneId.systemDefault()
    override fun withZone(zone: ZoneId): Clock = Clock.system(zone)
    override fun instant(): Instant = Instant.now()
    override fun millis(): Long = System.currentTimeMillis()
}
