package com.lyriva.ninfinity.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import com.lyriva.ninfinity.core.Hook
import com.lyriva.ninfinity.core.HookSpec
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Vẽ một khung hình lyrics lên android.graphics.Canvas. Dùng chung cho xem thử, xuất MP4,
 * ảnh bìa và lưới, nên hình luôn giống nhau. Toàn bộ tọa độ theo kích thước thật của video
 * (1080×1920 hoặc 1280×720), khi xem thử thì co canvas lại cho vừa màn hình.
 */
class LyricsRenderer(val fonts: Fonts, private val logoSrc: Bitmap) {

    class Lay(val z: Float, val ls: List<String>) {
        var mw: Float = 0f
    }

    private class Cache(var key: String, var lay: Lay) {
        var vkey: String = ""
        var vlay: Lay? = null
    }

    var data: RenderData = RenderData.EMPTY
        set(v) {
            field = v
            ver++
            cache.clear()
        }

    /** Khung hình lấy từ video nền để lồng vào thumbnail (do nơi dùng nạp). */
    var thumbBg: Bitmap? = null

    /** Video đang dừng ở đầu: hiện lời đủ sáng thay vì fade-in. */
    var paused: Boolean = false

    internal var ver = 0
    private val cache = HashMap<LyricItem, Cache>()
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bmpPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private lateinit var cv: Canvas

    /** Hệ số hiện của lời (0 khi hook đang chiếm màn hình, rồi hiện dần lại). */
    private var gate = 1f
    private var logoTinted: Bitmap? = null
    private var logoKey = 0
    val thumb = ThumbnailRenderer(this)

    internal val acc: Int get() = (0xFF shl 24) or data.lang.colorRgb

    companion object {
        const val BG = 0xFF111111.toInt()
        const val INK = 0xFFF5F2EA.toInt()
        const val MUTE = 0xFF858585.toInt()
        private const val VSUB = 0xFFE8DFC8.toInt()
        private const val MW = 500
        private const val LH = 1.3f
        private const val TR = 0.35f
        private val CJR = Regex("[\\u2e80-\\u9fff\\uac00-\\ud7af]")
        private val TK = Regex("[\\u2e80-\\u9fff\\uac00-\\ud7af\\uff00-\\uffef]|[^\\s\\u2e80-\\u9fff\\uac00-\\ud7af\\uff00-\\uffef]+|\\s+")
        private fun cl(x: Float) = max(0f, min(1f, x))
        private fun eo(x: Float) = 1f - (1f - cl(x)).pow(3)
        fun argb(rgb: Int, a: Float): Int = (((a.coerceIn(0f, 1f) * 255f).roundToInt()) shl 24) or (rgb and 0xFFFFFF)
    }

    // ---------- tiện ích chữ ----------
    internal fun face(weight: Int, main: Boolean): Typeface =
        if (main && data.lang.fontName != null) fonts.cjk(data.lang, weight) else fonts.latin(weight)

    internal fun disp(x: LyricItem): String = if (x.o.isNotEmpty()) x.o else x.s
    internal fun rom(x: LyricItem): String = if (x.o.isNotEmpty() && data.romanOn) x.s else ""
    internal fun mainOf(x: LyricItem): Boolean = x.o.isNotEmpty()
    internal fun hf(s: String): Boolean = data.lang.fontName != null && CJR.containsMatchIn(s)
    internal fun tokens(s: String): List<String> = TK.findAll(s).map { it.value }.toList()

    private fun setFont(z: Float, w: Int, main: Boolean, ls: Float) {
        p.typeface = face(w, main)
        p.textSize = z
        p.letterSpacing = if (z > 0f) ls / z else 0f
    }

    /** Vừa một dòng: giảm cỡ nếu quá rộng. Để lại font đã đặt trong paint. */
    private fun fit(s: String, z: Float, mw: Float, w: Int, ls: Float, main: Boolean): Float {
        setFont(z, w, main, ls)
        val k = p.measureText(s)
        var zz = z
        if (k > mw && k > 0f) zz *= mw / k
        setFont(zz, w, main, ls)
        return zz
    }

    private fun txt(s: String, x: Float, y: Float, color: Int, alpha: Float = 1f) {
        p.style = Paint.Style.FILL
        p.color = color
        p.alpha = (alpha.coerceIn(0f, 1f) * 255f).roundToInt()
        cv.drawText(s, x, y, p)
    }

