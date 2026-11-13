# ChordFlow Prompter

An offline-first, hands-free song teleprompter for church worship teams.

Build a **Service Lineup** before the service, tap **LAUNCH LINEUP**, and the
app plays through every song continuously — lyrics only, pure black and
phosphor green — scrolling as it hears your guitar or piano play the chords.

---

## Read this before you build

**This code has not been compiled.** It was written to compile cleanly, but the
first real compile will be your GitHub Actions run. If the build fails, the
workflow uploads the Gradle reports as an artifact and prints the stack trace —
see [Troubleshooting](#troubleshooting) below, which covers the two or three
failures that are most likely.

Dependency versions were deliberately chosen to be conservative and
mutually compatible (AGP 8.5.2 / Kotlin 2.0.21 / Compose BOM 2024.10.00 /
Room 2.6.1) rather than newest. They are all pinned in
`gradle/libs.versions.toml`.

### Three deliberate changes from the original spec

1. **"How Great Thou Art" was replaced with "Holy, Holy, Holy."**
   The English lyrics of "How Great Thou Art" (Stuart K. Hine, 1949) are still
   under copyright, so they cannot be bundled into an APK. "Holy, Holy, Holy"
   (Reginald Heber, 1826) is genuinely public domain. It is stored in its own
   key of **D** but added to the Sunday lineup in **Bb** — which is on purpose,
   because it exercises the transposition engine on first launch.
2. **OkHttp without Retrofit.** The downloader makes two plain GET requests
   (a GitHub tree listing and a raw file). Retrofit would add a dependency and
   an interface layer for no benefit. Kotlinx Serialization is still used to
   parse the tree response.
3. **Reorder is up/down arrows, not drag-and-drop.** The spec allowed either.
   Arrows need no extra library and are easier to hit on a dark stage.

### One thing I could not verify

The two starter download catalogues (`ChurchApps/WorshipCommonsContent` and
`opensong/opensong-songs`) are pre-filled as *starting points*. I could not
confirm from here that those repositories exist today, that those branch names
are right, or what licence each file carries. If a catalogue returns
"Not found (404)", that is the app working correctly — use the
**USE A DIFFERENT GITHUB REPO** panel or paste a raw file URL instead. The
download screen says this too.

**Check the licence of every song you download.** Public-domain hymns are
fine; most modern worship songs are not, and many churches need a CCLI licence
to display lyrics.

---

## Project structure

```
ChordFlowPrompter/
├── .github/workflows/build-apk.yml      GitHub Actions -> debug APK artifact
├── .gitignore
├── README.md
├── settings.gradle.kts
├── build.gradle.kts                     root build file
├── gradle.properties
├── gradle/libs.versions.toml            every dependency version, pinned
└── app/
    ├── build.gradle.kts
    ├── proguard-rules.pro
    └── src/main/
        ├── AndroidManifest.xml          RECORD_AUDIO, INTERNET, NETWORK_STATE
        ├── res/
        │   ├── values/strings.xml
        │   ├── values/colors.xml
        │   ├── values/themes.xml
        │   ├── drawable/ic_launcher_foreground.xml
        │   └── mipmap-anydpi-v26/ic_launcher*.xml
        └── java/com/chordflow/prompter/
            ├── ChordFlowApp.kt
            ├── MainActivity.kt
            ├── core/
            │   ├── ChordTransposer.kt       chord parsing, transposition, enharmonics
            │   ├── ChordParser.kt           ChordPro + two-line parsing, lineup flattening
            │   └── AudioChordDetector.kt    AudioRecord, FFT, chromagram, templates
            ├── data/
            │   ├── Entities.kt              SongEntity, LineupEntity, LineupItemEntity
            │   ├── SongDao.kt
            │   ├── ChordFlowDatabase.kt
            │   ├── SeedData.kt              three public-domain hymns
            │   ├── SongRepository.kt        Room + DataStore
            │   └── OnlineSongRepository.kt  the only networked class
            ├── ui/
            │   ├── theme/Theme.kt           black + #00FF41 monochrome theme
            │   ├── Common.kt                shared monospace widgets
            │   ├── AppRoot.kt
            │   ├── HomeLineupScreen.kt
            │   ├── SongLibraryScreen.kt
            │   ├── SongDownloaderScreen.kt
            │   ├── SongEditorScreen.kt
            │   └── TeleprompterScreen.kt
            └── vm/
                ├── MainViewModel.kt
                └── TeleprompterViewModel.kt
```

---

## Push it to GitHub and get the APK

You need a GitHub account and a browser. No Android Studio, no local JDK.

### 1. Create the repository

1. Go to <https://github.com/new>.
2. Name it `chordflow-prompter`. Public or private both work (private repos get
   free Actions minutes on personal accounts).
3. **Do not** add a README, .gitignore or licence — this zip already has them.
4. Click **Create repository**.

### 2. Upload the files

On the empty repository page, click **uploading an existing file**, then:

1. Unzip `chordflow-prompter.zip` on your computer.
2. Open the unzipped folder and select **everything inside it** — not the
   folder itself. Drag it all onto the GitHub upload area.
3. GitHub preserves the folder structure, including `.github/workflows/`.
   > If the drag-and-drop misses `.github`, that folder is hidden on macOS and
   > Linux. Press `Cmd+Shift+.` (macOS) or `Ctrl+H` (Linux) in the file
   > manager to show hidden files, then drag it in separately.
4. Type a commit message such as `Initial commit` and click
   **Commit changes**.

The push to `main` starts the build automatically.

### 3. Download the APK

1. Open the **Actions** tab of your repository.
2. Click the run named **Build Debug APK**. It takes roughly 5–12 minutes the
   first time, less afterwards because Gradle is cached.
3. When it shows a green tick, scroll to **Artifacts** at the bottom of the run
   page and download **chordflow-prompter-debug-apk**.
4. Unzip it. Inside is `app-debug.apk`.

### 4. Install it on the phone or tablet

1. Copy the APK to the device, or download it there directly.
2. Android will ask you to allow installing from that source — this is a debug
   APK, not a Play Store one, so that prompt is expected.
3. Open it, install, and grant the **microphone** permission when the
   teleprompter first opens.

To rebuild after any change: edit the file on GitHub, commit, and a new run
starts. You can also trigger one by hand from **Actions → Build Debug APK →
Run workflow**.

---

## How to use it

### Before the service

- **Home screen** shows the active lineup and its running order.
- **ALL SONGS** opens the offline library: add songs to the lineup, edit them,
  or delete them.
- **IMPORT FILE** loads a `.cho`, `.chopro`, `.pro`, `.onsong` or `.txt` chord
  sheet from the device.
- **DOWNLOAD** is the only screen that touches the internet.
- In the running order, each song has its own **key for this service**. Tap the
  `KEY: G` button for the dropdown, or `-` / `+` to move by semitone. The same
  song can sit in one lineup in G and another in F.
- `^` / `v` reorder. `EDIT` opens the song editor. `PLAY` runs that one song on
  its own. `X` removes it from the lineup (the song stays in the library).

### On stage

Tap **LAUNCH LINEUP**. The screen stays awake and shows lyrics only.

The top HUD reads like this:

```
[2/3] KEY: Bb  |  MIC: [F]  ->  NEXT: [Bb]
UP NEXT: Blessed Assurance (D)
```

- `[2/3]` — song 2 of 3.
- `KEY: Bb` — the key this song is being played in right now.
- `MIC: [F]` — the chord the microphone is hearing.
- `NEXT: [Bb]` — the chord that will scroll the next line.

Controls on the HUD row: `-1` / `+1` and `SET` change the key live, which
updates the invisible triggers and leaves the lyrics untouched. `AUTO: ON`
pauses and resumes auto-scroll. `HUD` opens the panel with the **mic gate**
slider, the **confidence** slider, text size, restart, and a jump-to-song list.

The bar along the bottom is the manual fallback: `<< PREV`, `- LINE`,
`+ LINE`, `NEXT >>`. You can also tap any line to make it the active one.

Between songs you get a banner followed immediately by the next song's opening
lines, so you can read ahead through an instrumental transition:

```
--- NEXT SONG: BLESSED ASSURANCE (KEY: D) ---
```

Nothing about this requires a tap. The lineup is one continuous scroll.

---

## How the engine works

### Chord recognition (`AudioChordDetector.kt`)

```
AudioRecord (16-bit PCM, 44.1 kHz, mono)
  -> 4096-sample window, 2048-sample hop
  -> RMS noise gate   (the MIC GATE slider)
  -> Hann window
  -> 4096-point radix-2 Cooley-Tukey FFT, written out in full
  -> magnitude spectrum, 70 Hz - 2200 Hz
  -> pitch class profile (12-bin chromagram), square-root compressed, L2-normalised
  -> cosine correlation against 120 chord templates
     (12 roots x maj, min, 7, m7, maj7, sus4, sus2, dim, aug, 5)
  -> two-frame agreement filter (~46 ms)
  -> StateFlow<ChordDetection?>
```

The bass note for slash chords is taken as the strongest bin between 70 Hz and
210 Hz.

### Scroll triggering (`TeleprompterViewModel.kt`)

Each lyric line carries the pitch classes of its chords, already transposed
into the key the song is being played in. When the detected root matches the
next triggered line's root, and confidence clears the slider, the active line
advances and `animateScrollToItem` slides it into the top third of the screen.
Two guards stop it from running away: a 320 ms minimum gap between advances,
and a 1.6 s gap when the chord that would fire is the same one that fired last
time.

### Honest limits of this approach

- **Chromagram chord recognition is approximate.** It is good on clean solo
  guitar or piano and degrades with a full band, cymbals, or a congregation
  singing. Expect to raise the mic gate and the confidence threshold at a real
  service, and expect to use the manual `+ LINE` button sometimes.
- **Inversions and slash chords are a guess.** The root-position template and
  the low-band bass estimate often disagree in a live room.
- **Songs that repeat one chord across several lines will not advance on their
  own** — there is no chord change for the engine to see. That is what
  `+ LINE` is for.
- **The parser cannot read every chord sheet.** Two-line chord-over-lyric
  detection requires *every* token on the chord line to be a recognised chord,
  which is what stops ordinary lyrics being eaten as chords. Unusual notation
  may need a small edit in the song editor.

I would test this with one guitar in an empty sanctuary before trusting it with
a congregation in front of you.

---

## Troubleshooting

**`Warning: Failed to find package 'tools'` in the Set up Android SDK step**
Already fixed in this workflow, and worth knowing about because it is not
your code. `android-actions/setup-android@v3` defaults its `packages` input to
`tools platform-tools`, and Google has withdrawn the legacy `tools` package
from the SDK repository, so the action's own `sdkmanager tools` call exits 1
and the job dies before Gradle starts. The workflow now passes `packages`
explicitly, which replaces that default so `tools` is never requested:

```yaml
- uses: android-actions/setup-android@v3
  with:
    packages: 'platform-tools platforms;android-34 build-tools;34.0.0'
```

If it ever recurs, `android-actions/setup-android@v4` also drops `tools` from
its defaults — but keep the explicit `packages` line either way, since that is
what actually prevents the call.

**`Could not find com.google.devtools.ksp:...:2.0.21-1.0.25`**
Open `gradle/libs.versions.toml` and change the `ksp` version to another
release for Kotlin 2.0.21 — `2.0.21-1.0.26`, `2.0.21-1.0.27` or
`2.0.21-1.0.28`. The prefix must match the Kotlin version exactly.

**`gradle: command not found` in the wrapper step**
The workflow installs Gradle 8.9 via `gradle/actions/setup-gradle@v4`. If that
action has changed, replace the wrapper step with
`gradle wrapper --gradle-version 8.9` removed entirely and run
`gradle assembleDebug` directly instead of `./gradlew assembleDebug`.

**Room / KSP errors mentioning a DAO query**
Room validates every SQL string at compile time, so these point at a real
mismatch between a query and `Entities.kt`. The error names the column.

**`Unresolved reference` in a Compose file**
Almost always a Material 3 API that moved between versions. Pin the Compose
BOM to the version named in `libs.versions.toml` rather than upgrading it, or
read the error — it names the symbol and the file.

**The build is green but the APK is missing**
Check the **List build outputs** step in the run log. The APK is written to
`app/build/outputs/apk/debug/`.

**Auto-scroll does nothing on the device**
Open the HUD. If it says the microphone permission was not granted, tap
**ALLOW MIC**. If `MIC:` stays at `----`, lower the mic gate slider. If it
shows chords but the page never moves, your confidence slider is too high.

---

## Licensing of what is in here

- The three bundled hymns are public domain (1779, 1826 and 1873 texts and
  pre-1900 tunes).
- The code is yours to do what you like with. There is no `LICENSE` file; add
  one if you intend to share the repository.
- Anything you download or import is covered by its own licence, which this
  app cannot check for you.
