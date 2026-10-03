package com.lyriva.ninfinity.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Thumbnail đầu video (ảnh bìa TikTok) và lưới hồ sơ 3 cột. Mọi bìa dùng chung các điểm neo ở mép
 * vùng cắt 3:4 nên xếp cạnh nhau vẫn nối liền; hoa văn bên trong đổi theo bài (seed từ tên bài + nghệ sĩ).
 */
class ThumbnailRenderer(private val r: LyricsRenderer) {

    class Info(val title: String, val artist: String, val item: LyricItem?)

    private class ThLay(
        val key: String,
        val zt: Float, val tl: List<String>, val lt: Float,
        val za: Float, val la: Float,
        val zl: Float, val ll: List<String>, val zr: Float, val rom: String,
        val hT: Float, val gA: Float, val hA: Float, val gL: Float, val hL: Float, val hR: Float,
        val fl: Boolean, val ft: Boolean, val fa: Boolean, val total: Float
    )

    private class Rng(seed: Int) {
        private var a = seed
        fun next(): Float {
            a += 0x6D2B79F5
            var t = (a xor (a ushr 15)) * (1 or a)
            t = (t + ((t xor (t ushr 7)) * (61 or t))) xor t
            return (((t xor (t ushr 14)).toLong() and 0xFFFFFFFFL) / 4294967296.0).toFloat()
        }
    }

    private val q = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private var thC: ThLay? = null
    private val d get() = r.data
    private val acc get() = r.acc

    companion object {
        fun hash(s: String): Long {
            var h = 2166136261L.toInt()
            for (ch in s) {
                h = h xor ch.code
                h *= 16777619
            }
            return h.toLong() and 0xFFFFFFFFL
        }

        private val ENDS = Regex("[\\s,.;:!?，。、…]+$")
    }

    fun info(): Info = Info(d.title, d.artist, d.items.firstOrNull { r.disp(it).isNotEmpty() })

    fun has(i: Info = info()): Boolean = i.title.isNotEmpty() || i.artist.isNotEmpty() || i.item != null

    fun seed(i: Info = info()): Long =
        hash(if (i.title.isNotEmpty() || i.artist.isNotEmpty()) i.title + "|" + i.artist else (i.item?.s ?: "lyriva"))

    private fun vis(W: Int, H: Int): Pair<Float, Float> =
        if (H > W) {
            val h = min(H.toFloat(), W * 4f / 3f)
            ((H - h) / 2f) to h
        } else 0f to H.toFloat()

    private fun rgba(rgb: Int, a: Float) = LyricsRenderer.argb(rgb, a)

    private fun sf(z: Float, w: Int, main: Boolean, ls: Float) {
        q.typeface = r.face(w, main)
        q.textSize = z
        q.letterSpacing = if (z > 0f) ls / z else 0f
    }

    private fun fill(color: Int, alpha: Float = 1f) {
        q.style = Paint.Style.FILL
        q.shader = null
        q.pathEffect = null
        q.color = color
        q.alpha = (alpha.coerceIn(0f, 1f) * 255f).roundToInt()
    }

    private fun stroke(color: Int, width: Float, cap: Paint.Cap = Paint.Cap.ROUND) {
        q.style = Paint.Style.STROKE
        q.shader = null
        q.color = color
        q.strokeWidth = width
        q.strokeCap = cap
        q.strokeJoin = Paint.Join.ROUND
    }

    // ---------- ngắt dòng ----------
    private fun wrapT(s: String, mw: Float): List<String> {
        val tk = r.tokens(s)
        val ls = ArrayList<String>()
        var cur = ""
        for (t in tk) {
            val n = cur + t
            if (cur.isBlank() || q.measureText(n.trimEnd()) <= mw) {
                cur = n
            } else {
                ls.add(cur.trim())
                cur = t.trimStart()
            }
        }
        if (cur.isNotBlank()) ls.add(cur.trim())
        return ls
    }

    private fun widest(ls: List<String>): Float {
        var m = 0f
        for (l in ls) m = max(m, q.measureText(l))
        return m
    }

