package io.github.kkrampis.clipharbor.download

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File
import java.nio.ByteBuffer

/** Joins a video-only MP4 and an M4A audio track into a single MP4, without re-encoding. */
object Muxer {
    fun merge(videoFile: File, audioFile: File, output: File) {
        val video = MediaExtractor().apply { setDataSource(videoFile.absolutePath) }
        val audio = MediaExtractor().apply { setDataSource(audioFile.absolutePath) }
        val muxer = MediaMuxer(output.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        try {
            val vTrack = findTrack(video, "video/")
            val aTrack = findTrack(audio, "audio/")
            video.selectTrack(vTrack)
            audio.selectTrack(aTrack)
            val vFormat = video.getTrackFormat(vTrack)
            val aFormat = audio.getTrackFormat(aTrack)
            val vOut = muxer.addTrack(vFormat)
            val aOut = muxer.addTrack(aFormat)
            muxer.start()
            copy(video, muxer, vOut, bufferSize(vFormat))
            copy(audio, muxer, aOut, bufferSize(aFormat))
            muxer.stop()
        } finally {
            runCatching { muxer.release() }
            video.release()
            audio.release()
        }
    }

    private fun findTrack(ex: MediaExtractor, prefix: String): Int {
        for (i in 0 until ex.trackCount) {
            val mime = ex.getTrackFormat(i).getString(MediaFormat.KEY_MIME) ?: continue
            if (mime.startsWith(prefix)) return i
        }
        error("No $prefix track found")
    }

    private fun bufferSize(format: MediaFormat): Int =
        if (format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
            maxOf(format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE), 256 * 1024)
        } else {
            4 * 1024 * 1024
        }

    private fun copy(ex: MediaExtractor, muxer: MediaMuxer, track: Int, size: Int) {
        val buffer = ByteBuffer.allocate(size)
        val info = MediaCodec.BufferInfo()
        while (true) {
            buffer.clear()
            val n = ex.readSampleData(buffer, 0)
            if (n < 0) break
            info.offset = 0
            info.size = n
            info.presentationTimeUs = ex.sampleTime
            info.flags = if (ex.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) {
                MediaCodec.BUFFER_FLAG_KEY_FRAME
            } else {
                0
            }
            muxer.writeSampleData(track, buffer, info)
            ex.advance()
        }
    }
}
