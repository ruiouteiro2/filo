package com.filo.app.ui.home

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.filo.app.R
import com.filo.app.core.battery.BatteryReader
import com.filo.app.core.geo.DistanceState
import com.filo.app.core.time.DayMath
import com.filo.app.core.time.DayStates
import com.filo.app.core.time.PgTime
import com.filo.app.core.time.SleepMath
import com.filo.app.data.model.BucketItem
import com.filo.app.data.model.CoupleSnapshot
import com.filo.app.data.model.Member
import com.filo.app.data.weather.Weather
import com.filo.app.data.weather.Wmo
import com.filo.app.ui.components.Avatar
import com.filo.app.ui.components.CardValue
import com.filo.app.ui.components.FiloButton
import com.filo.app.ui.components.FiloCard
import com.filo.app.ui.components.FiloSecondaryButton
import com.filo.app.ui.components.FiloTextField
import com.filo.app.ui.components.FullImageDialog
import com.filo.app.nowplaying.NotificationAccess
import com.filo.app.spotify.SpotifyLink
import com.filo.app.ui.components.MapPreview
import com.filo.app.ui.components.OrbBackground
import com.filo.app.ui.components.openInMaps
import com.filo.app.ui.components.SectionLabel
import com.filo.app.ui.components.StaggeredEntrance
import com.filo.app.ui.components.Timestamp
import com.filo.app.ui.permissions.openAppSettings
import com.filo.app.ui.theme.Ash
import com.filo.app.ui.theme.Blood
import com.filo.app.ui.theme.Bone
import com.filo.app.ui.theme.Crimson
import com.filo.app.ui.theme.Ember
import com.filo.app.ui.theme.FiloType
import com.filo.app.ui.theme.Ink
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Recomposes every 20 seconds so the clocks and the day ring dot stay honest. */
@Composable
fun rememberTick(): Instant {
    var now by remember { mutableStateOf(Instant.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(20_000)
            now = Instant.now()
        }
    }
    return now
}

@Composable
fun HomeScreen(
    snapshot: CoupleSnapshot,
    online: Boolean,
    locale: String,
    weather: Weather?,
    distance: DistanceState,
    photoUrls: Map<String, String>,
    clock24h: Boolean,
    onOpenSettings: () -> Unit,
    onOpenCountdowns: () -> Unit,
    onOpenBucket: () -> Unit,
    onSetMood: (String?, String?) -> Unit,
    onSetNote: (String) -> Unit,
    onPickAvatar: (Uri) -> Unit,
    onPickDailyPhoto: (Uri) -> Unit,
    onToggleBucket: (String, Boolean) -> Unit,
    updateAvailable: Boolean,
    onOpenSettingsForUpdate: () -> Unit,
    onPing: () -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val now = rememberTick()
    val partner = snapshot.partner
    val me = snapshot.me

    // Shared between the US card's edit icon and the composer card below it.
    var composing by remember { mutableStateOf(false) }

    // Which photo is being looked at full screen, if any.
    var viewing by remember { mutableStateOf<Pair<String, String?>?>(null) }
    viewing?.let { (url, name) ->
        FullImageDialog(url = url, name = name, onDismiss = { viewing = null })
    }

    LaunchedEffect(Unit) { onRefresh() }

    OrbBackground(modifier = modifier.fillMaxSize().background(Ink)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(stringResource(R.string.home_title), style = FiloType.Title, color = Bone)
                Text(
                    text = stringResource(R.string.settings_title),
                    style = FiloType.Label,
                    color = Ash,
                    modifier = Modifier.clickable { onOpenSettings() },
                )
            }

            if (!online) {
                Text(stringResource(R.string.state_offline), style = FiloType.Timestamp, color = Ash)
            }

            if (updateAvailable) {
                FiloCard(onClick = onOpenSettingsForUpdate) {
                    Text(
                        text = stringResource(R.string.update_banner),
                        style = FiloType.Body,
                        color = Crimson,
                    )
                }
            }

            StaggeredEntrance(index = 0) {
                UsCard(
                    me = me,
                    partner = partner,
                    now = now,
                    photoUrls = photoUrls,
                    clock24h = clock24h,
                    onPickAvatar = onPickAvatar,
                    onEdit = { composing = true },
                    onViewImage = { url, name -> viewing = url to name },
                )
            }

            StaggeredEntrance(index = 1) {
                ThoughtsCard(
                    me = me,
                    partner = partner,
                    editing = composing,
                    onEditingChange = { composing = it },
                    onSetMood = onSetMood,
                    onSetNote = onSetNote,
                )
            }

            StaggeredEntrance(index = 2) {
                CountdownCard(snapshot = snapshot, locale = locale, onOpenCountdowns = onOpenCountdowns)
            }

            StaggeredEntrance(index = 3) {
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    WeatherCard(weather = weather, modifier = Modifier.weight(1f))
                    BatteryCard(partner = partner, modifier = Modifier.weight(1f))
                }
            }

            StaggeredEntrance(index = 4) { DaysTogetherCard(snapshot = snapshot) }

            StaggeredEntrance(index = 5) { DistanceCard(distance = distance, me = me, partner = partner) }

            StaggeredEntrance(index = 6) {
                MusicCard(partner = partner, me = me)
            }

            StaggeredEntrance(index = 7) {
                PhotoCard(
                    onViewImage = { url, name -> viewing = url to name },
                    partner = partner,
                    me = me,
                    photoUrls = photoUrls,
                    onPickDailyPhoto = onPickDailyPhoto,
                )
            }

            StaggeredEntrance(index = 8) {
                BucketCard(
                    items = snapshot.bucket,
                    onToggle = onToggleBucket,
                    onOpenBucket = onOpenBucket,
                )
            }

            StaggeredEntrance(index = 9) { PingCard(onPing = onPing) }

            Spacer(Modifier.height(24.dp))
        }
    }
}

