package io.github.kkrampis.radiorecorder.engine

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import io.github.kkrampis.radiorecorder.core.Adts
import io.github.kkrampis.radiorecorder.core.Frame
import io.github.kkrampis.radiorecorder.core.FrameKind
import io.github.kkrampis.radiorecorder.core.FrameSplitter
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Writes one recording file. */
interface SegmentWriter {
    val file: File
    val formatLabel: String
    fun write(data: ByteArray, len: Int)
    /** Finishes the current file. The writer can't be used afterwards. */
    fun close()
}

/** "Original" quality: the broadcast bytes, untouched. */
class RawWriter(override val file: File, ext: String) : SegmentWriter {
    private val out = BufferedOutputStream(FileOutputStream(file), 64 * 1024)
    override val formatLabel = "original/$ext"
    override fun write(data: ByteArray, len: Int) = out.write(data, 0, len)
    override fun close() = out.close()
}

/**
 * Re-encodes an MP3 or AAC stream to AAC (ADTS .aac files) with MediaCodec.
 * The decoder lives across files so there's no gap when a new file starts; each file gets its
 * own encoder, which is flushed when the file ends.
 */
class AacTranscoder(private val kind: FrameKind, private val bitrateKbps: Int, private val mono: Boolean) {
    private val splitter = FrameSplitter(kind)
    private var decoder: MediaCodec? = null
    private var decPts = 0L
    private var pcmRate = 0
    private var pcmChannels = 0

    private var encoder: MediaCodec? = null
    private var encRate = 0
    private var encChannels = 0
    private var encSamples = 0L
    private var out: OutputStream? = null
    private val info = MediaCodec.BufferInfo()

    fun startFile(file: File) {
        out = BufferedOutputStream(FileOutputStream(file), 64 * 1024)
    }

    fun feed(data: ByteArray, len: Int) {
        for (f in splitter.feed(data, 0, len)) queueFrame(f)
    }

    fun endFile() {
        finishEncoder()
        out?.close()
        out = null
    }

    fun release() {
        try {
            endFile()
        } catch (_: Exception) {
        }
        decoder?.let {
            try {
                it.stop()
            } catch (_: Exception) {
            }
            it.release()
        }
        decoder = null
    }

    // --- decoding --------------------------------------------------------------------------

