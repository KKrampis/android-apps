package io.github.kkrampis.clipharbor.yt

import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.AudioTrackType
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.Stream
import org.schabi.newpipe.extractor.stream.VideoStream

enum class QualityMode {
    /** A single file with picture and sound (YouTube only offers these at low resolutions). */
    MUXED,

    /** Separate picture and sound streams that are played together, and merged into one MP4 on download. */
    MERGE,

    /** Sound only, saved as M4A. */
    AUDIO,
}

data class QualityOption(
    val mode: QualityMode,
    val label: String,
    val height: Int,
    val video: VideoStream?,
    val audio: AudioStream?,
) {
    val videoItag: Int get() = video?.itag ?: -1
    val audioItag: Int get() = audio?.itag ?: -1
    val isAudioOnly: Boolean get() = mode == QualityMode.AUDIO
}

object Quality {
    private fun Stream.isDirect() = isUrl && deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP

    private fun VideoStream.heightPx(): Int = resolution.takeWhile { it.isDigit() }.toIntOrNull() ?: 0

    private fun AudioStream.isOriginalTrack() =
        audioTrackType == null || audioTrackType == AudioTrackType.ORIGINAL

    fun bestAudio(audio: List<AudioStream>): AudioStream? =
        audio.filter { it.isDirect() && it.format == MediaFormat.M4A }
            .sortedWith(compareBy<AudioStream>({ it.isOriginalTrack() }, { it.averageBitrate }))
            .lastOrNull()

    /** All choices offered to the user, best video first, audio-only last. */
    fun options(d: StreamDetails): List<QualityOption> {
        val audio = bestAudio(d.audio)
        val muxed = d.muxed
            .filter { it.isDirect() && it.format == MediaFormat.MPEG_4 }
            .distinctBy { it.heightPx() }
            .map { QualityOption(QualityMode.MUXED, "${it.resolution} · single file", it.heightPx(), it, null) }

        val maxMuxed = muxed.maxOfOrNull { it.height } ?: 0
        val merged = if (audio == null) {
            emptyList()
        } else {
            d.videoOnly
                .filter {
                    it.isDirect() && it.format == MediaFormat.MPEG_4 &&
                        (it.codec ?: "").startsWith("avc1") && it.heightPx() > maxMuxed
                }
                .sortedByDescending { it.bitrate }
                .distinctBy { it.heightPx() }
                .map { QualityOption(QualityMode.MERGE, it.resolution, it.heightPx(), it, audio) }
        }

        val audioOnly = audio?.let {
            val kbps = if (it.averageBitrate > 0) " · ${it.averageBitrate} kbps" else ""
            listOf(QualityOption(QualityMode.AUDIO, "Audio only$kbps", 0, null, it))
        } ?: emptyList()

        return (merged + muxed).sortedByDescending { it.height } + audioOnly
    }

    /** What to play first: the best picture up to 720p, which streams well on most connections. */
    fun defaultForPlayback(options: List<QualityOption>): QualityOption? =
        options.filter { !it.isAudioOnly && it.height <= 720 }.maxByOrNull { it.height }
            ?: options.firstOrNull()

    /** Finds the option a queued download asked for, in a freshly fetched list of streams. */
    fun match(options: List<QualityOption>, mode: String, videoItag: Int, height: Int): QualityOption? {
        val sameMode = options.filter { it.mode.name == mode }
        return sameMode.firstOrNull { it.videoItag == videoItag && videoItag != -1 }
            ?: sameMode.firstOrNull { it.height == height }
            ?: sameMode.filter { it.height <= height }.maxByOrNull { it.height }
            ?: sameMode.firstOrNull()
    }
}
