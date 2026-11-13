package com.chordflow.prompter

import android.app.Application

/**
 * Application entry point. Deliberately empty of work: the Room database is
 * created lazily and the starter library is seeded from the ViewModel, so a
 * cold start on stage never blocks on disk.
 */
class ChordFlowApp : Application()
