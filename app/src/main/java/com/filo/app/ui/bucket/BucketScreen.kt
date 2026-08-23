package com.filo.app.ui.bucket

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.filo.app.R
import com.filo.app.core.time.DayMath
import com.filo.app.core.time.PgTime
import com.filo.app.data.model.BucketItem
import com.filo.app.ui.components.FiloCard
import com.filo.app.ui.components.FiloTextField
import com.filo.app.ui.components.SectionLabel
import com.filo.app.ui.components.Timestamp
import com.filo.app.ui.components.OrbBackground
import com.filo.app.ui.theme.Ash
import com.filo.app.ui.theme.Crimson
import com.filo.app.ui.theme.Bone
import com.filo.app.ui.theme.FiloType
import com.filo.app.ui.theme.Ink
import com.filo.app.ui.theme.LocalReducedMotion
import com.filo.app.ui.theme.Surface

@Composable
fun BucketScreen(
    items: List<BucketItem>,
    onBack: () -> Unit,
    onAdd: (String) -> Unit,
    onToggle: (String, Boolean) -> Unit,
    onDelete: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var draft by remember { mutableStateOf("") }
    var doneExpanded by remember { mutableStateOf(false) }
    val open = items.filter { !it.done }
    val done = items.filter { it.done }

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
                Text(stringResource(R.string.bucket_title), style = FiloType.Title, color = Bone)
                Text(
                    stringResource(R.string.pairing_back),
                    style = FiloType.Label,
                    color = Ash,
                    modifier = Modifier.clickable { onBack() },
                )
            }

            if (items.isNotEmpty()) {
                ProgressCard(doneCount = done.size, total = items.size)
            }

            // One composer, always at the top, so adding never means hunting for a button.
            FiloCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.weight(1f)) {
                        FiloTextField(
                            value = draft,
                            onValueChange = { draft = it.take(120) },
                            label = stringResource(R.string.bucket_add_hint),
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    AddButton(enabled = draft.isNotBlank()) {
                        onAdd(draft)
                        draft = ""
                    }
                }
            }

            if (items.isEmpty()) {
                FiloCard {
                    Text(
                        text = stringResource(R.string.bucket_empty),
                        style = FiloType.Body,
                        color = Ash,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    )
                }
            }

            // The whole list is one card with hairline dividers, rather than a card per row:
            // a stack of separate cards is what made this look like a debug screen.
            if (open.isNotEmpty()) {
                FiloCard {
                    open.forEachIndexed { index, item ->
                        BucketRow(item = item, onToggle = onToggle, onDelete = onDelete)
                        if (index != open.lastIndex) {
                            HorizontalDivider(color = Crimson.copy(alpha = 0.14f))
                        }
                    }
                }
            }

            if (done.isNotEmpty()) {
                FiloCard {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { doneExpanded = !doneExpanded },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        SectionLabel("${stringResource(R.string.bucket_done_section)}  ${done.size}")
                        Chevron(expanded = doneExpanded)
                    }
                    AnimatedVisibility(
                        visible = doneExpanded,
                        enter = expandVertically(),
                        exit = shrinkVertically(),
                    ) {
                        Column {
                            Spacer(Modifier.height(6.dp))
                            done.forEachIndexed { index, item ->
                                BucketRow(item = item, onToggle = onToggle, onDelete = onDelete)
                                if (index != done.lastIndex) {
                                    HorizontalDivider(color = Crimson.copy(alpha = 0.14f))
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

/** How far along the two of you are, which is the only number this screen has. */
@Composable
private fun ProgressCard(doneCount: Int, total: Int) {
    val fraction = if (total == 0) 0f else doneCount.toFloat() / total
    val reducedMotion = LocalReducedMotion.current
    val animated by animateFloatAsState(
        targetValue = fraction,
        animationSpec = if (reducedMotion) tween(0) else tween(420, easing = LinearOutSlowInEasing),
        label = "bucketProgress",
    )
    FiloCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            SectionLabel(stringResource(R.string.bucket_progress))
            Text(
                text = stringResource(R.string.bucket_progress_count, doneCount, total),
                style = FiloType.Value,
                color = Crimson,
            )
        }
        Spacer(Modifier.height(12.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Ink),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(animated)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Crimson),
            )
        }
    }
}

@Composable
private fun BucketRow(
    item: BucketItem,
    onToggle: (String, Boolean) -> Unit,
    onDelete: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle(item.id, !item.done) }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CheckCircle(checked = item.done)
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.text,
                style = FiloType.Body,
                color = if (item.done) Ash else Bone,
                textDecoration = if (item.done) TextDecoration.LineThrough else null,
            )
            if (item.done) {
                PgTime.instant(item.doneAt)?.let { instant ->
                    Timestamp(
                        stringResource(
                            R.string.bucket_done_on,
                            DayMath.relative(instant)?.toString().orEmpty(),
                        ),
                    )
                }
            }
        }
        Spacer(Modifier.width(10.dp))
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .clickable { onDelete(item.id) },
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.ic_trash),
                contentDescription = stringResource(R.string.bucket_delete),
                colorFilter = ColorFilter.tint(Ash.copy(alpha = 0.7f)),
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/** A real checkbox: a ring that fills and draws a tick, rather than a text glyph. */
@Composable
private fun CheckCircle(checked: Boolean) {
    val reducedMotion = LocalReducedMotion.current
    val progress by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = if (reducedMotion) tween(0) else tween(220, easing = LinearOutSlowInEasing),
        label = "checkCircle",
    )
    Canvas(modifier = Modifier.size(24.dp)) {
        val radius = size.minDimension / 2f
        val centre = Offset(size.width / 2f, size.height / 2f)

        drawCircle(color = Crimson, radius = radius * progress, center = centre)
        drawCircle(
            color = if (checked) Crimson else Ash.copy(alpha = 0.55f),
            radius = radius - 1.dp.toPx(),
            center = centre,
            style = Stroke(width = 1.6.dp.toPx()),
        )
        if (progress > 0.1f) {
            // A tick drawn as two strokes, revealed with the fill.
            val path = Path().apply {
                moveTo(size.width * 0.28f, size.height * 0.52f)
                lineTo(size.width * 0.44f, size.height * 0.68f)
                lineTo(size.width * 0.74f, size.height * 0.34f)
            }
            drawPath(
                path = path,
                color = Ink,
                style = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round),
                alpha = progress,
            )
        }
    }
}

@Composable
private fun AddButton(enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(52.dp)
            .clip(CircleShape)
            .background(if (enabled) Crimson else Surface)
            // Without an outline the disabled state is a floating glyph on black: it has to
            // still look like a button you could press once there is something to add.
            .border(1.dp, if (enabled) Crimson else Crimson.copy(alpha = 0.45f), CircleShape)
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_plus),
            contentDescription = stringResource(R.string.bucket_add),
            colorFilter = ColorFilter.tint(if (enabled) Ink else Ash),
            modifier = Modifier.size(22.dp),
        )
    }
}

@Composable
private fun Chevron(expanded: Boolean) {
    val reducedMotion = LocalReducedMotion.current
    val angle by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = if (reducedMotion) tween(0) else tween(200, easing = LinearOutSlowInEasing),
        label = "chevron",
    )
    Image(
        painter = painterResource(R.drawable.ic_chevron_down),
        contentDescription = null,
        colorFilter = ColorFilter.tint(Ash),
        modifier = Modifier.size(20.dp).rotate(angle),
    )
}
