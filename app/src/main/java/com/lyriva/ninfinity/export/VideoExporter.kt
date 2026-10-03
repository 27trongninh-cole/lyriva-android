package com.lyriva.ninfinity.export

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import com.lyriva.ninfinity.R
import com.lyriva.ninfinity.audio.AudioDecoder
import com.lyriva.ninfinity.audio.Pcm
import com.lyriva.ninfinity.core.TextUtils
import com.lyriva.ninfinity.data.Project
import com.lyriva.ninfinity.render.BgFrames
import com.lyriva.ninfinity.render.Fonts
import com.lyriva.ninfinity.render.LyricsRenderer
import com.lyriva.ninfinity.render.RenderData
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/**
 * Dựng MP4 (H.264 + AAC) ngoại tuyến: vẽ từng khung với mốc thời gian chính xác ở 30fps rồi mã hóa phần cứng.
 * Không ghi theo thời gian thực nên không bị rớt khung khi máy chậm, tiếng luôn khớp hình.
 */
object VideoExporter {
    private const val FPS = 30

    private class EncodedSample(val data: ByteArray, val pts: Long, val flags: Int)
    private class EncodedAudio(val format: MediaFormat, val samples: List<EncodedSample>)

    fun export(
        ctx: Context,
        p: Project,
        onProgress: (Float, String) -> Unit,
        cancelled: () -> Boolean
    ): Uri {
        val app = ctx.applicationContext
        val audioUri = Uri.parse(p.audioUri ?: throw IllegalStateException("Chưa có nhạc"))
        val s = p.cutStart
        var e = p.cutEnd
        if (e <= s) e = AudioDecoder.durationSec(app, audioUri) ?: throw IllegalStateException("Không đọc được độ dài nhạc")
        val total = e - s
        if (total < 0.5) throw IllegalStateException("Đoạn nhạc quá ngắn")

        val data = RenderData.from(p)
        if (data.items.isEmpty()) throw IllegalStateException("Chưa có lời")
        val w = data.width
        val h = data.height
        val frames = max(1, ceil(total * FPS).toInt())

        onProgress(0f, "Đang giải mã âm thanh…")
        val pcm = AudioDecoder.decodeRange(app, audioUri, s, e, cancelled)
        onProgress(0.04f, "Đang mã hóa âm thanh…")
        val audio = encodeAudio(pcm, cancelled)
        onProgress(0.08f, "Đang chuẩn bị video…")

        val opts = BitmapFactory.Options().apply { inScaled = false }
        val logo = BitmapFactory.decodeResource(app.resources, R.drawable.lyriva_logo, opts)
        val renderer = LyricsRenderer(Fonts(app), logo)
        renderer.data = data
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        val bgf: BgFrames? = if (data.bgOn) BgFrames(app).also { if (!it.open(p.bgUri)) { it.close() } } else null
        var bgBmp: Bitmap? = null

        val outName = TextUtils.fileStem(p.title) + "_LYRIVA.mp4"
        val pending = MediaSaver.createVideo(app, outName)
        var muxer: MediaMuxer? = null
        var enc: MediaCodec? = null
        var gl: EncoderGl? = null
        var ok = false
        try {
            val vf = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, w, h).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                setInteger(MediaFormat.KEY_BIT_RATE, if (w > h) 6_000_000 else 8_000_000)
                setInteger(MediaFormat.KEY_FRAME_RATE, FPS)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            }
            val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
            enc = codec
            codec.configure(vf, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            val input = codec.createInputSurface()
            codec.start()
            val glv = EncoderGl(input, w, h)
            gl = glv
            val mux = MediaMuxer(pending.pfd.fileDescriptor, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            muxer = mux

            val info = MediaCodec.BufferInfo()
            val ainfo = MediaCodec.BufferInfo()
            var videoTrack = -1
            var started = false

            fun drain(end: Boolean) {
                if (end) codec.signalEndOfInputStream()
                var waited = 0
                while (true) {
                    val oi = codec.dequeueOutputBuffer(info, 10_000)
                    if (oi == MediaCodec.INFO_TRY_AGAIN_LATER) {
                        if (!end) return
                        waited++
                        if (waited > 500) throw IllegalStateException("Bộ mã hóa video không phản hồi")
                        continue
                    }
                    if (oi == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        videoTrack = mux.addTrack(codec.outputFormat)
                        val audioTrack = mux.addTrack(audio.format)
                        mux.start()
                        started = true
                        for (a in audio.samples) {
                            ainfo.set(0, a.data.size, a.pts, a.flags)
                            mux.writeSampleData(audioTrack, ByteBuffer.wrap(a.data), ainfo)
                        }
                        continue
                    }
                    if (oi >= 0) {
                        val ob = codec.getOutputBuffer(oi)!!
                        if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) info.size = 0
                        if (info.size > 0 && started) {
                            ob.position(info.offset)
                            ob.limit(info.offset + info.size)
                            mux.writeSampleData(videoTrack, ob, info)
                        }
                        codec.releaseOutputBuffer(oi, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return
                    }
                }
            }

            for (i in 0 until frames) {
                if (cancelled()) throw ExportCancelled()
                val t = i / FPS.toDouble()
                if (bgf != null && (i % 2 == 0 || bgBmp == null)) {
                    val nb = bgf.frameAt(p.bgOffset + t, 1280)
                    if (nb != null) {
                        bgBmp?.recycle()
                        bgBmp = nb
                    }
                }
                renderer.draw(canvas, t, bgBmp)
                glv.draw(bmp, i * 1_000_000_000L / FPS)
                drain(false)
                if (i % 5 == 0) onProgress(0.10f + 0.88f * i / frames, "Đang dựng video ${i * 100 / frames}%")
            }
            drain(true)
            if (!started) throw IllegalStateException("Không tạo được video")
            mux.stop()
            ok = true
        } finally {
            try { enc?.stop() } catch (_: Exception) {}
            try { enc?.release() } catch (_: Exception) {}
            try { gl?.release() } catch (_: Exception) {}
            try { muxer?.release() } catch (_: Exception) {}
            bgf?.close()
            if (ok) MediaSaver.finishVideo(app, pending) else MediaSaver.discardVideo(app, pending)
        }
        onProgress(1f, "Hoàn tất")
        return pending.uri
    }