    private fun rect(l: Float, t: Float, r: Float, b: Float, color: Int, alpha: Float = 1f) {
        p.style = Paint.Style.FILL
        p.color = color
        p.alpha = (alpha.coerceIn(0f, 1f) * 255f).roundToInt()
        cv.drawRect(l, t, r, b, p)
    }

    // ---------- bố cục câu ----------
    /** Chia câu (Latinh theo từ, CJK theo ký tự) tối đa 2 dòng, giảm cỡ đến khi vừa vùng an toàn. */
    internal fun layout(s: String, z0: Float, mw: Float, main: Boolean): Lay {
        val tk = tokens(s)
        if (tk.isEmpty()) return Lay(z0, emptyList())
        var z = z0
        for (k in 0 until 30) {
            setFont(z, MW, main, 0f)
            if (p.measureText(s) <= mw) return Lay(z, listOf(s))
            var bestW = Float.MAX_VALUE
            var bestLs: List<String>? = null
            for (j in 1 until tk.size) {
                val a = tk.subList(0, j).joinToString("").trim()
                val b = tk.subList(j, tk.size).joinToString("").trim()
                if (a.isEmpty() || b.isEmpty()) continue
                val q = max(p.measureText(a), p.measureText(b))
                if (bestLs == null || q < bestW) {
                    bestW = q
                    bestLs = listOf(a, b)
                }
            }
            if (bestLs != null && bestW <= mw) return Lay(z, bestLs)
            z *= 0.94f
        }
        return Lay(z, listOf(s))
    }

    private var lzKey = ""
    private var lzVal = 0f

    /** Cỡ chữ cố định cho cả bài: theo câu dài nhất. */
    private fun lz(z: Float, mw: Float): Float {
        val k = "$ver|${data.items.size}|$z|$mw|${fonts.epoch}"
        if (k != lzKey) {
            lzKey = k
            var v = z
            for (x in data.items) v = min(v, layout(disp(x), z, mw, mainOf(x)).z)
            lzVal = v
        }
        return lzVal
    }

    private fun lay(x: LyricItem, z: Float, mw: Float): Lay {
        val k = "$ver|$z|$mw|${fonts.epoch}"
        val c = cache[x]
        if (c != null && c.key == k) return c.lay
        val l = layout(disp(x), z, mw, mainOf(x))
        l.mw = mw
        cache[x] = Cache(k, l)
        return l
    }

    /** Vietsub: tối đa 2 dòng, cỡ ~50% câu chính. */
    private fun vl(x: LyricItem, L: Lay): Lay? {
        if (x.v.isEmpty()) return null
        val c = cache.getOrPut(x) { Cache("", L) }
        val k = "$ver|${L.z}|${L.mw}|${x.v}|${fonts.epoch}"
        if (c.vkey != k) {
            c.vkey = k
            c.vlay = layout(x.v, L.z * 0.5f, L.mw, false)
        }
        return c.vlay
    }

    private fun vh(x: LyricItem, L: Lay): Float {
        val v = vl(x, L) ?: return 0f
        return v.ls.size * v.z * 1.25f + v.z * 0.55f
    }

    private fun rz(L: Lay) = L.z * 0.46f
    private fun bm(x: LyricItem, L: Lay) = L.ls.size * L.z * LH + (if (rom(x).isNotEmpty()) rz(L) * 1.9f else 0f)
    /** Nhãn tên người hát nằm phía trên câu: cỡ chữ và chiều cao khối nhãn. */
    private fun lblZ(L: Lay) = L.z * 0.36f
    private fun sh(x: LyricItem, L: Lay) = if (x.singers.isNotEmpty()) lblZ(L) * 1.9f else 0f
    private fun bh(x: LyricItem, L: Lay) = sh(x, L) + bm(x, L) + vh(x, L)

    private fun wd(x: LyricItem, t: Double): Double {
        var d = 0.0
        for (w in x.w ?: return 0.0) {
            if (t >= w.t) d = w.i + ((t - w.t) / max(0.05, w.e - w.t)).coerceIn(0.0, 1.0) * w.l
        }
        return d
    }

