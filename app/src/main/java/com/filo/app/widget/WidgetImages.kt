package com.filo.app.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.util.Log
import com.filo.app.core.time.SleepMath
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.get
import io.ktor.client.statement.readRawBytes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalTime
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

private const val TAG = "WidgetImages"

/**
 * Glance cannot load a URL and cannot draw a Canvas, so anything visual beyond text is
 * rendered to a bitmap here, cached on internal storage, and handed to the widget as a file.
 *
 * Everything written here is deliberately small. RemoteViews has a hard transaction limit
 * around 5MB and blowing it fails silently, leaving a blank widget.
 */
object WidgetImages {

    private const val DIR = "widget_images"
    private const val RING_PX = 320
    private const val PHOTO_MAX_PX = 720

    private val http by lazy { HttpClient(OkHttp) }

    private fun dir(context: Context): File =
        File(context.filesDir, DIR).apply { if (!exists()) mkdirs() }

    /**
     * The partner's face for the widget: circle cropped with the site's scarlet hairline,
     * rendered to a file because Glance wants bitmaps it can reload after process death.
     */
    suspend fun renderAvatar(context: Context, avatarUrl: String?): String? = withContext(Dispatchers.IO) {
        if (avatarUrl.isNullOrBlank()) return@withContext null
        val face = runCatching { loadBitmap(avatarUrl) }.getOrNull() ?: return@withContext null
        runCatching {
            val size = RING_PX
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val fill = Paint(Paint.ANTI_ALIAS_FLAG)
            val shader = BitmapShader(face, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
            val scale = size.toFloat() / min(face.width, face.height)
            val matrix = Matrix().apply {
                setScale(scale, scale)
                postTranslate(
                    size / 2f - face.width * scale / 2f,
                    size / 2f - face.height * scale / 2f,
                )
            }
            shader.setLocalMatrix(matrix)
            fill.shader = shader
            val radius = size / 2f - size * 0.02f
            canvas.drawCircle(size / 2f, size / 2f, radius, fill)
            canvas.drawCircle(
                size / 2f,
                size / 2f,
                radius,
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = size * 0.014f
                    color = 0x66E63946.toInt()
                },
            )
            face.recycle()
            val file = File(dir(context), "avatar.png")
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
            file.absolutePath
        }.onFailure { Log.w(TAG, "avatar render failed", it) }.getOrNull()
    }

    /** Downloads the partner's photo once and keeps a widget sized copy on disk. */
    suspend fun cachePhoto(context: Context, url: String?): String? = withContext(Dispatchers.IO) {
        if (url.isNullOrBlank()) return@withContext null
        runCatching {
            val source = loadBitmap(url) ?: return@runCatching null
            val longEdge = max(source.width, source.height)
            val scaled = if (longEdge > PHOTO_MAX_PX) {
                val scale = PHOTO_MAX_PX.toFloat() / longEdge
                Bitmap.createScaledBitmap(
                    source,
                    (source.width * scale).toInt().coerceAtLeast(1),
                    (source.height * scale).toInt().coerceAtLeast(1),
                    true,
                )
            } else {
                source
            }
            val file = File(dir(context), "photo.jpg")
            file.outputStream().use { scaled.compress(Bitmap.CompressFormat.JPEG, 85, it) }
            if (scaled !== source) scaled.recycle()
            source.recycle()
            file.absolutePath
        }.onFailure { Log.w(TAG, "photo cache failed", it) }.getOrNull()
    }

    private suspend fun loadBitmap(url: String): Bitmap? = runCatching {
        val bytes = http.get(url).readRawBytes()
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }.onFailure { Log.w(TAG, "download failed: $url", it) }.getOrNull()

    fun loadFile(path: String?): Bitmap? {
        if (path.isNullOrBlank()) return null
        val file = File(path)
        if (!file.exists()) return null
        return runCatching { BitmapFactory.decodeFile(path) }.getOrNull()
    }

    // Same palette as the app, as plain ints for android.graphics.
    private const val INK = 0xFF0A0709.toInt()
    private const val BONE = 0xFFF2E8E6.toInt()
    private const val SURFACE = 0xFF16090C.toInt()
    private const val BLOOD = 0xFFB01523.toInt()
    private const val CRIMSON = 0xFFD41E2F.toInt()

    private fun noonPosition(wakeStart: Int, wakeSpan: Int): Float {
        if (wakeSpan <= 0) return 0.5f
        val raw = Math.floorMod(720 - wakeStart, SleepMath.MINUTES_PER_DAY) / wakeSpan.toFloat()
        return if (raw > 1f) 0.5f else raw.coerceIn(0.05f, 0.95f)
    }

    private fun wakingColor(minute: Int, wakeStart: Int, wakeSpan: Int, noon: Float): Int {
        if (wakeSpan <= 0) return SURFACE
        val position =
            (Math.floorMod(minute - wakeStart, SleepMath.MINUTES_PER_DAY) / wakeSpan.toFloat()).coerceIn(0f, 1f)
        val toward = if (position <= noon) position / noon else (1f - position) / (1f - noon)
        return ramp(toward.coerceIn(0f, 1f))
    }

    private fun ramp(t: Float): Int = if (t < 0.55f) {
        blend(SURFACE, BLOOD, t / 0.55f)
    } else {
        blend(BLOOD, CRIMSON, (t - 0.55f) / 0.45f)
    }

    private fun blend(from: Int, to: Int, t: Float): Int = Color.rgb(
        (Color.red(from) + (Color.red(to) - Color.red(from)) * t).toInt(),
        (Color.green(from) + (Color.green(to) - Color.green(from)) * t).toInt(),
        (Color.blue(from) + (Color.blue(to) - Color.blue(from)) * t).toInt(),
    )
}