/**
 * Both faces, and what each of them is feeling. This card is the headline of the app, so it
 * carries the human content, not just clocks: telemetry lives further down.
 */
@Composable
private fun UsCard(
    me: Member?,
    partner: Member?,
    now: Instant,
    photoUrls: Map<String, String>,
    clock24h: Boolean,
    onPickAvatar: (Uri) -> Unit,
    onEdit: () -> Unit,
    onViewImage: (String, String?) -> Unit,
) {
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let(onPickAvatar)
    }
    val theirNote = partner?.noteText?.takeIf { it.isNotBlank() }
    val myNote = me?.noteText?.takeIf { it.isNotBlank() }

    FiloCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SectionLabel(stringResource(R.string.label_us))
            // The one edit affordance for everything in this card: mood, message, note.
            Image(
                painter = painterResource(R.drawable.ic_edit),
                contentDescription = stringResource(R.string.thoughts_edit),
                colorFilter = ColorFilter.tint(Blood),
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .clickable { onEdit() }
                    .padding(7.dp),
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.Top,
        ) {
            FaceColumn(
                member = partner,
                now = now,
                photoUrls = photoUrls,
                clock24h = clock24h,
                placeholder = stringResource(R.string.state_waiting_for_partner),
                modifier = Modifier.weight(1f),
                onTap = { url -> onViewImage(url, partner?.displayName) },
            )
            FaceColumn(
                member = me,
                now = now,
                photoUrls = photoUrls,
                clock24h = clock24h,
                placeholder = stringResource(R.string.state_no_data),
                modifier = Modifier.weight(1f),
                onTap = { url -> onViewImage(url, me?.displayName) },
                onHold = {
                    picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
            )
        }

        // The note is the only long-form thing here, so it drops out of the narrow columns
        // and runs full width. No divider and no empty rows when neither of you has written.
        if (theirNote != null || myNote != null) {
            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = Blood.copy(alpha = 0.18f))
            Spacer(Modifier.height(14.dp))
            theirNote?.let { NoteRow(partner!!.displayName, it, PgTime.instant(partner.noteUpdatedAt)) }
            if (theirNote != null && myNote != null) Spacer(Modifier.height(10.dp))
            myNote?.let { NoteRow(me!!.displayName, it, PgTime.instant(me.noteUpdatedAt)) }
        }

        if (me != null) {
            Spacer(Modifier.height(14.dp))
            Text(
                text = stringResource(R.string.avatar_change_hint),
                style = FiloType.Timestamp,
                color = Ash,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun NoteRow(name: String, note: String, at: Instant?) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(name, style = FiloType.Label, color = Ash)
        Spacer(Modifier.height(2.dp))
        Text(note, style = FiloType.Body, color = Bone)
        DayMath.relative(at)?.let { Timestamp(it.toString()) }
    }
}

