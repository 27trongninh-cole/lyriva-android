package com.lyriva.ninfinity.ui

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns

fun displayName(ctx: Context, uri: Uri): String {
    var name: String? = null
    try {
        ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) name = c.getString(0)
        }
    } catch (_: Exception) {
    }
    return name ?: (uri.lastPathSegment ?: "file")
}

fun readText(ctx: Context, uri: Uri): String? = try {
    ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
} catch (e: Exception) {
    null
}
