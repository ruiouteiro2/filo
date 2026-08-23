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
     * The day ring with the partner's face in the middle, drawn with plain Android graphics
     * so it matches the in-app Compose version.
     */
    suspend fun renderDayRing(
        context: Context,
        avatarUrl: String?,
        nowLocal: LocalTime?,
        sleepStart: LocalTime?,
        sleepEnd: LocalTime?,
    ): String? = withContext(Dispatchers.IO) {
        runCatching {
            val size = RING_PX
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val stroke = size * 0.075f
            val inset = stroke / 2f
            val rect = RectF(inset, inset, size - inset, size - inset)

            val (wakeStart, wakeSpan) = SleepMath.wakingSpan(sleepStart, sleepEnd)
            val noon = noonPosition(wakeStart, wakeSpan)

            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = stroke
            }
            val segments = 180
            val degreesPer = 360f / segments
            for (i in 0 until segments) {
                val midMinute = (((i + 0.5f) / segments) * SleepMath.MINUTES_PER_DAY).toInt()
                val asleep = SleepMath.isAsleep(
                    LocalTime.of((midMinute / 60) % 24, midMinute % 60),
                    sleepStart,
                    sleepEnd,
                ) ?: false
                paint.color = if (asleep) INK else wakingColor(midMinute, wakeStart, wakeSpan, noon)
                canvas.drawArc(rect, -90f + i * degreesPer, degreesPer + 0.6f, false, paint)
            }

            // Face in the middle, circular cropped.
            val faceRadius = size / 2f - stroke * 1.6f
            val face = avatarUrl?.let { loadBitmap(it) }
            if (face != null) {
                val fill = Paint(Paint.ANTI_ALIAS_FLAG)
                val shader = BitmapShader(face, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
                val scale = (faceRadius * 2f) / min(face.width, face.height)
                val matrix = Matrix().apply {
                    setScale(scale, scale)
                    postTranslate(
                        size / 2f - face.width * scale / 2f,
                        size / 2f - face.height * scale / 2f,
                    )
                }
                shader.setLocalMatrix(matrix)
                fill.shader = shader
                canvas.drawCircle(size / 2f, size / 2f, faceRadius, fill)
                face.recycle()
            } else {
                canvas.drawCircle(
                    size / 2f,
                    size / 2f,
                    faceRadius,
                    Paint(Paint.ANTI_ALIAS_FLAG).apply { color = INK },
                )
            }

            // The dot at their current local time.
            if (nowLocal != null) {
                val minute = nowLocal.hour * 60 + nowLocal.minute
                val degrees = -90f + (minute / SleepMath.MINUTES_PER_DAY.toFloat()) * 360f
                val radians = Math.toRadians(degrees.toDouble())
                val cx = size / 2f + (size / 2f - inset) * cos(radians).toFloat()
                val cy = size / 2f + (size / 2f - inset) * sin(radians).toFloat()
                canvas.drawCircle(cx, cy, stroke * 0.72f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = INK })
                canvas.drawCircle(cx, cy, stroke * 0.46f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = BONE })
            }

            val file = File(dir(context), "ring.png")
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
            file.absolutePath
        }.onFailure { Log.w(TAG, "ring render failed", it) }.getOrNull()
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
