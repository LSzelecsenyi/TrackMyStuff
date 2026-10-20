package app.mymusclemap.domain

import java.time.Clock
import java.time.Instant
import java.time.ZoneId

/**
 * Wall clock that reads the device zone on every query.
 * [Clock.systemDefaultZone] captures the zone at construction, so a later
 * timezone change would otherwise stay invisible for the life of the process.
 */
class SystemWallClock : Clock() {
    override fun getZone(): ZoneId = ZoneId.systemDefault()

    override fun instant(): Instant = Instant.ofEpochMilli(System.currentTimeMillis())

    override fun withZone(zone: ZoneId): Clock = Clock.system(zone)
}
