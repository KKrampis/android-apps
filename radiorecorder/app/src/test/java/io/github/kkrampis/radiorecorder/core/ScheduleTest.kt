package io.github.kkrampis.radiorecorder.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class ScheduleTest {
    private val ny = ZoneId.of("America/New_York")
    private fun at(y: Int, mo: Int, d: Int, h: Int, mi: Int, zone: ZoneId = ny) = ZonedDateTime.of(y, mo, d, h, mi, 0, 0, zone)

    private val nightOwl = Station(
        id = "n", name = "Night Owl FM", url = "http://x", timezone = "America/New_York",
        schedule = StationSchedule(
            mode = ScheduleMode.SCHEDULED,
            slots = listOf(
                Slot(days = setOf(1, 2, 3, 4, 5), start = "22:00", end = "02:00", program = "Night Owl Sessions"),
                Slot(days = setOf(6), start = "20:00", end = "23:00", program = "Saturday Soul"),
            ),
            oneOff = listOf(OneOff("2026-12-01", "19:00", "21:00", "Live from the Blue Room")),
        ),
    )

    @Test
    fun waitsUntilNextSlot() {
        // Friday 2026-11-27 17:00 -> next is Friday 22:00
        val e = Schedule.evaluate(nightOwl, at(2026, 11, 27, 17, 0))
        assertFalse(e.record)
        assertEquals(at(2026, 11, 27, 22, 0), e.boundary)
    }

    @Test
    fun slotCrossingMidnightIsActiveAfterMidnight() {
        // Saturday 01:30 is inside Friday's 22:00-02:00 slot
        val e = Schedule.evaluate(nightOwl, at(2026, 11, 28, 1, 30))
        assertTrue(e.record)
        assertEquals("Night Owl Sessions", e.program)
        assertEquals(at(2026, 11, 28, 2, 0), e.boundary)
    }

    @Test
    fun oneOffRecording() {
        val e = Schedule.evaluate(nightOwl, at(2026, 12, 1, 19, 30))
        assertTrue(e.record)
        assertEquals("Live from the Blue Room", e.program)
    }

    @Test
    fun alwaysModeUsesSlotsOnlyForNames() {
        val st = nightOwl.copy(schedule = nightOwl.schedule.copy(mode = ScheduleMode.ALWAYS))
        val outside = Schedule.evaluate(st, at(2026, 11, 27, 12, 0))
        assertTrue(outside.record)
        assertNull(outside.program)
        val inside = Schedule.evaluate(st, at(2026, 11, 27, 23, 0))
        assertEquals("Night Owl Sessions", inside.program)
    }

    @Test
    fun upcomingIsSortedAndIncludesActive() {
        val list = Schedule.upcoming(nightOwl, 3, at(2026, 11, 27, 23, 0))
        assertEquals(3, list.size)
        assertEquals("Night Owl Sessions", list[0].program) // active now
        assertEquals("Saturday Soul", list[1].program)
        assertTrue(list[0].start.isBefore(list[1].start))
    }

    @Test
    fun stationTimezoneIsRespected() {
        val athens = Station(
            id = "a", name = "Aegean", url = "http://x", timezone = "Europe/Athens",
            schedule = StationSchedule(ScheduleMode.SCHEDULED, listOf(Slot((1..7).toSet(), "00:00", "02:00", "Late Night"))),
        )
        // 17:30 in New York on Nov 27 = 00:30 in Athens on Nov 28
        val nowAthens = at(2026, 11, 27, 17, 30).withZoneSameInstant(ZoneId.of("Europe/Athens"))
        assertTrue(Schedule.evaluate(athens, nowAthens).record)
    }

    @Test
    fun validation() {
        assertNull(Validation.station(nightOwl))
        assertEquals("Give the station a name", Validation.station(nightOwl.copy(name = " ")))
        assertTrue(Validation.station(nightOwl.copy(url = "ftp://x"))!!.contains("http"))
        assertTrue(Validation.station(nightOwl.copy(timezone = "Mars/Base"))!!.contains("time zone"))
    }
}
