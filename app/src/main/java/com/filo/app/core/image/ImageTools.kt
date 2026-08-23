package com.filo.app.core.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

private const val TAG = "ImageTools"

/**
 * Everything that leaves the phone is downscaled and recompressed here first. A modern phone
 * camera JPEG is 4-8 MB; at 1080px on the long edge and quality 80 it lands around 150 KB,
 * which matters on an Italian mobile connection and keeps us inside the widget bitmap limit.
 */
object ImageTools {

    const val MAX_EDGE = 1080
    const val JPEG_QUALITY = 80

    suspend fun prepareForUpload(
        context: Context,
        uri: Uri,
        maxEdge: Int = MAX_EDGE,
        quality: Int = JPEG_QUALITY,
    ): ByteArray? = withContext(Dispatchers.IO) {
        runCatching {
            val bitmap = decodeScaled(context, uri, maxEdge) ?: return@runCatching null
            val rotated = applyExifRotation(context, uri, bitmap)
            ByteArrayOutputStream().use { out ->
                rotated.compress(Bitmap.CompressFormat.JPEG, quality, out)
                if (rotated !== bitmap) rotated.recycle()
                bitmap.recycle()
                out.toByteArray()
            }
        }.onFailure { Log.w(TAG, "image prepare failed", it) }.getOrNull()
    }

    /** Two pass decode: cheap subsample first, then an exact scale to the target edge. */
    private fun decodeScaled(context: Context, uri: Uri, maxEdge: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        val longEdge = max(bounds.outWidth, bounds.outHeight)
        if (longEdge <= 0) return null

        var sample = 1
        while (longEdge / sample > maxEdge * 2) sample *= 2

        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        val decoded = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        } ?: return null

        val decodedLong = max(decoded.width, decoded.height)
        if (decodedLong <= maxEdge) return decoded

        val scale = maxEdge.toFloat() / decodedLong
        val scaled = Bitmap.createScaledBitmap(
            decoded,
            (decoded.width * scale).roundToInt().coerceAtLeast(1),
            (decoded.height * scale).roundToInt().coerceAtLeast(1),
            true,
        )
        if (scaled !== decoded) decoded.recycle()
        return scaled
    }

    /** Phones write the orientation in EXIF rather than rotating the pixels. */
    private fun applyExifRotation(context: Context, uri: Uri, bitmap: Bitmap): Bitmap {
        val orientation = runCatching {
            context.contentResolver.openInputStream(uri)?.use {
                ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            }
        }.getOrNull() ?: ExifInterface.ORIENTATION_NORMAL

        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            else -> return bitmap
        }
        return runCatching {
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        }.getOrDefault(bitmap)
    }
}
