package com.lyriva.ninfinity

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lyriva.ninfinity.data.Project
import com.lyriva.ninfinity.data.ProjectStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val store = ProjectStore(app)
    private var saveJob: Job? = null

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
}