    private fun createDecoder(f: Frame): MediaCodec {
        val fmt = when (kind) {
            FrameKind.MP3 -> MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_MPEG, f.sampleRate, f.channels)
            FrameKind.ADTS -> MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, f.sampleRate, f.channels).apply {
                setByteBuffer("csd-0", ByteBuffer.wrap(Adts.audioSpecificConfig(f.objectType, f.sampleRateIndex, f.channels)))
            }
        }
        fmt.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16 * 1024)
        val mime = fmt.getString(MediaFormat.KEY_MIME)!!
        return MediaCodec.createDecoderByType(mime).apply {
            configure(fmt, null, null, 0)
            start()
        }.also {
            pcmRate = f.sampleRate
            pcmChannels = f.channels
        }
    }

    private fun queueFrame(f: Frame) {
        val dec = decoder ?: createDecoder(f).also { decoder = it }
        val offset = if (kind == FrameKind.ADTS) f.payloadOffset else 0
        val size = f.data.size - offset
        var idx: Int
        var spins = 0
        while (true) {
            idx = dec.dequeueInputBuffer(10_000)
            if (idx >= 0) break
            drainDecoder()
            if (++spins > 200) throw StreamException("The audio decoder stopped accepting data")
        }
        val buf = dec.getInputBuffer(idx)!!
        buf.clear()
        buf.put(f.data, offset, minOf(size, buf.remaining()))
        dec.queueInputBuffer(idx, 0, minOf(size, buf.capacity()), decPts, 0)
        decPts += f.samples * 1_000_000L / f.sampleRate
        drainDecoder()
    }

    private fun drainDecoder() {
        val dec = decoder ?: return
        while (true) {
            val o = dec.dequeueOutputBuffer(info, 0)
            when {
                o == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    val f = dec.outputFormat
                    pcmRate = f.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    pcmChannels = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                }
                o >= 0 -> {
                    if (info.size > 0) {
                        val b = dec.getOutputBuffer(o)!!
                        b.position(info.offset)
                        b.limit(info.offset + info.size)
                        val pcm = ByteArray(info.size)
                        b.get(pcm)
                        encodePcm(pcm)
                    }
                    dec.releaseOutputBuffer(o, false)
                }
                else -> return // INFO_TRY_AGAIN_LATER (or deprecated buffers-changed)
            }
        }
    }

    // --- encoding --------------------------------------------------------------------------

    private fun encodePcm(pcmIn: ByteArray) {
        if (out == null || pcmRate == 0) return
        val pcm = if (mono && pcmChannels == 2) downmix(pcmIn) else pcmIn
        val channels = if (mono) 1 else pcmChannels
        val enc = encoder?.takeIf { encRate == pcmRate && encChannels == channels }
            ?: run {
                finishEncoder()
                createEncoder(pcmRate, channels).also { encoder = it }
            }
        var off = 0
        var spins = 0
        while (off < pcm.size) {
            val idx = enc.dequeueInputBuffer(10_000)
            if (idx < 0) {
                drainEncoder(false)
                if (++spins > 300) throw StreamException("The AAC encoder stopped accepting data")
                continue
            }
            spins = 0
            val buf = enc.getInputBuffer(idx)!!
            buf.clear()
            val n = minOf(buf.remaining(), pcm.size - off)
            buf.put(pcm, off, n)
            val pts = encSamples * 1_000_000L / encRate
            enc.queueInputBuffer(idx, 0, n, pts, 0)
            encSamples += n / (2 * channels)
            off += n
            drainEncoder(false)
        }
    }

    private fun createEncoder(rate: Int, channels: Int): MediaCodec {
        val fmt = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, rate, channels).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, bitrateKbps * 1000)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 64 * 1024)
        }
        encRate = rate
        encChannels = channels
        encSamples = 0
        return MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC).apply {
            configure(fmt, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            start()
        }
    }

    private fun drainEncoder(untilEos: Boolean) {
        val enc = encoder ?: return
        val deadline = System.currentTimeMillis() + 3000
        while (true) {
            val o = enc.dequeueOutputBuffer(info, if (untilEos) 10_000 else 0)
            if (o >= 0) {
                val isConfig = info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0
                if (!isConfig && info.size > 0) {
                    val b = enc.getOutputBuffer(o)!!
                    b.position(info.offset)
                    b.limit(info.offset + info.size)
                    val aac = ByteArray(info.size)
                    b.get(aac)
                    out?.write(Adts.header(aac.size, encRate, encChannels))
                    out?.write(aac)
                }
                val eos = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                enc.releaseOutputBuffer(o, false)
                if (eos) return
            } else if (o == MediaCodec.INFO_TRY_AGAIN_LATER) {
                if (!untilEos || System.currentTimeMillis() > deadline) return
            }
        }
    }

    private fun finishEncoder() {
        val enc = encoder ?: return
        try {
            val idx = enc.dequeueInputBuffer(100_000)
            if (idx >= 0) {
                enc.queueInputBuffer(idx, 0, 0, encSamples * 1_000_000L / maxOf(1, encRate), MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                drainEncoder(true)
            }
        } catch (_: Exception) {
        } finally {
            try {
                enc.stop()
            } catch (_: Exception) {
            }
            enc.release()
            encoder = null
        }
    }

    private fun downmix(stereo: ByteArray): ByteArray {
        val src = ByteBuffer.wrap(stereo).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        val frames = src.remaining() / 2
        val dst = ByteBuffer.allocate(frames * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until frames) {
            dst.putShort(((src.get(2 * i).toInt() + src.get(2 * i + 1).toInt()) / 2).toShort())
        }
        return dst.array()
    }
}

/** A file written through [AacTranscoder]. */
class AacWriter(override val file: File, private val transcoder: AacTranscoder, bitrate: Int, mono: Boolean) : SegmentWriter {
    override val formatLabel = "aac/${bitrate}k" + if (mono) " mono" else ""

    init {
        transcoder.startFile(file)
    }

    override fun write(data: ByteArray, len: Int) = transcoder.feed(data, len)
    override fun close() = transcoder.endFile()
}
