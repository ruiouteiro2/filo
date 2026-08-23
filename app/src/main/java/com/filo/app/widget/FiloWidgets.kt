package com.filo.app.widget

import android.content.Context
import android.widget.RemoteViews
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.AndroidRemoteViews
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import androidx.glance.action.actionStartActivity
import com.filo.app.MainActivity
import com.filo.app.R
import com.filo.app.core.time.DayMath
import com.filo.app.core.time.PgTime
import com.filo.app.data.weather.Wmo

// The palette again, as Compose colours for Glance.
private val Ink = Color(0xFF060606)
private val Surface = Color(0xFF120607)
private val Crimson = Color(0xFFC1121F)
private val Scarlet = Color(0xFFE63946)
private val Bone = Color(0xFFF4E8E5)
private val Ash = Color(0xFF9C8A8C)
private val Ember = Color(0xFFFF5966)
private val RoseAsh = Color(0xFFE9BFC2)

const val EXTRA_DESTINATION = "filo.destination"
const val EXTRA_SPOTIFY_TRACK = "filo.spotify_track"
const val EXTRA_SPOTIFY_TITLE = "filo.spotify_title"
const val EXTRA_SPOTIFY_ARTIST = "filo.spotify_artist"

/**
 * Widgets sit on unknown wallpapers, so every one of them gets its own opaque ground and a
 * blood red hairline rather than trusting whatever is behind it.
 */
@Composable
private fun WidgetShell(
    destination: String,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .padding(2.dp)
            .clickable(
                actionStartActivity<MainActivity>(
                    actionParametersOf(ActionParameters.Key<String>(EXTRA_DESTINATION) to destination),
                ),
            ),
    ) {
        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                // A translucent drawable rather than a solid colour: the wallpaper shows
                // through, and the drawable carries the rounded corners and the hairline that
                // keep the shape readable on a light background.
                .background(ImageProvider(R.drawable.widget_background))
                .padding(16.dp),
            content = { content() },
        )
    }
}

@Composable
private fun Label(text: String) {
    Text(text = text.uppercase(), style = TextStyle(color = ColorProvider(Ash), fontSize = 11.sp, fontWeight = FontWeight.Medium))
}

@Composable
private fun Value(text: String, color: Color = Bone, size: Int = 18) {
    Text(text = text, style = TextStyle(color = ColorProvider(color), fontSize = size.sp, fontWeight = FontWeight.Bold))
}

@Composable
private fun Body(text: String, color: Color = Ash) {
    Text(text = text, style = TextStyle(color = ColorProvider(color), fontSize = 13.sp))
}

/**
 * The live clock, ticked by Android itself in the partner's timezone.
 *
 * Both format slots get the same pattern, so the clock obeys the app's 12/24 hour setting
 * rather than the phone's: TextClock picks a slot by system setting, and this makes that
 * choice irrelevant.
 */
@Composable
private fun PartnerClock(timezone: String?, use24: Boolean, small: Boolean = false) {
    val context = LocalContext.current
    val layout = if (small) R.layout.widget_clock_small else R.layout.widget_clock
    val views = RemoteViews(context.packageName, layout)
    if (!timezone.isNullOrBlank()) {
        views.setString(R.id.widget_clock, "setTimeZone", timezone)
    }
    val pattern = DayMath.clockPattern(use24)
    views.setCharSequence(R.id.widget_clock, "setFormat12Hour", pattern)
    views.setCharSequence(R.id.widget_clock, "setFormat24Hour", pattern)
    AndroidRemoteViews(views)
}

@Composable
private fun NotPaired() {
    Column(verticalAlignment = Alignment.Vertical.CenterVertically, horizontalAlignment = Alignment.Horizontal.CenterHorizontally, modifier = GlanceModifier.fillMaxSize()) {
        Body(LocalContext.current.getString(R.string.widget_not_paired))
    }
}

// ------------------------------------------------------------------ Together

class TogetherWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // Collected inside the composition on purpose: a value read once out here would be
        // frozen for the whole Glance session, and the widget would ignore every update.
        provideContent {
            val snapshot by WidgetSnapshotStore.flow(context)
                .collectAsState(initial = null)
            snapshot?.let { TogetherContent(it) }
        }
    }
}