    // ---------- câu đang hát / câu mờ ----------
    private fun act(x: LyricItem, L: Lay, by: Float, al0: Float, fr: Float, mx: Float, tx: Float, u: Float, mw: Float) {
        val al = al0 * gate
        if (disp(x).isEmpty() || al <= 0.01f) return
        val lh = L.z * LH
        val r = rom(x)
        val main = mainOf(x)
        rect(mx, by, mx + u * 0.006f, by + bh(x, L) + L.z * 0.1f, acc, al)
        val b0 = by + sh(x, L)
        val sg = x.singers
        if (sg.isNotEmpty()) {
            var lz = lblZ(L)
            val names = sg.map { it.name.uppercase() }
            val sep = "  •  "
            setFont(lz, 600, false, lz * 0.14f)
            val total = names.sumOf { p.measureText(it).toDouble() }.toFloat() + p.measureText(sep) * (names.size - 1)
            if (total > mw && total > 0f) lz *= mw / total
            var cx = tx
            names.forEachIndexed { i, nm ->
                if (i > 0) {
                    setFont(lz, 600, false, lz * 0.14f)
                    txt(sep, cx, by + lz, MUTE, al)
                    cx += p.measureText(sep)
                }
                setFont(lz, 600, hf(nm), lz * 0.14f)
                txt(nm, cx, by + lz, sg[i].color, al)
                cx += p.measureText(nm)
            }
        }
        var rem = cl(fr) * L.ls.sumOf { it.length }.toFloat()
        L.ls.forEachIndexed { q, ln ->
            val y = b0 + L.z + q * lh
            setFont(L.z, MW, main, 0f)
            val w = p.measureText(ln)
            val f = cl(rem / max(1, ln.length))
            rem -= ln.length
            txt(ln, tx, y, INK, al)
            if (f > 0f) {
                cv.save()
                cv.clipRect(tx, y - L.z * 1.2f, tx + w * f, y + L.z * 0.4f)
                txt(ln, tx, y, acc, al)
                cv.restore()
            }
        }
        if (r.isNotEmpty()) {
            val z = fit(r, rz(L), mw, 400, 0f, false)
            txt(r, tx, b0 + L.ls.size * lh + z * 0.9f, MUTE, al)
        }
        val vv = vl(x, L)
        if (vv != null) {
            setFont(vv.z, 500, false, 0f)
            val y0 = b0 + bm(x, L) + vv.z * 0.55f + vv.z * 0.9f
            vv.ls.forEachIndexed { q, ln -> txt(ln, tx, y0 + q * vv.z * 1.25f, VSUB, al) }
        }
    }

    private fun gry(x: LyricItem?, z: Float, y: Float, al0: Float, mw: Float, tx: Float) {
        val al = al0 * gate
        if (x == null || disp(x).isEmpty() || al <= 0.01f) return
        fit(disp(x), z, mw, 400, 0f, mainOf(x))
        txt(disp(x), tx, y, MUTE, al)
    }

    // ---------- hook đầu video ----------
    private class HookLay(val key: String, val z: Float, val xs: FloatArray, val ys: FloatArray, val ws: FloatArray, val main: Boolean)

    private var hookLay: HookLay? = null

    private fun hookLayout(h: HookSpec, wF: Float, hF: Float, u: Float, mx: Float, mw: Float, vert: Boolean): HookLay {
        val key = "$ver|$wF|$hF|${fonts.epoch}"
        hookLay?.let { if (it.key == key) return it }
        val n = h.n
        val main = data.lang.fontName != null && h.words.any { CJR.containsMatchIn(it) }
        var z = u * 0.095f
        val widths = FloatArray(n)
        val lines = ArrayList<IntRange>()
        var space = 0f
        for (k in 0 until 24) {
            setFont(z, 600, main, 0f)
            space = p.measureText(" ")
            for (i in 0 until n) widths[i] = p.measureText(h.words[i])
            lines.clear()
            var s0 = 0
            var lw = 0f
            for (i in 0 until n) {
                val add = if (i == s0) widths[i] else lw + space + widths[i]
                if (i > s0 && add > mw) {
                    lines.add(s0 until i)
                    s0 = i
                    lw = widths[i]
                } else {
                    lw = add
                }
            }
            lines.add(s0 until n)
            if (lines.size <= 3 && widths.max() <= mw) break
            z *= 0.92f
        }
        val xs = FloatArray(n)
        val ys = FloatArray(n)
        val lh = z * 1.25f
        val cy = hF * (if (vert) 0.48f else 0.5f) // giữa màn hình (nhích lên chút để tránh vùng caption của TikTok)
        lines.forEachIndexed { li, r ->
            var total = 0f
            for (i in r) total += widths[i] + (if (i > r.first) space else 0f)
            var x = mx + (mw - total) / 2f
            val y = cy + (li - (lines.size - 1) / 2f) * lh + z * 0.35f
            for (i in r) {
                xs[i] = x
                ys[i] = y
                x += widths[i] + space
            }
        }
        val out = HookLay(key, z, xs, ys, widths.copyOf(), main)
        hookLay = out
        return out
    }

