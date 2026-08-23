package com.filo.app.ui.pairing

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.filo.app.R
import com.filo.app.data.PairError
import com.filo.app.ui.components.FiloButton
import com.filo.app.ui.components.FiloSecondaryButton
import com.filo.app.ui.components.FiloSegmented
import com.filo.app.ui.components.FiloTextField
import com.filo.app.ui.theme.Ash
import com.filo.app.ui.theme.Ember
import com.filo.app.ui.theme.FiloType
import com.filo.app.ui.theme.NumeralFamily
import com.filo.app.ui.theme.Blood
import com.filo.app.ui.theme.Ink
import com.filo.app.ui.theme.Bone

private enum class Step { Welcome, Join, ShowCode }

/**
 * Pairing has to survive being explained over WhatsApp, so it is three plain steps and a
 * code made of unambiguous characters.
 */
@Composable
fun PairingScreen(
    busy: Boolean,
    error: PairError?,
    createdCode: String?,
    initialLocale: String,
    onCreate: (name: String, locale: String) -> Unit,
    onJoin: (code: String, name: String, locale: String) -> Unit,
    onNormaliseCode: (String) -> String,
    onDismissError: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var step by remember { mutableStateOf(Step.Welcome) }
    var name by remember { mutableStateOf("") }
    var locale by remember { mutableStateOf(initialLocale) }
    var code by remember { mutableStateOf("") }
    var nameMissing by remember { mutableStateOf(false) }

    // As soon as a code exists we are created and linked; show it.
    if (createdCode != null && step != Step.ShowCode) step = Step.ShowCode

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Ink)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.pairing_welcome), style = FiloType.Title, color = Bone)
        Text(
            stringResource(R.string.pairing_subtitle),
            style = FiloType.Body,
            color = Ash,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(40.dp))

        when (step) {
            Step.Welcome -> {
                FiloTextField(
                    value = name,
                    onValueChange = { name = it.take(24); nameMissing = false },
                    label = stringResource(R.string.pairing_name_label),
                    placeholder = stringResource(R.string.pairing_name_hint),
                    imeAction = ImeAction.Next,
                )
                Spacer(Modifier.height(20.dp))
                Text(
                    stringResource(R.string.pairing_language_label),
                    style = FiloType.Label,
                    color = Ash,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                FiloSegmented(
                    options = listOf(
                        "en" to stringResource(R.string.pairing_language_en),
                        "it" to stringResource(R.string.pairing_language_it),
                    ),
                    selectedKey = locale,
                    onSelect = { locale = it },
                )
                Spacer(Modifier.height(32.dp))
                FiloButton(
                    text = stringResource(R.string.pairing_create),
                    enabled = !busy,
                    onClick = {
                        if (name.isBlank()) nameMissing = true else onCreate(name.trim(), locale)
                    },
                )
                Spacer(Modifier.height(12.dp))
                FiloSecondaryButton(
                    text = stringResource(R.string.pairing_join),
                    enabled = !busy,
                    onClick = {
                        if (name.isBlank()) nameMissing = true else { onDismissError(); step = Step.Join }
                    },
                )
                if (nameMissing) {
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.pairing_name_required), style = FiloType.Body, color = Ember)
                }
            }

            Step.Join -> {
                FiloTextField(
                    value = code,
                    onValueChange = { code = onNormaliseCode(it) },
                    label = stringResource(R.string.pairing_code_label),
                    placeholder = stringResource(R.string.pairing_code_hint),
                    textStyle = FiloType.Value.copy(letterSpacing = 6.sp),
                )
                Spacer(Modifier.height(24.dp))
                FiloButton(
                    text = stringResource(R.string.pairing_code_submit),
                    enabled = !busy && code.length == 6,
                    onClick = { onJoin(code, name.trim(), locale) },
                )
                Spacer(Modifier.height(12.dp))
                FiloSecondaryButton(
                    text = stringResource(R.string.pairing_back),
                    enabled = !busy,
                    onClick = { onDismissError(); step = Step.Welcome },
                )
            }

            Step.ShowCode -> {
                val context = LocalContext.current
                val shown = createdCode.orEmpty()
                Text(stringResource(R.string.pairing_your_code_label), style = FiloType.Label, color = Ash)
                Spacer(Modifier.height(16.dp))
                Text(
                    text = shown,
                    fontFamily = NumeralFamily,
                    fontSize = 56.sp,
                    letterSpacing = 8.sp,
                    color = Blood,
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    stringResource(R.string.pairing_your_code_body),
                    style = FiloType.Body,
                    color = Ash,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(32.dp))
                val shareText = stringResource(R.string.pairing_share_text, shown)
                val chooserTitle = stringResource(R.string.pairing_share_chooser)
                FiloButton(
                    text = stringResource(R.string.pairing_share),
                    onClick = {
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, shareText)
                        }
                        context.startActivity(Intent.createChooser(send, chooserTitle))
                    },
                )
                Spacer(Modifier.height(12.dp))
                FiloSecondaryButton(text = stringResource(R.string.pairing_done), onClick = onDone)
            }
        }

        if (error != null) {
            Spacer(Modifier.height(20.dp))
            Text(
                text = stringResource(
                    when (error) {
                        PairError.NoSuchCode -> R.string.pairing_error_no_code
                        PairError.CoupleFull -> R.string.pairing_error_full
                        PairError.Offline -> R.string.pairing_error_offline
                        PairError.Unknown -> R.string.pairing_error_unknown
                    },
                ),
                style = FiloType.Body,
                color = Ember,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Suppress("unused")
private val keyboardCapitalizationHint = KeyboardCapitalization.Characters