    /** Tiêu đề ≤3 dòng, nghệ sĩ 1 dòng, câu đầu ≤3 dòng + "..."; tự thu nhỏ cho vừa vùng chữ. */
    private fun thLay(W: Int, H: Int, inf: Info): ThLay {
        val vs = vis(W, H)
        val u = min(W, H).toFloat()
        val mx = W * 0.085f
        val tx = mx + u * 0.036f
        val mw = W - tx - mx
        val zone = vs.second * 0.47f
        val item = inf.item
        val key = listOf(
            r.ver, W, H, d.lang.name, inf.title, inf.artist,
            if (item != null) r.disp(item) + "|" + r.rom(item) else "", r.fonts.epoch
        ).joinToString("¦")
        thC?.let { if (it.key == key) return it }

        val ft = r.hf(inf.title)
        val fa = r.hf(inf.artist)
        val fl = item != null && r.mainOf(item)
        val lyr = if (item != null) r.disp(item).replace(ENDS, "") else ""
        val rom = if (item != null) r.rom(item) else ""

        fun build(s: Float): ThLay {
            var zt = u * 0.092f * s
            var tl: List<String> = emptyList()
            var lt = 0f
            if (inf.title.isNotEmpty()) {
                for (k in 0 until 14) {
                    lt = zt * 0.04f
                    sf(zt, 600, ft, lt)
                    tl = wrapT(inf.title, mw)
                    if (tl.size <= 3 && widest(tl) <= mw) break
                    zt *= 0.92f
                }
            }
            var za = u * 0.034f * s
            var la = 0f
            if (inf.artist.isNotEmpty()) {
                la = za * 0.18f
                sf(za, 400, fa, la)
                val w = q.measureText(inf.artist)
                if (w > mw) {
                    za *= mw / w
                    la = za * 0.18f
                }
            }
            var zl = u * 0.052f * s
            val tk = if (lyr.isNotEmpty()) r.tokens(lyr).toMutableList() else mutableListOf<String>()
            var ll: List<String> = emptyList()
            if (lyr.isNotEmpty()) {
                for (k in 0 until 60) {
                    sf(zl, 500, fl, 0f)
                    ll = wrapT(tk.joinToString("").trim() + "...", mw)
                    if (ll.size <= 3 && widest(ll) <= mw) break
                    if (zl > u * 0.034f * s) zl *= 0.94f else if (tk.isNotEmpty()) tk.removeAt(tk.size - 1)
                }
            }
            q.letterSpacing = 0f
            val hT = tl.size * zt * 1.08f
            val gA = if (tl.isNotEmpty()) zt * 0.34f else 0f
            val hA = if (inf.artist.isNotEmpty()) za * 1.15f else 0f
            val gL = zt * 0.55f
            val hL = ll.size * zl * 1.28f
            val zr = zl * 0.46f
            val hR = if (rom.isNotEmpty() && ll.isNotEmpty()) zr * 1.9f else 0f
            val total = hT + (if (inf.artist.isNotEmpty()) gA + hA else 0f) +
                (if (ll.isNotEmpty()) (if (tl.isNotEmpty() || inf.artist.isNotEmpty()) gL else 0f) + hL + hR else 0f)
            return ThLay(key, zt, tl, lt, za, la, zl, ll, zr, rom, hT, gA, hA, gL, hL, hR, fl, ft, fa, total)
        }

        var s = 1f
        var L = build(s)
        var k = 0
        while (k < 16 && L.total > zone && s > 0.4f) {
            s *= 0.93f
            L = build(s)
            k++
        }
        thC = L
        return L
    }

    // ---------- hình minh họa ----------
    private fun deg(rad: Float) = (rad * 180.0 / PI).toFloat()

    private fun vinyl(cv: Canvas, x: Float, y: Float, rad: Float, rot: Float) {
        cv.save()
        cv.translate(x, y)
        fill(0xFF19191B.toInt())
        cv.drawCircle(0f, 0f, rad, q)
        stroke(rgba(acc, 0.6f), max(2f, rad * 0.025f))
        cv.drawCircle(0f, 0f, rad, q)
        stroke(rgba(LyricsRenderer.INK, 0.07f), max(1f, rad * 0.012f))
        for (k in floatArrayOf(0.88f, 0.78f, 0.68f, 0.58f, 0.48f)) cv.drawCircle(0f, 0f, rad * k, q)
        cv.rotate(deg(rot))
        stroke(rgba(LyricsRenderer.INK, 0.09f), rad * 0.14f, Paint.Cap.BUTT)
        val rr = rad * 0.74f
        for (a in floatArrayOf(0f, PI.toFloat())) {
            cv.drawArc(RectF(-rr, -rr, rr, rr), deg(a + 0.15f), deg(0.6f), false, q)
        }
        fill(acc)
        cv.drawCircle(0f, 0f, rad * 0.34f, q)
        fill(LyricsRenderer.BG)
        cv.drawCircle(0f, 0f, rad * 0.06f, q)
        cv.restore()
    }