    /** Từng cụm chữ hiện đúng lúc có tiếng chuông, giữ một lúc rồi mờ dần. */
    private fun drawHook(h: HookSpec, t: Double, wF: Float, hF: Float, u: Float, mx: Float, mw: Float, vert: Boolean) {
        val tEnd = h.endTime
        if (t < h.t0 || t > tEnd + Hook.FADE) return
        val L = hookLayout(h, wF, hF, u, mx, mw, vert)
        val gAll = if (t <= tEnd) 1f else 1f - eo(((t - tEnd) / Hook.FADE).toFloat())
        setFont(L.z, 600, L.main, 0f)
        p.setShadowLayer(u * 0.012f, 0f, 0f, Color.argb(140, 0, 0, 0))
        for (i in 0 until h.n) {
            val ti = h.wordTime(i)
            if (t < ti) break
            val e = eo(((t - ti) / 0.16).toFloat())
            // mỗi cụm "nảy" từ to xuống đúng cỡ, kéo mắt người xem vào chữ
            val sc = 1f + (1f - e) * 0.3f
            cv.save()
            cv.scale(sc, sc, L.xs[i] + L.ws[i] / 2f, L.ys[i] - L.z * 0.35f)
            txt(h.words[i], L.xs[i], L.ys[i], if (h.hi[i]) acc else INK, e * gAll)
            cv.restore()
        }
        p.clearShadowLayer()
    }

    // ---------- logo ----------
    private fun logo(): Bitmap {
        val key = acc
        val cur = logoTinted
        if (cur != null && logoKey == key) return cur
        val w = logoSrc.width
        val h = logoSrc.height
        val px = IntArray(w * h)
        logoSrc.getPixels(px, 0, w, 0, 0, w, h)
        val rgb = key and 0xFFFFFF
        for (i in px.indices) {
            val c = px[i]
            val a = c ushr 24
            val b = c and 0xFF
            val g = (c shr 8) and 0xFF
            if (a != 0 && b - g > 35) px[i] = (a shl 24) or rgb
        }
        val out = Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
        logoTinted = out
        logoKey = key
        return out
    }

    internal fun drawLogo(c: Canvas, x: Float, yTop: Float, lw: Float, yMid: Float?) {
        val lg = logo()
        val lh = lw * lg.height / lg.width
        val y = if (yMid != null) yMid - lh / 2f else yTop
        c.drawBitmap(lg, null, RectF(x, y, x + lw, y + lh), bmpPaint)
    }

    // ---------- khung hình ----------
    private fun thumbShow(t: Double): Boolean = data.thumbOn && thumb.has() && t < data.thumbDur

