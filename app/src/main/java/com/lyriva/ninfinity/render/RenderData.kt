package com.lyriva.ninfinity.render

import com.lyriva.ninfinity.core.Hide
import com.lyriva.ninfinity.core.LrcParser
import com.lyriva.ninfinity.core.LrcWord
import com.lyriva.ninfinity.core.TextUtils
import com.lyriva.ninfinity.data.Aspect
import com.lyriva.ninfinity.data.BgFit
import com.lyriva.ninfinity.data.Lang
import com.lyriva.ninfinity.data.Project

/** Một câu lời: [s] lời chính, [o] lời gốc (CJK), [v] Vietsub. */
class LyricItem(val t: Double, val s: String, val w: List<LrcWord>?, val o: String, val v: String)

/** Ảnh chụp mọi thứ cần để vẽ một khung hình. Dựng lại mỗi khi dự án đổi. */
class RenderData(
    val items: List<LyricItem>,
    val title: String,
    val artist: String,
    val credit: String,
    val lang: Lang,
    val vertical: Boolean,
    val romanOn: Boolean,
    val bgOn: Boolean,
    val bgFitCover: Boolean,
    val bgZoom: Int,
    val bgPanX: Int,
    val bgPanY: Int,
    val bgDim: Int,
    val leadSec: Double,
    val thumbOn: Boolean,
    val thumbDur: Double
) {
    val width: Int get() = if (vertical) 1080 else 1280
    val height: Int get() = if (vertical) 1920 else 720

    companion object {
        val EMPTY = RenderData(
            emptyList(), "", "", "", Lang.EN, true, true, false, true, 100, 0, 0, 70, 0.1, false, 0.02
        )

        fun from(p: Project): RenderData {
            val parsed = LrcParser.parse(p.lrcText).items
            val cjk = p.language.fontName != null
            fun clean(s: String) = s.split(Regex("\\r?\\n")).map { it.trim() }.filter { it.isNotEmpty() }
            val lc = Hide.lines(clean(p.originalLyrics))
            val vc = Hide.lines(clean(p.vietsubText))
            val items = parsed.mapIndexed { k, x ->
                LyricItem(
                    x.t, x.s, x.w,
                    if (cjk) lc.getOrElse(k) { "" } else "",
                    if (p.vietsubOn) vc.getOrElse(k) { "" } else ""
                )
            }
            return RenderData(
                items,
                p.title.trim().uppercase(),
                TextUtils.artistLine(p.artist),
                p.credit.trim(),
                p.language,
                p.aspect == Aspect.V,
                p.romanOn,
                p.bgEnabled && p.bgUri != null,
                p.bgFit == BgFit.COVER,
                p.bgZoom, p.bgPanX, p.bgPanY, p.bgDim,
                p.leadMs.coerceIn(0, 600) / 1000.0,
                p.thumbEnabled,
                p.thumbDuration.coerceIn(0.02, 0.5)
            )
        }
    }
}