    private fun note(cv: Canvas, x: Float, y: Float, s: Float, col: Int, rot: Float, dbl: Boolean) {
        cv.save()
        cv.translate(x, y)
        cv.rotate(deg(rot))
        fun head(hx: Float, hy: Float) {
            cv.save()
            cv.rotate(deg(-0.45f), hx, hy)
            fill(col)
            cv.drawOval(RectF(hx - s * 0.5f, hy - s * 0.36f, hx + s * 0.5f, hy + s * 0.36f), q)
            cv.restore()
        }
        head(0f, 0f)
        stroke(col, s * 0.16f)
        path.reset()
        if (dbl) {
            head(s * 1.5f, -s * 0.3f)
            stroke(col, s * 0.16f)
            path.moveTo(s * 0.44f, -s * 0.1f); path.lineTo(s * 0.44f, -s * 2.1f)
            path.moveTo(s * 1.94f, -s * 0.4f); path.lineTo(s * 1.94f, -s * 2.4f)
            cv.drawPath(path, q)
            q.strokeWidth = s * 0.34f
            path.reset()
            path.moveTo(s * 0.44f, -s * 2.1f); path.lineTo(s * 1.94f, -s * 2.4f)
            cv.drawPath(path, q)
        } else {
            path.moveTo(s * 0.44f, -s * 0.1f)
            path.lineTo(s * 0.44f, -s * 2.1f)
            path.cubicTo(s * 0.44f, -s * 1.6f, s * 1.3f, -s * 1.6f, s * 1.1f, -s * 0.8f)
            cv.drawPath(path, q)
        }
        cv.restore()
    }

    private fun spark(cv: Canvas, x: Float, y: Float, rad: Float, col: Int, a: Float) {
        cv.save()
        cv.translate(x, y)
        fill(col, a)
        path.reset()
        path.moveTo(0f, -rad)
        path.quadTo(0f, 0f, rad, 0f)
        path.quadTo(0f, 0f, 0f, rad)
        path.quadTo(0f, 0f, -rad, 0f)
        path.quadTo(0f, 0f, 0f, -rad)
        path.close()
        cv.drawPath(path, q)
        cv.restore()
    }

