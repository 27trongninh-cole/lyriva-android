package com.lyriva.ninfinity.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lyriva.ninfinity.core.TimeUtils

/*
 * Quy tắc chung: mọi thành phần có CHIỀU CAO CỐ ĐỊNH, chữ dài thì cắt bằng "…",
 * không có nhãn nổi hay animation đổi kích thước nên bố cục không bao giờ nhảy.
 */

val Tnum = TextStyle(fontFeatureSettings = "tnum")
val R10 = RoundedCornerShape(10.dp)
val R12 = RoundedCornerShape(12.dp)
val R14 = RoundedCornerShape(14.dp)

@Composable
fun Btn(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    small: Boolean = false,
    enabled: Boolean = true,
    icon: String? = null,
    iconRight: Boolean = false
) {
    // Màu theo từng trạng thái (không dùng độ trong suốt) để nút luôn rõ ràng trên nền tối.
    val bg = when {
        primary && enabled -> Lc.Acc
        primary -> Lc.Hl
        else -> Lc.Card2
    }
    val fg = when {
        primary && enabled -> Lc.OnAcc
        enabled -> Lc.Ink
        else -> Lc.Mute
    }
    val border = if (primary && enabled) Lc.Acc else Lc.Line
    Box(
        modifier.height(if (small) 40.dp else 48.dp)
            .background(bg, R14)
            .border(1.dp, border, R14)
            .clip(R14)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(horizontal = 12.dp)
        ) {
            if (icon != null && !iconRight) Ico(icon, fg, 18.dp)
            Text(
                text, color = fg, fontSize = if (small) 13.sp else 15.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            if (icon != null && iconRight) Ico(icon, fg, 18.dp)
        }
    }
}

@Composable
fun IconBtn(icon: String, onClick: () -> Unit, modifier: Modifier = Modifier, filled: Boolean = false, enabled: Boolean = true) {
    Box(
        modifier.size(40.dp).clip(CircleShape).alpha(if (enabled) 1f else 0.4f)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Ico(icon, Lc.Ink, 22.dp, filled) }
}

