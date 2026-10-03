package com.lyriva.ninfinity.export

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class ExportCancelled : RuntimeException("Đã hủy")

/** Trạng thái xuất video dùng chung giữa service và giao diện. */
object ExportState {
    var running by mutableStateOf(false)
    var progress by mutableFloatStateOf(0f)
    var message by mutableStateOf("")
    var done by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var resultUri by mutableStateOf<String?>(null)

    @Volatile
    var cancelRequested = false

    val visible: Boolean get() = running || done || error != null || message.isNotEmpty()

    fun begin() {
        running = true
        progress = 0f
        message = "Đang chuẩn bị…"
        done = false
        error = null
        resultUri = null
        cancelRequested = false
    }

    fun dismiss() {
        if (running) return
        done = false
        error = null
        resultUri = null
        message = ""
        progress = 0f
    }
}