    private fun encodeAudio(pcm: Pcm, cancelled: () -> Boolean): EncodedAudio {
        var ch = pcm.channels
        var src = pcm.samples
        if (ch > 2) {
            val fr = src.size / ch
            val o = ShortArray(fr * 2)
            for (f in 0 until fr) {
                o[f * 2] = src[f * ch]
                o[f * 2 + 1] = src[f * ch + 1]
            }
            src = o
            ch = 2
        }
        val sr = pcm.sampleRate
        val fmt = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sr, ch).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, 192_000)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 64 * 1024)
        }
        val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        val samples = ArrayList<EncodedSample>()
        var outFormat: MediaFormat? = null
        try {
            codec.configure(fmt, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec.start()
            val info = MediaCodec.BufferInfo()
            var pos = 0
            var inDone = false
            var outDone = false
            while (!outDone) {
                if (cancelled()) throw ExportCancelled()
                if (!inDone) {
                    val ii = codec.dequeueInputBuffer(10_000)
                    if (ii >= 0) {
                        val ib = codec.getInputBuffer(ii)!!
                        ib.clear()
                        ib.order(ByteOrder.LITTLE_ENDIAN)
                        val cap = ib.capacity() / 2
                        val n = min(cap - cap % ch, src.size - pos)
                        val pts = (pos / ch).toLong() * 1_000_000L / sr
                        if (n <= 0) {
                            codec.queueInputBuffer(ii, 0, 0, pts, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inDone = true
                        } else {
                            ib.asShortBuffer().put(src, pos, n)
                            codec.queueInputBuffer(ii, 0, n * 2, pts, 0)
                            pos += n
                        }
                    }
                }
                val oi = codec.dequeueOutputBuffer(info, 10_000)
                if (oi == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    outFormat = codec.outputFormat
                } else if (oi >= 0) {
                    val ob = codec.getOutputBuffer(oi)!!
                    if (info.size > 0 && info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0) {
                        val bytes = ByteArray(info.size)
                        ob.position(info.offset)
                        ob.limit(info.offset + info.size)
                        ob.get(bytes)
                        samples.add(EncodedSample(bytes, info.presentationTimeUs, info.flags))
                    }
                    codec.releaseOutputBuffer(oi, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outDone = true
                }
            }
        } finally {
            try { codec.stop() } catch (_: Exception) {}
            codec.release()
        }
        val f = outFormat ?: throw IllegalStateException("Mã hóa âm thanh lỗi")
        return EncodedAudio(f, samples)
    }
}
