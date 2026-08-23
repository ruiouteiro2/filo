package com.filo.app.ui.countdowns

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.filo.app.R
import com.filo.app.core.time.DayMath
import com.filo.app.core.time.PgTime
import com.filo.app.data.model.Countdown
import com.filo.app.ui.components.CardValue
import com.filo.app.ui.components.FiloButton
import com.filo.app.ui.components.FiloCard
import com.filo.app.ui.components.FiloSecondaryButton
import com.filo.app.ui.components.FiloTextField
import com.filo.app.ui.components.SectionLabel
import com.filo.app.ui.components.Timestamp
import com.filo.app.ui.theme.Ash
import com.filo.app.ui.theme.Ember
import com.filo.app.ui.theme.FiloType
import com.filo.app.ui.theme.Blood
import com.filo.app.ui.theme.Ink
import com.filo.app.ui.theme.Bone
import com.filo.app.ui.theme.Crimson
import com.filo.app.ui.theme.Surface
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

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

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Ink)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(R.string.countdowns_title), style = FiloType.Title, color = Bone)
            Text(
                stringResource(R.string.pairing_back),
                style = FiloType.Label,
                color = Ash,
                modifier = Modifier.clickable { onBack() },
            )
        }

        if (countdowns.isEmpty()) {
            FiloCard { CardValue(stringResource(R.string.countdowns_empty)) }
        }

        countdowns.forEach { countdown ->
            val date = PgTime.localDate(countdown.date)
            FiloCard(modifier = Modifier.clickable { editing = countdown }) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        if (countdown.isPrimary) SectionLabel(stringResource(R.string.label_next_visit))
                        CardValue(
                            listOfNotNull(
                                countdown.emoji?.takeIf { it.isNotBlank() },
                                countdown.label(locale),
                            ).joinToString(" "),
                        )
                        if (date != null) {
                            Timestamp(DayMath.formatDate(date))
                        }
                    }
                    if (date != null) {
                        Text(
                            text = DayMath.countdownText(context, date, today),
                            style = FiloType.Value,
                            color = if (date.isBefore(today)) Ash else Blood,
                        )
                    }
                }
                if (!countdown.isPrimary) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        stringResource(R.string.countdowns_make_primary),
                        style = FiloType.Label,
                        color = Ash,
                        modifier = Modifier.clickable { onSetPrimary(countdown.id) },
                    )
                }
            }
        }

        FiloButton(text = stringResource(R.string.countdowns_add), onClick = { adding = true })
        Spacer(Modifier.height(24.dp))
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
private fun CountdownEditor(
    existing: Countdown?,
    onDismiss: () -> Unit,
    onSave: (String, String, LocalDate, String?, Boolean) -> Unit,
    onDelete: (() -> Unit)?,
) {
    var labelEn by remember { mutableStateOf(existing?.labelEn.orEmpty()) }
    var labelIt by remember { mutableStateOf(existing?.labelIt.orEmpty()) }
    var emoji by remember { mutableStateOf(existing?.emoji.orEmpty()) }
    var primary by remember { mutableStateOf(existing?.isPrimary ?: false) }
    var date by remember {
        mutableStateOf(PgTime.localDate(existing?.date) ?: LocalDate.now(ZoneId.systemDefault()).plusDays(30))
    }
    var showDate by remember { mutableStateOf(false) }

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .background(Surface, androidx.compose.foundation.shape.RoundedCornerShape(20.dp))
                .padding(20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            FiloTextField(
                value = labelEn,
                onValueChange = { labelEn = it.take(40) },
                label = stringResource(R.string.countdowns_label_en),
            )
            FiloTextField(
                value = labelIt,
                onValueChange = { labelIt = it.take(40) },
                label = stringResource(R.string.countdowns_label_it),
                placeholder = stringResource(R.string.countdowns_label_it_hint),
            )
            FiloTextField(
                value = emoji,
                onValueChange = { emoji = it.take(4) },
                label = stringResource(R.string.countdowns_emoji),
            )
            Column(modifier = Modifier.clickable { showDate = true }) {
                Timestamp(stringResource(R.string.countdowns_date))
                CardValue(DayMath.formatDate(date))
            }
            if (existing == null) {
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { primary = !primary },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (primary) "●" else "○",
                        style = FiloType.Value,
                        color = if (primary) Crimson else Ash,
                    )
                    Spacer(Modifier.height(0.dp))
                    Text(
                        text = "  " + stringResource(R.string.countdowns_primary),
                        style = FiloType.Body,
                        color = Bone,
                    )
                }
            }
            FiloButton(
                text = stringResource(R.string.countdowns_save),
                enabled = labelEn.isNotBlank(),
                onClick = { onSave(labelEn.trim(), labelIt.trim(), date, emoji.ifBlank { null }, primary) },
            )
            FiloSecondaryButton(text = stringResource(R.string.countdowns_cancel), onClick = onDismiss)
            if (onDelete != null) {
                Text(
                    text = stringResource(R.string.countdowns_delete),
                    style = FiloType.Label,
                    color = Ember,
                    modifier = Modifier.fillMaxWidth().clickable { onDelete() }.padding(8.dp),
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
            colors = DatePickerDefaults.colors(containerColor = Surface),
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let {
                        date = Instant.ofEpochMilli(it).atZone(ZoneId.of("UTC")).toLocalDate()
                    }
                    showDate = false
                }) { Text(stringResource(R.string.save), color = Crimson, style = FiloType.Label) }
            },
        ) {
            DatePicker(
                state = state,
                colors = DatePickerDefaults.colors(
                    containerColor = Surface,
                    titleContentColor = Bone,
                    headlineContentColor = Bone,
                    weekdayContentColor = Ash,
                    dayContentColor = Bone,
                    selectedDayContainerColor = Crimson,
                    selectedDayContentColor = Ink,
                    todayContentColor = Blood,
                    todayDateBorderColor = Blood,
                ),
            )
        }
    }
}