@Composable
private fun FaceColumn(
    member: Member?,
    now: Instant,
    photoUrls: Map<String, String>,
    clock24h: Boolean,
    placeholder: String,
    modifier: Modifier = Modifier,
    onTap: ((String) -> Unit)? = null,
    onHold: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        if (member == null) {
            Avatar(null, null, size = 96.dp)
            Spacer(Modifier.height(10.dp))
            Text(placeholder, style = FiloType.Body, color = Ash, textAlign = TextAlign.Center)
            return@Column
        }

        val zone = PgTime.zone(member.timezone)
        val state = DayStates.of(
            now.atZone(zone),
            PgTime.localTime(member.sleepStart),
            PgTime.localTime(member.sleepEnd),
        )
        val emoji = member.moodEmoji?.takeIf { it.isNotBlank() }
        val photoUrl = photoUrls[member.photoUrl]

        // Tap looks, hold changes. Holding a face that is not yours does nothing, which is
        // the correct amount of power over somebody else's picture.
        Box(contentAlignment = Alignment.BottomEnd) {
            Avatar(
                displayName = member.displayName,
                photoUrl = photoUrl,
                size = 96.dp,
                modifier = Modifier.combinedClickable(
                    onClick = { if (photoUrl != null && onTap != null) onTap(photoUrl) },
                    onLongClick = onHold,
                ),
            )
            if (emoji != null) {
                MoodBadge(emoji)
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(member.displayName, style = FiloType.Value, color = Bone)
        Text(DayMath.formatTime(now, zone, clock24h), style = FiloType.Mono, color = Crimson)
        Text(
            text = DayStates.sentence(context, state, clock24h),
            style = FiloType.Timestamp,
            color = Ash,
            textAlign = TextAlign.Center,
        )
        member.moodText?.takeIf { it.isNotBlank() }?.let { mood ->
            Spacer(Modifier.height(6.dp))
            Text(
                text = mood,
                style = FiloType.Body,
                color = Bone,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** The mood emoji, tucked onto the corner of the face it belongs to. */
@Composable
private fun MoodBadge(emoji: String) {
    Box(
        modifier = Modifier
            .size(30.dp)
            .background(Ink, CircleShape)
            .border(1.dp, Blood.copy(alpha = 0.6f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(emoji, fontSize = 15.sp)
    }
}

private val MoodPresets = listOf("\uD83D\uDE0A", "\uD83E\uDD70", "\uD83D\uDE34", "\uD83D\uDE14", "\uD83D\uDE24", "\uD83E\uDD72", "\u2615", "\uD83C\uDF19")

/**
 * My composer, and only mine. What the two of us are feeling is shown above in US; this card
 * is purely the write surface. Its editing state lives in the screen so the pencil on the US
 * card can open it too.
 *
 * The eight presets are shortcuts, not a menu: the + at the end opens the full editor, where
 * any emoji from the keyboard works.
 */
@Composable
private fun ThoughtsCard(
    me: Member?,
    partner: Member?,
    editing: Boolean,
    onEditingChange: (Boolean) -> Unit,
    onSetMood: (String?, String?) -> Unit,
    onSetNote: (String) -> Unit,
) {
    var noteDraft by remember(me?.noteText) { mutableStateOf(me?.noteText.orEmpty()) }
    var moodDraft by remember(me?.moodText) { mutableStateOf(me?.moodText.orEmpty()) }
    var emojiDraft by remember(me?.moodEmoji) { mutableStateOf(me?.moodEmoji.orEmpty()) }

    // "The message" is what people actually come here to write, so it takes the keyboard the
    // moment the editor opens instead of making them find it.
    val noteFocus = remember { FocusRequester() }
    LaunchedEffect(editing) {
        if (editing) {
            // The field has to exist before it can take focus; one frame is enough.
            kotlinx.coroutines.delay(80)
            runCatching { noteFocus.requestFocus() }
        }
    }

    FiloCard {
        SectionLabel(stringResource(R.string.label_you))
        Spacer(Modifier.height(12.dp))

        // The fast path: one tap sets the mood, the + opens everything else.
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            MoodPresets.forEach { preset ->
                val selected = me?.moodEmoji == preset
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .background(if (selected) Blood.copy(alpha = 0.35f) else Color.Transparent, CircleShape)
                        .clickable {
                            emojiDraft = preset
                            onSetMood(preset, moodDraft)
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(text = preset, fontSize = if (selected) 22.sp else 19.sp)
                }
            }
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .background(Blood.copy(alpha = 0.25f), CircleShape)
                    .border(1.dp, Blood.copy(alpha = 0.6f), CircleShape)
                    .clickable { onEditingChange(true) },
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_plus),
                    contentDescription = stringResource(R.string.thoughts_edit),
                    colorFilter = ColorFilter.tint(Crimson),
                    modifier = Modifier.size(16.dp),
                )
            }
        }

        if (editing) {
            Spacer(Modifier.height(14.dp))
            FiloTextField(
                value = noteDraft,
                onValueChange = { noteDraft = it.take(140) },
                label = stringResource(R.string.note_hint),
                singleLine = false,
                modifier = Modifier.focusRequester(noteFocus),
            )
            Timestamp(stringResource(R.string.note_counter, noteDraft.length, 140))
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.width(110.dp)) {
                    FiloTextField(
                        value = emojiDraft,
                        // Two glyphs is enough for anything, including flags and skin tones,
                        // which are several code points each.
                        onValueChange = { emojiDraft = it.take(8) },
                        label = stringResource(R.string.mood_emoji_hint),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Box(modifier = Modifier.weight(1f)) {
                    FiloTextField(
                        value = moodDraft,
                        onValueChange = { moodDraft = it.take(40) },
                        label = stringResource(R.string.mood_text_hint),
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Timestamp(stringResource(R.string.mood_emoji_any))
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FiloButton(
                    text = stringResource(R.string.save),
                    onClick = {
                        onSetMood(emojiDraft.trim().ifBlank { null }, moodDraft)
                        onSetNote(noteDraft)
                        onEditingChange(false)
                    },
                    modifier = Modifier.weight(1f),
                )
                FiloSecondaryButton(
                    text = stringResource(R.string.cancel),
                    onClick = { onEditingChange(false) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** How long you have been each other's, front and centre rather than a footnote. */
@Composable
private fun DaysTogetherCard(snapshot: CoupleSnapshot) {
    val since = PgTime.localDate(snapshot.couple?.sinceDate) ?: return
    val days = DayMath.daysBetween(since, LocalDate.now(ZoneId.systemDefault()))
    FiloCard {
        SectionLabel(stringResource(R.string.label_together))
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(DayMath.number(days), style = FiloType.Numeral, color = Crimson)
            Spacer(Modifier.width(10.dp))
            Text(
                stringResource(R.string.countdown_days),
                style = FiloType.Value,
                color = Ash,
                modifier = Modifier.padding(bottom = 10.dp),
            )
        }
        Timestamp(stringResource(R.string.together_since, DayMath.formatDate(since)))
    }
}

@Composable
private fun PhotoCard(
    onViewImage: (String, String?) -> Unit,
    partner: Member?,
    me: Member?,
    photoUrls: Map<String, String>,
    onPickDailyPhoto: (Uri) -> Unit,
) {
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let(onPickDailyPhoto)
    }
    val theirPhoto = photoUrls[partner?.dailyPhotoUrl]
    val minePhoto = photoUrls[me?.dailyPhotoUrl]

    FiloCard {
        SectionLabel(stringResource(R.string.label_photo))
        Spacer(Modifier.height(12.dp))
        if (theirPhoto != null) {
            coil.compose.AsyncImage(
                model = theirPhoto,
                contentDescription = partner?.displayName,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(14.dp))
                    // Tap looks at it properly; holding it swaps YOURS, because that is the
                    // only photo here you have any business replacing.
                    .combinedClickable(
                        onClick = { onViewImage(theirPhoto, partner?.displayName) },
                        onLongClick = {
                            picker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                            )
                        },
                    ),
            )
            DayMath.relative(PgTime.instant(partner?.dailyPhotoAt))?.let {
                Spacer(Modifier.height(6.dp))
                Timestamp(it.toString())
            }
        } else {
            CardValue(stringResource(R.string.widget_no_photo))
        }

        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (minePhoto != null) {
                coil.compose.AsyncImage(
                    model = minePhoto,
                    contentDescription = null,
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
                        .clickable { onViewImage(minePhoto, null) },
                )
                Spacer(Modifier.width(12.dp))
            }
            Text(
                text = stringResource(
                    if (minePhoto != null) R.string.photo_replace_yours else R.string.photo_set_yours,
                ),
                style = FiloType.Label,
                color = Blood,
                modifier = Modifier.clickable {
                    picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
            )
        }
    }
}

/**
 * The bucket list as an actual list, not a score. Three open items are tickable right here,
 * because the whole point of a shared list is that ticking something off is one tap from
 * wherever you are; everything else is behind "see all".
 */
private const val BUCKET_PREVIEW_COUNT = 3

@Composable
private fun BucketCard(
    items: List<BucketItem>,
    onToggle: (String, Boolean) -> Unit,
    onOpenBucket: () -> Unit,
) {
    val open = items.filter { !it.done }
    val done = items.count { it.done }
    val preview = open.take(BUCKET_PREVIEW_COUNT)
    val remaining = open.size - preview.size

    FiloCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SectionLabel(stringResource(R.string.label_bucket))
            if (items.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.bucket_progress_count, done, items.size),
                    style = FiloType.Label,
                    color = Crimson,
                )
            }
        }

        if (items.isEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.bucket_empty),
                style = FiloType.Body,
                color = Ash,
                modifier = Modifier.clickable { onOpenBucket() },
            )
            return@FiloCard
        }

        Spacer(Modifier.height(12.dp))
        preview.forEachIndexed { index, item ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggle(item.id, true) }
                    .padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HomeCheckCircle()
                Spacer(Modifier.width(14.dp))
                Text(
                    text = item.text,
                    style = FiloType.Body,
                    color = Bone,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (index != preview.lastIndex) {
                HorizontalDivider(color = Blood.copy(alpha = 0.14f))
            }
        }

        if (open.isEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text(stringResource(R.string.bucket_all_done), style = FiloType.Body, color = Ash)
        }

        Spacer(Modifier.height(12.dp))
        Text(
            text = if (remaining > 0) {
                stringResource(R.string.bucket_see_more, remaining)
            } else {
                stringResource(R.string.bucket_see_all)
            },
            style = FiloType.Label,
            color = Blood,
            modifier = Modifier.clickable { onOpenBucket() },
        )
    }
}

/** The same check affordance as the full screen, in its unticked state. */
@Composable
private fun HomeCheckCircle() {
    Canvas(modifier = Modifier.size(22.dp)) {
        drawCircle(
            color = Ash.copy(alpha = 0.55f),
            radius = size.minDimension / 2f - 1.dp.toPx(),
            style = Stroke(width = 1.6.dp.toPx()),
        )
    }
}

/**
 * Music is a fixture, not a card that appears when the stars align. Both of you, whatever
 * you are playing or last played, always in the same place. Tapping a track opens it in your
 * own Spotify: exact link when the phone gave us an id, a search when it did not - Premium is
 * never involved either way.
 */
@Composable
private fun MusicCard(partner: Member?, me: Member?) {
    val context = LocalContext.current
    val listenerOn = remember { NotificationAccess.isGranted(context) }

    FiloCard {
        SectionLabel(stringResource(R.string.label_music))
        Spacer(Modifier.height(12.dp))

        MusicRow(member = partner, isMine = false)
        Spacer(Modifier.height(12.dp))
        HorizontalDivider(color = Blood.copy(alpha = 0.14f))
        Spacer(Modifier.height(12.dp))
        MusicRow(member = me, isMine = true)

        if (!listenerOn) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.now_playing_enable),
                style = FiloType.Label,
                color = Blood,
                modifier = Modifier.clickable { NotificationAccess.openSettings(context) },
            )
        }
    }
}

