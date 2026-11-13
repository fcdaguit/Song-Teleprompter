package com.chordflow.prompter.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.unit.sp
import com.chordflow.prompter.data.LineupSlot
import com.chordflow.prompter.ui.theme.PhosphorBright
import com.chordflow.prompter.ui.theme.PhosphorDim
import com.chordflow.prompter.ui.theme.PhosphorGreen
import com.chordflow.prompter.ui.theme.PhosphorMid
import com.chordflow.prompter.ui.theme.StageBlack
import com.chordflow.prompter.vm.MainViewModel
import com.chordflow.prompter.vm.Screen

@Composable
fun HomeLineupScreen(main: MainViewModel) {
    val board by main.board.collectAsState()
    var showLineupPicker by remember { mutableStateOf(false) }
    var newLineupName by remember { mutableStateOf("") }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) main.importFromUri(uri)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(StageBlack)
    ) {
        // ---------------------------------------------------------- title bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "CHORDFLOW PROMPTER",
                    color = PhosphorGreen,
                    style = MaterialTheme.typography.headlineSmall
                )
                Text(
                    text = "OFFLINE  /  HANDS-FREE  /  STAGE READY",
                    color = PhosphorMid,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
        TerminalRule()

        // ------------------------------------------------- active lineup card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, PhosphorGreen, RectangleShape)
                    .padding(14.dp)
            ) {
                TerminalLabel(text = "Active service lineup")
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = board.activeLineup?.name ?: "No lineup yet",
                    color = PhosphorBright,
                    style = MaterialTheme.typography.headlineLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${board.activeSlots.size} song(s) queued  |  " +
                        "${board.library.size} in offline library",
                    color = PhosphorMid,
                    style = MaterialTheme.typography.labelSmall
                )
                Spacer(modifier = Modifier.height(12.dp))
                TerminalButton(
                    label = ">>  LAUNCH LINEUP",
                    onClick = { main.launchActiveLineup() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(58.dp),
                    enabled = board.activeSlots.isNotEmpty()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TerminalOutlineButton(
                        label = if (showLineupPicker) "HIDE LINEUPS" else "SWITCH LINEUP",
                        onClick = { showLineupPicker = !showLineupPicker },
                        modifier = Modifier.weight(1f)
                    )
                    TerminalOutlineButton(
                        label = "+ SONGS",
                        onClick = { main.go(Screen.Library) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            if (showLineupPicker) {
                LineupPicker(
                    main = main,
                    newLineupName = newLineupName,
                    onNameChange = { newLineupName = it },
                    onCreate = {
                        main.createLineup(newLineupName)
                        newLineupName = ""
                        showLineupPicker = false
                    }
                )
            }
        }

        TerminalRule(color = PhosphorDim)

        // ------------------------------------------------------- the song queue
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TerminalLabel(text = "Running order", modifier = Modifier.weight(1f))
            Text(
                text = "TAP A KEY TO CHANGE IT",
                color = PhosphorDim,
                style = MaterialTheme.typography.labelSmall
            )
        }

        if (board.activeSlots.isEmpty()) {
            Box(modifier = Modifier.padding(horizontal = 14.dp)) {
                TerminalNotice(
                    message = "This lineup is empty. Open ALL SONGS and add a few, " +
                        "or import a chord sheet from your device."
                )
            }
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 14.dp,
                end = 14.dp,
                top = 4.dp,
                bottom = 14.dp
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(
                items = board.activeSlots,
                key = { _, slot -> slot.item.id }
            ) { index, slot ->
                LineupSlotRow(
                    index = index,
                    slot = slot,
                    isFirst = index == 0,
                    isLast = index == board.activeSlots.lastIndex,
                    onMoveUp = { main.moveSlot(index, index - 1) },
                    onMoveDown = { main.moveSlot(index, index + 1) },
                    onRemove = { main.removeSlot(slot.item) },
                    onKeySelected = { key -> main.setSlotKey(slot.item, key) },
                    onNudgeKey = { delta -> main.nudgeSlotKey(slot.item, slot.song, delta) },
                    onEdit = { main.go(Screen.Editor(slot.song.id)) },
                    onPlay = { main.playSingleSong(slot.song) }
                )
            }
        }

        TerminalRule(color = PhosphorDim)

        // ------------------------------------------------------- bottom nav bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TerminalOutlineButton(
                label = "ALL SONGS",
                onClick = { main.go(Screen.Library) },
                modifier = Modifier.weight(1f)
            )
            TerminalOutlineButton(
                label = "IMPORT FILE",
                onClick = {
                    importLauncher.launch(arrayOf("*/*"))
                },
                modifier = Modifier.weight(1f)
            )
            TerminalOutlineButton(
                label = "DOWNLOAD",
                onClick = { main.go(Screen.Downloader) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun LineupPicker(
    main: MainViewModel,
    newLineupName: String,
    onNameChange: (String) -> Unit,
    onCreate: () -> Unit
) {
    val board by main.board.collectAsState()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, PhosphorDim, RectangleShape)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        TerminalLabel(text = "Saved lineups")
        board.lineups.forEach { lineup ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = (if (lineup.isActive) "[*] " else "[ ] ") + lineup.name,
                    color = if (lineup.isActive) PhosphorBright else PhosphorMid,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                TerminalMiniButton(
                    label = "USE",
                    onClick = { main.setActiveLineup(lineup.id) },
                    enabled = !lineup.isActive
                )
                Spacer(modifier = Modifier.width(6.dp))
                TerminalMiniButton(
                    label = "DEL",
                    onClick = { main.deleteLineup(lineup.id) },
                    enabled = board.lineups.size > 1
                )
            }
        }
        TerminalRule(color = PhosphorDim)
        TerminalTextField(
            value = newLineupName,
            onValueChange = onNameChange,
            label = "New lineup name",
            modifier = Modifier.fillMaxWidth()
        )
        TerminalOutlineButton(
            label = "+ CREATE LINEUP",
            onClick = onCreate,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun LineupSlotRow(
    index: Int,
    slot: LineupSlot,
    isFirst: Boolean,
    isLast: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit,
    onKeySelected: (String) -> Unit,
    onNudgeKey: (Int) -> Unit,
    onEdit: () -> Unit,
    onPlay: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, PhosphorDim, RectangleShape)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "${index + 1}.",
                color = PhosphorMid,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.width(32.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = slot.song.title,
                    color = PhosphorGreen,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val detail = buildString {
                    slot.item.slotLabel?.let { append(it) }
                    val original = slot.song.originalKey
                    if (original != null && original != slot.effectiveKey) {
                        if (isNotEmpty()) append("  |  ")
                        append("written in $original")
                    }
                }
                if (detail.isNotEmpty()) {
                    Text(
                        text = detail,
                        color = PhosphorDim,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                TerminalMiniButton(
                    label = "^",
                    onClick = onMoveUp,
                    enabled = !isFirst
                )
                Spacer(modifier = Modifier.height(4.dp))
                TerminalMiniButton(
                    label = "v",
                    onClick = onMoveDown,
                    enabled = !isLast
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            KeyDropdown(
                currentKey = slot.effectiveKey,
                onKeySelected = onKeySelected
            )
            TerminalMiniButton(label = "-", onClick = { onNudgeKey(-1) })
            TerminalMiniButton(label = "+", onClick = { onNudgeKey(1) })
            Spacer(modifier = Modifier.weight(1f))
            TerminalMiniButton(label = "EDIT", onClick = onEdit)
            TerminalMiniButton(label = "PLAY", onClick = onPlay)
            TerminalMiniButton(label = "X", onClick = onRemove)
        }
    }
}
