package com.lyriva.ninfinity.render

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import kotlin.math.max
import kotlin.math.min

/**
 * Lấy khung hình của video cover nền theo thời điểm bất kỳ (MediaMetadataRetriever).
 * Không thread-safe: mỗi nơi dùng (xem thử, xuất) tạo một bản riêng.
 */
class BgFrames(private val ctx: Context) {
    private var r: MediaMetadataRetriever? = null
    private var vw = 0
    private var vh = 0
    var durationSec = 0.0
        private set

    fun open(uri: String?): Boolean {
        close()
        if (uri == null) return false
        return try {
            val m = MediaMetadataRetriever()
            m.setDataSource(ctx, Uri.parse(uri))
            var w = m.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            var h = m.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            val rot = m.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            if (rot == 90 || rot == 270) {
                val t = w; w = h; h = t
            }
            vw = w
            vh = h
            durationSec = (m.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toDoubleOrNull() ?: 0.0) / 1000.0
            r = m
            w > 0 && h > 0
        } catch (e: Exception) {
            false
        }
    }

    /** Khung hình tại [sec], cạnh dài tối đa [maxSide] px (giữ tỉ lệ). Null nếu lỗi. */
    fun frameAt(sec: Double, maxSide: Int): Bitmap? {
        val m = r ?: return null
        if (vw <= 0 || vh <= 0) return null
        val t = if (durationSec > 0) min(max(0.0, sec), max(0.0, durationSec - 0.05)) else max(0.0, sec)
        val sc = min(1.0, maxSide.toDouble() / max(vw, vh))
        val dw = max(2, (vw * sc).toInt())
        val dh = max(2, (vh * sc).toInt())
        return try {
            m.getScaledFrameAtTime((t * 1_000_000).toLong(), MediaMetadataRetriever.OPTION_CLOSEST, dw, dh)
        } catch (e: Exception) {
            null
        }
    }

    fun close() {
        try { r?.release() } catch (_: Exception) {}
        r = null
    }
}
