package com.chordflow.prompter.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.chordflow.prompter.core.ChordDetection
import com.chordflow.prompter.core.ChordSymbol
import com.chordflow.prompter.core.ChordTransposer
import com.chordflow.prompter.core.PromptItem
import com.chordflow.prompter.ui.theme.PanelBlack
import com.chordflow.prompter.ui.theme.PhosphorBright
import com.chordflow.prompter.ui.theme.PhosphorDim
import com.chordflow.prompter.ui.theme.PhosphorGreen
import com.chordflow.prompter.ui.theme.PhosphorMid
import com.chordflow.prompter.ui.theme.StageBlack
import com.chordflow.prompter.vm.MainViewModel
import com.chordflow.prompter.vm.PrompterUiState
import com.chordflow.prompter.vm.TeleprompterViewModel

@Composable
fun TeleprompterScreen(
    main: MainViewModel,
    prompter: TeleprompterViewModel,
    lineupId: Long,
    singleSongId: Long?
) {
    val state by prompter.state.collectAsState()
    val context = LocalContext.current
    val view = LocalView.current
    val configuration = LocalConfiguration.current

    var showControls by remember { mutableStateOf(false) }
    var hasMicPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasMicPermission = granted
    }

    LaunchedEffect(lineupId, singleSongId) {
        prompter.load(lineupId, singleSongId)
    }

    LaunchedEffect(Unit) {
        if (!hasMicPermission) {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    LaunchedEffect(hasMicPermission) {
        if (hasMicPermission) prompter.startListening()
    }

    // Stage screens must not sleep mid-song.
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose {
            view.keepScreenOn = false
            prompter.stopListening()
        }
    }

    val listState = rememberLazyListState()
    LaunchedEffect(state.activeIndex, state.items.size) {
        if (state.items.isNotEmpty()) {
            listState.animateScrollToItem(
                state.activeIndex.coerceIn(0, state.items.lastIndex)
            )
        }
    }

    // The active line sits in the top third of the screen, which is where the
    // eye naturally rests when reading ahead.
    val readingZoneTop = (configuration.screenHeightDp / 3).dp
    val lyricSize = (30 * state.fontScalePercent / 100).coerceIn(16, 72).sp
    val breakSize = (16 * state.fontScalePercent / 100).coerceIn(11, 30).sp

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(StageBlack)
    ) {
        PrompterHud(
            state = state,
            hasMicPermission = hasMicPermission,
            showControls = showControls,
            onToggleControls = { showControls = !showControls },
            onRequestPermission = {
                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            },
            onExit = { main.goHome() },
            prompter = prompter
        )

        TerminalRule(color = PhosphorDim)

        val message = state.emptyMessage
        if (state.loading) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "LOADING LINEUP...",
                    color = PhosphorMid,
                    style = MaterialTheme.typography.headlineSmall
                )
            }
        } else if (message != null) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(20.dp),
                contentAlignment = Alignment.Center
            ) {
                TerminalNotice(message = message, isError = true)
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(
                    start = 18.dp,
                    end = 18.dp,
                    top = readingZoneTop,
                    bottom = 420.dp
                )
            ) {
                itemsIndexed(items = state.items) { index, item ->
                    when (item) {
                        is PromptItem.SongBreak -> SongBreakBanner(
                            item = item,
                            fontSize = breakSize,
                            isActive = index == state.activeIndex,
                            onClick = { prompter.setActiveIndex(index) }
                        )

                        is PromptItem.Lyric -> LyricRow(
                            item = item,
                            fontSize = lyricSize,
                            isActive = index == state.activeIndex,
                            onClick = { prompter.setActiveIndex(index) }
                        )
                    }
                }
            }
        }

        TerminalRule(color = PhosphorDim)

        // Always-visible fallback bar, so a skipped song is one tap away.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TerminalMiniButton(
                label = "<< PREV",
                onClick = { prompter.previousSong() },
                enabled = state.currentSongIndex > 0
            )
            TerminalMiniButton(label = "- LINE", onClick = { prompter.stepLine(-1) })
            TerminalMiniButton(label = "+ LINE", onClick = { prompter.stepLine(1) })
            Spacer(modifier = Modifier.weight(1f))
            TerminalMiniButton(
                label = "NEXT >>",
                onClick = { prompter.nextSong() },
                enabled = state.currentSongIndex < state.songs.lastIndex
            )
        }
    }
}

