package com.chordflow.prompter.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import com.chordflow.prompter.ui.theme.PhosphorDim
import com.chordflow.prompter.ui.theme.PhosphorGreen
import com.chordflow.prompter.ui.theme.PhosphorMid
import com.chordflow.prompter.ui.theme.StageBlack
import com.chordflow.prompter.vm.MainViewModel
import com.chordflow.prompter.vm.Screen

@Composable
fun SongLibraryScreen(main: MainViewModel) {
    val board by main.board.collectAsState()
    var query by remember { mutableStateOf("") }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) main.importFromUri(uri)
    }

    val filtered = remember(board.library, query) {
        if (query.isBlank()) {
            board.library
        } else {
            val needle = query.trim().lowercase()
            board.library.filter { song ->
                song.title.lowercase().contains(needle) ||
                    song.artist?.lowercase()?.contains(needle) == true
            }
        }
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
                    text = "ALL SONGS",
                    color = PhosphorGreen,
                    style = MaterialTheme.typography.headlineSmall
                )
                Text(
                    text = "${board.library.size} SONGS STORED OFFLINE",
                    color = PhosphorMid,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
        TerminalRule()

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TerminalTextField(
                value = query,
                onValueChange = { query = it },
                label = "Search library",
                modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TerminalOutlineButton(
                    label = "IMPORT FILE",
                    onClick = { importLauncher.launch(arrayOf("*/*")) },
                    modifier = Modifier.weight(1f)
                )
                TerminalOutlineButton(
                    label = "DOWNLOAD",
                    onClick = { main.go(Screen.Downloader) },
                    modifier = Modifier.weight(1f)
                )
                TerminalOutlineButton(
                    label = "+ BLANK",
                    onClick = { main.createBlankSong() },
                    modifier = Modifier.weight(1f)
                )
            }
            Text(
                text = "Supported files: .cho  .chopro  .pro  .onsong  .txt",
                color = PhosphorDim,
                style = MaterialTheme.typography.labelSmall
            )
        }

        TerminalRule(color = PhosphorDim)

        if (filtered.isEmpty()) {
            Column(modifier = Modifier.padding(14.dp)) {
                TerminalNotice(
                    message = if (board.library.isEmpty()) {
                        "Your library is empty. Import a chord sheet or download a free song."
                    } else {
                        "Nothing matches \"$query\"."
                    }
                )
            }
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(items = filtered, key = { it.id }) { song ->
                val lineCount = remember(song.id, song.updatedAt) {
                    ChordParser.parse(song.content).lines.count { it.text.isNotBlank() }
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, PhosphorDim, RectangleShape)
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = song.title,
                        color = PhosphorGreen,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = buildString {
                            append("KEY ${song.originalKey ?: "?"}")
                            append("  |  $lineCount lyric lines")
                            song.artist?.let { append("  |  $it") }
                        },
                        color = PhosphorMid,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        TerminalMiniButton(
                            label = "+ LINEUP",
                            onClick = { main.addSongToActiveLineup(song) }
                        )
                        TerminalMiniButton(
                            label = "EDIT",
                            onClick = { main.go(Screen.Editor(song.id)) }
                        )
                        TerminalMiniButton(
                            label = "PLAY",
                            onClick = { main.playSingleSong(song) }
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        TerminalMiniButton(
                            label = "DELETE",
                            onClick = { main.deleteSong(song) }
                        )
                    }
                }
            }
        }
    }
}
