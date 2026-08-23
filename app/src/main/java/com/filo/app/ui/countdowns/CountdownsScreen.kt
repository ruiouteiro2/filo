package com.filo.app.ui.countdowns

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.filo.app.R
import com.filo.app.core.time.DayMath
import com.filo.app.core.time.PgTime
import com.filo.app.data.model.Countdown
import com.filo.app.ui.components.FiloButton
import com.filo.app.ui.components.FiloCard
import com.filo.app.ui.components.FiloSecondaryButton
import com.filo.app.ui.components.FiloTextField
import com.filo.app.ui.components.OrbBackground
import com.filo.app.ui.components.SectionLabel
import com.filo.app.ui.components.Timestamp
import com.filo.app.ui.theme.Ash
import com.filo.app.ui.theme.Crimson
import com.filo.app.ui.theme.Bone
import com.filo.app.ui.theme.Ember
import com.filo.app.ui.theme.FiloType
import com.filo.app.ui.theme.Ink
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Things people actually count down to, so nobody has to hunt for an emoji keyboard. */
private val EmojiSuggestions = listOf("✈️", "🚆", "🏠", "🎂", "❤️", "🌊", "🎉", "🎄")

@Composable
fun CountdownsScreen(
    countdowns: List<Countdown>,
    locale: String,
    onBack: () -> Unit,
    onAdd: (String, String, LocalDate, String?, Boolean) -> Unit,
    onUpdate: (String, String, String, LocalDate, String?) -> Unit,
    onDelete: (String) -> Unit,
    onSetPrimary: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var editing by remember { mutableStateOf<Countdown?>(null) }
    var adding by remember { mutableStateOf(false) }
    val today = LocalDate.now(ZoneId.systemDefault())

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
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.countdowns_title), style = FiloType.Title, color = Bone)
                Text(
                    stringResource(R.string.pairing_back),
                    style = FiloType.Label,
                    color = Ash,
                    modifier = Modifier.clickable { onBack() },
                )
            }

            if (countdowns.isEmpty()) {
                FiloCard {
                    Text(
                        text = stringResource(R.string.countdowns_empty),
                        style = FiloType.Body,
                        color = Ash,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    )
                }
            }

            countdowns.forEach { countdown ->
                CountdownRow(
                    countdown = countdown,
                    locale = locale,
                    today = today,
                    onEdit = { editing = countdown },
                    onMakePrimary = { onSetPrimary(countdown.id) },
                )
            }

            FiloButton(text = stringResource(R.string.countdowns_add), onClick = { adding = true })
            Spacer(Modifier.height(24.dp))
        }
    }

    if (adding) {
        CountdownEditor(
            existing = null,
            onDismiss = { adding = false },
            onSave = { en, it2, date, emoji, primary ->
                onAdd(en, it2, date, emoji, primary)
                adding = false
            },
            onDelete = null,
        )
    }

    editing?.let { current ->
        CountdownEditor(
            existing = current,
            onDismiss = { editing = null },
            onSave = { en, it2, date, emoji, _ ->
                onUpdate(current.id, en, it2, date, emoji)
                editing = null
            },
            onDelete = {
                onDelete(current.id)
                editing = null
            },
        )
    }
}

@Composable
private fun CountdownRow(
    countdown: Countdown,
    locale: String,
    today: LocalDate,
    onEdit: () -> Unit,
    onMakePrimary: () -> Unit,
) {
    val context = LocalContext.current
    val date = PgTime.localDate(countdown.date)
    FiloCard(onClick = onEdit) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                countdown.emoji?.takeIf { it.isNotBlank() }?.let {
                    Text(it, fontSize = 26.sp)
                    Spacer(Modifier.width(12.dp))
                }
                Column {
                    if (countdown.isPrimary) {
                        Text(
                            stringResource(R.string.label_next_visit).uppercase(),
                            style = FiloType.Label,
                            color = Crimson,
                        )
                        Spacer(Modifier.height(2.dp))
                    }
                    Text(countdown.label(locale), style = FiloType.Value, color = Bone)
                    date?.let { Timestamp(DayMath.formatDate(it)) }
                }
            }
            if (date != null) {
                Column(horizontalAlignment = Alignment.End) {
                    val numeral = DayMath.countdownNumeral(date, today)
                    if (numeral != null) {
                        Text(
                            text = numeral,
                            style = FiloType.Value,
                            color = if (date.isBefore(today)) Ash else Crimson,
                        )
                        DayMath.countdownUnit(context, date, today)?.let {
                            Text(it, style = FiloType.Timestamp, color = Ash)
                        }
                    } else {
                        Text(
                            text = DayMath.countdownText(context, date, today),
                            style = FiloType.Value,
                            color = Crimson,
                        )
                    }
                }
            }
        }
        if (!countdown.isPrimary) {
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(R.string.countdowns_make_primary),
                style = FiloType.Label,
                color = Crimson,
                modifier = Modifier.clickable { onMakePrimary() },
            )
        }
    }
}

/**
 * One name, one date, one emoji.
 *
 * The Italian label is stored separately because both people have to be able to read it, but
 * asking for two labels up front made the form look twice as long as the job. It is now a
 * link that most people will never open, and leaving it closed simply reuses the same text.
 */
