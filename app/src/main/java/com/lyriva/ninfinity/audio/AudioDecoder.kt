package com.lyriva.ninfinity.audio

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.lyriva.ninfinity.export.ExportCancelled
import java.nio.ByteOrder
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/** Đỉnh biên độ theo thời gian: [perSec] giá trị mỗi giây, dùng để vẽ sóng nhạc. */
data class PeakData(val peaks: FloatArray, val perSec: Int, val duration: Double, val uri: String = "")

class Pcm(val samples: ShortArray, val sampleRate: Int, val channels: Int)

/** Giải mã theo luồng bằng MediaExtractor + MediaCodec nên không nạp cả file vào RAM. */
object AudioDecoder {
    private const val STALL_NS = 10_000_000_000L // 10 giây không có tiến triển thì dừng và báo lỗi

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

    /**
     * cb(shorts, số mẫu, sampleRate, channels, thời điểm bắt đầu buffer): trả false để dừng.
     * Vòng lặp nạp hết buffer đầu vào trống rồi rút hết đầu ra, chỉ chờ khi thật sự không có việc,
     * nên chạy nhanh hơn thời gian thực hàng chục lần.
     */
    private fun run(
        ctx: Context,
        uri: Uri,
        startSec: Double,
        cancelled: () -> Boolean = { false },
        progress: ((Float) -> Unit)? = null,
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
            val durUs = if (fmt.containsKey(MediaFormat.KEY_DURATION)) fmt.getLong(MediaFormat.KEY_DURATION) else 0L
            val info = MediaCodec.BufferInfo()
            var inDone = false
            var outDone = false
            var scratch = ShortArray(0)
            var lastProgress = System.nanoTime()
            var lastReport = 0L
            while (!outDone) {
                if (cancelled()) throw ExportCancelled()
                var progressed = false
                // 1) nạp hết các buffer đầu vào đang trống
                while (!inDone) {
                    val ii = codec.dequeueInputBuffer(0)
                    if (ii < 0) break
                    val ib = codec.getInputBuffer(ii)!!
                    val n = ex.readSampleData(ib, 0)
                    if (n < 0) {
                        codec.queueInputBuffer(ii, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        inDone = true
                    } else {
                        codec.queueInputBuffer(ii, 0, n, ex.sampleTime, 0)
                        ex.advance()
                    }
                    progressed = true
                }
                // 2) rút hết đầu ra; chỉ chờ ngắn ở lần đầu khi không nạp thêm được gì
                var first = true
                while (!outDone) {
                    val oi = codec.dequeueOutputBuffer(info, if (first && !progressed) 2000 else 0)
                    first = false
                    if (oi == MediaCodec.INFO_TRY_AGAIN_LATER) break
                    progressed = true
                    if (oi == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        val f = codec.outputFormat
                        sr = f.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        ch = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        continue
                    }
                    if (oi < 0) continue
                    if (info.size > 0) {
                        val ob = codec.getOutputBuffer(oi)!!
                        ob.position(info.offset)
                        ob.limit(info.offset + info.size)
                        val sb = ob.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                        val n = sb.remaining()
                        if (scratch.size < n) scratch = ShortArray(n)
                        sb.get(scratch, 0, n)
                        if (!cb(scratch, n, sr, ch, info.presentationTimeUs / 1_000_000.0)) outDone = true
                        if (progress != null && durUs > 0) {
                            val now = System.nanoTime()
                            if (now - lastReport > 100_000_000L) {
                                lastReport = now
                                progress((info.presentationTimeUs.toDouble() / durUs).toFloat().coerceIn(0f, 1f))
                            }
                        }
                    }
                    codec.releaseOutputBuffer(oi, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outDone = true
                }
                val now = System.nanoTime()
                if (progressed) lastProgress = now
                else if (now - lastProgress > STALL_NS) throw IllegalStateException("Bộ giải mã âm thanh không phản hồi")
            }
        } finally {
            try { codec.stop() } catch (_: Exception) {}
            codec.release()
            ex.release()
        }
    }

    /** Tính đỉnh biên độ cả bài (mặc định 50 giá trị mỗi giây). [onProgress] 0..1. */
    fun peaks(ctx: Context, uri: Uri, perSec: Int = 50, onProgress: (Float) -> Unit = {}): PeakData {
        var arr = FloatArray(perSec * 600)
        var len = 0
        var curMax = 0
        var cnt = 0
        var totalFrames = 0L
        var rate = 44100
        run(ctx, uri, 0.0, progress = onProgress) { s, n, sr, ch, _ ->
            rate = sr
            val win = max(1, sr / perSec) * ch // số mẫu (mọi kênh) trong một cửa sổ 20ms
            totalFrames += n / ch
            // dùng biến cục bộ trong vòng lặp nóng để tránh truy cập qua biến bắt (chậm)
            var cm = curMax
            var c = cnt
            var l = len
            var buf = arr
            var i = 0
            while (i < n) {
                val v = s[i].toInt()
                val a = if (v < 0) -v else v
                if (a > cm) cm = a
                i++
                if (++c >= win) {
                    if (l >= buf.size) buf = buf.copyOf(buf.size * 2)
                    buf[l++] = cm / 32768f
                    cm = 0
                    c = 0
                }
            }
            curMax = cm
            cnt = c
            len = l
            arr = buf
            true
        }
        if (cnt > 0) {
            if (len >= arr.size) arr = arr.copyOf(arr.size + 1)
            arr[len++] = curMax / 32768f
        }
        if (len <= 0) throw IllegalStateException("Không đọc được âm thanh")
        val duration = totalFrames.toDouble() / rate
        return PeakData(arr.copyOf(len), perSec, duration, uri.toString())
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
        run(ctx, uri, startSec, cancelled) { s, n, sr, ch, pts ->
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
