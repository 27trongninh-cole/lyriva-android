package com.lyriva.ninfinity

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lyriva.ninfinity.audio.AudioDecoder
import com.lyriva.ninfinity.audio.PeakData
import com.lyriva.ninfinity.data.Project
import com.lyriva.ninfinity.data.ProjectStore
import com.lyriva.ninfinity.render.FontCatalog
import com.lyriva.ninfinity.render.FontSpec
import com.lyriva.ninfinity.render.Fonts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val store = ProjectStore(app)
    private var saveJob: Job? = null

    val fonts = Fonts(app)
    val logo: Bitmap by lazy {
        val o = BitmapFactory.Options().apply { inScaled = false }
        BitmapFactory.decodeResource(app.resources, R.drawable.lyriva_logo, o)
    }

    var project by mutableStateOf(store.load())
        private set

    /** Cập nhật dự án; tự lưu sau 400ms kể từ lần sửa cuối. */
    fun update(block: (Project) -> Project) {
        project = block(project)
        val snapshot = project
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(400)
            withContext(Dispatchers.IO) { store.save(snapshot) }
        }
    }

    /** Lưu ngay (trước khi đẩy việc xuất video sang service). */
    fun saveNow() {
        saveJob?.cancel()
        store.save(project)
    }

    // ---------- phân tích sóng nhạc ----------
    var peaks by mutableStateOf<PeakData?>(null)
        private set
    var analyzing by mutableStateOf(false)
        private set
    var analyzeError by mutableStateOf<String?>(null)
        private set

    fun analyze(uriStr: String) {
        if (analyzing) return
        analyzing = true
        analyzeError = null
        viewModelScope.launch {
            try {
                val d = withContext(Dispatchers.Default) {
                    AudioDecoder.peaks(getApplication(), Uri.parse(uriStr))
                }
                peaks = d.copy(uri = uriStr)
                update {
                    if (it.audioUri == uriStr && it.cutEnd <= it.cutStart) it.copy(cutStart = 0.0, cutEnd = d.duration) else it
                }
            } catch (e: Exception) {
                analyzeError = "Không đọc được âm thanh từ file này. Thử MP3, WAV, M4A hoặc MP4."
                peaks = null
            }
            analyzing = false
        }
    }

    // ---------- tải font ----------
    var fontProgress by mutableStateOf<Float?>(null)
        private set
    var fontError by mutableStateOf<String?>(null)
        private set

    fun downloadFonts() {
        if (fontProgress != null) return
        fontError = null
        fontProgress = 0f
        val specs: List<FontSpec> = listOfNotNull(FontCatalog.LATIN, FontCatalog.forLang(project.language))
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    specs.forEachIndexed { i, s ->
                        fonts.download(s) { f -> fontProgress = (i + f) / specs.size }
                    }
                }
            } catch (e: Exception) {
                fontError = "Không tải được font (cần mạng): " + (e.message ?: "")
            }
            fontProgress = null
        }
    }
}