    // ---------- thumbnail ----------
    /** [ghost] = ô "hàng xóm" giả (chỉ vẽ khung chữ mờ) để xem lưới. */
    fun draw(cv: Canvas, W: Int, H: Int, seed: Long, ghost: Boolean) {
        val inf = info()
        val vs = vis(W, H)
        val vy = vs.first
        val vh = vs.second
        val wF = W.toFloat()
        val hF = H.toFloat()
        val u = min(wF, hF)
        val mx = wF * 0.085f
        val tx = mx + u * 0.036f
        val mw = wF - tx - mx
        val yc = vy + vh * 0.7f
        val vp = H > W
        val rd = Rng(seed.toInt())
        val INK = LyricsRenderer.INK

        cv.save()
        q.letterSpacing = 0f
        q.clearShadowLayer()
        fill(LyricsRenderer.BG)
        cv.drawRect(0f, 0f, wF, hF, q)

        // dải sáng chạy ngang quanh sợi nối
        val y0 = yc - vh * 0.22f
        val y1 = yc + vh * 0.22f
        val colors = IntArray(13) { k -> rgba(acc, 0.14f * sin(PI * k / 12.0).pow(2.0).toFloat()) }
        val pos = FloatArray(13) { it / 12f }
        q.style = Paint.Style.FILL
        q.pathEffect = null
        q.shader = LinearGradient(0f, y0, 0f, y1, colors, pos, Shader.TileMode.CLAMP)
        q.alpha = 255
        cv.drawRect(0f, y0, wF, yc + vh * 0.22f, q)
        q.shader = null

        // lưới chấm mờ
        val pg = wF / 18f
        fill(INK, 0.075f)
        var gy = vy - pg * floor(vy / pg)
        while (gy <= hF + 1f) {
            var gx = 0f
            while (gx <= wF + 1f) {
                cv.drawRect(gx - 1.5f, gy - 1.5f, gx + 1.5f, gy + 1.5f, q)
                gx += pg
            }
            gy += pg
        }

        // sợi chấm xuyên hai mép + hai đoạn chấm dọc giữa mép trên/dưới
        stroke(rgba(acc, 0.85f), 5f)
        q.pathEffect = DashPathEffect(floatArrayOf(0.01f, 20f), 0f)
        path.reset()
        path.moveTo(0f, yc); path.lineTo(wF, yc)
        val ls = vh * 0.05f
        path.moveTo(wF / 2f, vy); path.lineTo(wF / 2f, vy + ls)
        path.moveTo(wF / 2f, vy + vh); path.lineTo(wF / 2f, vy + vh - ls)
        cv.drawPath(path, q)
        q.pathEffect = null

        // sóng nhạc: thấp dần về 0 ở hai mép; trái tô màu theme, phải xám
        val amp = vh * 0.05f
        val n = 45
        val st = wF / n
        val ph = rd.next() * 6.283f
        val prog = 0.3f + rd.next() * 0.4f
        for (i in 0 until n) {
            val x = (i + 0.5f) * st
            val e = sin(PI * x / wF).pow(1.6).toFloat()
            val h = amp * e * (0.18f + 0.82f * (0.5f * rd.next() + 0.5f * (0.5f + 0.5f * sin(i * 0.6f + ph))))
            if (h < 2.5f) continue
            stroke(if (x / wF < prog) acc else rgba(INK, 0.4f), st * 0.36f)
            cv.drawLine(x, yc - h, x, yc + h, q)
        }

        // nút tròn đúng trên mép
        stroke(acc, 4f)
        val rr = u * 0.014f
        cv.drawCircle(0f, yc, rr, q)
        cv.drawCircle(wF, yc, rr, q)
        cv.drawCircle(wF / 2f, vy, rr, q)
        cv.drawCircle(wF / 2f, vy + vh, rr, q)

        // vật minh họa: đĩa than + nốt nhạc + tia sáng
        val vx = if (rd.next() < 0.5f) 0.26f else 0.74f
        val ox = if (vx < 0.5f) 0.66f else 0.18f
        vinyl(cv, wF * vx, vy + vh * 0.86f, if (vp) u * 0.115f else u * 0.08f, rd.next() * 6.283f)
        note(cv, wF * (ox + 0.02f + rd.next() * 0.08f), vy + vh * (0.815f + rd.next() * 0.02f), u * 0.03f, acc, (rd.next() - 0.5f) * 0.5f, rd.next() < 0.5f)
        note(cv, wF * (ox + 0.12f + rd.next() * 0.08f), vy + vh * (0.895f + rd.next() * 0.02f), u * 0.024f, INK, (rd.next() - 0.5f) * 0.5f, rd.next() < 0.5f)
        spark(cv, wF * (0.68f + rd.next() * 0.2f), vy + vh * (0.04f + rd.next() * 0.04f), u * (0.024f + rd.next() * 0.014f), acc, 0.95f)
        spark(cv, wF * (0.6f + rd.next() * 0.08f), vy + vh * (0.095f + rd.next() * 0.02f), u * (0.012f + rd.next() * 0.006f), INK, 0.8f)
        spark(cv, wF * (if (vx < 0.5f) vx + 0.19f else vx - 0.19f), vy + vh * (0.78f + rd.next() * 0.02f), u * (0.016f + rd.next() * 0.008f), INK, 0.85f)
        r.drawLogo(cv, mx, vy + vh * 0.06f, u * 0.12f, null)

        if (ghost) {
            val zt = u * 0.092f
            val yy = vy + vh * 0.13f + vh * 0.47f * 0.22f
            fun bar(x: Float, y: Float, w: Float, h: Float, a: Float) {
                fill(INK, a)
                cv.drawRoundRect(RectF(x, y, x + w, y + h), h / 2f, h / 2f, q)
            }
            fill(acc)
            cv.drawRect(mx, yy, mx + u * 0.007f, yy + zt * 1.9f, q)
            bar(tx, yy + zt * 0.15f, mw * (0.6f + rd.next() * 0.3f), zt * 0.62f, 0.2f)
            bar(tx, yy + zt * 1.2f, mw * (0.2f + rd.next() * 0.2f), zt * 0.26f, 0.12f)
            bar(tx, yy + zt * 2.5f, mw * (0.8f + rd.next() * 0.15f), u * 0.034f, 0.16f)
            bar(tx, yy + zt * 2.5f + u * 0.07f, mw * (0.4f + rd.next() * 0.3f), u * 0.034f, 0.16f)
        } else if (has(inf)) {
            val L = thLay(W, H, inf)
            val hasT = L.tl.isNotEmpty()
            var y = vy + vh * 0.13f + max(0f, (vh * 0.47f - L.total) / 2f)
            if (hasT || inf.artist.isNotEmpty()) {
                val ya = y + L.hT + L.gA + L.za * 0.9f
                val top = if (hasT) y + L.zt * 0.12f else y
                val bot = if (inf.artist.isNotEmpty()) ya + L.za * 0.25f else y + L.hT - L.zt * 0.1f
                fill(acc)
                cv.drawRect(mx, top, mx + u * 0.007f, bot, q)
                sf(L.zt, 600, L.ft, L.lt)
                fill(INK)
                L.tl.forEachIndexed { k, l -> cv.drawText(l, tx, y + L.zt * 0.9f + k * L.zt * 1.08f, q) }
                if (inf.artist.isNotEmpty()) {
                    sf(L.za, 400, L.fa, L.la)
                    fill(LyricsRenderer.MUTE)
                    cv.drawText(inf.artist, tx, ya, q)
                }
                q.letterSpacing = 0f
                y += L.hT + (if (inf.artist.isNotEmpty()) L.gA + L.hA else 0f) + L.gL
            }
            if (L.ll.isNotEmpty()) {
                sf(L.zl, 500, L.fl, 0f)
                L.ll.forEachIndexed { k, ln ->
                    val last = k == L.ll.size - 1
                    val t = if (last) ln.replace(Regex("\\.{3}$"), "") else ln
                    val b = y + L.zl * 0.95f + k * L.zl * 1.28f
                    fill(INK)
                    cv.drawText(t, tx, b, q)
                    if (last) {
                        val w = q.measureText(t)
                        fill(acc)
                        cv.drawText("...", tx + w, b, q)
                    }
                }
                if (L.rom.isNotEmpty()) {
                    sf(L.zr, 400, false, 0f)
                    val w = q.measureText(L.rom)
                    if (w > mw) q.textSize = L.zr * mw / w
                    fill(LyricsRenderer.MUTE)
                    cv.drawText(L.rom, tx, y + L.hL + L.zr, q)
                }
            }
        }
        q.letterSpacing = 0f
        cv.restore()
    }

