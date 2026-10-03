package com.lyriva.ninfinity

import android.graphics.Color as AColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lyriva.ninfinity.ui.Dots
import com.lyriva.ninfinity.ui.ExportDialog
import com.lyriva.ninfinity.ui.GridScreen
import com.lyriva.ninfinity.ui.IconBtn
import com.lyriva.ninfinity.ui.Ic
import com.lyriva.ninfinity.ui.Ico
import com.lyriva.ninfinity.ui.Lc
import com.lyriva.ninfinity.ui.LrcScreen
import com.lyriva.ninfinity.ui.LyrivaTheme
import com.lyriva.ninfinity.ui.SyncScreen
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

private val PAGE_TITLES = listOf("Chọn nhạc", "Lời bài hát", "Xuất file")

@Composable
fun AppRoot() {
    val vm: AppViewModel = viewModel()
    var tab by rememberSaveable { mutableStateOf(0) }
    var page by rememberSaveable { mutableStateOf(0) }
    var overlay by rememberSaveable { mutableStateOf("") }
    val logoImg = remember { vm.logo.asImageBitmap() }

    BackHandler(enabled = overlay.isEmpty() && tab == 0 && page > 0) { page -= 1 }

    Box(Modifier.fillMaxSize().background(Lc.Bg)) {
        Column(Modifier.fillMaxSize().statusBarsPadding().imePadding()) {
            // đầu trang: kích thước cố định ở mọi trang
            Row(
                Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (tab == 0 && page > 0) IconBtn(Ic.BACK, { page -= 1 }) else Spacer(Modifier.width(40.dp))
                Image(
                    bitmap = logoImg, contentDescription = null,
                    modifier = Modifier.height(18.dp).aspectRatio(vm.logo.width.toFloat() / vm.logo.height)
                )
                Spacer(Modifier.weight(1f))
                Text(
                    if (tab == 0) PAGE_TITLES[page.coerceIn(0, 2)] else "Dựng video",
                    color = Lc.Mute, fontSize = 13.sp, maxLines = 1
                )
                Box(Modifier.width(66.dp).padding(start = 10.dp), contentAlignment = Alignment.CenterEnd) {
                    if (tab == 0) Dots(3, page)
                }
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (tab == 0) {
                    LrcScreen(
                        vm, page, { page = it },
                        openSync = { overlay = "sync" },
                        goVideo = { tab = 1 }
                    )
                } else {
                    VideoScreen(vm, openGrid = { overlay = "grid" })
                }
            }
            Row(Modifier.fillMaxWidth().background(Lc.Card).navigationBarsPadding().height(58.dp)) {
                listOf("Tạo LRC" to Ic.WAVE, "Dựng video" to Ic.FILM).forEachIndexed { i, (label, icon) ->
                    val on = tab == i
                    Column(
                        Modifier.weight(1f).fillMaxHeight().clickable { tab = i },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
                    ) {
                        Ico(icon, if (on) Lc.Acc else Lc.Mute, 22.dp)
                        Text(
                            label, color = if (on) Lc.Acc else Lc.Mute, fontSize = 11.sp, maxLines = 1,
                            fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal
                        )
                    }
                }
            }
        }

        when (overlay) {
            "sync" -> Box(Modifier.fillMaxSize().pointerInput(Unit) {}) {
                SyncScreen(vm, onClose = { overlay = "" }, onDone = {
                    overlay = ""
                    page = 2
                })
            }
            "grid" -> Box(Modifier.fillMaxSize().pointerInput(Unit) {}) {
                GridScreen(vm) { overlay = "" }
            }
        }
        ExportDialog()
    }
}
