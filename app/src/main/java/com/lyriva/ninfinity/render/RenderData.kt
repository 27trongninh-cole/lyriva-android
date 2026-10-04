package com.lyriva.ninfinity.render

import com.lyriva.ninfinity.core.Hide
import com.lyriva.ninfinity.core.LrcParser
import com.lyriva.ninfinity.core.LrcWord
import com.lyriva.ninfinity.core.SingerPalette
import com.lyriva.ninfinity.core.TextUtils
import com.lyriva.ninfinity.data.Aspect
import com.lyriva.ninfinity.data.BgFit
import com.lyriva.ninfinity.data.Lang
import com.lyriva.ninfinity.data.Project

/** Một câu lời: [s] lời chính, [o] lời gốc (CJK), [v] Vietsub. */
class Singer(val name: String, val color: Int)

class LyricItem(
    val t: Double, val s: String, val w: List<LrcWord>?, val o: String, val v: String,
    val singers: List<Singer> = emptyList()
)

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
    val thumbDur: Double,
    val thumbBgOn: Boolean,
    val thumbBgDim: Int
) {
    val width: Int get() = if (vertical) 1080 else 1280
    val height: Int get() = if (vertical) 1920 else 720

    companion object {
        val EMPTY = RenderData(
            emptyList(), "", "", "", Lang.EN, true, true, false, true, 100, 0, 0, 70, 0.1, false, 0.02, false, 60
        )

        fun from(p: Project): RenderData {
            val parsed = LrcParser.parse(p.lrcText).items
            val cjk = p.language.fontName != null
            fun clean(s: String) = s.split(Regex("\\r?\\n")).map { it.trim() }.filter { it.isNotEmpty() }
            val lc = Hide.lines(clean(p.originalLyrics))
            val vc = Hide.lines(clean(p.vietsubText))
            // người hát: dòng trống = như câu trước, "-" = không hiện nhãn
            val names = p.singersText.split(Regex("\\r?\\n")).map { it.trim() }
            val groups = SingerPalette.parse(p.palettesText)
            val auto = LinkedHashMap<String, Int>()
            fun colorOf(n: String): Int {
                val rgb = SingerPalette.resolve(n, groups, p.activeGroup)
                    ?: SingerPalette.AUTO[auto.getOrPut(SingerPalette.norm(n)) { auto.size } % SingerPalette.AUTO.size]
                return (0xFF shl 24) or rgb
            }
            var curNames: List<String> = emptyList()
            val items = parsed.mapIndexed { k, x ->
                val nm = names.getOrNull(k) ?: ""
                if (nm == "-") curNames = emptyList() else if (nm.isNotEmpty()) curNames = SingerPalette.split(nm)
                LyricItem(
                    x.t, x.s, x.w,
                    if (cjk) lc.getOrElse(k) { "" } else "",
                    if (p.vietsubOn) vc.getOrElse(k) { "" } else "",
                    if (p.singerOn) curNames.map { Singer(it, colorOf(it)) } else emptyList()
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
                p.thumbDuration.coerceIn(0.02, 0.5),
                p.thumbBgOn && p.bgUri != null,
                p.thumbBgDim.coerceIn(0, 95)
            )
        }
    }
}