@Composable
fun Label(text: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(16.dp), contentAlignment = Alignment.CenterStart) {
        Text(text, color = Lc.Mute, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Ô nhập có áp dụng khi bấm Xong hoặc rời ô, dùng cho số/thời gian. */
@Composable
fun CommitBox(
    shown: String,
    onCommit: (String) -> Unit,
    modifier: Modifier = Modifier,
    center: Boolean = true,
    decimal: Boolean = false
) {
    var text by remember { mutableStateOf(shown) }
    var focused by remember { mutableStateOf(false) }
    val fm = LocalFocusManager.current
    LaunchedEffect(shown, focused) { if (!focused) text = shown }
    Box(
        modifier.height(44.dp).clip(R12).background(Lc.Card2)
            .border(1.dp, if (focused) Lc.Acc else Lc.Line, R12).padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        BasicTextField(
            value = text, onValueChange = { text = it }, singleLine = true,
            textStyle = TextStyle(
                color = Lc.Ink, fontSize = 16.sp,
                textAlign = if (center) TextAlign.Center else TextAlign.Start,
                fontFeatureSettings = "tnum"
            ),
            cursorBrush = SolidColor(Lc.Acc),
            keyboardOptions = KeyboardOptions(
                keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Text,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(onDone = {
                onCommit(text)
                fm.clearFocus()
            }),
            modifier = Modifier.fillMaxWidth().onFocusChanged { st ->
                if (focused && !st.isFocused) onCommit(text)
                focused = st.isFocused
            }
        )
    }
}

@Composable
fun TimeField(label: String, seconds: Double, onCommit: (Double) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Label(label)
        CommitBox(TimeUtils.fmt(seconds), { s -> TimeUtils.parse(s)?.let(onCommit) }, Modifier.fillMaxWidth())
    }
}

/** Nhãn tĩnh phía trên + ô nhập một dòng. Tổng cao 65dp. */
@Composable
fun LField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = ""
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Label(label)
        Box(
            Modifier.fillMaxWidth().height(44.dp).clip(R12).background(Lc.Card2)
                .border(1.dp, Lc.Line, R12).padding(horizontal = 12.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            BasicTextField(
                value = value, onValueChange = onChange, singleLine = true,
                textStyle = TextStyle(color = Lc.Ink, fontSize = 16.sp),
                cursorBrush = SolidColor(Lc.Acc), modifier = Modifier.fillMaxWidth()
            )
            if (value.isEmpty()) {
                Text(
                    placeholder, color = Lc.Mute.copy(alpha = 0.6f), fontSize = 16.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/** Ô nhập nhiều dòng; nội dung dài thì cuộn bên trong chính ô. */
@Composable
fun TextArea(
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    size: Int = 16
) {
    Box(
        modifier.fillMaxWidth().clip(R14).background(Lc.Screen).border(1.dp, Lc.Line, R14).padding(12.dp)
    ) {
        BasicTextField(
            value = value, onValueChange = onChange,
            textStyle = TextStyle(color = Lc.Ink, fontSize = size.sp, lineHeight = (size * 1.5f).sp),
            cursorBrush = SolidColor(Lc.Acc), modifier = Modifier.fillMaxSize()
        )
        if (value.isEmpty()) {
            Text(placeholder, color = Lc.Mute.copy(alpha = 0.6f), fontSize = size.sp, lineHeight = (size * 1.5f).sp)
        }
    }
}

@Composable
fun Seg(
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    colors: List<Color>? = null
) {
    Row(
        modifier.fillMaxWidth().height(44.dp).clip(R12).background(Lc.Card2)
            .border(1.dp, Lc.Line, R12).padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        options.forEachIndexed { i, o ->
            val on = i == selected
            val c = colors?.getOrNull(i) ?: Lc.Acc
            Box(
                Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(9.dp))
                    .background(if (on) c else Color.Transparent).clickable { onSelect(i) },
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (colors != null) Box(Modifier.size(8.dp).clip(CircleShape).background(if (on) Lc.OnAcc else c))
                    Text(
                        o, color = if (on) Lc.OnAcc else Lc.Mute, fontSize = 13.sp,
                        fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium,
                        maxLines = 1, overflow = TextOverflow.Clip
                    )
                }
            }
        }
    }
}

@Composable
fun Sw(checked: Boolean, onChange: (Boolean) -> Unit) {
    Box(
        Modifier.width(46.dp).height(28.dp).clip(CircleShape)
            .background(if (checked) Lc.Acc else Color(0xFF35353A))
            .clickable { onChange(!checked) }
    ) {
        Box(
            Modifier.padding(start = if (checked) 21.dp else 3.dp, top = 3.dp)
                .size(22.dp).clip(CircleShape).background(Color.White)
        )
    }
}

@Composable
fun SwitchLine(
    title: String,
    sub: String?,
    checked: Boolean,
    card: Boolean = false,
    onChange: (Boolean) -> Unit
) {
    val m = if (card) {
        Modifier.fillMaxWidth().height(56.dp).clip(R14).background(Lc.Card).border(1.dp, Lc.Line, R14).padding(horizontal = 14.dp)
    } else {
        Modifier.fillMaxWidth().height(44.dp)
    }
    Row(m.clickable { onChange(!checked) }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Lc.Ink, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (sub != null) Text(sub, color = Lc.Mute, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Sw(checked, onChange)
    }
}

/** Thẻ chọn file cao 58dp. */
@Composable
fun FileCard(icon: String, title: String, sub: String, has: Boolean, action: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(58.dp).clip(R14).background(Lc.Card2).border(1.dp, Lc.Line, R14)
            .clickable(onClick = onClick).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            Modifier.size(36.dp).clip(R10).background(if (has) Lc.Acc else Lc.Hl),
            contentAlignment = Alignment.Center
        ) { Ico(icon, if (has) Lc.OnAcc else Lc.Acc, 20.dp) }
        Column(Modifier.weight(1f)) {
            Text(title, color = Lc.Ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text(sub, color = Lc.Mute, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(action, color = Lc.Acc, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

/** Ghi chú nhỏ, chiều cao giữ chỗ theo số dòng. */
@Composable
fun Note(text: String, lines: Int = 1, modifier: Modifier = Modifier, center: Boolean = false) {
    Box(modifier.fillMaxWidth().height((17 * lines).dp), contentAlignment = if (center) Alignment.Center else Alignment.CenterStart) {
        Text(
            text, color = Lc.Mute, fontSize = 12.sp, lineHeight = 16.sp, maxLines = lines,
            overflow = TextOverflow.Ellipsis, textAlign = if (center) TextAlign.Center else TextAlign.Start
        )
    }
}

@Composable
fun SectionHead(title: String, right: String = "") {
    Row(Modifier.fillMaxWidth().height(24.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = Lc.Ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 1)
        Text(right, color = Lc.Mute, fontSize = 12.sp, maxLines = 1)
    }
}

/** Hàng thanh trượt: nhãn - thanh - giá trị, cao 44dp. */
@Composable
fun SliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    shown: String,
    onChange: (Float) -> Unit
) {
    Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Lc.Mute, fontSize = 13.sp, maxLines = 1, modifier = Modifier.width(76.dp))
        Box(Modifier.weight(1f).height(48.dp), contentAlignment = Alignment.Center) {
            Slider(
                value = value, onValueChange = onChange, valueRange = range,
                colors = SliderDefaults.colors(
                    thumbColor = Lc.Acc, activeTrackColor = Lc.Acc, inactiveTrackColor = Lc.Line
                )
            )
        }
        Text(shown, color = Lc.Ink, fontSize = 13.sp, maxLines = 1, textAlign = TextAlign.End, modifier = Modifier.width(52.dp))
    }
}

@Composable
fun Dots(n: Int, active: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
        for (i in 0 until n) {
            Box(
                Modifier.width(if (i == active) 16.dp else 6.dp).height(6.dp).clip(CircleShape)
                    .background(if (i <= active) Lc.Acc else Color(0xFF3A3A3E))
            )
        }
    }
}

/** Khung có tỉ lệ cố định, vừa khít vùng chứa, không bao giờ giãn méo. */
@Composable
fun FitBox(ratio: Float, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val bw = maxWidth
        val bh = maxHeight
        var w = bw
        var h = bw / ratio
        if (h > bh) {
            h = bh
            w = bh * ratio
        }
        Box(Modifier.size(w, h), content = content)
    }
}

@Composable
fun Spacer12() = Spacer(Modifier.height(12.dp))

@Composable
fun TextBtn(text: String, onClick: () -> Unit) {
    Box(
        Modifier.size(40.dp).clip(CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Text(text, color = Lc.Ink, fontSize = 20.sp) }
}
