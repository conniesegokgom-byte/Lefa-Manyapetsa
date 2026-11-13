package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.google.firebase.Timestamp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Locale

object MediaUtils {
    suspend fun encodeUriToBase64(context: Context, uri: Uri, maxDimension: Int = 720): String? =
        withContext(Dispatchers.IO) {
            runCatching {
                val inputStream = context.contentResolver.openInputStream(uri) ?: return@runCatching null
                val original = inputStream.use { BitmapFactory.decodeStream(it) } ?: return@runCatching null
                val scale = minOf(
                    1f,
                    maxDimension.toFloat() / maxOf(original.width, original.height).coerceAtLeast(1)
                )
                val targetW = (original.width * scale).toInt().coerceAtLeast(1)
                val targetH = (original.height * scale).toInt().coerceAtLeast(1)
                val scaled = if (scale < 1f) {
                    Bitmap.createScaledBitmap(original, targetW, targetH, true)
                } else {
                    original
                }
                val out = ByteArrayOutputStream()
                var quality = 72
                scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)
                while (out.size() > 220_000 && quality > 30) {
                    out.reset()
                    quality -= 12
                    scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)
                }
                if (scaled !== original) scaled.recycle()
                original.recycle()
                Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
            }.getOrNull()
        }

    fun decodeBase64ToImageBitmap(base64: String): ImageBitmap? {
        if (base64.isBlank()) return null
        return runCatching {
            val bytes = Base64.decode(base64, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        }.getOrNull()
    }

    fun parseHexColor(hex: String, fallback: Color = Color(0xFF00E5A3)): Color {
        return runCatching {
            Color(android.graphics.Color.parseColor(hex))
        }.getOrDefault(fallback)
    }

    fun formatRelativeTime(timestamp: Timestamp?): String {
        val date = timestamp?.toDate() ?: return "Just now"
        val diffSec = ((System.currentTimeMillis() - date.time) / 1000L).coerceAtLeast(0L)
        return when {
            diffSec < 45 -> "Just now"
            diffSec < 3600 -> "${diffSec / 60}m"
            diffSec < 86400 -> "${diffSec / 3600}h"
            else -> SimpleDateFormat("MMM d", Locale.getDefault()).format(date)
        }
    }

    fun formatClockTime(timestamp: Timestamp?): String {
        val date = timestamp?.toDate() ?: return ""
        return SimpleDateFormat("h:mm a", Locale.getDefault()).format(date)
    }
}