@Composable
private fun PrompterHud(
    state: PrompterUiState,
    hasMicPermission: Boolean,
    showControls: Boolean,
    onToggleControls: () -> Unit,
    onRequestPermission: () -> Unit,
    onExit: () -> Unit,
    prompter: TeleprompterViewModel
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(PanelBlack)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // ------------------------------------------------- one-line status row
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = statusLine(state),
                color = PhosphorGreen,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.weight(1f),
                maxLines = 2
            )
            MicLevelMeter(level = state.inputLevel, running = state.micRunning)
            Spacer(modifier = Modifier.width(8.dp))
            TerminalMiniButton(
                label = if (showControls) "HIDE" else "HUD",
                onClick = onToggleControls
            )
        }

        // ------------------------------------------------ key + pause controls
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "KEY: ${state.currentKey}",
                color = PhosphorBright,
                style = MaterialTheme.typography.titleMedium
            )
            TerminalMiniButton(label = "-1", onClick = { prompter.nudgeKey(-1) })
            TerminalMiniButton(label = "+1", onClick = { prompter.nudgeKey(1) })
            KeyDropdown(
                currentKey = state.currentKey,
                onKeySelected = { prompter.setKey(it) },
                labelPrefix = "SET"
            )
            Spacer(modifier = Modifier.weight(1f))
            TerminalMiniButton(
                label = if (state.autoScroll) "AUTO: ON" else "AUTO: OFF",
                onClick = { prompter.setAutoScroll(!state.autoScroll) }
            )
            TerminalMiniButton(label = "EXIT", onClick = onExit)
        }

        if (!hasMicPermission) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "MICROPHONE PERMISSION NOT GRANTED - AUTO-SCROLL IS OFF",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.weight(1f)
                )
                TerminalMiniButton(label = "ALLOW MIC", onClick = onRequestPermission)
            }
        }

        state.micError?.let { error ->
            Text(
                text = error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.labelSmall
            )
        }

        if (showControls) {
            TerminalRule(color = PhosphorDim)
            PrompterControls(state = state, prompter = prompter)
        }
    }
}

@Composable
private fun PrompterControls(state: PrompterUiState, prompter: TeleprompterViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "MIC GATE",
                color = PhosphorMid,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.width(96.dp)
            )
            Slider(
                value = state.micGate,
                onValueChange = { prompter.setMicGate(it) },
                valueRange = 0f..1f,
                modifier = Modifier.weight(1f),
                colors = SliderDefaults.colors(
                    thumbColor = PhosphorGreen,
                    activeTrackColor = PhosphorGreen,
                    inactiveTrackColor = PhosphorDim
                )
            )
            Text(
                text = "${(state.micGate * 100).toInt()}%",
                color = PhosphorGreen,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.width(48.dp),
                textAlign = TextAlign.End
            )
        }
        Text(
            text = "Raise the gate to ignore drums, crowd noise and stage bleed.",
            color = PhosphorDim,
            style = MaterialTheme.typography.labelSmall
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "CONFIDENCE",
                color = PhosphorMid,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.width(96.dp)
            )
            Slider(
                value = state.confidence,
                onValueChange = { prompter.setConfidence(it) },
                valueRange = 0.5f..0.99f,
                modifier = Modifier.weight(1f),
                colors = SliderDefaults.colors(
                    thumbColor = PhosphorGreen,
                    activeTrackColor = PhosphorGreen,
                    inactiveTrackColor = PhosphorDim
                )
            )
            Text(
                text = "${(state.confidence * 100).toInt()}%",
                color = PhosphorGreen,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.width(48.dp),
                textAlign = TextAlign.End
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "TEXT SIZE",
                color = PhosphorMid,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.width(96.dp)
            )
            TerminalMiniButton(
                label = "A-",
                onClick = { prompter.setFontScale(state.fontScalePercent - 10) }
            )
            Text(
                text = "${state.fontScalePercent}%",
                color = PhosphorGreen,
                style = MaterialTheme.typography.labelSmall
            )
            TerminalMiniButton(
                label = "A+",
                onClick = { prompter.setFontScale(state.fontScalePercent + 10) }
            )
            Spacer(modifier = Modifier.weight(1f))
            Switch(
                checked = state.autoScroll,
                onCheckedChange = { prompter.setAutoScroll(it) },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = StageBlack,
                    checkedTrackColor = PhosphorGreen,
                    uncheckedThumbColor = PhosphorDim,
                    uncheckedTrackColor = StageBlack,
                    uncheckedBorderColor = PhosphorDim
                )
            )
            Text(
                text = "AUTO",
                color = PhosphorMid,
                style = MaterialTheme.typography.labelSmall
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            TerminalMiniButton(label = "RESTART LINEUP", onClick = { prompter.restartLineup() })
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = state.lineupName.uppercase(),
                color = PhosphorDim,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1
            )
        }

        if (state.songs.size > 1) {
            TerminalRule(color = PhosphorDim)
            Text(
                text = "JUMP TO SONG",
                color = PhosphorMid,
                style = MaterialTheme.typography.labelSmall
            )
            state.songs.forEachIndexed { index, song ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${index + 1}. ${song.title}  (${song.targetKey})",
                        color = if (index == state.currentSongIndex) PhosphorBright else PhosphorMid,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.weight(1f),
                        maxLines = 1
                    )
                    TerminalMiniButton(
                        label = "GO",
                        onClick = { prompter.jumpToSong(index) },
                        enabled = index != state.currentSongIndex
                    )
                }
            }
        }
    }
}

