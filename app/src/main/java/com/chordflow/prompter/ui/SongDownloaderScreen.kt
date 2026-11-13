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
import com.chordflow.prompter.ui.theme.PhosphorBright
import com.chordflow.prompter.ui.theme.PhosphorDim
import com.chordflow.prompter.ui.theme.PhosphorGreen
import com.chordflow.prompter.ui.theme.PhosphorMid
import com.chordflow.prompter.ui.theme.StageBlack
import com.chordflow.prompter.vm.MainViewModel

/**
 * The only networked screen in the app. Built as one LazyColumn so there is no
 * nested scrolling and the result list can be long without trouble.
 */
@Composable
fun SongDownloaderScreen(main: MainViewModel) {
    val state by main.downloader.collectAsState()
    var showCustom by remember { mutableStateOf(false) }

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
                    text = "DOWNLOAD FREE SONGS",
                    color = PhosphorGreen,
                    style = MaterialTheme.typography.headlineSmall
                )
                Text(
                    text = "NEEDS INTERNET  /  PREP USE ONLY",
                    color = PhosphorMid,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
        TerminalRule()

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                TerminalNotice(
                    message = "Use this before the service, not during it. Nothing here runs " +
                        "while the teleprompter is live. Check each song's licence before " +
                        "use - public repositories get renamed and restructured, so a " +
                        "catalogue can move or disappear."
                )
            }

            item { TerminalLabel(text = "Open catalogues") }

            itemsIndexedCatalogs(state, main)

            item {
                TerminalButton(
                    label = if (state.loading) "WORKING..." else "BROWSE SELECTED CATALOGUE",
                    onClick = { main.browseSelectedCatalog() },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.loading
                )
            }

            item {
                TerminalOutlineButton(
                    label = if (showCustom) "HIDE CUSTOM REPO" else "USE A DIFFERENT GITHUB REPO",
                    onClick = { showCustom = !showCustom },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (showCustom) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, PhosphorDim, RectangleShape)
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TerminalTextField(
                            value = state.customOwner,
                            onValueChange = { main.setCustomCatalogField(it, null, null) },
                            label = "GitHub owner",
                            modifier = Modifier.fillMaxWidth()
                        )
                        TerminalTextField(
                            value = state.customRepo,
                            onValueChange = { main.setCustomCatalogField(null, it, null) },
                            label = "Repository name",
                            modifier = Modifier.fillMaxWidth()
                        )
                        TerminalTextField(
                            value = state.customBranch,
                            onValueChange = { main.setCustomCatalogField(null, null, it) },
                            label = "Branch (main / master)",
                            modifier = Modifier.fillMaxWidth()
                        )
                        TerminalOutlineButton(
                            label = "BROWSE THIS REPO",
                            onClick = { main.browseCustomCatalog() },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !state.loading
                        )
                    }
                }
            }

            item { TerminalRule(color = PhosphorDim) }
            item { TerminalLabel(text = "Or paste a direct song file URL") }
            item {
                TerminalTextField(
                    value = state.rawUrl,
                    onValueChange = { main.setRawUrl(it) },
                    label = "raw.githubusercontent.com/... .cho",
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = false,
                    minLines = 2
                )
            }
            item {
                TerminalOutlineButton(
                    label = "DOWNLOAD THIS URL",
                    onClick = { main.downloadFromRawUrl() },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.loading
                )
            }

            val error = state.error
            if (error != null) {
                item { TerminalNotice(message = error, isError = true) }
            }

            val saved = state.lastDownloadedTitle
            if (saved != null) {
                item {
                    TerminalNotice(message = "Saved \"$saved\" to your offline library.")
                }
            }

            if (state.results.isNotEmpty()) {
                item { TerminalRule(color = PhosphorDim) }
                item { TerminalLabel(text = "${state.results.size} song files found") }
                item {
                    TerminalTextField(
                        value = state.query,
                        onValueChange = { main.setDownloaderQuery(it) },
                        label = "Filter results",
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                items(items = state.filteredResults, key = { it.rawUrl }) { song ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, PhosphorDim, RectangleShape)
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = song.title,
                                color = PhosphorGreen,
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = song.path,
                                color = PhosphorDim,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        TerminalMiniButton(
                            label = "GET",
                            onClick = { main.downloadSong(song) },
                            enabled = !state.loading
                        )
                    }
                }
            }
        }
    }
}

/** Catalogue rows, pulled out so the LazyColumn body stays readable. */
private fun androidx.compose.foundation.lazy.LazyListScope.itemsIndexedCatalogs(
    state: com.chordflow.prompter.vm.DownloaderState,
    main: MainViewModel
) {
    state.catalogs.forEachIndexed { index, catalog ->
        item(key = "catalog-${catalog.slug}-${catalog.branch}") {
            val selected = index == state.selectedCatalogIndex
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, if (selected) PhosphorGreen else PhosphorDim, RectangleShape)
                    .padding(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = (if (selected) "[*] " else "[ ] ") + catalog.label,
                        color = if (selected) PhosphorBright else PhosphorMid,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    TerminalMiniButton(
                        label = "SELECT",
                        onClick = { main.selectCatalog(index) },
                        enabled = !selected
                    )
                }
                Text(
                    text = "github.com/${catalog.slug} @ ${catalog.branch}",
                    color = PhosphorDim,
                    style = MaterialTheme.typography.labelSmall
                )
                if (catalog.note.isNotBlank()) {
                    Text(
                        text = catalog.note,
                        color = PhosphorDim,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    }
}
