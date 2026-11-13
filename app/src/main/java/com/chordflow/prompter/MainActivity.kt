package com.chordflow.prompter

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.chordflow.prompter.ui.AppRoot
import com.chordflow.prompter.ui.theme.ChordFlowTheme
import com.chordflow.prompter.vm.MainViewModel
import com.chordflow.prompter.vm.TeleprompterViewModel

class MainActivity : ComponentActivity() {

    private val mainViewModel: MainViewModel by viewModels()
    private val prompterViewModel: TeleprompterViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ChordFlowTheme {
                AppRoot(
                    main = mainViewModel,
                    prompter = prompterViewModel
                )
            }
        }
    }

    override fun onStop() {
        super.onStop()
        // Never hold the microphone while the app is in the background.
        prompterViewModel.stopListening()
    }
}