@Composable
private fun CountdownEditor(
    existing: Countdown?,
    onDismiss: () -> Unit,
    onSave: (String, String, LocalDate, String?, Boolean) -> Unit,
    onDelete: (() -> Unit)?,
) {
    val context = LocalContext.current
    var label by remember { mutableStateOf(existing?.labelEn.orEmpty()) }
    var labelIt by remember {
        mutableStateOf(existing?.labelIt.takeIf { it != existing?.labelEn }.orEmpty())
    }
    var showTranslation by remember { mutableStateOf(labelIt.isNotBlank()) }
    var emoji by remember { mutableStateOf(existing?.emoji.orEmpty()) }
    var primary by remember { mutableStateOf(existing?.isPrimary ?: false) }
    var date by remember {
        mutableStateOf(PgTime.localDate(existing?.date) ?: LocalDate.now(ZoneId.systemDefault()).plusDays(30))
    }
    var showDate by remember { mutableStateOf(false) }
    val today = LocalDate.now(ZoneId.systemDefault())

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .background(Ink, RoundedCornerShape(22.dp))
                .border(1.dp, Crimson.copy(alpha = 0.35f), RoundedCornerShape(22.dp))
                .padding(22.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                text = stringResource(
                    if (existing == null) R.string.countdowns_new else R.string.countdowns_edit,
                ),
                style = FiloType.Value,
                color = Bone,
            )

            Spacer(Modifier.height(20.dp))
            SectionLabel(stringResource(R.string.countdowns_what))
            Spacer(Modifier.height(10.dp))
            FiloTextField(
                value = label,
                onValueChange = { label = it.take(40) },
                label = stringResource(R.string.countdowns_name),
            )
            Spacer(Modifier.height(8.dp))
            if (!showTranslation) {
                Text(
                    text = stringResource(R.string.countdowns_add_translation),
                    style = FiloType.Label,
                    color = Crimson,
                    modifier = Modifier.clickable { showTranslation = true },
                )
            }
            AnimatedVisibility(visible = showTranslation) {
                Column {
                    FiloTextField(
                        value = labelIt,
                        onValueChange = { labelIt = it.take(40) },
                        label = stringResource(R.string.countdowns_label_it),
                        placeholder = stringResource(R.string.countdowns_label_it_hint),
                    )
                    Spacer(Modifier.height(4.dp))
                    Timestamp(stringResource(R.string.countdowns_translation_hint))
                }
            }

            Spacer(Modifier.height(20.dp))
            SectionLabel(stringResource(R.string.countdowns_emoji))
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                EmojiSuggestions.forEach { suggestion ->
                    val selected = emoji == suggestion
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .background(
                                if (selected) Crimson.copy(alpha = 0.4f) else Color.Transparent,
                                CircleShape,
                            )
                            .clickable { emoji = if (selected) "" else suggestion },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(suggestion, fontSize = if (selected) 21.sp else 18.sp)
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            SectionLabel(stringResource(R.string.countdowns_when))
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Crimson.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
                    .clickable { showDate = true }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(DayMath.formatDate(date), style = FiloType.Body, color = Bone)
                    // A live answer to the only question this form is really asking.
                    Text(
                        text = DayMath.countdownText(context, date, today),
                        style = FiloType.Timestamp,
                        color = Crimson,
                    )
                }
                Text(stringResource(R.string.countdowns_change), style = FiloType.Label, color = Crimson)
            }

            Spacer(Modifier.height(18.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { primary = !primary }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .background(if (primary) Crimson else Color.Transparent, RoundedCornerShape(6.dp))
                        .border(
                            1.5.dp,
                            if (primary) Crimson else Ash.copy(alpha = 0.6f),
                            RoundedCornerShape(6.dp),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (primary) Text("✓", fontSize = 13.sp, color = Ink)
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(stringResource(R.string.countdowns_primary), style = FiloType.Body, color = Bone)
                    Timestamp(stringResource(R.string.countdowns_primary_hint))
                }
            }

            Spacer(Modifier.height(22.dp))
            FiloButton(
                text = stringResource(R.string.countdowns_save),
                enabled = label.isNotBlank(),
                onClick = {
                    // Blank translation means "same in both", which is what the spec always
                    // intended and what most of these labels are anyway.
                    onSave(
                        label.trim(),
                        labelIt.trim().ifBlank { label.trim() },
                        date,
                        emoji.ifBlank { null },
                        primary,
                    )
                },
            )
            Spacer(Modifier.height(10.dp))
            FiloSecondaryButton(text = stringResource(R.string.countdowns_cancel), onClick = onDismiss)
            if (onDelete != null) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.countdowns_delete),
                    style = FiloType.Label,
                    color = Ember,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onDelete() }
                        .padding(12.dp),
                )
            }
        }
    }

    if (showDate) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            colors = DatePickerDefaults.colors(containerColor = Ink),
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let {
                        date = Instant.ofEpochMilli(it).atZone(ZoneId.of("UTC")).toLocalDate()
                    }
                    showDate = false
                }) { Text(stringResource(R.string.save), color = Crimson, style = FiloType.Label) }
            },
            dismissButton = {
                TextButton(onClick = { showDate = false }) {
                    Text(stringResource(R.string.cancel), color = Ash, style = FiloType.Label)
                }
            },
        ) {
            DatePicker(
                state = state,
                colors = DatePickerDefaults.colors(
                    containerColor = Ink,
                    titleContentColor = Bone,
                    headlineContentColor = Bone,
                    weekdayContentColor = Ash,
                    dayContentColor = Bone,
                    selectedDayContainerColor = Crimson,
                    selectedDayContentColor = Ink,
                    todayContentColor = Crimson,
                    todayDateBorderColor = Crimson,
                ),
            )
        }
    }
}
