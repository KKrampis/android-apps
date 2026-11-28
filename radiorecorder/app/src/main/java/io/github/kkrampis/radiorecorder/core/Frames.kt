package io.github.kkrampis.radiorecorder.core

/**
 * Finds whole audio frames in a byte stream so they can be fed one by one to Android's
 * MediaCodec decoder (which needs frame-aligned input) when re-encoding to AAC.
 */
enum class FrameKind { MP3, ADTS }

data class Frame(
    val data: ByteArray,
    val sampleRate: Int,
    val channels: Int,
    val samples: Int,
    /** For ADTS: the AAC payload without the 7/9-byte header, and the audio object type. */
    val payloadOffset: Int = 0,
    val objectType: Int = 2,
    val sampleRateIndex: Int = 0,
)

data class FrameHeader(
    val length: Int,
    val sampleRate: Int,
    val channels: Int,
    val samples: Int,
    val headerLength: Int = 4,
    val objectType: Int = 2,
    val sampleRateIndex: Int = 0,
)

object Mp3 {
    private val BR_V1 = arrayOf(
        intArrayOf(0, 32, 64, 96, 128, 160, 192, 224, 256, 288, 320, 352, 384, 416, 448), // Layer I
        intArrayOf(0, 32, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320, 384), // Layer II
        intArrayOf(0, 32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320), // Layer III
    )
    private val BR_V2 = arrayOf(
        intArrayOf(0, 32, 48, 56, 64, 80, 96, 112, 128, 144, 160, 176, 192, 224, 256),
        intArrayOf(0, 8, 16, 24, 32, 40, 48, 56, 64, 80, 96, 112, 128, 144, 160),
        intArrayOf(0, 8, 16, 24, 32, 40, 48, 56, 64, 80, 96, 112, 128, 144, 160),
    )
    private val SR = mapOf(3 to intArrayOf(44100, 48000, 32000), 2 to intArrayOf(22050, 24000, 16000), 0 to intArrayOf(11025, 12000, 8000))

    fun parse(b: ByteArray, off: Int, end: Int = b.size): FrameHeader? {
        if (end - off < 4) return null
        val b0 = b[off].toInt() and 0xFF
        val b1 = b[off + 1].toInt() and 0xFF
        val b2 = b[off + 2].toInt() and 0xFF
        val b3 = b[off + 3].toInt() and 0xFF
        if (b0 != 0xFF || (b1 and 0xE0) != 0xE0) return null
        val version = (b1 shr 3) and 3 // 3 = MPEG1, 2 = MPEG2, 0 = MPEG2.5
        val layerBits = (b1 shr 1) and 3 // 3 = I, 2 = II, 1 = III
        if (version == 1 || layerBits == 0) return null
        val layer = 4 - layerBits // 1, 2 or 3
        val brIdx = (b2 shr 4) and 0xF
        val srIdx = (b2 shr 2) and 3
        if (brIdx == 0 || brIdx == 15 || srIdx == 3) return null
        val pad = (b2 shr 1) and 1
        val bitrate = (if (version == 3) BR_V1 else BR_V2)[layer - 1][brIdx] * 1000
        val sr = SR.getValue(version)[srIdx]
        val channels = if (((b3 shr 6) and 3) == 3) 1 else 2
        val len = when (layer) {
            1 -> (12 * bitrate / sr + pad) * 4
            2 -> 144 * bitrate / sr + pad
            else -> (if (version == 3) 144 else 72) * bitrate / sr + pad
        }
        val samples = when (layer) {
            1 -> 384
            2 -> 1152
            else -> if (version == 3) 1152 else 576
        }
        return if (len < 21) null else FrameHeader(len, sr, channels, samples)
    }
}

object Adts {
    val SAMPLE_RATES = intArrayOf(96000, 88200, 64000, 48000, 44100, 32000, 24000, 22050, 16000, 12000, 11025, 8000, 7350)

