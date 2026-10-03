package com.lyriva.ninfinity.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lyriva.ninfinity.AppViewModel
import com.lyriva.ninfinity.core.LyricsBuilder
import com.lyriva.ninfinity.core.SyncMode

@Composable
fun LrcScreen(vm: AppViewModel) {
    val p = vm.project
    val ctx = LocalContext.current

    val pickSource = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: Exception) {
            }
            val name = displayName(ctx, uri)
            val isVideo = ctx.contentResolver.getType(uri)?.startsWith("video/") == true
            vm.update {
                it.copy(audioUri = uri.toString(), sourceName = name, sourceIsVideo = isVideo, marks = emptyList())
            }
        }
    }

    val built = remember(p.lyricsText, p.syncMode) { LyricsBuilder.build(p.lyricsText, p.syncMode) }
    var xl by rememberSaveable { mutableStateOf(true) }
    var xe by rememberSaveable { mutableStateOf(false) }
    var xs by rememberSaveable { mutableStateOf(true) }
    var xw by rememberSaveable { mutableStateOf(true) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SectionCard(1, "Nhạc/video và đoạn cần cắt") {
            LButton("Chọn nhạc hoặc video", { pickSource.launch(arrayOf("audio/*", "video/*")) },
                Modifier.fillMaxWidth(), primary = true)
            StatusLine(
                if (p.audioUri == null) "Chưa chọn file"
                else (if (p.sourceIsVideo) "Video: " else "Nhạc: ") + p.sourceName
            )
            Box(
                Modifier.fillMaxWidth().height(110.dp).clip(RoundedCornerShape(10.dp)).background(Lc.Screen),
                contentAlignment = Alignment.Center
            ) {
                Text("Sóng nhạc và kéo cắt đoạn: có ở giai đoạn P2", color = Lc.Mute, fontSize = 12.sp)
            }
        }

        SectionCard(2, "Lời bài hát") {
            LTextArea(
                p.lyricsText, { v -> vm.update { it.copy(lyricsText = v) } }, 180.dp,
                "Mỗi dòng là một câu. Dòng trống sẽ được bỏ qua."
            )
        }

        SectionCard(3, "Kiểu hiển thị") {
            OptionRow("Cả câu", "Mỗi dòng xuất hiện một lần", p.syncMode == SyncMode.LINE) {
                vm.update { it.copy(syncMode = SyncMode.LINE) }
            }
            OptionRow("Từng chữ một", "Trước → mắt → em", p.syncMode == SyncMode.WORD) {
                vm.update { it.copy(syncMode = SyncMode.WORD) }
            }
            OptionRow("Tăng dần", "Trước → Trước mắt → Trước mắt em", p.syncMode == SyncMode.GROW) {
                vm.update { it.copy(syncMode = SyncMode.GROW) }
            }
        }

        SectionCard(4, "Đồng bộ") {
            OptionRow("Chạm đầu tiên chỉ phát nhạc", "Các lần chạm sau mới đánh dấu", !p.firstTapMarks) {
                vm.update { it.copy(firstTapMarks = false) }
            }
            OptionRow("Chạm đầu tiên phát nhạc và đánh dấu luôn", "Mục đầu có mốc 0:00.00", p.firstTapMarks) {
                vm.update { it.copy(firstTapMarks = true) }
            }
            LButton("Mở cửa sổ đồng bộ (P2)", {}, Modifier.fillMaxWidth(), primary = true, enabled = false)
            StatusLine("Cần đánh dấu ${built.items.size} mục")
        }

        SectionCard(5, "Xuất file") {
            LCheck("LRC", xl) { xl = it }
            LCheck("LRC nâng cao (mốc từng chữ)", xe) { xe = it }
            LCheck("SRT", xs) { xs = it }
            LCheck("Nhạc đã cắt (WAV)", xw) { xw = it }
            LButton("Tải xuống (P2)", {}, Modifier.fillMaxWidth(), enabled = false)
        }
    }
}
