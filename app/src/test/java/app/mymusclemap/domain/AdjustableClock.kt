package app.mymusclemap.domain

import java.time.Clock
import java.time.Instant
import java.time.ZoneId

/** Test clock whose instant and zone can move without replacing the instance. */
class AdjustableClock(
    instant: Instant,
    zone: ZoneId
) : Clock() {
    private var currentInstant: Instant = instant
    private var currentZone: ZoneId = zone

    override fun getZone(): ZoneId = currentZone

    override fun instant(): Instant = currentInstant

    override fun withZone(zone: ZoneId): Clock = AdjustableClock(currentInstant, zone)

    fun setInstant(instant: Instant) {
        currentInstant = instant
    }

    fun setZone(zone: ZoneId) {
        currentZone = zone
    }
}