    fun parse(b: ByteArray, off: Int, end: Int = b.size): FrameHeader? {
        if (end - off < 7) return null
        val b1 = b[off + 1].toInt() and 0xFF
        if ((b[off].toInt() and 0xFF) != 0xFF || (b1 and 0xF6) != 0xF0) return null
        val b2 = b[off + 2].toInt() and 0xFF
        val b3 = b[off + 3].toInt() and 0xFF
        val b4 = b[off + 4].toInt() and 0xFF
        val b5 = b[off + 5].toInt() and 0xFF
        val b6 = b[off + 6].toInt() and 0xFF
        val protectionAbsent = b1 and 1
        val objectType = ((b2 shr 6) and 3) + 1
        val srIdx = (b2 shr 2) and 0xF
        if (srIdx >= SAMPLE_RATES.size) return null
        val chan = ((b2 and 1) shl 2) or ((b3 shr 6) and 3)
        val len = ((b3 and 3) shl 11) or (b4 shl 3) or ((b5 shr 5) and 7)
        val headerLen = if (protectionAbsent == 1) 7 else 9
        if (len <= headerLen) return null
        val blocks = (b6 and 3) + 1
        return FrameHeader(len, SAMPLE_RATES[srIdx], if (chan == 0) 2 else chan, 1024 * blocks, headerLen, objectType, srIdx)
    }

    fun sampleRateIndex(rate: Int): Int = SAMPLE_RATES.indexOf(rate).let { if (it < 0) 4 else it }

    /** 7-byte ADTS header (MPEG-4, no CRC) for one raw AAC frame of [payloadLength] bytes. */
    fun header(payloadLength: Int, sampleRate: Int, channels: Int, objectType: Int = 2): ByteArray {
        val len = payloadLength + 7
        val sf = sampleRateIndex(sampleRate)
        return byteArrayOf(
            0xFF.toByte(),
            0xF1.toByte(),
            (((objectType - 1) shl 6) or (sf shl 2) or ((channels shr 2) and 1)).toByte(),
            (((channels and 3) shl 6) or ((len shr 11) and 3)).toByte(),
            ((len shr 3) and 0xFF).toByte(),
            (((len and 7) shl 5) or 0x1F).toByte(),
            0xFC.toByte(),
        )
    }

    /** AudioSpecificConfig (csd-0) for MediaCodec's AAC decoder. */
    fun audioSpecificConfig(objectType: Int, sampleRateIndex: Int, channels: Int): ByteArray {
        val v = (objectType shl 11) or (sampleRateIndex shl 7) or (channels shl 3)
        return byteArrayOf(((v shr 8) and 0xFF).toByte(), (v and 0xFF).toByte())
    }
}

/**
 * Accumulates stream bytes and returns complete frames. A candidate frame is accepted only when
 * the next frame also starts with a valid header (or the buffer ends exactly there), which avoids
 * locking onto false sync words inside audio data.
 */
class FrameSplitter(private val kind: FrameKind) {
    private var buf = ByteArray(64 * 1024)
    private var len = 0

    private fun header(off: Int): FrameHeader? = when (kind) {
        FrameKind.MP3 -> Mp3.parse(buf, off, len)
        FrameKind.ADTS -> Adts.parse(buf, off, len)
    }

    fun feed(data: ByteArray, off: Int = 0, count: Int = data.size): List<Frame> {
        if (len + count > buf.size) buf = buf.copyOf(maxOf(buf.size * 2, len + count))
        System.arraycopy(data, off, buf, len, count)
        len += count
        val out = ArrayList<Frame>()
        var pos = 0
        while (len - pos >= 10) {
            val h = header(pos)
            if (h == null) {
                pos++
                continue
            }
            if (pos + h.length > len) break // need more data
            val next = pos + h.length
            if (len - next >= 4 && header(next) == null) {
                pos++ // false sync
                continue
            }
            if (len - next < 4) break // wait until we can confirm the next header
            val bytes = buf.copyOfRange(pos, next)
            out += Frame(bytes, h.sampleRate, h.channels, h.samples, h.headerLength, h.objectType, h.sampleRateIndex)
            pos = next
        }
        if (pos > 0) {
            System.arraycopy(buf, pos, buf, 0, len - pos)
            len -= pos
        }
        if (len > 1024 * 1024) len = 0 // garbage; resync
        return out
    }

    fun reset() {
        len = 0
    }
}
