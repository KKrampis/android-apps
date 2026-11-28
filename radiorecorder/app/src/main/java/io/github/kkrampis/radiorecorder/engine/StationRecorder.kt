package io.github.kkrampis.radiorecorder.engine

import android.util.Log
import io.github.kkrampis.radiorecorder.core.FrameKind
import io.github.kkrampis.radiorecorder.core.Names
import io.github.kkrampis.radiorecorder.core.OutFormat
import io.github.kkrampis.radiorecorder.core.Schedule
import io.github.kkrampis.radiorecorder.core.Station
import io.github.kkrampis.radiorecorder.data.TitleMark
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicBoolean

enum class RecState { STARTING, CONNECTING, RECORDING, WAITING, RETRYING, DISABLED, PAUSED, STOPPED }

data class RecorderStatus(
    val state: RecState = RecState.STARTING,
    val error: String? = null,
    val program: String? = null,
    val title: String? = null,
    val fileName: String? = null,
    val sinceMs: Long? = null,
    val bytesIn: Long = 0,
    val nextStart: ZonedDateTime? = null,
    val hasMetadata: Boolean? = null,
    val note: String? = null,
)

/**
 * One thread per station: connect, write files, split on program change / schedule / max length,
 * reconnect with back-off. A port of the desktop app's recorder.
 */
class StationRecorder(private val engine: Engine, @Volatile var station: Station) : Thread("rec-${station.id}") {
    @Volatile var status = RecorderStatus()
        private set

    private val stopFlag = AtomicBoolean(false)
    private val restartFlag = AtomicBoolean(false)
    private val splitFlag = AtomicBoolean(false)
    private val wake = Object()

    init {
        isDaemon = true
    }

    fun updateStation(st: Station) {
        station = st
        restartFlag.set(true)
        poke()
    }

    fun shutdown() {
        stopFlag.set(true)
        poke()
        interrupt() // unblocks a waiting read only when idle; reads are closed via the source below
        current?.close()
    }

    fun splitNow() = splitFlag.set(true)

    fun poke() = synchronized(wake) { wake.notifyAll() }

    private fun idle(ms: Long) {
        try {
            synchronized(wake) { wake.wait(ms) }
        } catch (_: InterruptedException) {
        }
    }

    @Volatile private var current: StreamSource? = null

    override fun run() {
        var backoff = 2_000L
        while (!stopFlag.get()) {
            restartFlag.set(false)
            val st = station
            if (engine.paused) {
                status = RecorderStatus(RecState.PAUSED)
                idle(30_000)
                continue
            }
            if (!st.enabled) {
                status = RecorderStatus(RecState.DISABLED)
                idle(30_000)
                continue
            }
            val ev = Schedule.evaluate(st)
            if (!ev.record) {
                status = RecorderStatus(RecState.WAITING, nextStart = ev.boundary)
                idle(5_000)
                continue
            }
            val started = System.currentTimeMillis()
            try {
                status = status.copy(state = RecState.CONNECTING, nextStart = null)
                session(st)
                backoff = 2_000L
            } catch (e: Exception) {
                if (stopFlag.get()) break
                val msg = e.message ?: e.javaClass.simpleName
                Log.w(TAG, "[${st.name}] $msg")
                if (System.currentTimeMillis() - started > 120_000) backoff = 2_000L
                status = RecorderStatus(RecState.RETRYING, error = msg)
                idle(backoff)
                backoff = minOf(60_000L, backoff * 2)
            }
        }
        status = RecorderStatus(RecState.STOPPED)
    }

    private class Seg(
        val id: Long,
        val writer: SegmentWriter,
        val startMs: Long,
        val program: String,
        val deadline: ZonedDateTime,
        val titles: MutableList<TitleMark>,
    )

