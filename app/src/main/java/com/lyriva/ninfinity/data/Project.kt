package com.lyriva.ninfinity.data

import com.lyriva.ninfinity.core.SyncMode
import org.json.JSONArray
import org.json.JSONObject

enum class Aspect { V, H }
enum class BgFit { COVER, FIT }

/** Ngôn ngữ bài hát: mỗi nước một màu, CJK có thêm font chữ gốc. */
enum class Lang(val label: String, val colorRgb: Int, val fontName: String?) {
    EN("Anh", 0xA78BFA, null),
    VN("Việt", 0xF2C14E, null),
    KR("Hàn", 0x6FA8FF, "Noto Sans KR"),
    JP("Nhật", 0xF28CAB, "Noto Sans JP"),
    CN("Trung", 0xEF5B52, "Noto Sans SC")
}

/** Toàn bộ trạng thái một dự án, dùng chung cho hai tab. Lưu thành JSON cục bộ. */
data class Project(
    // nguồn nhạc / video và đoạn cắt
    val audioUri: String? = null,
    val sourceName: String = "",
    val sourceIsVideo: Boolean = false,
    val cutStart: Double = 0.0,
    val cutEnd: Double = 0.0,
    // lời và đồng bộ
    val lyricsText: String = "",
    val syncMode: SyncMode = SyncMode.WORD,
    val firstTapMarks: Boolean = false,
    val marks: List<Double> = emptyList(),
    val latencyMs: Int = 150,
    // LRC đang dùng để dựng video
    val lrcText: String = "",
    // thông tin bài hát
    val title: String = "",
    val artist: String = "",
    val language: Lang = Lang.EN,
    val originalLyrics: String = "",
    val aspect: Aspect = Aspect.V,
    // Vietsub và romanized
    val vietsubText: String = "",
    val vietsubOn: Boolean = true,
    val romanOn: Boolean = true,
    // video cover làm nền
    val bgEnabled: Boolean = true,
    val bgUri: String? = null,
    val bgOffset: Double = 0.0,
    val bgFit: BgFit = BgFit.COVER,
    val bgZoom: Int = 100,
    val bgPanX: Int = 0,
    val bgPanY: Int = 0,
    val bgDim: Int = 70,
    val credit: String = "",
    // hiện lời sớm (ms)
    val leadMs: Int = 100,
    // xuất nhanh: 24fps, video nền lấy khung thưa hơn
    val fastExport: Boolean = false,
    // người hát (mỗi người một màu) và bảng màu cố định theo nhóm
    val singerOn: Boolean = true,
    val singersText: String = "",
    val palettesText: String = "",
    val activeGroup: String = "",
    // xuất kèm ảnh bìa (PNG) mỗi lần xuất video
    val thumbExport: Boolean = true,
    // nền thumbnail lấy từ một khung của video nền
    val thumbBgOn: Boolean = false,
    val thumbBgTime: Double = 0.0,
    val thumbBgDim: Int = 60,
    // nhạc vào dần / ra dần (giây)
    val fadeIn: Double = 0.0,
    val fadeOut: Double = 0.0,
    // thumbnail đầu video
    val thumbEnabled: Boolean = true,
    val thumbDuration: Double = 0.02,
    val thumbGridPos: Int = 0
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("audioUri", audioUri ?: JSONObject.NULL)
        put("sourceName", sourceName)
        put("sourceIsVideo", sourceIsVideo)
        put("cutStart", cutStart)
        put("cutEnd", cutEnd)
        put("lyricsText", lyricsText)
        put("syncMode", syncMode.name)
        put("firstTapMarks", firstTapMarks)
        put("marks", JSONArray().also { a -> marks.forEach { a.put(it) } })
        put("latencyMs", latencyMs)
        put("lrcText", lrcText)
        put("title", title)
        put("artist", artist)
        put("language", language.name)
        put("originalLyrics", originalLyrics)
        put("aspect", aspect.name)
        put("vietsubText", vietsubText)
        put("vietsubOn", vietsubOn)
        put("romanOn", romanOn)
        put("bgEnabled", bgEnabled)
        put("bgUri", bgUri ?: JSONObject.NULL)
        put("bgOffset", bgOffset)
        put("bgFit", bgFit.name)
        put("bgZoom", bgZoom)
        put("bgPanX", bgPanX)
        put("bgPanY", bgPanY)
        put("bgDim", bgDim)
        put("credit", credit)
        put("leadMs", leadMs)
        put("fastExport", fastExport)
        put("singerOn", singerOn)
        put("singersText", singersText)
        put("palettesText", palettesText)
        put("activeGroup", activeGroup)
        put("thumbExport", thumbExport)
        put("thumbBgOn", thumbBgOn)
        put("thumbBgTime", thumbBgTime)
        put("thumbBgDim", thumbBgDim)
        put("fadeIn", fadeIn)
        put("fadeOut", fadeOut)
        put("thumbEnabled", thumbEnabled)
        put("thumbDuration", thumbDuration)
        put("thumbGridPos", thumbGridPos)
    }

    companion object {
        private inline fun <reified E : Enum<E>> enumOr(name: String?, def: E): E =
            try { if (name == null) def else enumValueOf<E>(name) } catch (e: Exception) { def }

        private fun JSONObject.strOrNull(k: String): String? =
            if (has(k) && !isNull(k)) getString(k) else null

        fun fromJson(j: JSONObject): Project {
            val d = Project()
            val marks = ArrayList<Double>()
            j.optJSONArray("marks")?.let { a -> for (i in 0 until a.length()) marks.add(a.getDouble(i)) }
            return Project(
                audioUri = j.strOrNull("audioUri"),
                sourceName = j.optString("sourceName", d.sourceName),
                sourceIsVideo = j.optBoolean("sourceIsVideo", d.sourceIsVideo),
                cutStart = j.optDouble("cutStart", d.cutStart),
                cutEnd = j.optDouble("cutEnd", d.cutEnd),
                lyricsText = j.optString("lyricsText", d.lyricsText),
                syncMode = enumOr(j.strOrNull("syncMode"), d.syncMode),
                firstTapMarks = j.optBoolean("firstTapMarks", d.firstTapMarks),
                marks = marks,
                latencyMs = j.optInt("latencyMs", d.latencyMs),
                lrcText = j.optString("lrcText", d.lrcText),
                title = j.optString("title", d.title),
                artist = j.optString("artist", d.artist),
                language = enumOr(j.strOrNull("language"), d.language),
                originalLyrics = j.optString("originalLyrics", d.originalLyrics),
                aspect = enumOr(j.strOrNull("aspect"), d.aspect),
                vietsubText = j.optString("vietsubText", d.vietsubText),
                vietsubOn = j.optBoolean("vietsubOn", d.vietsubOn),
                romanOn = j.optBoolean("romanOn", d.romanOn),
                bgEnabled = j.optBoolean("bgEnabled", d.bgEnabled),
                bgUri = j.strOrNull("bgUri"),
                bgOffset = j.optDouble("bgOffset", d.bgOffset),
                bgFit = enumOr(j.strOrNull("bgFit"), d.bgFit),
                bgZoom = j.optInt("bgZoom", d.bgZoom),
                bgPanX = j.optInt("bgPanX", d.bgPanX),
                bgPanY = j.optInt("bgPanY", d.bgPanY),
                bgDim = j.optInt("bgDim", d.bgDim),
                credit = j.optString("credit", d.credit),
                leadMs = j.optInt("leadMs", d.leadMs),
                fastExport = j.optBoolean("fastExport", d.fastExport),
                singerOn = j.optBoolean("singerOn", d.singerOn),
                singersText = j.optString("singersText", d.singersText),
                palettesText = j.optString("palettesText", d.palettesText),
                activeGroup = j.optString("activeGroup", d.activeGroup),
                thumbExport = j.optBoolean("thumbExport", d.thumbExport),
                thumbBgOn = j.optBoolean("thumbBgOn", d.thumbBgOn),
                thumbBgTime = j.optDouble("thumbBgTime", d.thumbBgTime),
                thumbBgDim = j.optInt("thumbBgDim", d.thumbBgDim),
                fadeIn = j.optDouble("fadeIn", d.fadeIn),
                fadeOut = j.optDouble("fadeOut", d.fadeOut),
                thumbEnabled = j.optBoolean("thumbEnabled", d.thumbEnabled),
                thumbDuration = j.optDouble("thumbDuration", d.thumbDuration),
                thumbGridPos = j.optInt("thumbGridPos", d.thumbGridPos)
            )
        }
    }
}
