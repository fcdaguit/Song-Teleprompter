package com.chordflow.prompter.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chordflow.prompter.core.ChordTransposer
import com.chordflow.prompter.ui.theme.PhosphorDim
import com.chordflow.prompter.ui.theme.PhosphorGreen
import com.chordflow.prompter.ui.theme.PhosphorMid
import com.chordflow.prompter.ui.theme.StageBlack

/** Thin green rule, used instead of Material dividers so the look stays flat. */
@Composable
fun TerminalRule(modifier: Modifier = Modifier, color: Color = PhosphorDim) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(color)
    )
}

/** All-caps monospace label, the workhorse of this UI. */
@Composable
fun TerminalLabel(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = PhosphorMid
) {
    Text(
        text = text.uppercase(),
        modifier = modifier,
        color = color,
        style = MaterialTheme.typography.labelLarge
    )
}

/** Bordered panel. */
@Composable
fun TerminalPanel(
    modifier: Modifier = Modifier,
    borderColor: Color = PhosphorDim,
    content: @Composable () -> Unit
) {
    Column(
        modifier = modifier
            .border(width = 1.dp, color = borderColor, shape = RectangleShape)
            .padding(12.dp)
    ) {
        content()
    }
}

/** Filled stage button: black text on green, for primary actions. */
@Composable
fun TerminalButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = RectangleShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = PhosphorGreen,
            contentColor = StageBlack,
            disabledContainerColor = PhosphorDim,
            disabledContentColor = StageBlack
        )
    ) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** Outlined stage button: green text on black, for secondary actions. */
@Composable
fun TerminalOutlineButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = RectangleShape,
        border = BorderStroke(1.dp, if (enabled) PhosphorGreen else PhosphorDim),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = PhosphorGreen,
            disabledContentColor = PhosphorDim
        )
    ) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** Compact square-ish button for arrows and +/- controls. */
@Composable
fun TerminalMiniButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = RectangleShape,
        border = BorderStroke(1.dp, if (enabled) PhosphorGreen else PhosphorDim),
        contentPadding = PaddingValues(
            horizontal = 10.dp,
            vertical = 4.dp
        ),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = PhosphorGreen,
            disabledContentColor = PhosphorDim
        )
    ) {
        Text(text = label, fontSize = 14.sp, maxLines = 1)
    }
}

/** Monospace text field. Colours come from the theme, so it stays green on black. */
@Composable
fun TerminalTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    minLines: Int = 1
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        label = { Text(text = label.uppercase(), style = MaterialTheme.typography.labelSmall) },
        singleLine = singleLine,
        minLines = minLines,
        shape = RectangleShape,
        textStyle = MaterialTheme.typography.bodyLarge
    )
}

/**
 * Target-key dropdown. Shows the current key and lets the user jump straight
 * to any of the twelve keys.
 */
@Composable
fun KeyDropdown(
    currentKey: String,
    onKeySelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    labelPrefix: String = "KEY"
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        TerminalOutlineButton(
            label = "$labelPrefix: $currentKey",
            onClick = { expanded = true }
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(StageBlack)
        ) {
            ChordTransposer.SELECTABLE_KEYS.forEach { key ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = key,
                            color = if (key == currentKey) PhosphorGreen else PhosphorMid,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    },
                    onClick = {
                        expanded = false
                        onKeySelected(key)
                    }
                )
            }
        }
    }
}

/** Standard screen frame: title bar, optional back action, scrolling body. */
@Composable
fun ScreenFrame(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    scrollable: Boolean = false,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(StageBlack)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (onBack != null) {
                TerminalMiniButton(label = "< BACK", onClick = onBack)
                Spacer(modifier = Modifier.width(12.dp))
            }
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = title.uppercase(),
                    color = PhosphorGreen,
                    style = MaterialTheme.typography.headlineSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        color = PhosphorMid,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        TerminalRule()
        if (scrollable) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                content()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                content()
            }
        }
    }
}

/** Inline message block for errors and empty states. */
@Composable
fun TerminalNotice(message: String, modifier: Modifier = Modifier, isError: Boolean = false) {
    val colour = if (isError) MaterialTheme.colorScheme.error else PhosphorMid
    Box(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, colour, RectangleShape)
            .padding(10.dp)
    ) {
        Text(text = message, color = colour, style = MaterialTheme.typography.bodyMedium)
    }
}
