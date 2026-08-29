package org.primftpd.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.primftpd.R
import org.primftpd.ui.data.ColorBag

@Composable
internal fun SwitchPrefRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    colorBag: ColorBag
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedTrackColor = if(colorBag.useM3Color) ButtonDefaults.buttonColors().containerColor else colorBag.darkMuted
            )
        )
    }
}
@Composable
internal fun EditPrefRow(
    title: String,
    description: String,
    currentValue: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = currentValue,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
internal fun ListPrefRow(
    title: String,
    description: String,
    selectedLabel: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = selectedLabel,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
internal fun ClickPrefRow(
    title: String,
    description: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
internal fun SliderRow(
    sliderStep : Int,
    maxSlideValue: Float,
    sliderTitle: String,
    sliderDescription: String,
    rememberedSliderPosition: Float,
    onSliderValueChange: (Float) -> Unit = {},
    colorBag: ColorBag
){
    Row(
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 12.dp)
        //verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(text = sliderTitle, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = sliderDescription,
                style = MaterialTheme.typography.bodySmall,
                color =  MaterialTheme.colorScheme.onSurfaceVariant
            )
            SliderAdvancedExample(sliderStep, maxSlideValue, rememberedSliderPosition = rememberedSliderPosition, onSliderValueChange = onSliderValueChange,
                colorBag = colorBag)
        }
    }
}

@Composable
internal fun SliderAdvancedExample(
    sliderStep: Int,
    maxSlideValue: Float,
    rememberedSliderPosition: Float,
    onSliderValueChange: (Float) -> Unit,
    colorBag: ColorBag
) {
    var sliderPosition by remember { mutableFloatStateOf(rememberedSliderPosition) }
    val sliderColors = if (!colorBag.useM3Color) {
        SliderDefaults.colors(
            thumbColor = colorBag.vibrant,
            activeTrackColor = colorBag.vibrant,
            inactiveTrackColor = colorBag.darkMuted,
            activeTickColor = colorBag.muted,
            inactiveTickColor = colorBag.vibrant
        )
        //这里记一下，就是滚动到ui选项的函数其实是一个挂起函数，所以这里可能看到取色会慢半拍显示
    } else {
        SliderDefaults.colors(
        )
    }
    Column {
        Slider(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth(),
            value = sliderPosition,
            onValueChange = { sliderPosition = it; onSliderValueChange(it) },
            colors = sliderColors,
            steps = sliderStep,
            valueRange = 0f..maxSlideValue
        )
        Text(
            text = "Current intensity: ${sliderPosition.toInt()}",
            style = MaterialTheme.typography.bodySmall)
    }
}
// ─── Dialog Composables ──────────────────────────────────────────

@Composable
internal fun EditTextDialog(
    title: String,
    currentValue: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    validate: (String) -> String?,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var text by remember { mutableStateOf(currentValue) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it; error = validate(it) },
                singleLine = true,
                isError = error != null,
                supportingText = error?.let { { Text(it) } },
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                modifier = Modifier.offset(y = (-2).dp),
                onClick = {
                    if (error == null) {
                        onConfirm(text)
                        onDismiss()
                    }
                },
                enabled = error == null && text.isNotEmpty()
            ) {
                Text("OK", fontSize = MaterialTheme.typography.bodyLarge.fontSize)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss,
                modifier = Modifier.offset(y = (-2).dp)
            ) {
                Text(stringResource(R.string.cancel), fontSize = MaterialTheme.typography.bodyLarge.fontSize)
            }
        }
    )
}

@Composable
internal fun PasswordEditDialog(
    title: String,
    onDismiss: () -> Unit,
    onConfirm: (String?) -> Unit
) {
    var text by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    var clear by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    enabled = !clear,
                    visualTransformation = if (visible) VisualTransformation.None
                    else PasswordVisualTransformation(),
                    label = { Text(stringResource(R.string.prefTitlePassword)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { visible = !visible }) {
                        Text(if (visible) "Hide" else "Show")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(onClick = { clear = !clear; text = "" }) {
                        Text(
                            if (clear) "Keep password" else "Clear password",
                            color = if (clear) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (clear) {
                        onConfirm(null)
                    } else if (text.isNotBlank()) {
                        onConfirm(text)
                    }
                    onDismiss()
                },
                modifier = Modifier.offset(y = (-2).dp)
            ) {
                Text("OK",fontSize = MaterialTheme.typography.bodyLarge.fontSize)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss,
                modifier = Modifier.offset(y = (-2).dp)) {
                Text(stringResource(R.string.cancel), fontSize = MaterialTheme.typography.bodyLarge.fontSize)
            }
        }
    )
}

@Composable
internal fun ListSelectionDialog(
    title: String,
    entries: List<String>,
    entryValues: List<String>,
    selectedIndex: Int,
    onDismiss: () -> Unit,
    onSelected: (Int, String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                entries.forEachIndexed { index, entry ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onSelected(index, entryValues[index])
                                onDismiss()
                            }
                            .padding(vertical = 12.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = index == selectedIndex,
                            onClick = {
                                onSelected(index, entryValues[index])
                                onDismiss()
                            }
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(text = entry, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
internal fun MultiSelectDialog(
    title: String,
    entries: List<String>,
    entryValues: List<String>,
    initialSelected: Set<String>,
    onDismiss: () -> Unit,
    onConfirm: (Set<String>) -> Unit
) {
    var selected by remember { mutableStateOf(initialSelected) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                entries.forEachIndexed { index, entry ->
                    val value = entryValues[index]
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selected = if (value in selected) {
                                    // Don't allow deselecting last item
                                    if (selected.size > 1) selected - value else selected
                                } else {
                                    selected + value
                                }
                            }
                            .padding(vertical = 12.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = value in selected,
                            onCheckedChange = {
                                selected = if (it) {
                                    selected + value
                                } else {
                                    if (selected.size > 1) selected - value else selected
                                }
                            }
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(text = entry, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(selected); onDismiss() },
                modifier = Modifier.offset(y = (-8).dp)
            ) {
                Text("OK", fontSize = MaterialTheme.typography.bodyLarge.fontSize)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.offset(y = (-8).dp)
                ) {
                Text(stringResource(R.string.cancel), fontSize = MaterialTheme.typography.bodyLarge.fontSize)
            }
        }
    )
}


@Preview
@Composable
fun MutiSelectDialogPreview() {
    MaterialTheme {
        MultiSelectDialog(
            title = "Select Options",
            entries = listOf("Option 1", "Option 2", "Option 3"),
            entryValues = listOf("opt1", "opt2", "opt3"),
            initialSelected = setOf("opt1"),
            onDismiss = {},
            onConfirm = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
fun SliderOfRowPreview() {
    MaterialTheme {
        SliderRow(
            sliderStep = 7,
            maxSlideValue = 40f,
            sliderTitle = "Slider Title",
            sliderDescription = "Slider Description",
            rememberedSliderPosition = 20f,
            colorBag = ColorBag(
                useM3Color = true,
                lightMuted = MaterialTheme.colorScheme.primary,
                darkMuted = MaterialTheme.colorScheme.onSurfaceVariant,
                vibrant = MaterialTheme.colorScheme.secondary,
                muted = MaterialTheme.colorScheme.surfaceVariant
            )
        )
    }
}
