package com.lyriva.ninfinity.core

import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Ghi PCM 16-bit xen kẽ các kênh thành file WAV. */
object WavWriter {
    fun write(out: OutputStream, pcm: ShortArray, sampleRate: Int, channels: Int) {
        val dataBytes = pcm.size * 2
        val h = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        h.put("RIFF".toByteArray()).putInt(36 + dataBytes)
        h.put("WAVE".toByteArray()).put("fmt ".toByteArray())
        h.putInt(16).putShort(1).putShort(channels.toShort())
        h.putInt(sampleRate).putInt(sampleRate * channels * 2)
        h.putShort((channels * 2).toShort()).putShort(16)
        h.put("data".toByteArray()).putInt(dataBytes)
        out.write(h.array())

        val chunk = 16384
        val buf = ByteBuffer.allocate(chunk * 2).order(ByteOrder.LITTLE_ENDIAN)
        var i = 0
        while (i < pcm.size) {
            buf.clear()
            val n = minOf(chunk, pcm.size - i)
            for (k in 0 until n) buf.putShort(pcm[i + k])
            out.write(buf.array(), 0, n * 2)
            i += n
        }
        out.flush()
    }
}
