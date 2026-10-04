package com.lyriva.ninfinity.ui

import android.net.Uri
import android.view.TextureView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import com.lyriva.ninfinity.audio.AudioDecoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

@Composable
fun rememberPlayer(uri: String?): ExoPlayer? {
    val ctx = LocalContext.current
    val player = remember(uri) {
        if (uri == null) null
        else ExoPlayer.Builder(ctx).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.parse(uri)))
            prepare()
            playWhenReady = false
        }
    }
    DisposableEffect(player) { onDispose { player?.release() } }
    return player
}

/** Vị trí phát (giây) cập nhật mỗi khung hình; [onTick] gọi cùng nhịp. */
@Composable
fun rememberPlayerPos(player: ExoPlayer?, onTick: (Double) -> Unit = {}): State<Double> {
    val pos = remember { mutableDoubleStateOf(0.0) }
    val tick by rememberUpdatedState(onTick)
    LaunchedEffect(player) {
        if (player != null) {
            while (isActive) {
                withFrameNanos { }
                val t = player.currentPosition / 1000.0
                pos.doubleValue = t
                tick(t)
            }
        }
    }
    return pos
}

/** Hiện video đúng tỉ lệ gốc (9:16, 16:9…), vừa khít khung cố định, không bị kéo méo. */
@Composable
fun VideoSurface(player: ExoPlayer?, uri: String?, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val ratio by produceState(9f / 16f, uri) {
        val r = if (uri == null) null else withContext(Dispatchers.IO) { AudioDecoder.videoRatio(ctx, Uri.parse(uri)) }
        if (r != null) value = r
    }
    FitBox(ratio, modifier) {
        AndroidView(
            factory = { c -> TextureView(c) },
            modifier = Modifier.fillMaxSize(),
            update = { tv -> player?.setVideoTextureView(tv) }
        )
    }
}