@Composable
private fun MicLevelMeter(level: Float, running: Boolean) {
    val bars = 6
    val lit = if (!running) 0 else (level * 14f).toInt().coerceIn(0, bars)
    Row(verticalAlignment = Alignment.CenterVertically) {
        for (i in 0 until bars) {
            Box(
                modifier = Modifier
                    .padding(horizontal = 1.dp)
                    .width(4.dp)
                    .height((6 + i * 2).dp)
                    .background(if (i < lit) PhosphorGreen else PhosphorDim)
            )
        }
    }
}

@Composable
private fun SongBreakBanner(
    item: PromptItem.SongBreak,
    fontSize: androidx.compose.ui.unit.TextUnit,
    isActive: Boolean,
    onClick: () -> Unit
) {
    val prefix = if (item.isFirst) "SONG ${item.positionLabel}" else "NEXT SONG"
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 22.dp)
    ) {
        Text(
            text = "--- $prefix: ${item.title.uppercase()} (KEY: ${item.keyName}) ---",
            color = if (isActive) PhosphorBright else PhosphorMid,
            fontSize = fontSize,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(PhosphorDim)
        )
    }
}

@Composable
private fun LyricRow(
    item: PromptItem.Lyric,
    fontSize: androidx.compose.ui.unit.TextUnit,
    isActive: Boolean,
    onClick: () -> Unit
) {
    if (item.sectionLabel != null && item.text.isBlank()) {
        Text(
            text = "[ ${item.sectionLabel.uppercase()} ]",
            color = PhosphorDim,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onClick() }
                .padding(top = 16.dp, bottom = 4.dp)
        )
        return
    }

    if (item.text.isBlank()) {
        Spacer(modifier = Modifier.height(fontSize.value.dp * 0.5f))
        return
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isActive) {
                    Modifier.border(1.dp, PhosphorDim, RectangleShape)
                } else {
                    Modifier
                }
            )
            .clickable { onClick() }
            .padding(horizontal = 6.dp, vertical = 6.dp)
    ) {
        Text(
            text = item.text,
            color = if (isActive) PhosphorBright else PhosphorGreen,
            fontSize = fontSize,
            lineHeight = fontSize * 1.35f,
            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal
        )
    }
}

/** `[2/4] KEY: Bb | MIC: [F] -> NEXT: [Bb]` */
private fun statusLine(state: PrompterUiState): String {
    val sb = StringBuilder()
    sb.append('[').append(state.positionLabel).append("] ")
    sb.append("KEY: ").append(state.currentKey)
    sb.append("  |  MIC: ").append(
        state.detected?.let { formatDetected(it, state.currentKey) } ?: "----"
    )
    state.awaitedChord?.let { sb.append("  ->  NEXT: [").append(it).append(']') }
    state.nextSong?.let { sb.append("\nUP NEXT: ").append(it.title).append(" (").append(it.targetKey).append(')') }
    return sb.toString()
}

/** Re-spell the detected chord with the sharps or flats of the current key. */
private fun formatDetected(detection: ChordDetection, currentKey: String): String {
    val preferFlats = ChordTransposer.keyPrefersFlats(currentKey)
    val symbol = ChordSymbol(
        rootPc = detection.rootPc,
        quality = detection.quality,
        bassPc = detection.bassPc?.takeIf { it != detection.rootPc },
        raw = ""
    )
    return "[" + ChordTransposer.format(symbol, preferFlats) + "]"
}
