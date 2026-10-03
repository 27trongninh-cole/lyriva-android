package com.lyriva.ninfinity.export

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import java.io.OutputStream

/** Lưu file vào Download/LYRIVA, Pictures/LYRIVA, Movies/LYRIVA qua MediaStore (không cần quyền lưu trữ). */
object MediaSaver {

    fun saveDownload(ctx: Context, name: String, mime: String, write: (OutputStream) -> Unit): Uri? {
        val cv = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, name)
            put(MediaStore.Downloads.MIME_TYPE, mime)
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/LYRIVA")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val res = ctx.contentResolver
        val uri = res.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv) ?: return null
        return try {
            res.openOutputStream(uri)?.use { write(it) } ?: throw IllegalStateException("Không mở được file")
            val done = ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }
            res.update(uri, done, null, null)
            uri
        } catch (e: Exception) {
            res.delete(uri, null, null)
            null
        }
    }

    fun savePng(ctx: Context, name: String, bmp: Bitmap): Uri? {
        val cv = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/LYRIVA")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val res = ctx.contentResolver
        val uri = res.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv) ?: return null
        return try {
            res.openOutputStream(uri)?.use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
                ?: throw IllegalStateException("Không mở được file")
            val done = ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }
            res.update(uri, done, null, null)
            uri
        } catch (e: Exception) {
            res.delete(uri, null, null)
            null
        }
    }

    class PendingVideo(val uri: Uri, val pfd: ParcelFileDescriptor)

    fun createVideo(ctx: Context, name: String): PendingVideo {
        val cv = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, name)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/LYRIVA")
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }
        val res = ctx.contentResolver
        val uri = res.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, cv)
            ?: throw IllegalStateException("Không tạo được file video")
        val pfd = res.openFileDescriptor(uri, "rw")
        if (pfd == null) {
            res.delete(uri, null, null)
            throw IllegalStateException("Không mở được file video")
        }
        return PendingVideo(uri, pfd)
    }

    fun finishVideo(ctx: Context, v: PendingVideo) {
        try { v.pfd.close() } catch (_: Exception) {}
        val done = ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }
        ctx.contentResolver.update(v.uri, done, null, null)
    }

    fun discardVideo(ctx: Context, v: PendingVideo) {
        try { v.pfd.close() } catch (_: Exception) {}
        try { ctx.contentResolver.delete(v.uri, null, null) } catch (_: Exception) {}
    }
}