@Composable
private fun MusicRow(member: Member?, isMine: Boolean) {
    val context = LocalContext.current
    val name = member?.displayName ?: stringResource(R.string.state_waiting_for_partner)
    val track = member?.spotifyTrackName?.takeIf { it.isNotBlank() }
    val playing = member?.spotifyIsPlaying == true

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = track != null) {
                val id = member?.spotifyTrackId?.takeIf { it.isNotBlank() }
                if (id != null) {
                    SpotifyLink.openTrack(context, id)
                } else if (member != null) {
                    SpotifyLink.openSearch(context, member.spotifyTrackName.orEmpty(), member.spotifyArtist.orEmpty())
                }
            },
    ) {
        val art = member?.spotifyArtUrl?.takeIf { it.isNotBlank() }
        if (art != null && track != null) {
            coil.compose.AsyncImage(
                model = art,
                contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier.size(48.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp)),
            )
        } else {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(Ink, androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                    .border(1.dp, Blood.copy(alpha = 0.35f), androidx.compose.foundation.shape.RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center,
            ) { Text("\u266A", style = FiloType.Value, color = Ash) }
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(name, style = FiloType.Label, color = Ash)
                if (playing) {
                    Spacer(Modifier.width(6.dp))
                    Text("\u25CF", fontSize = 9.sp, color = Crimson)
                }
            }
            if (track == null) {
                Text(stringResource(R.string.music_nothing_yet), style = FiloType.Body, color = Ash)
            } else {
                Text(
                    text = track,
                    style = FiloType.Value,
                    color = Bone,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = member.spotifyArtist.orEmpty(),
                        style = FiloType.Body,
                        color = Ash,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (!playing) {
                        DayMath.relative(PgTime.instant(member.spotifyUpdatedAt))?.let {
                            Spacer(Modifier.width(8.dp))
                            Timestamp(it.toString())
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WeatherCard(weather: Weather?, modifier: Modifier = Modifier) {
    FiloCard(modifier = modifier) {
        SectionLabel(stringResource(R.string.label_weather))
        Spacer(Modifier.height(10.dp))
        if (weather == null) {
            CardValue(stringResource(R.string.weather_unknown))
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(Wmo.iconRes(weather.code)),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(Crimson),
                    modifier = Modifier.size(28.dp),
                )
                Spacer(Modifier.width(10.dp))
                CardValue(
                    stringResource(R.string.weather_temperature, DayMath.number(weather.temperatureC.toLong())),
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(stringResource(Wmo.descriptionRes(weather.code)), style = FiloType.Body, color = Ash)
        }
    }
}

@Composable
private fun BatteryCard(partner: Member?, modifier: Modifier = Modifier) {
    FiloCard(modifier = modifier) {
        SectionLabel(stringResource(R.string.label_battery))
        Spacer(Modifier.height(10.dp))
        val level = partner?.batteryLevel
        if (level == null) {
            CardValue(stringResource(R.string.battery_unknown))
        } else {
            val charging = partner.batteryCharging == true
            CardValue(
                text = if (charging) {
                    stringResource(R.string.battery_charging, level)
                } else {
                    stringResource(R.string.battery_plain, level)
                },
                // Not a gimmick: a flat battery is what explains the silence.
                color = if (level < BatteryReader.LOW_THRESHOLD && !charging) Ember else Bone,
            )
            DayMath.relative(PgTime.instant(partner.batteryUpdatedAt))?.let { Timestamp(it.toString()) }
        }
    }
}

/** How recently a position was written before we stop calling it live. */
private const val LIVE_WINDOW_MS = 3 * 60 * 1000L

@Composable
private fun DistanceCard(distance: DistanceState, me: Member?, partner: Member?) {
    val context = LocalContext.current
    val theirUpdate = PgTime.instant(partner?.locationUpdatedAt)
    val myUpdate = PgTime.instant(me?.locationUpdatedAt)
    val theirsLive = theirUpdate != null &&
        System.currentTimeMillis() - theirUpdate.toEpochMilli() < LIVE_WINDOW_MS

    FiloCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionLabel(stringResource(R.string.label_distance))
            if (theirsLive) {
                Spacer(Modifier.width(10.dp))
                // Live means live: their phone wrote a position in the last few minutes.
                Text("\u25CF", fontSize = 10.sp, color = Crimson)
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.distance_live), style = FiloType.Label, color = Crimson)
            }
        }
        Spacer(Modifier.height(8.dp))
        when (distance) {
            is DistanceState.Known -> {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(DayMath.number(distance.km), style = FiloType.Numeral, color = Crimson)
                    Spacer(Modifier.width(8.dp))
                    Text("km", style = FiloType.Value, color = Ash, modifier = Modifier.padding(bottom = 10.dp))
                }
                if (distance.myCity != null || distance.theirCity != null) {
                    Text(
                        text = stringResource(
                            R.string.distance_cities,
                            distance.myCity ?: "?",
                            distance.theirCity ?: "?",
                        ),
                        style = FiloType.Body,
                        color = Bone,
                    )
                }

                // Where they actually are, on a map, tappable through to the real maps app.
                if (partner?.lat != null && partner.lon != null) {
                    Spacer(Modifier.height(14.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(170.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .border(1.dp, Blood.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
                            .clickable {
                                openInMaps(context, partner.lat, partner.lon, partner.displayName)
                            },
                    ) {
                        MapPreview(
                            lat = partner.lat,
                            lon = partner.lon,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Timestamp(stringResource(R.string.map_open))
                        // CARTO and OpenStreetMap both require attribution for these tiles.
                        Timestamp(stringResource(R.string.map_attribution))
                    }
                }

                Spacer(Modifier.height(8.dp))
                DayMath.relative(theirUpdate)?.let {
                    Timestamp(stringResource(R.string.distance_their_update, it.toString()))
                }
                DayMath.relative(myUpdate)?.let {
                    Timestamp(stringResource(R.string.distance_your_update, it.toString()))
                }
                if (!theirsLive) {
                    Spacer(Modifier.height(4.dp))
                    Timestamp(stringResource(R.string.distance_live_hint))
                }
            }
            DistanceState.Stale -> CardValue(stringResource(R.string.distance_stale))
            DistanceState.Missing -> CardValue(stringResource(R.string.distance_unknown))
            DistanceState.PermissionDenied -> {
                CardValue(stringResource(R.string.distance_location_off))
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.distance_open_settings),
                    style = FiloType.Label,
                    color = Crimson,
                    modifier = Modifier.clickable { openAppSettings(context) },
                )
            }
        }
    }
}

@Composable
private fun CountdownCard(
    snapshot: CoupleSnapshot,
    locale: String,
    onOpenCountdowns: () -> Unit,
) {
    val context = LocalContext.current
    val primary = snapshot.primaryCountdown
    FiloCard(onClick = onOpenCountdowns) {
        SectionLabel(stringResource(R.string.label_next_visit))
        Spacer(Modifier.height(8.dp))
        if (primary == null) {
            CardValue(stringResource(R.string.countdowns_empty))
        } else {
            val date = PgTime.localDate(primary.date)
            if (date == null) {
                CardValue(stringResource(R.string.state_no_data))
            } else {
                val today = LocalDate.now(ZoneId.systemDefault())
                val numeral = DayMath.countdownNumeral(date, today)
                val unit = DayMath.countdownUnit(context, date, today)
                if (numeral != null) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(numeral, style = FiloType.Numeral, color = Crimson)
                        Spacer(Modifier.width(10.dp))
                        unit?.let {
                            Text(it, style = FiloType.Value, color = Ash, modifier = Modifier.padding(bottom = 10.dp))
                        }
                    }
                } else {
                    // Today and Tomorrow are whole phrases, so they carry the card on their own.
                    CardValue(DayMath.countdownText(context, date, today))
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = listOfNotNull(primary.emoji?.takeIf { it.isNotBlank() }, primary.label(locale))
                        .joinToString(" "),
                    style = FiloType.Body,
                    color = Bone,
                )
                Timestamp(DayMath.formatDate(date))
            }
        }
    }
}

@Composable
private fun PingCard(onPing: () -> Unit) {
    var sentAt by remember { mutableStateOf<Long?>(null) }
    val recentlySent = sentAt != null && System.currentTimeMillis() - sentAt!! < 60_000

    FiloCard(
        onClick = {
            if (!recentlySent) {
                onPing()
                sentAt = System.currentTimeMillis()
            }
        },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("♥", fontSize = 26.sp, color = if (recentlySent) Ash else Crimson)
            Spacer(Modifier.width(12.dp))
            Text(
                text = stringResource(if (recentlySent) R.string.ping_sent else R.string.ping_button),
                style = FiloType.Value,
                color = if (recentlySent) Ash else Bone,
            )
        }
    }
}
