package com.lyriva.ninfinity.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.lyriva.ninfinity.export.ExportState
import kotlin.math.roundToInt

/** Hộp thoại tiến trình xuất video: kích thước cố định, chỉ nội dung chữ và thanh tiến trình thay đổi. */
@Composable
fun ExportDialog() {
    if (!ExportState.visible) return
    val ctx = LocalContext.current
    val running = ExportState.running
    val err = ExportState.error
    Dialog(
        onDismissRequest = { ExportState.dismiss() },
        properties = DialogProperties(dismissOnBackPress = !running, dismissOnClickOutside = false)
    ) {
        Column(
            Modifier.fillMaxWidth().height(236.dp).clip(R14).background(Lc.Card).border(1.dp, Lc.Line, R14).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                when {
                    running -> "Đang xuất video"
                    ExportState.done -> "Xuất xong"
                    err != null -> "Không xuất được"
                    else -> "Đã dừng"
                },
                color = Lc.Ink, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 1
            )
            Box(Modifier.fillMaxWidth().height(40.dp)) {
                Text(
                    err ?: ExportState.message, color = Lc.Mute, fontSize = 13.sp, lineHeight = 18.sp,
                    maxLines = 2, overflow = TextOverflow.Ellipsis
                )
            }
            Box(Modifier.fillMaxWidth().height(8.dp).clip(R10).background(Lc.Line)) {
                Box(
                    Modifier.fillMaxWidth(ExportState.progress.coerceIn(0f, 1f)).height(8.dp).clip(R10).background(Lc.Acc)
                )
            }
            Text(
                "${(ExportState.progress * 100).roundToInt()}%", color = Lc.Ink, fontSize = 13.sp,
                style = Tnum, maxLines = 1
            )
            Spacer(Modifier.weight(1f))
            if (running) {
                Btn("Hủy", { ExportState.cancelRequested = true }, Modifier.fillMaxWidth(), small = true)
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    val uri = ExportState.resultUri
                    if (ExportState.done && uri != null) {
                        Btn("Mở video", {
                            try {
                                ctx.startActivity(
                                    Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(uri), "video/mp4")
                                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                )
                            } catch (_: Exception) {
                            }
                        }, Modifier.weight(1f), small = true)
                    }
                    Btn("Đóng", { ExportState.dismiss() }, Modifier.weight(1f), primary = true, small = true)
                }
            }
        }
    }
}
