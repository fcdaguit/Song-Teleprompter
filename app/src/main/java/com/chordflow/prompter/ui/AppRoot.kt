package com.chordflow.prompter.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp
import com.chordflow.prompter.ui.theme.PhosphorGreen
import com.chordflow.prompter.ui.theme.StageBlack
import com.chordflow.prompter.vm.MainViewModel
import com.chordflow.prompter.vm.Screen
import com.chordflow.prompter.vm.TeleprompterViewModel
import kotlinx.coroutines.delay

@Composable
fun AppRoot(main: MainViewModel, prompter: TeleprompterViewModel) {
    val screen by main.screen.collectAsState()
    val status by main.status.collectAsState()

    BackHandler(enabled = screen != Screen.Home) {
        main.goHome()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(StageBlack)
    ) {
        when (val current = screen) {
            Screen.Home -> HomeLineupScreen(main)
            Screen.Library -> SongLibraryScreen(main)
            Screen.Downloader -> SongDownloaderScreen(main)
            is Screen.Editor -> SongEditorScreen(main, current.songId)
            is Screen.Prompter -> TeleprompterScreen(
                main = main,
                prompter = prompter,
                lineupId = current.lineupId,
                singleSongId = current.singleSongId
            )
        }

        val message = status
        if (message != null) {
            StatusBanner(
                message = message,
                onDismiss = { main.clearStatus() },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}

@Composable
private fun StatusBanner(
    message: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    LaunchedEffect(message) {
        delay(4_000)
        onDismiss()
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(10.dp)
            .background(StageBlack)
            .border(1.dp, PhosphorGreen, RectangleShape)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = message,
            color = PhosphorGreen,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(10.dp))
        TerminalMiniButton(label = "OK", onClick = onDismiss)
    }
}
