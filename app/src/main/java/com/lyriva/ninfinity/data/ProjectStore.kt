package com.lyriva.ninfinity.data

import android.content.Context
import org.json.JSONObject
import java.io.File

/** Lưu/đọc dự án đang làm vào bộ nhớ riêng của app. */
class ProjectStore(context: Context) {
    private val file = File(context.filesDir, "project.json")
    private val tmp = File(context.filesDir, "project.json.tmp")

    fun load(): Project = try {
        if (file.exists()) Project.fromJson(JSONObject(file.readText())) else Project()
    } catch (e: Exception) {
        Project()
    }

    fun save(p: Project) {
        try {
            tmp.writeText(p.toJson().toString())
            if (!tmp.renameTo(file)) {
                file.writeText(tmp.readText())
                tmp.delete()
            }
        } catch (_: Exception) {
        }
    }
}
