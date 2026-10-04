package com.lyriva.ninfinity.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.lyriva.ninfinity.AppViewModel
import com.lyriva.ninfinity.audio.AudioDecoder
import com.lyriva.ninfinity.core.Fade
import com.lyriva.ninfinity.core.LrcParser
import com.lyriva.ninfinity.core.SingerPalette
import com.lyriva.ninfinity.core.TextUtils
import com.lyriva.ninfinity.core.TimeUtils
import com.lyriva.ninfinity.data.Aspect
import com.lyriva.ninfinity.data.BgFit
import com.lyriva.ninfinity.data.Lang
import com.lyriva.ninfinity.export.ExportService
import com.lyriva.ninfinity.export.ExportState
import com.lyriva.ninfinity.export.MediaSaver
import com.lyriva.ninfinity.render.BgFrames
import com.lyriva.ninfinity.render.LyricsRenderer
import com.lyriva.ninfinity.render.RenderData
import com.lyriva.ninfinity.render.ThumbBg
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/** Khung xem thử vẽ bằng chính LyricsRenderer, co theo màn hình nhưng giữ đúng bố cục video thật. */
@Composable
fun LyricCanvas(
    renderer: LyricsRenderer,
    t: () -> Double,
    bg: () -> Bitmap?,
    paused: () -> Boolean,
    thumbBg: () -> Bitmap?,
    modifier: Modifier = Modifier
) {
    Canvas(modifier) {
        val tt = t()
        val b = bg()
        renderer.paused = paused()
        renderer.thumbBg = thumbBg()
        val sc = size.width / renderer.data.width
        drawIntoCanvas { c ->
            val nc = c.nativeCanvas
            nc.save()
            nc.scale(sc, sc)
            renderer.draw(nc, tt, b)
            nc.restore()
        }
    }
}

@Composable
private fun PlayerBar(
    playing: Boolean,
    onToggle: () -> Unit,
    frac: Float,
    onSeek: (Float) -> Unit,
    label: String,
    onExpand: (() -> Unit)?
) {
    Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
        IconBtn(if (playing) Ic.PAUSE else Ic.PLAY, onToggle, filled = true)
        Box(Modifier.weight(1f).height(48.dp), contentAlignment = Alignment.Center) {
            Slider(
                value = frac, onValueChange = onSeek,
                colors = SliderDefaults.colors(thumbColor = Lc.Acc, activeTrackColor = Lc.Acc, inactiveTrackColor = Lc.Line)
            )
        }
        Text(label, color = Lc.Mute, fontSize = 12.sp, style = Tnum, maxLines = 1, modifier = Modifier.padding(horizontal = 6.dp))
        if (onExpand != null) IconBtn(Ic.EXPAND, onExpand)
    }
}

private val TOOLS = listOf(
    "Nguồn" to Ic.WAVE, "Bài hát" to Ic.MUSIC, "Lời phụ" to Ic.SUB, "Nền" to Ic.FILM,
    "Khung" to Ic.CROP, "Bìa" to Ic.IMG, "Hiệu ứng" to Ic.SPARK, "Xuất" to Ic.UP
)

