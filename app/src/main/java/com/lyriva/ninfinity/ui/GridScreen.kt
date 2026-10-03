package com.lyriva.ninfinity.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lyriva.ninfinity.AppViewModel
import com.lyriva.ninfinity.render.LyricsRenderer
import com.lyriva.ninfinity.render.RenderData

/** Lưới hồ sơ 3×3: chạm một ô để đổi vị trí video này trong lưới. */
@Composable
fun GridScreen(vm: AppViewModel, onClose: () -> Unit) {
    val p = vm.project
    BackHandler { onClose() }
    val renderer = remember { LyricsRenderer(vm.fonts, vm.logo) }
    val data = remember(p) { RenderData.from(p) }
    remember(data) {
        renderer.data = data
        0
    }
    val bmp = remember(data, p.thumbGridPos) { renderer.thumb.renderGrid(p.thumbGridPos).asImageBitmap() }
    Column(
        Modifier.fillMaxSize().background(Lc.Bg).statusBarsPadding().navigationBarsPadding().padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(Modifier.fillMaxWidth().height(46.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBtn(Ic.BACK, onClose)
            Text("Lưới hồ sơ 3 cột", color = Lc.Ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }
        Note("Chạm một ô bất kỳ để đổi vị trí của video này trong lưới. Ô sáng chữ là video này.", 2)
        FitBox(bmp.width.toFloat() / bmp.height, Modifier.weight(1f).fillMaxWidth()) {
            Image(
                bitmap = bmp, contentDescription = null, contentScale = ContentScale.FillBounds,
                modifier = Modifier.fillMaxSize().clip(R10).pointerInput(Unit) {
                    detectTapGestures { o ->
                        val c = (o.x / size.width * 3).toInt().coerceIn(0, 2)
                        val r = (o.y / size.height * 3).toInt().coerceIn(0, 2)
                        vm.update { it.copy(thumbGridPos = r * 3 + c) }
                    }
                }
            )
        }
        Btn("Xong", onClose, Modifier.fillMaxWidth(), primary = true)
    }
}
