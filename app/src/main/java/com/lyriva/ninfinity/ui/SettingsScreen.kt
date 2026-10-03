package com.lyriva.ninfinity.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lyriva.ninfinity.BuildConfig

@Composable
fun SettingsScreen() {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SectionCard(1, "Font chữ") {
            listOf(
                "Be Vietnam Pro" to "Chữ Latinh, tiếng Việt",
                "Noto Sans KR" to "Tiếng Hàn",
                "Noto Sans JP" to "Tiếng Nhật",
                "Noto Sans SC" to "Tiếng Trung"
            ).forEach { (name, sub) ->
                Row(Modifier.fillMaxWidth().height(64.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(name, color = Lc.Ink, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(sub, color = Lc.Mute, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Box(Modifier.width(96.dp)) { LButton("Tải (P3)", {}, Modifier.fillMaxWidth(), enabled = false) }
                }
            }
        }
        SectionCard(2, "Thông tin") {
            StatusLine("LYRIVA ${BuildConfig.VERSION_NAME}")
            StatusLine(BuildConfig.APPLICATION_ID)
        }
    }
}
