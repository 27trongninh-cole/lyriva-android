package com.lyriva.ninfinity.audio

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.lyriva.ninfinity.export.ExportCancelled
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/** Đỉnh biên độ theo thời gian: [perSec] giá trị mỗi giây, dùng để vẽ sóng nhạc. */
data class PeakData(val peaks: FloatArray, val perSec: Int, val duration: Double, val uri: String = "")

class Pcm(val samples: ShortArray, val sampleRate: Int, val channels: Int)

/** Giải mã theo luồng bằng MediaExtractor + MediaCodec nên không nạp cả file vào RAM. */
object AudioDecoder {

    private fun openAudio(ctx: Context, uri: Uri): Pair<MediaExtractor, MediaFormat> {
        val ex = MediaExtractor()
        try {
            ex.setDataSource(ctx, uri, null)
            for (i in 0 until ex.trackCount) {
                val f = ex.getTrackFormat(i)
                if (f.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) {
                    ex.selectTrack(i)
                    return ex to f
                }
            }
        } catch (e: Exception) {
            ex.release()
            throw e
        }
        ex.release()
        throw IllegalStateException("File này không có âm thanh")
    }

    /** cb(shorts, số mẫu, sampleRate, channels, thời điểm bắt đầu buffer): trả false để dừng. */
    private fun run(
        ctx: Context,
        uri: Uri,
        startSec: Double,
        cb: (ShortArray, Int, Int, Int, Double) -> Boolean
    ) {
        val (ex, fmt) = openAudio(ctx, uri)
        val mime = fmt.getString(MediaFormat.KEY_MIME)!!
        val codec = MediaCodec.createDecoderByType(mime)
        try {
            codec.configure(fmt, null, null, 0)
            codec.start()
            if (startSec > 0.5) {
                ex.seekTo(((startSec - 0.5) * 1_000_000).toLong(), MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            }
            var sr = fmt.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var ch = fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            val info = MediaCodec.BufferInfo()
            var inDone = false
            var outDone = false
            var scratch = ShortArray(0)
            while (!outDone) {
                if (!inDone) {
                    val ii = codec.dequeueInputBuffer(10_000)
                    if (ii >= 0) {
                        val ib = codec.getInputBuffer(ii)!!
                        val n = ex.readSampleData(ib, 0)
                        if (n < 0) {
                            codec.queueInputBuffer(ii, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inDone = true
                        } else {
                            codec.queueInputBuffer(ii, 0, n, ex.sampleTime, 0)
                            ex.advance()
                        }
                    }
                }
                val oi = codec.dequeueOutputBuffer(info, 10_000)
                if (oi == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    val f = codec.outputFormat
                    sr = f.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    ch = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                } else if (oi >= 0) {
                    if (info.size > 0) {
                        val ob = codec.getOutputBuffer(oi)!!
                        ob.position(info.offset)
                        ob.limit(info.offset + info.size)
                        val sb = ob.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                        val n = sb.remaining()
                        if (scratch.size < n) scratch = ShortArray(n)
                        sb.get(scratch, 0, n)
                        if (!cb(scratch, n, sr, ch, info.presentationTimeUs / 1_000_000.0)) outDone = true
                    }
                    codec.releaseOutputBuffer(oi, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outDone = true
                }
            }
        } finally {
            try { codec.stop() } catch (_: Exception) {}
            codec.release()
            ex.release()
        }
    }

    /** Tính đỉnh biên độ cả bài (mặc định 50 giá trị mỗi giây). */
    fun peaks(ctx: Context, uri: Uri, perSec: Int = 50): PeakData {
        var arr = FloatArray(perSec * 600)
        var maxIdx = -1
        run(ctx, uri, 0.0) { s, n, sr, ch, pts ->
            val frames = n / ch
            for (f in 0 until frames) {
                var m = 0
                val base = f * ch
                for (c in 0 until ch) {
                    val v = abs(s[base + c].toInt())
                    if (v > m) m = v
                }
                val idx = ((pts + f.toDouble() / sr) * perSec).toInt()
                if (idx < 0) continue
                if (idx >= arr.size) arr = arr.copyOf(max(arr.size * 2, idx + 1))
                val fv = m / 32768f
                if (fv > arr[idx]) arr[idx] = fv
                if (idx > maxIdx) maxIdx = idx
            }
            true
        }
        val len = maxIdx + 1
        if (len <= 0) throw IllegalStateException("Không đọc được âm thanh")
        return PeakData(arr.copyOf(len), perSec, len.toDouble() / perSec, uri.toString())
    }

    /** Giải mã đoạn [startSec, endSec) thành PCM 16-bit xen kẽ các kênh. */
    fun decodeRange(
        ctx: Context,
        uri: Uri,
        startSec: Double,
        endSec: Double,
        cancelled: () -> Boolean = { false }
    ): Pcm {
        var out = ShortArray(1 shl 20)
        var len = 0
        var srOut = 44100
        var chOut = 2
        run(ctx, uri, startSec) { s, n, sr, ch, pts ->
            if (cancelled()) throw ExportCancelled()
            srOut = sr
            chOut = ch
            val frames = n / ch
            val f0 = max(0, ceil((startSec - pts) * sr).toInt())
            val f1 = min(frames, floor((endSec - pts) * sr).toInt())
            if (f1 > f0) {
                val cnt = (f1 - f0) * ch
                if (len + cnt > out.size) out = out.copyOf(max(out.size * 2, len + cnt))
                System.arraycopy(s, f0 * ch, out, len, cnt)
                len += cnt
            }
            pts + frames.toDouble() / sr < endSec
        }
        if (len == 0) throw IllegalStateException("Đoạn đã chọn không có âm thanh")
        return Pcm(out.copyOf(len), srOut, chOut)
    }

    /** Thời lượng (giây) theo metadata, hoặc null nếu không đọc được. */
    fun durationSec(ctx: Context, uri: Uri): Double? {
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(ctx, uri)
            r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toDoubleOrNull()?.div(1000.0)
        } catch (e: Exception) {
            null
        } finally {
            try { r.release() } catch (_: Exception) {}
        }
    }

    /** Tỉ lệ rộng/cao của video (đã tính xoay), hoặc null nếu không đọc được. */
    fun videoRatio(ctx: Context, uri: Uri): Float? {
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(ctx, uri)
            var w = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            var h = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            val rot = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            if (rot == 90 || rot == 270) {
                val t = w
                w = h
                h = t
            }
            if (w > 0 && h > 0) w.toFloat() / h else null
        } catch (e: Exception) {
            null
        } finally {
            try { r.release() } catch (_: Exception) {}
        }
    }
}
