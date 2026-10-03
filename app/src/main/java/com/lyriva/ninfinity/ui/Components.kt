package com.lyriva.ninfinity.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * Quy tắc chung: mọi thành phần có CHIỀU CAO CỐ ĐỊNH, chữ dài thì cắt bằng "…",
 * không có nhãn nổi/animation đổi kích thước, nên bố cục không bao giờ nhảy.
 */

private val R10 = RoundedCornerShape(10.dp)
private val R14 = RoundedCornerShape(14.dp)

@Composable
fun SectionCard(step: Int, title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(R14).background(Lc.Card)
            .border(1.dp, Lc.Line, R14).padding(16.dp)
    ) {
        Row(Modifier.fillMaxWidth().height(28.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(24.dp).clip(RoundedCornerShape(7.dp)).background(Lc.Hl),
                contentAlignment = Alignment.Center
            ) {
                Text(step.toString(), color = Lc.Acc, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.width(10.dp))
            Text(
                title, color = Lc.Ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.height(12.dp))
        content()
    }
}

@Composable
fun LButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    enabled: Boolean = true
) {
    Box(
        modifier.height(48.dp).clip(R10)
            .background(if (primary) Lc.Acc else Lc.Card2)
            .border(1.dp, if (primary) Lc.Acc else Lc.Line, R10)
            .alpha(if (enabled) 1f else 0.4f)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text, color = if (primary) Lc.OnAcc else Lc.Ink, fontSize = 15.sp,
            fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 12.dp)
        )
    }
}

/** Ô nhập một dòng: nhãn tĩnh phía trên, tổng cao 72dp. */
@Composable
fun LField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = ""
) {
    Column(modifier.height(72.dp)) {
        Text(
            label, color = Lc.Mute, fontSize = 12.sp, maxLines = 1,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.height(18.dp)
        )
        Spacer(Modifier.height(4.dp))
        Box(
            Modifier.fillMaxWidth().height(48.dp).clip(R10).background(Lc.Card2)
                .border(1.dp, Lc.Line, R10).padding(horizontal = 14.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            BasicTextField(
                value = value, onValueChange = onChange, singleLine = true,
                textStyle = TextStyle(color = Lc.Ink, fontSize = 15.sp),
                cursorBrush = SolidColor(Lc.Acc), modifier = Modifier.fillMaxWidth()
            )
            if (value.isEmpty()) {
                Text(
                    placeholder, color = Lc.Mute.copy(alpha = 0.6f), fontSize = 15.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/** Ô nhập nhiều dòng, chiều cao cố định, nội dung dài thì cuộn bên trong. */
@Composable
fun LTextArea(
    value: String,
    onChange: (String) -> Unit,
    height: androidx.compose.ui.unit.Dp,
    placeholder: String = ""
) {
    Box(
        Modifier.fillMaxWidth().height(height).clip(R10).background(Lc.Card2)
            .border(1.dp, Lc.Line, R10)
    ) {
        Box(Modifier.fillMaxWidth().fillMaxHeight().verticalScroll(rememberScrollState()).padding(14.dp)) {
            BasicTextField(
                value = value, onValueChange = onChange,
                textStyle = TextStyle(color = Lc.Ink, fontSize = 15.sp, lineHeight = 22.sp),
                cursorBrush = SolidColor(Lc.Acc), modifier = Modifier.fillMaxWidth()
            )
            if (value.isEmpty()) {
                Text(placeholder, color = Lc.Mute.copy(alpha = 0.6f), fontSize = 15.sp)
            }
        }
    }
}

@Composable
fun Segmented(
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier.fillMaxWidth().height(44.dp).clip(R10).background(Lc.Card2)
            .border(1.dp, Lc.Line, R10).padding(3.dp)
    ) {
        options.forEachIndexed { i, o ->
            val on = i == selected
            Box(
                Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(8.dp))
                    .background(if (on) Lc.Acc else Color.Transparent)
                    .clickable { onSelect(i) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    o, color = if (on) Lc.OnAcc else Lc.Mute, fontSize = 13.sp,
                    fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
        }
    }
}

/** Dòng lựa chọn kiểu radio, cao 64dp. */
@Composable
fun OptionRow(title: String, subtitle: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(64.dp).clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(20.dp).clip(CircleShape)
                .border(2.dp, if (selected) Lc.Acc else Lc.Mute, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (selected) Box(Modifier.size(10.dp).clip(CircleShape).background(Lc.Acc))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
            Text(title, color = Lc.Ink, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, color = Lc.Mute, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Ô tích, cao 48dp. */
@Composable
fun LCheck(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(48.dp).clickable { onChange(!checked) },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(22.dp).clip(RoundedCornerShape(6.dp))
                .background(if (checked) Lc.Acc else Color.Transparent)
                .border(2.dp, if (checked) Lc.Acc else Lc.Mute, RoundedCornerShape(6.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (checked) Text("✓", color = Lc.OnAcc, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(14.dp))
        Text(label, color = Lc.Ink, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Dòng trạng thái giữ sẵn chỗ (cao 20dp), có chữ hay không thì bố cục vẫn đứng yên. */
@Composable
fun StatusLine(text: String, color: Color = Lc.Mute) {
    Box(Modifier.fillMaxWidth().height(20.dp), contentAlignment = Alignment.CenterStart) {
        Text(text, color = color, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