@Composable
fun VideoScreen(vm: AppViewModel, openGrid: () -> Unit) {
    val p = vm.project
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var tool by rememberSaveable { mutableStateOf(0) }
    var big by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf<List<String>?>(null) }
    var phuTab by rememberSaveable { mutableStateOf(0) }
    var biaTab by rememberSaveable { mutableStateOf(0) }
    var showPalette by remember { mutableStateOf(false) }
    val groups = remember(p.palettesText) { SingerPalette.parse(p.palettesText) }
    val groupNames = listOf("Tự động") + groups.map { it.name.ifEmpty { "(không tên)" } }
    val gi = if (p.activeGroup.isEmpty()) 0 else groups.indexOfFirst { it.name == p.activeGroup }.let { if (it < 0) 0 else it + 1 }
    fun stepGroup(d: Int) {
        val n = groupNames.size
        val ni = ((gi + d) % n + n) % n
        vm.update { it.copy(activeGroup = if (ni == 0) "" else groups[ni - 1].name) }
    }

    // ---- renderer dùng chung cho xem thử ----
    val renderer = remember { LyricsRenderer(vm.fonts, vm.logo) }
    val data = remember(p) { RenderData.from(p) }
    remember(data) {
        renderer.data = data
        0
    }
    val empty = p.audioUri == null || data.items.isEmpty()

    // ---- player ----
    val s = p.cutStart
    val player = rememberPlayer(p.audioUri)
    var durGuess by remember { mutableStateOf(0.0) }
    val e = if (p.cutEnd > p.cutStart) p.cutEnd else max(s + 1.0, durGuess)
    val eNow by rememberUpdatedState(e)
    var playing by remember { mutableStateOf(false) }
    val fadeInNow by rememberUpdatedState(p.fadeIn)
    val fadeOutNow by rememberUpdatedState(p.fadeOut)
    val pos = rememberPlayerPos(player) { t ->
        player?.volume = Fade.gain(t - s, eNow - s, fadeInNow, fadeOutNow)
        if (durGuess == 0.0) {
            val d = player?.duration ?: 0L
            if (d > 0) durGuess = d / 1000.0
        }
        if (playing && t >= eNow) {
            player?.pause()
            playing = false
        }
    }
    LaunchedEffect(player, s) {
        player?.seekTo((s * 1000).toLong())
        player?.pause()
        playing = false
    }
    fun toggle() {
        val pl = player ?: return
        if (playing) {
            pl.pause()
            playing = false
        } else {
            if (pos.value >= e - 0.05 || pos.value < s - 0.05) pl.seekTo((s * 1000).toLong())
            pl.playWhenReady = true
            playing = true
        }
    }
    fun seekFrac(f: Float) {
        player?.seekTo(((s + f * (e - s)) * 1000).toLong())
    }

    // ---- khung video nền cho xem thử ----
    var bgBmp by remember { mutableStateOf<Bitmap?>(null) }
    val sForBg by rememberUpdatedState(s)
    val offForBg by rememberUpdatedState(p.bgOffset)
    LaunchedEffect(p.bgUri, p.bgEnabled) {
        bgBmp = null
        val uri = p.bgUri
        if (!p.bgEnabled || uri == null) return@LaunchedEffect
        val bf = BgFrames(ctx)
        val ok = withContext(Dispatchers.IO) { bf.open(uri) }
        if (!ok) {
            bf.close()
            return@LaunchedEffect
        }
        try {
            var last = -1.0
            while (isActive) {
                val want = offForBg + max(0.0, pos.value - sForBg)
                if (abs(want - last) > 0.1) {
                    last = want
                    val b = withContext(Dispatchers.IO) { bf.frameAt(want, 720) }
                    if (b != null) bgBmp = b
                } else {
                    delay(30)
                }
            }
        } finally {
            bf.close()
        }
    }

    // ---- khung video nền lồng vào thumbnail ----
    var thumbBgBmp by remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(p.bgUri, p.thumbBgOn, p.thumbBgTime) {
        thumbBgBmp = withContext(Dispatchers.IO) { ThumbBg.load(ctx, p) }
    }

    // ---- chọn file ----
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
    val pickAudio = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: Exception) {
            }
            val name = displayName(ctx, uri)
            val isVideo = ctx.contentResolver.getType(uri)?.startsWith("video/") == true
            scope.launch {
                val d = withContext(Dispatchers.IO) { AudioDecoder.durationSec(ctx, uri) } ?: 0.0
                vm.update {
                    it.copy(
                        audioUri = uri.toString(), sourceName = name, sourceIsVideo = isVideo,
                        cutStart = 0.0, cutEnd = d, marks = emptyList(),
                        bgUri = if (isVideo && it.bgUri == null) uri.toString() else it.bgUri
                    )
                }
            }
        }
    }
    val pickBg = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: Exception) {
            }
            vm.update { it.copy(bgUri = uri.toString(), bgOffset = 0.0, bgEnabled = true) }
        }
    }

    // ---- xuất video ----
    val goExport: () -> Unit = {
        vm.saveNow()
        ExportState.begin()
        ContextCompat.startForegroundService(ctx, Intent(ctx, ExportService::class.java))
    }
    val notifPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { _ -> goExport() }
    fun startFlow() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            goExport()
        }
    }
    fun requestExport() {
        if (empty) {
            msg = "Cần có nhạc và lời trước khi xuất."
            return
        }
        val warns = ArrayList<String>()
        if (data.bgOn && p.credit.isBlank()) warns.add("Bạn đang dùng video nền nhưng chưa nhập credit cho tác giả video.")
        if (!vm.fonts.readyFor(p.language)) warns.add("Chưa tải font, video sẽ dùng font hệ thống thay cho Be Vietnam Pro.")
        if (warns.isEmpty()) startFlow() else confirm = warns
    }

    val stem = TextUtils.fileStem(p.title.ifBlank { "lyriva" })
    val ratio = if (p.aspect == Aspect.V) 9f / 16f else 16f / 9f
    val frac = if (e > s) ((pos.value - s) / (e - s)).toFloat().coerceIn(0f, 1f) else 0f
    val label = TimeUtils.fmtShort(max(0.0, pos.value - s)) + " / " + TimeUtils.fmtShort(max(0.0, e - s))

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val sheetH = (maxHeight * 0.45f).coerceIn(250.dp, 340.dp)
        Column(Modifier.fillMaxSize()) {
            // ---------- sân khấu xem thử (luôn cùng kích thước) ----------
            Column(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp)) {
                FitBox(ratio, Modifier.weight(1f).fillMaxWidth()) {
                    LyricCanvas(renderer, { max(0.0, pos.value - s) }, { bgBmp }, { !playing }, { thumbBgBmp }, Modifier.fillMaxSize().clip(R10))
                    if (empty) {
                        Box(Modifier.fillMaxSize().background(Color(0xCC111111)), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(16.dp)) {
                                Text("Chưa có nhạc và lời", color = Lc.Ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                                Text("Chọn nguồn ở bảng bên dưới", color = Lc.Mute, fontSize = 12.sp)
                            }
                        }
                    }
                }
                PlayerBar(playing, { toggle() }, frac, { f -> seekFrac(f) }, label, { big = true })
            }

            // ---------- bảng cài đặt: chiều cao cố định ----------
            Column(
                Modifier.fillMaxWidth().height(sheetH).background(Lc.Card)
                    .verticalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                when (tool) {
                    0 -> {
                        SectionHead("Nguồn")
                        FileCard(
                            Ic.TEXT, "Lời (.lrc)",
                            if (data.items.isEmpty()) "Chưa chọn" else "${data.items.size} dòng lời",
                            data.items.isNotEmpty(), if (data.items.isEmpty()) "Chọn" else "Đổi"
                        ) { pickLrc.launch(arrayOf("*/*")) }
                        FileCard(
                            Ic.MUSIC, "Nhạc",
                            if (p.audioUri == null) "Chưa chọn" else p.sourceName + " • " + TimeUtils.fmtShort(max(0.0, e - s)),
                            p.audioUri != null, if (p.audioUri == null) "Chọn" else "Đổi"
                        ) { pickAudio.launch(arrayOf("audio/*", "video/*")) }
                        val fp = vm.fontProgress
                        val fontReady = vm.fonts.readyFor(p.language)
                        FileCard(
                            Ic.AA, "Font chữ",
                            when {
                                fp != null -> "Đang tải… ${(fp * 100).roundToInt()}%"
                                vm.fontError != null -> "Tải lỗi, chạm để thử lại"
                                fontReady -> "Be Vietnam Pro" + (p.language.fontName?.let { " + $it" } ?: "") + " • đã tải"
                                else -> "Chưa tải, đang dùng font hệ thống"
                            },
                            fontReady, if (fontReady) "Đã tải" else if (fp != null) "…" else "Tải"
                        ) { if (fp == null && !fontReady) vm.downloadFonts() }
                    }
                    1 -> {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            LField("Tên bài", p.title, { v -> vm.update { it.copy(title = v) } }, Modifier.weight(1f), "I Wanna Be Yours")
                            LField("Nghệ sĩ", p.artist, { v -> vm.update { it.copy(artist = v) } }, Modifier.weight(1f), "Arctic Monkeys")
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Label("Ngôn ngữ bài hát")
                            Seg(
                                Lang.values().map { it.label }, p.language.ordinal,
                                { i -> vm.update { it.copy(language = Lang.values()[i]) } },
                                colors = Lang.values().map { Color(0xFF000000 or it.colorRgb.toLong()) }
                            )
                        }
                        if (p.language.fontName != null) {
                            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                Label("Lời gốc (mỗi dòng ứng với một câu)")
                                TextArea(
                                    p.originalLyrics, { v -> vm.update { it.copy(originalLyrics = v) } },
                                    Modifier.height(72.dp), "Dán lời tiếng gốc…", size = 14
                                )
                            }
                        }
                    }
                    2 -> {
                        Seg(listOf("Vietsub", "Người hát"), phuTab, { phuTab = it })
                        if (phuTab == 0) {
                            SwitchLine("Hiện Vietsub", null, p.vietsubOn) { v -> vm.update { it.copy(vietsubOn = v) } }
                            TextArea(
                                p.vietsubText, { v -> vm.update { it.copy(vietsubText = v) } },
                                Modifier.height(88.dp), "Dán bản dịch, mỗi dòng một câu…", size = 14
                            )
                            Note("Dòng n ứng với câu n. Dùng //…// để ẩn phần không muốn hiện.", 1)
                            if (p.language.fontName != null) {
                                SwitchLine("Hiện romanized", null, p.romanOn) { v -> vm.update { it.copy(romanOn = v) } }
                            }
                        } else {
                            SwitchLine("Hiện tên người hát", null, p.singerOn) { v -> vm.update { it.copy(singerOn = v) } }
                            TextArea(
                                p.singersText, { v -> vm.update { it.copy(singersText = v) } },
                                Modifier.height(72.dp), "Dòng n = người hát câu n. Để trống = như câu trước, “-” = không hiện.", size = 14
                            )
                            Row(Modifier.fillMaxWidth().height(44.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("Bảng màu", color = Lc.Mute, fontSize = 13.sp, maxLines = 1, modifier = Modifier.width(72.dp))
                                TextBtn("‹") { stepGroup(-1) }
                                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                    Text(groupNames[gi], color = Lc.Ink, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                TextBtn("›") { stepGroup(1) }
                                Btn("Sửa", { showPalette = true }, Modifier.width(72.dp), small = true)
                            }
                            Note("Nhiều người cùng hát: ngăn cách bằng dấu phẩy hoặc &.", 1)
                        }
                    }
                    3 -> {
                        SwitchLine("Video nền", "Dùng video cover thay màu đen", p.bgEnabled) { v -> vm.update { it.copy(bgEnabled = v) } }
                        FileCard(
                            Ic.FILM, "Video cover",
                            if (p.bgUri == null) "Chưa chọn" else "Đã chọn",
                            p.bgUri != null, if (p.bgUri == null) "Chọn" else "Đổi"
                        ) { pickBg.launch(arrayOf("video/*")) }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            TimeField("Bắt đầu từ", p.bgOffset, { x -> vm.update { it.copy(bgOffset = max(0.0, x)) } }, Modifier.weight(1f))
                            LField("Credit", p.credit, { v -> vm.update { it.copy(credit = v) } }, Modifier.weight(1.4f), "Video by …")
                        }
                    }
                    4 -> {
                        Seg(listOf("Phủ kín khung", "Vừa khung"), p.bgFit.ordinal, { i -> vm.update { it.copy(bgFit = BgFit.values()[i]) } })
                        SliderRow("Thu phóng", p.bgZoom.toFloat(), 50f..300f, "${p.bgZoom}%") { v -> vm.update { it.copy(bgZoom = v.roundToInt()) } }
                        SliderRow("Dời ngang", p.bgPanX.toFloat(), -50f..50f, "${p.bgPanX}") { v -> vm.update { it.copy(bgPanX = v.roundToInt()) } }
                        SliderRow("Dời dọc", p.bgPanY.toFloat(), -50f..50f, "${p.bgPanY}") { v -> vm.update { it.copy(bgPanY = v.roundToInt()) } }
                        SliderRow("Độ tối", p.bgDim.toFloat(), 0f..85f, "${p.bgDim}%") { v -> vm.update { it.copy(bgDim = v.roundToInt()) } }
                    }
                    5 -> {
                        Seg(listOf("Chung", "Nền bìa"), biaTab, { biaTab = it })
                        if (biaTab == 0) {
                            SwitchLine("Thumbnail đầu video", null, p.thumbEnabled) { v -> vm.update { it.copy(thumbEnabled = v) } }
                            Row(Modifier.fillMaxWidth().height(44.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("Thời lượng (giây)", color = Lc.Mute, fontSize = 13.sp, modifier = Modifier.weight(1f), maxLines = 1)
                                CommitBox(
                                    String.format(Locale.US, "%.2f", p.thumbDuration),
                                    { x -> x.replace(',', '.').toDoubleOrNull()?.let { d -> vm.update { it.copy(thumbDuration = d.coerceIn(0.02, 0.5)) } } },
                                    Modifier.width(100.dp), decimal = true
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Btn("Lưới 3 cột", { openGrid() }, Modifier.weight(1f), icon = Ic.GRID)
                                Btn("Tải ảnh bìa", {
                                    scope.launch {
                                        val r = withContext(Dispatchers.IO) {
                                            runCatching {
                                                val rr = LyricsRenderer(vm.fonts, vm.logo)
                                                rr.data = RenderData.from(vm.project)
                                                rr.thumbBg = ThumbBg.load(ctx, vm.project)
                                                val uri = MediaSaver.savePng(ctx, stem + "_cover.png", rr.thumb.renderCover())
                                                if (uri == null) error("Không lưu được ảnh")
                                            }
                                        }
                                        msg = if (r.isSuccess) "Đã lưu ảnh bìa vào Pictures/LYRIVA" else "Lỗi: " + (r.exceptionOrNull()?.message ?: "")
                                    }
                                }, Modifier.weight(1f), icon = Ic.DL)
                            }
                            Note(if (msg.isNotEmpty()) msg else "Ô thứ ${p.thumbGridPos + 1} trong lưới hồ sơ.", 1)
                        } else {
                            SwitchLine("Lồng khung video vào nền bìa", null, p.thumbBgOn) { v -> vm.update { it.copy(thumbBgOn = v) } }
                            Row(Modifier.fillMaxWidth().height(65.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Bottom) {
                                TimeField("Thời điểm trong video nền", p.thumbBgTime, { x -> vm.update { it.copy(thumbBgTime = max(0.0, x)) } }, Modifier.weight(1f))
                                Btn("Lấy khung hiện tại", {
                                    val t = p.bgOffset + max(0.0, pos.value - s)
                                    vm.update { it.copy(thumbBgTime = t, thumbBgOn = true) }
                                }, Modifier.weight(1f), small = true, enabled = p.bgUri != null)
                            }
                            SliderRow("Độ tối", p.thumbBgDim.toFloat(), 20f..90f, "${p.thumbBgDim}%") { v -> vm.update { it.copy(thumbBgDim = v.roundToInt()) } }
                            Note(
                                if (p.bgUri == null) "Cần chọn video nền ở bảng Nền trước."
                                else "Tua bản xem thử tới khung đẹp rồi bấm Lấy khung hiện tại.", 2
                            )
                        }
                    }
                    6 -> {
                        SliderRow("Hiện lời sớm", p.leadMs.toFloat(), 0f..600f, "${p.leadMs}ms") { v ->
                            vm.update { it.copy(leadMs = (v / 10f).roundToInt() * 10) }
                        }
                        SliderRow("Nhạc vào dần", p.fadeIn.toFloat(), 0f..8f, String.format(Locale.US, "%.1fs", p.fadeIn)) { v ->
                            vm.update { it.copy(fadeIn = (v * 2).roundToInt() / 2.0) }
                        }
                        SliderRow("Nhạc ra dần", p.fadeOut.toFloat(), 0f..8f, String.format(Locale.US, "%.1fs", p.fadeOut)) { v ->
                            vm.update { it.copy(fadeOut = (v * 2).roundToInt() / 2.0) }
                        }
                        Note("Nhạc vào/ra dần áp dụng cho tiếng của video xuất và nghe được ngay ở bản xem thử.", 2)
                    }
                    else -> {
                        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Label("Khung hình")
                            Seg(
                                listOf("Dọc 9:16 · 1080×1920", "Ngang 16:9 · 1280×720"), p.aspect.ordinal,
                                { i -> vm.update { it.copy(aspect = Aspect.values()[i]) } }
                            )
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Label("Tốc độ xuất")
                            Seg(
                                listOf("Chuẩn 30fps", "Nhanh 24fps"), if (p.fastExport) 1 else 0,
                                { i -> vm.update { it.copy(fastExport = i == 1) } }
                            )
                        }
                        Btn(
                            "Xuất video MP4", { requestExport() }, Modifier.fillMaxWidth(), primary = true,
                            enabled = !ExportState.running, icon = Ic.UP
                        )
                        if (msg.isNotEmpty()) Note(msg, 1, center = true)
                    }
                }
            }

            // ---------- thanh công cụ ----------
            Row(Modifier.fillMaxWidth().height(58.dp).background(Lc.Bg)) {
                TOOLS.forEachIndexed { i, (name, icon) ->
                    val on = i == tool
                    Column(
                        Modifier.weight(1f).fillMaxHeight().clickable { tool = i; msg = "" },
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
                    ) {
                        Ico(icon, if (on) Lc.Acc else Lc.Mute, 22.dp)
                        Text(name, color = if (on) Lc.Acc else Lc.Mute, fontSize = 10.sp, maxLines = 1, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }

    // ---------- xem thử toàn màn hình ----------
    if (big) {
        Dialog(
            onDismissRequest = { big = false },
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
        ) {
            Column(Modifier.fillMaxSize().background(Lc.Bg).statusBarsPadding().navigationBarsPadding().padding(horizontal = 14.dp)) {
                Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconBtn(Ic.X, { big = false })
                }
                FitBox(ratio, Modifier.weight(1f).fillMaxWidth()) {
                    LyricCanvas(renderer, { max(0.0, pos.value - s) }, { bgBmp }, { !playing }, { thumbBgBmp }, Modifier.fillMaxSize().clip(R10))
                }
                PlayerBar(playing, { toggle() }, frac, { f -> seekFrac(f) }, label, null)
            }
        }
    }

    // ---------- bảng màu ca sĩ ----------
    if (showPalette) {
        Dialog(
            onDismissRequest = { showPalette = false },
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
        ) {
            Column(
                Modifier.fillMaxSize().background(Lc.Bg).statusBarsPadding().navigationBarsPadding().imePadding().padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(Modifier.fillMaxWidth().height(46.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconBtn(Ic.X, { showPalette = false })
                    Text("Bảng màu ca sĩ", color = Lc.Ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }
                Note("Mỗi nhóm bắt đầu bằng dòng “Tên nhóm: …”, sau đó mỗi dòng “Tên: #RRGGBB”. Màu chỉ áp dụng cho tên người hát.", 3)
                TextArea(
                    p.palettesText, { v -> vm.update { it.copy(palettesText = v) } }, Modifier.weight(1f),
                    "Tên nhóm: CORTIS\nJames: #123456\nMartin: #234567", size = 14
                )
                Note("Đọc được ${groups.size} nhóm, ${groups.sumOf { it.members.size }} màu.", 1)
                Btn("Xong", { showPalette = false }, Modifier.fillMaxWidth(), primary = true)
            }
        }
    }

    // ---------- xác nhận trước khi xuất ----------
    val warns = confirm
    if (warns != null) {
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text("Kiểm tra trước khi xuất") },
            text = { Text(warns.joinToString("\n\n")) },
            confirmButton = {
                TextButton(onClick = {
                    confirm = null
                    startFlow()
                }) { Text("Vẫn xuất", color = Lc.Acc) }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Quay lại", color = Lc.Mute) } }
        )
    }
}
