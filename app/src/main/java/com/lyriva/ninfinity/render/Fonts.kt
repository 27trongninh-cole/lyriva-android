package com.lyriva.ninfinity.render

import android.content.Context
import android.graphics.Typeface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import com.lyriva.ninfinity.data.Lang
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Một bộ font cần tải: danh sách (tên file, URL). Đổi URL ở đây nếu nguồn thay đổi. */
class FontSpec(val id: String, val name: String, val files: List<Pair<String, String>>)

object FontCatalog {
    private const val BASE = "https://raw.githubusercontent.com/google/fonts/main/ofl/"

    val LATIN = FontSpec(
        "latin", "Be Vietnam Pro",
        listOf(
            "BeVietnamPro-Regular.ttf" to BASE + "bevietnampro/BeVietnamPro-Regular.ttf",
            "BeVietnamPro-Medium.ttf" to BASE + "bevietnampro/BeVietnamPro-Medium.ttf",
            "BeVietnamPro-SemiBold.ttf" to BASE + "bevietnampro/BeVietnamPro-SemiBold.ttf"
        )
    )
    val KR = FontSpec("kr", "Noto Sans KR", listOf("NotoSansKR.ttf" to BASE + "notosanskr/NotoSansKR%5Bwght%5D.ttf"))
    val JP = FontSpec("jp", "Noto Sans JP", listOf("NotoSansJP.ttf" to BASE + "notosansjp/NotoSansJP%5Bwght%5D.ttf"))
    val SC = FontSpec("cn", "Noto Sans SC", listOf("NotoSansSC.ttf" to BASE + "notosanssc/NotoSansSC%5Bwght%5D.ttf"))

    fun forLang(l: Lang): FontSpec? = when (l) {
        Lang.KR -> KR
        Lang.JP -> JP
        Lang.CN -> SC
        else -> null
    }
}

class Fonts(ctx: Context) {
    private val dir = File(ctx.filesDir, "fonts").apply { mkdirs() }
    private val cache = HashMap<String, Typeface>()

    /** Tăng mỗi khi font thay đổi để renderer biết đo lại chữ. */
    var epoch by mutableIntStateOf(0)
        private set

    fun installed(spec: FontSpec): Boolean = spec.files.all { File(dir, it.first).length() > 10_000 }

    /** Đã đủ font cho ngôn ngữ này chưa (Latinh + CJK nếu cần). */
    fun readyFor(lang: Lang): Boolean {
        val c = FontCatalog.forLang(lang)
        return installed(FontCatalog.LATIN) && (c == null || installed(c))
    }

    @Synchronized
    fun latin(weight: Int): Typeface {
        val w = when {
            weight >= 600 -> 600
            weight >= 500 -> 500
            else -> 400
        }
        return cache.getOrPut("l$w") {
            val name = when (w) {
                600 -> "BeVietnamPro-SemiBold.ttf"
                500 -> "BeVietnamPro-Medium.ttf"
                else -> "BeVietnamPro-Regular.ttf"
            }
            val f = File(dir, name)
            var tf: Typeface? = null
            if (f.length() > 10_000) tf = try { Typeface.createFromFile(f) } catch (e: Exception) { null }
            tf ?: Typeface.create(Typeface.DEFAULT, w, false)
        }
    }

    @Synchronized
    fun cjk(lang: Lang, weight: Int): Typeface {
        val spec = FontCatalog.forLang(lang) ?: return latin(weight)
        val w = if (weight >= 500) 500 else 400
        return cache.getOrPut("${lang.name}$w") {
            val f = File(dir, spec.files[0].first)
            var tf: Typeface? = null
            if (f.length() > 10_000) {
                tf = try {
                    Typeface.Builder(f).setFontVariationSettings("'wght' $w").build()
                } catch (e: Exception) {
                    null
                }
            }
            tf ?: Typeface.create(Typeface.DEFAULT, w, false)
        }
    }

    /** Tải (chặn luồng gọi), [onProgress] từ 0 đến 1. */
    fun download(spec: FontSpec, onProgress: (Float) -> Unit) {
        var done = 0
        val n = spec.files.size
        for ((name, url) in spec.files) {
            val out = File(dir, name)
            if (out.length() > 10_000) {
                done++
                onProgress(done / n.toFloat())
                continue
            }
            val tmp = File(dir, "$name.part")
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000
            conn.instanceFollowRedirects = true
            try {
                if (conn.responseCode !in 200..299) throw IOException("HTTP ${conn.responseCode}")
                val total = conn.contentLengthLong
                conn.inputStream.use { ins ->
                    tmp.outputStream().use { os ->
                        val buf = ByteArray(32768)
                        var read = 0L
                        while (true) {
                            val r = ins.read(buf)
                            if (r < 0) break
                            os.write(buf, 0, r)
                            read += r
                            if (total > 0) onProgress((done + read.toFloat() / total) / n)
                        }
                    }
                }
                if (tmp.length() < 10_000) throw IOException("File tải về quá nhỏ")
                out.delete()
                if (!tmp.renameTo(out)) throw IOException("Không lưu được file font")
            } finally {
                conn.disconnect()
                if (tmp.exists()) tmp.delete()
            }
            done++
            onProgress(done / n.toFloat())
        }
        synchronized(this) { cache.clear() }
        epoch++
    }
}