    // ---------- ảnh bìa và lưới ----------
    fun renderCover(): Bitmap {
        val W = d.width
        val H = d.height
        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        draw(Canvas(bmp), W, H, seed(), false)
        return bmp
    }

    /** Lưới 3×3: ô [pos] là video này, 8 ô còn lại là hàng xóm giả. */
    fun renderGrid(pos: Int): Bitmap {
        val W = d.width
        val H = d.height
        val vs = vis(W, H)
        val gap = 6f
        val cw = 900f
        val tw = (cw - gap * 2) / 3f
        val sc = tw / W
        val th = vs.second * sc
        val sd = seed()
        val bmp = Bitmap.createBitmap(cw.toInt(), (th * 3 + gap * 2).roundToInt(), Bitmap.Config.ARGB_8888)
        val cv = Canvas(bmp)
        cv.drawColor(Color.BLACK)
        for (i in 0 until 9) {
            val x = (i % 3) * (tw + gap)
            val y = (i / 3) * (th + gap)
            cv.save()
            cv.translate(x, y)
            cv.clipRect(0f, 0f, tw, th)
            cv.scale(sc, sc)
            cv.translate(0f, -vs.first)
            draw(cv, W, H, if (i == pos) sd else hash("n$i|$sd"), i != pos)
            cv.restore()
        }
        return bmp
    }
}
