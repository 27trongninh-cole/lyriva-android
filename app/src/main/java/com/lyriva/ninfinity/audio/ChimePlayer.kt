package com.lyriva.ninfinity.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack

/** Phát tiếng chuông của hook trong lúc xem thử (trộn cùng nhạc do hệ thống tự làm). */
class ChimePlayer {
    private var track: AudioTrack? = null

    /** Phát [samples] (mono 16-bit) bắt đầu từ giây [fromSec]. */
    fun play(samples: ShortArray, sampleRate: Int, fromSec: Double) {
        stop()
        val start = (fromSec * sampleRate).toInt()
        if (samples.isEmpty() || start >= samples.size) return
        try {
            val t = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(samples.size * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()
            t.write(samples, 0, samples.size)
            t.setPlaybackHeadPosition(maxOf(0, start))
            t.play()
            track = t
        } catch (_: Exception) {
            track = null
        }
    }

    fun stop() {
        val t = track ?: return
        track = null
        try { t.stop() } catch (_: Exception) {}
        try { t.release() } catch (_: Exception) {}
    }
}