    private fun session(st: Station) {
        val src = StreamSource.open(engine.http, st.url)
        current = src
        // Re-encode only what Android can decode frame by frame; otherwise keep the original.
        val kind = when (src.ext) {
            "mp3" -> FrameKind.MP3
            "aac" -> FrameKind.ADTS
            else -> null
        }
        val transcode = st.quality.format == OutFormat.AAC && kind != null
        val note = if (st.quality.format == OutFormat.AAC && kind == null) {
            "This stream is ${src.ext.uppercase()}; saving the original (AAC re-encoding needs an MP3 or AAC stream)."
        } else {
            null
        }
        val transcoder = if (transcode) AacTranscoder(kind!!, st.quality.bitrateKbps, st.quality.mono) else null
        status = RecorderStatus(RecState.RECORDING, hasMetadata = src.hasMetadata, note = note)

        var seg: Seg? = null
        var schedProgram: String? = Schedule.evaluate(st).program
        var title: String? = null
        val pending = ByteArrayOutputStream()
        var lastEval = 0L

        fun finish(s: Seg, reason: String) {
            finishSegment(st, s, reason)
        }

        try {
            while (true) {
                if (stopFlag.get() || restartFlag.get()) break
                val chunk = src.next()
                var reason: String? = null
                if (chunk.title != null) {
                    title = chunk.title
                    status = status.copy(title = title)
                    val s = seg
                    if (s != null) {
                        val age = (System.currentTimeMillis() - s.startMs) / 1000.0
                        if (st.split.onMetadata && title.isNotBlank() && age >= st.split.minMinutes * 60) {
                            reason = "program-change"
                        } else {
                            s.titles += TitleMark(age, title)
                        }
                    }
                }
                val nowMs = System.currentTimeMillis()
                if (nowMs - lastEval >= 1000) {
                    lastEval = nowMs
                    val ev = Schedule.evaluate(st)
                    if (!ev.record) {
                        seg?.let { finish(it, "schedule-end") }
                        seg = null
                        return
                    }
                    if (seg != null && ev.program != schedProgram) reason = reason ?: "schedule-program"
                    schedProgram = ev.program
                    if (seg != null && !Schedule.now(st).isBefore(seg.deadline)) reason = reason ?: "max-length"
                    if (splitFlag.getAndSet(false) && seg != null) reason = "manual"
                }
                if (seg != null && reason != null) {
                    finish(seg, reason)
                    seg = null
                }
                if (seg == null && title == null && src.hasMetadata && pending.size() < 3 * src.metaInt) {
                    // ICY metadata arrives just after the first audio block: wait for it so the first
                    // file is named after the program, not the station.
                    chunk.audio?.let { pending.write(it, 0, chunk.audioLength) }
                    continue
                }
                if (seg == null) {
                    seg = newSegment(st, src, schedProgram, title, transcoder)
                    if (pending.size() > 0) {
                        val p = pending.toByteArray()
                        seg.writer.write(p, p.size)
                        pending.reset()
                    }
                }
                chunk.audio?.let {
                    seg.writer.write(it, chunk.audioLength)
                    status = status.copy(bytesIn = status.bytesIn + chunk.audioLength)
                }
            }
        } finally {
            seg?.let { finish(it, if (stopFlag.get()) "stopped" else if (restartFlag.get()) "settings-changed" else "disconnected") }
            transcoder?.release()
            src.close()
            current = null
        }
    }

    private fun newSegment(st: Station, src: StreamSource, schedProgram: String?, title: String?, transcoder: AacTranscoder?): Seg {
        val now = Schedule.now(st)
        val program = schedProgram ?: title ?: src.name
        val dir = File(engine.recordingsDir, "${Names.slug(st.name, 30)}/${now.toLocalDate()}").apply { mkdirs() }
        val base = Names.fileBase(st.name, now, program)
        val writer: SegmentWriter = if (transcoder != null) {
            AacWriter(File(dir, "$base.aac"), transcoder, st.quality.bitrateKbps, st.quality.mono)
        } else {
            RawWriter(File(dir, "$base.${src.ext}"), src.ext)
        }
        val startMs = System.currentTimeMillis()
        val id = engine.db.insertStart(
            st.id, st.name, writer.file.absolutePath, startMs,
            now.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME), program.ifBlank { null }, writer.formatLabel,
        )
        status = status.copy(fileName = writer.file.name, sinceMs = startMs, program = program.ifBlank { null }, bytesIn = 0)
        Log.i(TAG, "[${st.name}] recording -> ${writer.file}")
        return Seg(
            id, writer, startMs, program,
            Names.nextBoundary(now, st.split.maxMinutes, st.split.alignToClock),
            if (title != null) mutableListOf(TitleMark(0.0, title)) else mutableListOf(),
        )
    }

    private fun finishSegment(st: Station, s: Seg, reason: String) {
        try {
            s.writer.close()
        } catch (e: Exception) {
            Log.w(TAG, "closing ${s.writer.file.name}: ${e.message}")
        }
        val size = s.writer.file.length()
        val durMs = System.currentTimeMillis() - s.startMs
        if (size < 2048 || durMs < 3000) {
            s.writer.file.delete()
            engine.db.delete(s.id)
            return
        }
        val program = s.program.ifBlank { null } ?: s.titles.firstOrNull()?.title
        engine.db.finish(s.id, System.currentTimeMillis(), size, s.titles, reason, program)
        status = status.copy(fileName = null, bytesIn = 0)
        Log.i(TAG, "[${st.name}] finished ${s.writer.file.name} ($reason, ${durMs / 1000}s, ${size / 1024} KB)")
        engine.onSegmentFinished()
    }

    private companion object {
        const val TAG = "RadioRecorder"
    }
}