@Composable
private fun TogetherContent(snapshot: WidgetSnapshot) {
    val context = LocalContext.current
    WidgetShell(destination = "home") {
        if (!snapshot.paired) return@WidgetShell NotPaired()
        Column(modifier = GlanceModifier.fillMaxSize()) {
            // Who and when: their name, their clock ticking natively in their timezone.
            Row(verticalAlignment = Alignment.Vertical.CenterVertically) {
                WidgetImages.loadFile(snapshot.partnerAvatar)?.let { face ->
                    Image(
                        provider = ImageProvider(face),
                        contentDescription = snapshot.partnerName,
                        modifier = GlanceModifier.size(44.dp),
                    )
                    Spacer(GlanceModifier.width(10.dp))
                }
                Column(modifier = GlanceModifier.defaultWeight()) {
                    Text(
                        text = snapshot.partnerName ?: context.getString(R.string.widget_no_data),
                        style = TextStyle(color = ColorProvider(Bone), fontSize = 15.sp, fontWeight = FontWeight.Bold),
                    )
                    Body(
                        when (snapshot.partnerAsleep()) {
                            true -> context.getString(R.string.state_asleep)
                            false -> context.getString(R.string.state_awake)
                            null -> context.getString(R.string.state_unknown)
                        },
                    )
                }
                PartnerClock(snapshot.partnerTimezone, snapshot.clock24h)
            }

            Spacer(GlanceModifier.height(8.dp))

            // The vitals in one line: weather, battery, distance.
            Row(verticalAlignment = Alignment.Vertical.CenterVertically) {
                if (snapshot.weatherCode != null && snapshot.weatherTemp != null) {
                    Image(
                        provider = ImageProvider(Wmo.iconRes(snapshot.weatherCode)),
                        contentDescription = null,
                        colorFilter = androidx.glance.ColorFilter.tint(ColorProvider(Scarlet)),
                        modifier = GlanceModifier.size(15.dp),
                    )
                    Spacer(GlanceModifier.width(4.dp))
                    Body(
                        context.getString(
                            R.string.weather_temperature,
                            DayMath.number(snapshot.weatherTemp.toLong()),
                        ),
                        color = Bone,
                    )
                    Spacer(GlanceModifier.width(12.dp))
                }
                snapshot.partnerBattery?.let { level ->
                    Body(
                        context.getString(R.string.battery_plain, level),
                        color = if (level < 15 && !snapshot.partnerCharging) Ember else Bone,
                    )
                    Spacer(GlanceModifier.width(12.dp))
                }
                if (snapshot.distanceKnown && snapshot.distanceKm != null) {
                    Body(context.getString(R.string.distance_km, DayMath.number(snapshot.distanceKm)))
                }
            }

            // The next visit, counted at render time so the number is right at midnight too.
            PgTime.localDate(snapshot.countdownDate)?.let { date ->
                DayMath.countdownNumeral(date)?.let { numeral ->
                    Spacer(GlanceModifier.height(6.dp))
                    Row(verticalAlignment = Alignment.Vertical.CenterVertically) {
                        Text(
                            text = (snapshot.countdownEmoji ?: "✈"),
                            style = TextStyle(fontSize = 12.sp),
                        )
                        Spacer(GlanceModifier.width(5.dp))
                        Text(
                            text = context.getString(
                                R.string.widget_days_left,
                                numeral,
                                snapshot.countdownLabel() ?: context.getString(R.string.label_next_visit),
                            ),
                            style = TextStyle(color = ColorProvider(Bone), fontSize = 12.sp),
                            maxLines = 1,
                        )
                    }
                }
            }

            // Their mood, if they have one out.
            snapshot.partnerMoodEmoji?.let { emoji ->
                Spacer(GlanceModifier.height(6.dp))
                Body("$emoji ${snapshot.partnerMoodText.orEmpty()}".trim(), color = Bone)
            }

            // What they are listening to, straight on the home screen.
            snapshot.partnerTrack?.let { track ->
                Spacer(GlanceModifier.height(6.dp))
                Row(
                    verticalAlignment = Alignment.Vertical.CenterVertically,
                    // Straight to the song: this line opens Spotify, not the app.
                    modifier = GlanceModifier.clickable(
                        actionStartActivity<MainActivity>(
                            actionParametersOf(
                                ActionParameters.Key<String>(EXTRA_SPOTIFY_TRACK) to
                                    (snapshot.partnerTrackId ?: ""),
                                ActionParameters.Key<String>(EXTRA_SPOTIFY_TITLE) to track,
                                ActionParameters.Key<String>(EXTRA_SPOTIFY_ARTIST) to
                                    (snapshot.partnerArtist ?: ""),
                            ),
                        ),
                    ),
                ) {
                    Text(
                        text = if (snapshot.partnerMusicLive()) "\u266A" else "\u23F8",
                        style = TextStyle(color = ColorProvider(Scarlet), fontSize = 12.sp),
                    )
                    Spacer(GlanceModifier.width(5.dp))
                    Text(
                        text = listOfNotNull(track, snapshot.partnerArtist).joinToString(" \u2014 "),
                        style = TextStyle(color = ColorProvider(RoseAsh), fontSize = 12.sp),
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

class TogetherWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TogetherWidget()
}

// ----------------------------------------------------------------- Countdown

class CountdownWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // Collected inside the composition on purpose: a value read once out here would be
        // frozen for the whole Glance session, and the widget would ignore every update.
        provideContent {
            val snapshot by WidgetSnapshotStore.flow(context)
                .collectAsState(initial = null)
            snapshot?.let { CountdownContent(it) }
        }
    }
}

@Composable
private fun CountdownContent(snapshot: WidgetSnapshot) {
    val context = LocalContext.current
    WidgetShell(destination = "countdowns") {
        if (!snapshot.paired) return@WidgetShell NotPaired()
        // Recomputed at render time from the date, so a stale snapshot still shows the right day.
        val date = PgTime.localDate(snapshot.countdownDate)
        if (date == null) {
            Column(
                modifier = GlanceModifier.fillMaxSize(),
                verticalAlignment = Alignment.Vertical.CenterVertically,
                horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
            ) { Body(context.getString(R.string.widget_no_countdown)) }
            return@WidgetShell
        }
        val numeral = DayMath.countdownNumeral(date)
        val unit = DayMath.countdownUnit(context, date)
        Column(modifier = GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.Vertical.CenterVertically) {
            Label(context.getString(R.string.label_next_visit))
            Spacer(GlanceModifier.height(4.dp))
            if (numeral != null) {
                Row(verticalAlignment = Alignment.Vertical.Bottom) {
                    Text(
                        text = numeral,
                        style = TextStyle(color = ColorProvider(Crimson), fontSize = 44.sp, fontWeight = FontWeight.Bold),
                    )
                    Spacer(GlanceModifier.width(6.dp))
                    unit?.let { Body(it) }
                }
            } else {
                Value(DayMath.countdownText(context, date), color = Crimson, size = 26)
            }
            Body(
                listOfNotNull(snapshot.countdownEmoji, snapshot.countdownLabel()).joinToString(" "),
                color = Bone,
            )
            Body(DayMath.formatDate(date))
        }
    }
}

class CountdownWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CountdownWidget()
}

// --------------------------------------------------------------------- Heart

class HeartWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val justSent by PingState.recentlySentFlow(context).collectAsState(initial = false)
            HeartContent(justSent)
        }
    }
}

