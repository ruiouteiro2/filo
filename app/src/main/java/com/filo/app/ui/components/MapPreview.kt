package com.filo.app.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.filo.app.ui.theme.Bone
import com.filo.app.ui.theme.Crimson
import com.filo.app.ui.theme.Ink
import kotlin.math.PI
import kotlin.math.ln
import kotlin.math.roundToInt
import kotlin.math.tan

/**
 * A small slippy map centred on one person.
 *
 * Raster tiles, not a map SDK: Google Maps would need an API key and a billing account for an
 * app that will only ever run on two phones. These are CARTO's dark OpenStreetMap tiles,
 * which need no key and happen to match the palette. Attribution is required and is drawn by
 * the caller.
 */
private const val TILE_SIZE_PX = 256

private fun tileUrl(z: Int, x: Int, y: Int) =
    "https://basemaps.cartocdn.com/dark_all/$z/$x/$y.png"

/** Web Mercator: fractional tile coordinates for a position at a zoom level. */
private fun tileCoordinates(lat: Double, lon: Double, zoom: Int): Pair<Double, Double> {
    val n = 1 shl zoom
    val x = (lon + 180.0) / 360.0 * n
    val latRad = lat * PI / 180.0
    val y = (1.0 - ln(tan(latRad) + 1.0 / kotlin.math.cos(latRad)) / PI) / 2.0 * n
    return x to y
}

@Composable
fun MapPreview(
    lat: Double,
    lon: Double,
    modifier: Modifier = Modifier,
    zoom: Int = 12,
) {
    val density = LocalDensity.current
    val tileDp = with(density) { TILE_SIZE_PX.toDp() }

    val (xf, yf) = tileCoordinates(lat, lon, zoom)
    val centreX = xf.toInt()
    val centreY = yf.toInt()
    // How far the point sits inside its own tile, so the grid can be nudged to centre it.
    val withinX = ((xf - centreX) * TILE_SIZE_PX).roundToInt()
    val withinY = ((yf - centreY) * TILE_SIZE_PX).roundToInt()

    Box(
        modifier = modifier.background(Ink),
        contentAlignment = Alignment.Center,
    ) {
        // A 3x3 grid so panning the point to the centre never exposes an empty edge.
        for (dy in -1..1) {
            for (dx in -1..1) {
                val tx = centreX + dx
                val ty = centreY + dy
                val maxTile = (1 shl zoom) - 1
                if (ty < 0 || ty > maxTile) continue
                val wrappedX = ((tx % (maxTile + 1)) + maxTile + 1) % (maxTile + 1)

                AsyncImage(
                    model = tileUrl(zoom, wrappedX, ty),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(tileDp)
                        .offset(
                            x = with(density) { (dx * TILE_SIZE_PX - withinX + TILE_SIZE_PX / 2).toDp() },
                            y = with(density) { (dy * TILE_SIZE_PX - withinY + TILE_SIZE_PX / 2).toDp() },
                        ),
                )
            }
        }

        // The marker sits dead centre, because that is where the point was moved to.
        Canvas(modifier = Modifier.size(28.dp)) {
            val centre = Offset(size.width / 2f, size.height / 2f)
            drawCircle(color = Crimson.copy(alpha = 0.25f), radius = size.minDimension / 2f, center = centre)
            drawCircle(color = Crimson, radius = 6.dp.toPx(), center = centre)
            drawCircle(
                color = Bone,
                radius = 6.dp.toPx(),
                center = centre,
                style = Stroke(width = 2.dp.toPx()),
            )
        }
    }
}

/** Hands the position to whatever maps app the phone actually has. */
fun openInMaps(context: Context, lat: Double, lon: Double, label: String?) {
    val encoded = Uri.encode(label ?: "")
    val geo = Uri.parse("geo:$lat,$lon?q=$lat,$lon($encoded)")
    val intent = Intent(Intent.ACTION_VIEW, geo).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    if (runCatching { context.startActivity(intent); true }.getOrDefault(false)) return

    // No maps app: fall back to the browser.
    runCatching {
        context.startActivity(
            Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://www.openstreetmap.org/?mlat=$lat&mlon=$lon#map=13/$lat/$lon"),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}
