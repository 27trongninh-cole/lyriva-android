package com.lyriva.ninfinity

import android.graphics.Color as AColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lyriva.ninfinity.ui.LrcScreen
import com.lyriva.ninfinity.ui.Lc
import com.lyriva.ninfinity.ui.LyrivaTheme
import com.lyriva.ninfinity.ui.SettingsScreen
import com.lyriva.ninfinity.ui.VideoScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AColor.TRANSPARENT)
        )
        setContent { LyrivaTheme { AppRoot() } }
    }
}

@Composable
fun AppRoot() {
    val vm: AppViewModel = viewModel()
    var tab by rememberSaveable { mutableStateOf(0) }

    Column(Modifier.fillMaxSize().background(Lc.Bg).statusBarsPadding().imePadding()) {
        // Đầu trang cố định
        Row(
            Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("LYRIVA", color = Lc.Ink, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 5.sp)
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (tab) {
                0 -> LrcScreen(vm)
                1 -> VideoScreen(vm)
                else -> SettingsScreen()
            }
        }
        // Thanh điều hướng dưới cố định
        Row(Modifier.fillMaxWidth().background(Lc.Card).navigationBarsPadding().height(60.dp)) {
            listOf("Tạo LRC", "Dựng video", "Cài đặt").forEachIndexed { i, label ->
                Box(
                    Modifier.weight(1f).fillMaxHeight().clickable { tab = i },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        label, fontSize = 14.sp, maxLines = 1,
                        fontWeight = if (i == tab) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (i == tab) Lc.Acc else Lc.Mute
                    )
                }
            }
        }
    }
}
