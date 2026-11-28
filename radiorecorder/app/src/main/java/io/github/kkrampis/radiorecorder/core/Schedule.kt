package io.github.kkrampis.radiorecorder.core

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/** A recording window from the weekly slots or one-off recordings, in the station's zone. */
data class Window(
    val start: ZonedDateTime,
    val end: ZonedDateTime,
    val program: String,
    val once: Boolean,
)

data class Evaluation(
    val record: Boolean,
    /** Program name from the active slot, if any. */
    val program: String?,
    /** When recording: end of the active window. When waiting: next start. */
    val boundary: ZonedDateTime?,
)

object Schedule {
    fun zone(st: Station): ZoneId =
        if (st.timezone.isBlank()) ZoneId.systemDefault()
        else try {
            ZoneId.of(st.timezone)
        } catch (e: Exception) {
            ZoneId.systemDefault()
        }

    fun now(st: Station): ZonedDateTime = ZonedDateTime.now(zone(st))

    private fun time(s: String): LocalTime {
        val (h, m) = s.split(":").map { it.trim().toInt() }
        return LocalTime.of(h, m)
    }

    private fun window(day: LocalDate, start: String, end: String, zone: ZoneId, program: String, once: Boolean): Window {
        val s = ZonedDateTime.of(day, time(start), zone)
        var e = ZonedDateTime.of(day, time(end), zone)
        if (!e.isAfter(s)) e = ZonedDateTime.of(day.plusDays(1), time(end), zone) // crosses midnight
        return Window(s, e, program, once)
    }

    fun windows(st: Station, ref: ZonedDateTime, daysBack: Long = 1, daysForward: Long = 8): List<Window> {
        val zone = ref.zone
        val out = ArrayList<Window>()
        for (slot in st.schedule.slots) {
            if (!Validation.isTime(slot.start) || !Validation.isTime(slot.end)) continue
            for (d in -daysBack until daysForward) {
                val day = ref.toLocalDate().plusDays(d)
                if (day.dayOfWeek.value in slot.days) out += window(day, slot.start, slot.end, zone, slot.program, false)
            }
        }
        for (o in st.schedule.oneOff) {
            val day = try {
                LocalDate.parse(o.date)
            } catch (e: Exception) {
                continue
            }
            if (!Validation.isTime(o.start) || !Validation.isTime(o.end)) continue
            out += window(day, o.start, o.end, zone, o.program, true)
        }
        return out
    }

    /**
     * Should this station be recording now, and under which program name?
     * In "always" mode slots don't limit recording; they only name programs (and split files
     * at their boundaries).
     */
    fun evaluate(st: Station, now: ZonedDateTime = now(st)): Evaluation {
        val all = windows(st, now)
        val active = all.filter { !it.start.isAfter(now) && now.isBefore(it.end) }.sortedBy { it.start }
        if (active.isNotEmpty()) {
            return Evaluation(true, active.last().program.ifBlank { null }, active.maxOf { it.end })
        }
        if (st.schedule.mode == ScheduleMode.ALWAYS) return Evaluation(true, null, null)
        val next = all.filter { it.start.isAfter(now) }.minByOrNull { it.start }
        return Evaluation(false, null, next?.start)
    }

    fun upcoming(st: Station, limit: Int = 10, now: ZonedDateTime = now(st)): List<Window> =
        windows(st, now).filter { it.end.isAfter(now) }.sortedBy { it.start }.take(limit)
}