@Composable
private fun HeartContent(justSent: Boolean) {
    val context = LocalContext.current
    Box(modifier = GlanceModifier.fillMaxSize().padding(2.dp)) {
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(
                    ImageProvider(
                        if (justSent) R.drawable.widget_background_sent
                        else R.drawable.widget_background_heart,
                    ),
                )
                .padding(8.dp)
                // A callback, not an activity: the heart must not need the app to open.
                .clickable(actionRunCallback<SendPingAction>()),
            verticalAlignment = Alignment.Vertical.CenterVertically,
            horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
        ) {
            Text(
                text = "♥",
                style = TextStyle(
                    color = ColorProvider(if (justSent) Bone else Crimson),
                    fontSize = 30.sp,
                ),
            )
            Text(
                text = context.getString(if (justSent) R.string.ping_sent else R.string.ping_button),
                style = TextStyle(color = ColorProvider(if (justSent) Bone else Ash), fontSize = 11.sp),
            )
        }
    }
}

class HeartWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = HeartWidget()
}

// --------------------------------------------------------------------- Photo

class PhotoWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // Collected inside the composition on purpose: a value read once out here would be
        // frozen for the whole Glance session, and the widget would ignore every update.
        provideContent {
            val snapshot by WidgetSnapshotStore.flow(context)
                .collectAsState(initial = null)
            snapshot?.let { PhotoContent(it) }
        }
    }
}

@Composable
private fun PhotoContent(snapshot: WidgetSnapshot) {
    val context = LocalContext.current
    WidgetShell(destination = "home") {
        if (!snapshot.paired) return@WidgetShell NotPaired()
        val photo = WidgetImages.loadFile(snapshot.photoImage)
        Box(modifier = GlanceModifier.fillMaxSize()) {
            if (photo != null) {
                Image(
                    provider = ImageProvider(photo),
                    contentDescription = snapshot.partnerName,
                    contentScale = androidx.glance.layout.ContentScale.Crop,
                    modifier = GlanceModifier.fillMaxSize().cornerRadius(18.dp),
                )
            } else {
                Column(
                    modifier = GlanceModifier.fillMaxSize(),
                    verticalAlignment = Alignment.Vertical.CenterVertically,
                    horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
                ) { Body(context.getString(R.string.widget_no_photo)) }
            }
            snapshot.partnerNote?.let { note ->
                Column(
                    modifier = GlanceModifier.fillMaxSize(),
                    verticalAlignment = Alignment.Vertical.Bottom,
                ) {
                    Box(
                        modifier = GlanceModifier
                            .fillMaxWidth()
                            .background(ImageProvider(R.drawable.widget_note_scrim))
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                    ) {
                        Text(
                            text = note,
                            style = TextStyle(color = ColorProvider(Bone), fontSize = 13.sp),
                            maxLines = 3,
                        )
                    }
                }
            }
        }
    }
}

class PhotoWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = PhotoWidget()
}

/** Tapping the heart widget sends the ping without opening anything. */
class SendPingAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        PingState.send(context)
    }
}
