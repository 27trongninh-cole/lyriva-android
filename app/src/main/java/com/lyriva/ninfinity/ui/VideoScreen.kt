package com.lyriva.ninfinity.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lyriva.ninfinity.AppViewModel
import com.lyriva.ninfinity.core.LrcParser
import com.lyriva.ninfinity.core.TextUtils
import com.lyriva.ninfinity.data.Aspect
import com.lyriva.ninfinity.data.Lang
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun VideoScreen(vm: AppViewModel) {
    val p = vm.project
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val parsed = remember(p.lrcText) { LrcParser.parse(p.lrcText) }

    val pickLrc = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                val text = withContext(Dispatchers.IO) { readText(ctx, uri) }
                if (text != null) {
                    val r = LrcParser.parse(text)
                    vm.update {
                        it.copy(
                            lrcText = text,
                            title = if (it.title.isBlank()) (r.meta["ti"] ?: "") else it.title,
                            artist = if (it.artist.isBlank()) (r.meta["ar"] ?: "") else it.artist
                        )
                    }
                }
            }
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Khung xem thử: cao cố định 440dp, bên trong chỉ khung hình đổi tỉ lệ
        Box(
            Modifier.fillMaxWidth().height(440.dp).clip(RoundedCornerShape(14.dp))
                .background(Lc.Screen).border(1.dp, Lc.Line, RoundedCornerShape(14.dp))
                .padding(10.dp),
            contentAlignment = Alignment.Center
        ) {
            val vertical = p.aspect == Aspect.V
            Box(
                (if (vertical) Modifier.fillMaxHeight() else Modifier.fillMaxWidth())
                    .aspectRatio(if (vertical) 9f / 16f else 16f / 9f)
                    .clip(RoundedCornerShape(6.dp)).background(Lc.Bg).padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        p.title.uppercase().ifEmpty { "TÊN BÀI" }, color = Lc.Ink, fontSize = 13.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center
                    )
                    Text(
                        TextUtils.artistLine(p.artist).ifEmpty { "NGHỆ SĨ" }, color = Lc.Mute, fontSize = 11.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center
                    )
                    Text(
                        parsed.items.firstOrNull()?.s ?: "Xem thử đầy đủ: giai đoạn P3",
                        color = Lc.Acc, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center, modifier = Modifier.padding(top = 16.dp)
                    )
                }
            }
        }

        SectionCard(1, "Lời (.lrc)") {
            LButton("Chọn file LRC", { pickLrc.launch(arrayOf("*/*")) }, Modifier.fillMaxWidth(), primary = true)
            StatusLine(
                if (parsed.items.isEmpty()) "Chưa có lời"
                else "${parsed.items.size} dòng lời" + if (parsed.items.any { it.w != null }) " (tô theo từng chữ)" else ""
            )
        }

        SectionCard(2, "Thông tin bài hát") {
            LField("Tên bài", p.title, { v -> vm.update { it.copy(title = v) } }, placeholder = "I Wanna Be Yours")
            LField("Nghệ sĩ", p.artist, { v -> vm.update { it.copy(artist = v) } }, placeholder = "Arctic Monkeys")
        }

        SectionCard(3, "Ngôn ngữ và khung hình") {
            Text("Ngôn ngữ bài hát", color = Lc.Mute, fontSize = 12.sp, modifier = Modifier.height(18.dp))
            Segmented(Lang.values().map { it.label }, p.language.ordinal, { i ->
                vm.update { it.copy(language = Lang.values()[i]) }
            })
            Box(Modifier.height(12.dp))
            Text("Khung hình", color = Lc.Mute, fontSize = 12.sp, modifier = Modifier.height(18.dp))
            Segmented(listOf("Dọc 9:16 · 1080×1920", "Ngang 16:9 · 1280×720"), p.aspect.ordinal, { i ->
                vm.update { it.copy(aspect = Aspect.values()[i]) }
            })
        }

        LButton("Xuất video MP4 (P5)", {}, Modifier.fillMaxWidth(), primary = true, enabled = false)
    }
}