    /** Vẽ khung hình tại thời điểm [t] (giây, tính từ đầu đoạn cắt). [bg] là khung video nền nếu có. */
    fun draw(canvas: Canvas, t: Double, bg: Bitmap?) {
        cv = canvas
        val d = data
        val W = d.width
        val H = d.height
        val wF = W.toFloat()
        val hF = H.toFloat()
        val V = H > W
        val u = min(wF, hF)
        val mx = wF * 0.085f
        val rr = if (V) wF * 0.17f else wF * 0.08f // chừa lề phải cho nút TikTok
        val mw = wF - mx - rr

        p.shader = null
        p.clearShadowLayer()
        p.pathEffect = null
        p.textAlign = Paint.Align.LEFT
        p.letterSpacing = 0f

        if (thumbShow(t)) {
            thumb.draw(canvas, W, H, thumb.seed(), false)
            return
        }
        canvas.drawColor(BG)

        if (d.bgOn && bg != null && bg.width > 0 && bg.height > 0) {
            val vw = bg.width.toFloat()
            val vh0 = bg.height.toFloat()
            val sc = if (d.bgFitCover) max(wF / vw, hF / vh0) else min(wF / vw, hF / vh0)
            val z = d.bgZoom / 100f
            val dw = vw * sc * z
            val dh = vh0 * sc * z
            val left = (wF - dw) / 2f + wF * d.bgPanX / 100f
            val top = (hF - dh) / 2f + hF * d.bgPanY / 100f
            canvas.drawBitmap(bg, null, RectF(left, top, left + dw, top + dh), bmpPaint)
            rect(0f, 0f, wF, hF, Color.BLACK, d.bgDim / 100f)
        }

        // đầu trang: tên bài + nghệ sĩ
        val ti = d.title
        val ar = d.artist
        val hy = hF * (if (V) 0.09f else 0.1f)
        if (ti.isNotEmpty() || ar.isNotEmpty()) {
            rect(mx, hy, mx + u * 0.006f, hy + u * 0.07f, acc)
            val hx = mx + u * 0.03f
            fit(ti, u * 0.0264f, mw - u * 0.03f, 600, u * 0.007f, hf(ti))
            txt(ti, hx, hy + u * 0.0276f, INK)
            fit(ar, u * 0.0204f, mw - u * 0.03f, 400, u * 0.006f, hf(ar))
            txt(ar, hx, hy + u * 0.066f, MUTE)
        }
        val cr = d.credit
        if (cr.isNotEmpty()) {
            p.setShadowLayer(u * 0.012f, 0f, 0f, Color.argb(166, 0, 0, 0))
            fit(cr, u * 0.0204f, mw - u * 0.03f, 500, u * 0.004f, hf(cr))
            txt(cr, mx + u * 0.03f, hy + (if (ti.isNotEmpty() || ar.isNotEmpty()) u * 0.108f else u * 0.03f), INK, 0.92f)
            p.clearShadowLayer()
        }

        // lời: ẩn hẳn trong lúc hook hiện để người xem chú ý hook trước, rồi hiện dần lại
        val hk = d.hook
        gate = when {
            hk == null -> 1f
            t < hk.endTime -> 0f
            t < hk.endTime + Hook.FADE -> eo(((t - hk.endTime) / Hook.FADE).toFloat())
            else -> 1f
        }
        val items = d.items
        if (items.isNotEmpty()) {
            fun ts(j: Int): Double =
                if (j <= 0) items[0].t else items[j].t - min(d.leadSec, 0.6 * (items[j].t - items[j - 1].t))
            var i = -1
            for (j in items.indices) if (ts(j) <= t) i = j
            val k = max(0, i)
            val cu = items[k]
            val nx = items.getOrNull(k + 1)
            val pv = if (k > 0) items[k - 1] else null
            val w2 = mw - u * 0.04f
            val tx = mx + u * 0.04f
            val cjkLang = d.lang.fontName != null
            val Z = lz(u * (if (cjkLang) 0.05f else 0.055f), w2)
            val gz = Z * 0.62f
            val cy = hF * (if (V) 0.47f else 0.45f)
            val pp = (t - ts(k)).toFloat()
            val tt = (t - cu.t).toFloat()
            val e = if (k == 0) 1f else eo(pp / TR)
            val ap = if (k == 0) (if (paused && t < 0.05) 1f else eo((t / 0.6).toFloat())) else 1f
            val dur = min(8.0, max(1.0, if (nx != null) nx.t - cu.t else 4.0)).toFloat()
            val Lc = lay(cu, Z, w2)
            val by = cy - bh(cu, Lc) / 2f
            if (pv != null && e < 1f) {
                val Lp = lay(pv, Z, w2)
                val ty = cy - bh(pv, Lp) / 2f
                act(pv, Lp, ty - e * u * 0.05f, 1f - e, 1f, mx, tx, u, w2)
                val ys = ty + bh(pv, Lp) + gz * 1.3f
                gry(cu, gz, ys + (by + Z - ys) * e, 1f - e, w2, tx)
            }
            val fr = when {
                i < 0 -> 0f
                cu.w != null -> (wd(cu, t) / max(1, cu.s.length)).toFloat()
                else -> max(0f, tt) / (dur * 0.85f)
            }
            act(cu, Lc, by + (1f - e) * u * 0.03f, e * ap, fr, mx, tx, u, w2)
            if (disp(cu).isNotEmpty()) {
                gry(nx, gz, by + bh(cu, Lc) + gz * 1.3f + (1f - e) * u * 0.03f, 0.9f * (if (k == 0) ap else e), w2, tx)
            }
        }

        // hook căn giữa đúng tâm khung hình: lề hai bên bằng nhau (theo lề phải chừa cho nút TikTok)
        d.hook?.let { drawHook(it, t, wF, hF, u, rr, wF - 2f * rr, V) }

        val lw = u * (if (V) 0.13f else 0.11f)
        drawLogo(canvas, mx - lw * 0.04f, 0f, lw, hF * (if (V) 0.76f else 0.8f))
        p.letterSpacing = 0f
    }
}
