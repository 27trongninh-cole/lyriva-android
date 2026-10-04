package com.lyriva.ninfinity.render

import android.content.Context
import android.graphics.Bitmap
import com.lyriva.ninfinity.data.Project

/** Lấy khung hình đã chọn trong video nền để lồng vào nền thumbnail. */
object ThumbBg {
    fun load(ctx: Context, p: Project): Bitmap? {
        val uri = p.bgUri
        if (!p.thumbBgOn || uri == null) return null
        val bf = BgFrames(ctx)
        return try {
            if (bf.open(uri)) bf.frameAt(p.thumbBgTime, 1280) else null
        } finally {
            bf.close()
        }
    }
}
