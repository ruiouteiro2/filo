package com.filo.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.filo.app.ui.theme.Ash
import com.filo.app.ui.theme.FiloType
import com.filo.app.ui.theme.Crimson
import com.filo.app.ui.theme.Ink
import com.filo.app.ui.theme.Bone
import com.filo.app.ui.theme.Surface

@Composable
fun FiloButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(999.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Crimson,
            contentColor = Ink,
            disabledContainerColor = Surface,
            disabledContentColor = Ash,
        ),
        modifier = modifier.fillMaxWidth().height(52.dp),
    ) {
        Text(text, style = FiloType.Label)
    }
}

@Composable
fun FiloSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(999.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = Color.Transparent,
            contentColor = Bone,
            disabledContentColor = Ash,
        ),
        modifier = modifier.fillMaxWidth().height(52.dp),
    ) {
        Text(text, style = FiloType.Label)
    }
}

@Composable
fun FiloTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    singleLine: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Done,
    textStyle: TextStyle = FiloType.Body,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, style = FiloType.Label) },
        placeholder = placeholder?.let { { Text(it, style = FiloType.Body, color = Ash) } },
        singleLine = singleLine,
        textStyle = textStyle,
        shape = RoundedCornerShape(14.dp),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = Bone,
            unfocusedTextColor = Bone,
            focusedContainerColor = Surface,
            unfocusedContainerColor = Surface,
            cursorColor = Crimson,
            focusedBorderColor = Crimson,
            unfocusedBorderColor = Surface,
            focusedLabelColor = Crimson,
            unfocusedLabelColor = Ash,
        ),
        modifier = modifier.fillMaxWidth(),
    )
}

/** Two option toggle used for the language choice. */
@Composable
fun FiloSegmented(
    options: List<Pair<String, String>>,
    selectedKey: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Surface, RoundedCornerShape(14.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEach { (key, label) ->
            val selected = key == selectedKey
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .background(if (selected) Crimson else Color.Transparent, RoundedCornerShape(11.dp))
                    .then(
                        if (selected) Modifier else Modifier.border(1.dp, Crimson.copy(alpha = 0.2f), RoundedCornerShape(11.dp)),
                    )
                    .clickable { onSelect(key) },
                contentAlignment = Alignment.Center,
            ) {
                Text(label, style = FiloType.Label, color = if (selected) Ink else Bone)
            }
        }
    }
}
