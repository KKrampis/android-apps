package io.github.kkrampis.radiorecorder.core

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class StreamFormatTest {
    @Test
    fun icyTitle() {
        val raw = "StreamTitle='Harbor Talk with Dana Reyes';StreamUrl='';".toByteArray() + ByteArray(7)
        assertEquals("Harbor Talk with Dana Reyes", Icy.parseTitle(raw))
        assertEquals("It's Jazz", Icy.parseTitle("StreamTitle='It's Jazz';".toByteArray()))
        assertEquals("Καλημέρα", Icy.parseTitle("StreamTitle='Καλημέρα';".toByteArray(Charsets.UTF_8)))
        assertEquals("Café", Icy.parseTitle("StreamTitle='Café';".toByteArray(Charsets.ISO_8859_1)))
        assertNull(Icy.parseTitle("nothing".toByteArray()))
    }

    @Test
    fun playlists() {
        assertEquals("http://h:8000/live", Icy.firstPlaylistEntry("[playlist]\nFile1=http://h:8000/live\n", "http://h/x.pls"))
        assertEquals("http://h/a.mp3", Icy.firstPlaylistEntry("#EXTM3U\n#EXTINF:-1,X\nhttp://h/a.mp3\n", "http://h/x.m3u"))
        assertTrue(Icy.isPlaylist("audio/x-scpls", "http://h/listen"))
        assertTrue(Icy.isPlaylist(null, "http://h/listen.m3u?x=1"))
        assertTrue(Icy.isHls("http://h/live.m3u8"))
        assertEquals("aac", Icy.extension("audio/aacp"))
        assertEquals("mp3", Icy.extension("audio/mpeg; charset=x"))
    }

    /** MPEG-1 Layer III, 128 kbps, 44.1 kHz, stereo, no padding: 417-byte frames. */
    private fun mp3Frame(): ByteArray = ByteArray(417).also {
        it[0] = 0xFF.toByte(); it[1] = 0xFB.toByte(); it[2] = 0x90.toByte(); it[3] = 0x64
    }

    @Test
    fun mp3Header() {
        val h = Mp3.parse(mp3Frame(), 0)!!
        assertEquals(417, h.length)
        assertEquals(44100, h.sampleRate)
        assertEquals(2, h.channels)
        assertEquals(1152, h.samples)
    }

    @Test
    fun splitterFindsFramesAcrossChunksAndSkipsJunk() {
        val stream = byteArrayOf(1, 2, 3, 0xFF.toByte(), 9) + mp3Frame() + mp3Frame() + mp3Frame() + mp3Frame()
        val sp = FrameSplitter(FrameKind.MP3)
        val frames = ArrayList<Frame>()
        var i = 0
        while (i < stream.size) {
            val n = minOf(300, stream.size - i)
            frames += sp.feed(stream, i, n)
            i += n
        }
        // The last frame waits for the next header to confirm it.
        assertEquals(3, frames.size)
        assertTrue(frames.all { it.data.size == 417 && it.sampleRate == 44100 })
    }

    @Test
    fun adtsRoundTrip() {
        val payload = 300
        val hdr = Adts.header(payload, 44100, 2)
        val frame = hdr + ByteArray(payload)
        val h = Adts.parse(frame, 0)!!
        assertEquals(payload + 7, h.length)
        assertEquals(44100, h.sampleRate)
        assertEquals(2, h.channels)
        assertEquals(7, h.headerLength)
        assertEquals(2, h.objectType)
        // LC, 44.1 kHz (index 4), stereo -> 0x12 0x10
        assertArrayEquals(byteArrayOf(0x12, 0x10), Adts.audioSpecificConfig(2, 4, 2))
    }

    @Test
    fun boundaries() {
        val ny = ZoneId.of("America/New_York")
        val start = ZonedDateTime.of(2026, 11, 27, 9, 20, 0, 0, ny)
        assertEquals(ZonedDateTime.of(2026, 11, 27, 10, 0, 0, 0, ny), Names.nextBoundary(start, 60.0, true))
        assertEquals(ZonedDateTime.of(2026, 11, 27, 10, 20, 0, 0, ny), Names.nextBoundary(start, 60.0, false))
        assertEquals(ZonedDateTime.of(2026, 11, 27, 9, 30, 0, 0, ny), Names.nextBoundary(start, 30.0, true))
        // 30 s before the hour: skip to the next one instead of making a 30-second file
        val late = ZonedDateTime.of(2026, 11, 27, 9, 59, 30, 0, ny)
        assertEquals(ZonedDateTime.of(2026, 11, 27, 11, 0, 0, 0, ny), Names.nextBoundary(late, 60.0, true))
        assertEquals("harbor-city-radio_20261127-092000_morning-show", Names.fileBase("Harbor City Radio", start, "Morning Show!"))
    }

    @Test
    fun archiveMetadataAndHeaders() {
        val ny = ZoneId.of("America/New_York")
        val start = ZonedDateTime.of(2026, 11, 27, 9, 0, 0, 0, ny)
        val rec = RecordingInfo("Aegean Talk 98.4", "Kalimera Πρωινό", start, start.plusMinutes(60), listOf("A", "A", "B"), "http://s/x")
        val meta = ArchiveMeta.metadata(rec, StationArchive(collection = "aegean-talk", tags = listOf("greek")), ArchiveSettings())
        assertEquals("aegean-talk", meta["collection"])
        assertEquals("2026-11-27", meta["date"])
        @Suppress("UNCHECKED_CAST")
        val subjects = meta["subject"] as List<String>
        assertTrue(subjects.containsAll(listOf("radio", "Aegean Talk 98.4", "2026-11-27", "09:00", "Friday", "greek")))
        assertTrue((meta["description"] as String).contains("(60 min)"))
        assertEquals("A; B", meta["stream_titles"])

        val headers = ArchiveMeta.headers(meta).toMap()
        assertEquals("audio", headers["x-archive-meta-mediatype"])
        assertEquals("radio", headers["x-archive-meta01-subject"])
        assertTrue(headers.containsKey("x-archive-meta-recording--start"))
        assertTrue(headers.getValue("x-archive-meta-title").startsWith("uri(Aegean%20Talk"))
        assertTrue(headers.values.all { v -> v.all { it.code in 32..126 } })

        val test = ArchiveMeta.metadata(rec, StationArchive(collection = "x"), ArchiveSettings(testMode = true))
        assertEquals("test_collection", test["collection"])
        assertEquals("radiorec-aegean-talk-98-4-20261127-090000", ArchiveMeta.identifier("radiorec", rec.stationName, start))
    }
}
