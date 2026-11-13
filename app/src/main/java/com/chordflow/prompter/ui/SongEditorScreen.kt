package com.chordflow.prompter.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.chordflow.prompter.core.ChordParser
import com.chordflow.prompter.core.ChordTransposer
import com.chordflow.prompter.data.SongEntity
import com.chordflow.prompter.ui.theme.PhosphorBright
import com.chordflow.prompter.ui.theme.PhosphorDim
import com.chordflow.prompter.ui.theme.PhosphorGreen
import com.chordflow.prompter.ui.theme.PhosphorMid
import com.chordflow.prompter.ui.theme.StageBlack
import com.chordflow.prompter.vm.MainViewModel

@Composable
fun SongEditorScreen(main: MainViewModel, songId: Long) {
    var song by remember(songId) { mutableStateOf<SongEntity?>(null) }
    var title by remember(songId) { mutableStateOf("") }
    var key by remember(songId) { mutableStateOf("C") }
    var content by remember(songId) { mutableStateOf("") }
    var transpose by remember(songId) { mutableStateOf(0) }
    var loaded by remember(songId) { mutableStateOf(false) }

    LaunchedEffect(songId) {
        val found = main.repository.getSong(songId)
        if (found != null) {
            song = found
            title = found.title
            key = found.originalKey?.takeIf { it.isNotBlank() } ?: "C"
            content = found.content
        }
        loaded = true
    }

    val preferFlats = ChordTransposer.keyPrefersFlats(
        ChordTransposer.transposeKeyName(key, transpose)
    )
    val previewKey = ChordTransposer.transposeKeyName(key, transpose)
    val transposedSource = remember(content, transpose, preferFlats) {
        ChordParser.transposeSource(content, transpose, preferFlats)
    }
    val lyricPreview = remember(content) {
        ChordParser.stripToLyrics(content)
            .lineSequence()
            .filter { it.isNotBlank() }
            .take(8)
            .joinToString("\n")
    }
    val chordPreview = remember(transposedSource) {
        ChordParser.parse(transposedSource)
            .allChords()
            .filter { ChordTransposer.isChordToken(it) }
            .distinct()
            .take(24)
            .joinToString("   ")
    }

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
            TerminalMiniButton(label = "< BACK", onClick = { main.goHome() })
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "SONG EDITOR",
                    color = PhosphorGreen,
                    style = MaterialTheme.typography.headlineSmall
                )
                Text(
                    text = title.ifBlank { "..." }.uppercase(),
                    color = PhosphorMid,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            val current = song
            if (current != null) {
                TerminalButton(
                    label = "SAVE",
                    onClick = { main.saveSongEdits(current, title, key, content) }
                )
            }
        }
        TerminalRule()

        if (loaded && song == null) {
            Column(modifier = Modifier.padding(14.dp)) {
                TerminalNotice(
                    message = "That song is no longer in the library.",
                    isError = true
                )
            }
            return@Column
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                TerminalTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = "Title",
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, PhosphorDim, RectangleShape)
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TerminalLabel(text = "Written key")
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        KeyDropdown(
                            currentKey = key,
                            onKeySelected = { key = it },
                            labelPrefix = "KEY"
                        )
                    }

                    TerminalRule(color = PhosphorDim)
                    TerminalLabel(text = "Preview transpose")
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TerminalMiniButton(
                            label = "-",
                            onClick = { transpose = ChordTransposer.clampSemitones(transpose - 1) }
                        )
                        Text(
                            text = (if (transpose > 0) "+$transpose" else "$transpose") +
                                "  ->  $previewKey",
                            color = PhosphorBright,
                            style = MaterialTheme.typography.titleMedium
                        )
                        TerminalMiniButton(
                            label = "+",
                            onClick = { transpose = ChordTransposer.clampSemitones(transpose + 1) }
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        TerminalMiniButton(
                            label = "RESET",
                            onClick = { transpose = 0 },
                            enabled = transpose != 0
                        )
                    }
                    if (chordPreview.isNotBlank()) {
                        Text(
                            text = chordPreview,
                            color = PhosphorGreen,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    TerminalOutlineButton(
                        label = "APPLY TRANSPOSE TO THE SOURCE",
                        onClick = {
                            content = transposedSource
                            key = previewKey
                            transpose = 0
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = transpose != 0
                    )
                }
            }

            item { TerminalLabel(text = "Chord sheet source") }
            item {
                Text(
                    text = "ChordPro directives, [G]inline chords and plain " +
                        "chord-over-lyric lines are all understood. Chords never appear " +
                        "on the teleprompter - they become invisible scroll triggers.",
                    color = PhosphorDim,
                    style = MaterialTheme.typography.labelSmall
                )
            }
            item {
                TerminalTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = "Source",
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = false,
                    minLines = 14
                )
            }

            item { TerminalRule(color = PhosphorDim) }
            item { TerminalLabel(text = "Teleprompter preview (lyrics only)") }
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, PhosphorDim, RectangleShape)
                        .padding(10.dp)
                ) {
                    Text(
                        text = lyricPreview.ifBlank { "(no lyrics yet)" },
                        color = PhosphorGreen,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }

            item {
                val current = song
                if (current != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TerminalButton(
                            label = "SAVE CHANGES",
                            onClick = { main.saveSongEdits(current, title, key, content) },
                            modifier = Modifier.weight(1f)
                        )
                        TerminalOutlineButton(
                            label = "+ ADD TO LINEUP",
                            onClick = { main.addSongToActiveLineup(current) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}
