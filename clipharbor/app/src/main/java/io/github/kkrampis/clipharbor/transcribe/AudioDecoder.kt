package io.github.kkrampis.clipharbor.transcribe

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.nio.ByteOrder

/**
 * Decodes the first audio track of a media file to 16 kHz mono 16-bit samples,
 * which is what the speech recognizer expects. Works on M4A audio and on MP4 videos.
 */
object AudioDecoder {
    const val SAMPLE_RATE = 16_000

    suspend fun decode(
        context: Context,
        source: Uri,
        onProgress: suspend (Float) -> Unit,
        sink: (ShortArray, Int) -> Unit,
    ) {
        val extractor = MediaExtractor()
        extractor.setDataSource(context, source, null)
        var codec: MediaCodec? = null
        try {
            val track = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: error("This file has no audio track")
            extractor.selectTrack(track)
            val format = extractor.getTrackFormat(track)
            val durationUs = if (format.containsKey(MediaFormat.KEY_DURATION)) format.getLong(MediaFormat.KEY_DURATION) else 0L

            val c = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!)
            codec = c
            c.configure(format, null, null, 0)
            c.start()

            var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            var resampler = Resampler(format.getInteger(MediaFormat.KEY_SAMPLE_RATE), SAMPLE_RATE)
            var floatPcm = false
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false
            var lastReported = -1

            while (!outputDone) {
                currentCoroutineContext().ensureActive()
                if (!inputDone) {
                    val i = c.dequeueInputBuffer(10_000)
                    if (i >= 0) {
                        val buf = c.getInputBuffer(i)!!
                        val n = extractor.readSampleData(buf, 0)
                        if (n < 0) {
                            c.queueInputBuffer(i, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            c.queueInputBuffer(i, 0, n, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                val o = c.dequeueOutputBuffer(info, 10_000)
                if (o == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    val f = c.outputFormat
                    channels = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    resampler = Resampler(f.getInteger(MediaFormat.KEY_SAMPLE_RATE), SAMPLE_RATE)
                    floatPcm = f.containsKey(MediaFormat.KEY_PCM_ENCODING) &&
                        f.getInteger(MediaFormat.KEY_PCM_ENCODING) == AudioFormat.ENCODING_PCM_FLOAT
                } else if (o >= 0) {
                    if (info.size > 0) {
                        val out = c.getOutputBuffer(o)!!
                        out.position(info.offset)
                        out.limit(info.offset + info.size)
                        val mono: FloatArray
                        if (floatPcm) {
                            val fb = out.order(ByteOrder.nativeOrder()).asFloatBuffer()
                            val frames = fb.remaining() / channels
                            mono = FloatArray(frames)
                            for (f in 0 until frames) {
                                var sum = 0f
                                for (ch in 0 until channels) sum += fb.get(f * channels + ch)
                                mono[f] = sum / channels
                            }
                        } else {
                            val sb = out.order(ByteOrder.nativeOrder()).asShortBuffer()
                            val frames = sb.remaining() / channels
                            mono = FloatArray(frames)
                            for (f in 0 until frames) {
                                var sum = 0f
                                for (ch in 0 until channels) sum += sb.get(f * channels + ch)
                                mono[f] = sum / channels / 32768f
                            }
                        }
                        resampler.process(mono, sink)
                        if (durationUs > 0) {
                            val pct = (info.presentationTimeUs * 100 / durationUs).toInt()
                            if (pct != lastReported) {
                                lastReported = pct
                                onProgress((pct / 100f).coerceIn(0f, 1f))
                            }
                        }
                    }
                    c.releaseOutputBuffer(o, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                }
            }
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            extractor.release()
        }
    }
}

/** Linear-interpolation resampler that keeps its position across chunks. */
class Resampler(inRate: Int, outRate: Int) {
    private val step = inRate.toDouble() / outRate
    private var t = 0.0 // next output position, in input samples, relative to the next chunk
    private var prev = 0f

    fun process(input: FloatArray, sink: (ShortArray, Int) -> Unit) {
        val n = input.size
        if (n < 2) {
            if (n == 1) prev = input[0]
            return
        }
        val out = ShortArray(((n - t) / step).toInt() + 2)
        var k = 0
        while (t < n - 1 && k < out.size) {
            val i = kotlin.math.floor(t).toInt()
            val frac = (t - i).toFloat()
            val a = if (i < 0) prev else input[i]
            val b = input[i + 1]
            val v = a + (b - a) * frac
            out[k++] = (v * 32767f).coerceIn(-32768f, 32767f).toInt().toShort()
            t += step
        }
        t -= n
        prev = input[n - 1]
        if (k > 0) sink(out, k)
    }
}
