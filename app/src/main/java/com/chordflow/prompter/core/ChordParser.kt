package com.chordflow.prompter.core

/** A chord attached to a lyric line at a character column. */
data class ChordOccurrence(val chord: String, val column: Int)

/**
 * One line of a song after parsing. [text] is already stripped of chords and
 * directives, so it is safe to show straight on the teleprompter.
 */
data class ParsedLine(
    val text: String,
    val chords: List<ChordOccurrence> = emptyList(),
    val sectionLabel: String? = null
)

/** A whole song after parsing. */
data class ParsedSong(
    val title: String?,
    val artist: String?,
    val key: String?,
    val tempo: String?,
    val lines: List<ParsedLine>
) {
    /** Every chord in the song, in reading order, with duplicates kept. */
    fun allChords(): List<String> = lines.flatMap { line -> line.chords.map { it.chord } }

    /** First parsable chord, used to guess the key when none was declared. */
    fun firstChord(): String? = allChords().firstOrNull { ChordTransposer.isChordToken(it) }
}

/** One entry of a service lineup, resolved and ready to be flattened. */
data class LineupSong(
    val lineupItemId: Long,
    val songId: Long,
    val title: String,
    val originalKey: String?,
    val targetKey: String,
    val parsed: ParsedSong
)

/**
 * A single scrollable row of the continuous teleprompter stream.
 * The lineup is flattened into these so that moving from one song to the next
 * is just scrolling further down the same list - no screen changes, no taps.
 */
sealed interface PromptItem {
    val songIndex: Int

    /** Divider banner shown at the start of each song in the lineup. */
    data class SongBreak(
        override val songIndex: Int,
        val title: String,
        val keyName: String,
        val isFirst: Boolean,
        val positionLabel: String
    ) : PromptItem

    /** A lyric line, plus the invisible chord triggers mapped to it. */
    data class Lyric(
        override val songIndex: Int,
        val text: String,
        val sectionLabel: String?,
        val triggers: List<String>,
        val triggerPitchClasses: List<Int>
    ) : PromptItem {
        val isBlank: Boolean get() = text.isBlank() && sectionLabel == null
        val hasTrigger: Boolean get() = triggerPitchClasses.isNotEmpty()
    }
}

/**
 * Parses ChordPro, OnSong-style and plain chord-over-lyric text, and flattens
 * service lineups into a continuous prompt stream.
 */
object ChordParser {

    private val DIRECTIVE = Regex("^\\s*\\{\\s*([^:}]+?)\\s*(?::\\s*(.*?)\\s*)?}\\s*$")
    private val INLINE_DIRECTIVE = Regex("\\{[^}]*}")
    private val WHITESPACE = Regex("\\s+")

    // ----------------------------------------------------------------- public

    fun parse(raw: String): ParsedSong {
        val source = raw.replace("\r\n", "\n").replace('\r', '\n')
        val rawLines = source.split('\n')

        var title: String? = null
        var artist: String? = null
        var key: String? = null
        var tempo: String? = null

        val out = ArrayList<ParsedLine>(rawLines.size)
        var i = 0

        while (i < rawLines.size) {
            val line = rawLines[i]

            // --- ChordPro directive line, e.g. {title: Amazing Grace}
            val directive = DIRECTIVE.matchEntire(line)
            if (directive != null) {
                val name = directive.groupValues[1].trim().lowercase()
                val value = directive.groupValues.getOrNull(2)?.trim().orEmpty()
                when (name) {
                    "title", "t" -> if (value.isNotEmpty()) title = value
                    "artist", "a", "composer", "author" ->
                        if (value.isNotEmpty()) artist = value
                    "subtitle", "st" -> if (artist == null && value.isNotEmpty()) artist = value
                    "key", "k" -> if (value.isNotEmpty()) key = value
                    "tempo", "bpm" -> if (value.isNotEmpty()) tempo = value
                    "comment", "c", "comment_italic", "ci", "comment_box", "cb" ->
                        if (value.isNotEmpty()) out.add(ParsedLine("", sectionLabel = value))
                    "start_of_chorus", "soc" -> out.add(ParsedLine("", sectionLabel = "CHORUS"))
                    "start_of_verse", "sov" -> out.add(ParsedLine("", sectionLabel = "VERSE"))
                    "start_of_bridge", "sob" -> out.add(ParsedLine("", sectionLabel = "BRIDGE"))
                    else -> { /* unsupported directive: drop it from the display */ }
                }
                i++
                continue
            }

            // --- Inline bracketed chords, e.g. [G]Amazing [D]grace
            if (line.contains('[') && line.contains(']')) {
                out.add(extractInlineChords(line))
                i++
                continue
            }

            // --- Two-line chord-over-lyric block
            if (isChordOnlyLine(line)) {
                val next = rawLines.getOrNull(i + 1)
                val nextIsLyric = next != null &&
                    next.isNotBlank() &&
                    !isChordOnlyLine(next) &&
                    DIRECTIVE.matchEntire(next) == null
                if (nextIsLyric) {
                    out.add(pairChordLineWithLyric(line, next!!))
                    i += 2
                    continue
                }
                // A chord line with no lyric under it (intro, turnaround, tag).
                out.add(chordOnlyAsLine(line))
                i++
                continue
            }

            // --- Plain lyric or blank line
            out.add(ParsedLine(cleanLyricText(line)))
            i++
        }

        val parsed = ParsedSong(
            title = title,
            artist = artist,
            key = key,
            tempo = tempo,
            lines = trimTrailingBlanks(out)
        )

        if (parsed.key != null) return parsed

        // No {key:} directive - fall back to the first chord in the song.
        val guessed = parsed.firstChord()?.let { token ->
            ChordTransposer.parseChord(token)?.let { symbol ->
                val minor = symbol.quality.startsWith("m") && !symbol.quality.startsWith("maj")
                ChordTransposer.preferredKeyName(symbol.rootPc) + if (minor) "m" else ""
            }
        }
        return parsed.copy(key = guessed)
    }

    /** Lyrics only, for previews and search. */
    fun stripToLyrics(raw: String): String =
        parse(raw).lines.joinToString("\n") { it.text }

    /**
     * Re-render a whole song with every chord transposed, keeping ChordPro
     * inline bracket format. Used by the song editor preview.
     */
    fun transposeSource(raw: String, semitones: Int, preferFlats: Boolean): String {
        if (semitones == 0) return raw
        val source = raw.replace("\r\n", "\n").replace('\r', '\n')
        val sb = StringBuilder(source.length + 32)

        for ((index, line) in source.split('\n').withIndex()) {
            if (index > 0) sb.append('\n')
            val directive = DIRECTIVE.matchEntire(line)
            if (directive != null) {
                val name = directive.groupValues[1].trim().lowercase()
                val value = directive.groupValues.getOrNull(2)?.trim().orEmpty()
                if ((name == "key" || name == "k") && value.isNotEmpty()) {
                    sb.append("{key: ")
                        .append(ChordTransposer.transposeKeyName(value, semitones))
                        .append('}')
                } else {
                    sb.append(line)
                }
                continue
            }
            if (line.contains('[') && line.contains(']')) {
                sb.append(transposeInline(line, semitones, preferFlats))
                continue
            }
            if (isChordOnlyLine(line)) {
                sb.append(transposeChordOnlyLine(line, semitones, preferFlats))
                continue
            }
            sb.append(line)
        }
        return sb.toString()
    }

    // ---------------------------------------------------------- lineup stream

    /**
     * Flattens an entire service lineup into one continuous list of prompt
     * items. Song boundaries become [PromptItem.SongBreak] banners and every
     * lyric line carries its own already-transposed chord triggers, so the
     * teleprompter never has to reload or switch screens mid-service.
     */
    fun flattenLineup(songs: List<LineupSong>): List<PromptItem> {
        val items = ArrayList<PromptItem>(256)

        songs.forEachIndexed { songIndex, song ->
            val semitones = ChordTransposer.clampSemitones(
                ChordTransposer.semitonesBetween(song.originalKey, song.targetKey)
            )
            val preferFlats = ChordTransposer.keyPrefersFlats(song.targetKey)

            items.add(
                PromptItem.SongBreak(
                    songIndex = songIndex,
                    title = song.title,
                    keyName = song.targetKey,
                    isFirst = songIndex == 0,
                    positionLabel = "${songIndex + 1}/${songs.size}"
                )
            )

            for (line in song.parsed.lines) {
                // Only visible lyric lines become trigger targets. A chord-only
                // line (an intro or a turnaround) has nothing on screen to
                // scroll to, so giving it a trigger would make the prompter
                // look stuck on an invisible row.
                val chordTokens = if (line.text.isBlank()) {
                    emptyList()
                } else {
                    line.chords
                        .map { it.chord }
                        .filter { ChordTransposer.isChordToken(it) }
                }

                val triggers = chordTokens.map {
                    ChordTransposer.transposeChord(it, semitones, preferFlats)
                }
                val pitchClasses = chordTokens
                    .flatMap { ChordTransposer.triggerPitchClasses(it, semitones) }
                    .distinct()

                items.add(
                    PromptItem.Lyric(
                        songIndex = songIndex,
                        text = line.text,
                        sectionLabel = line.sectionLabel,
                        triggers = triggers,
                        triggerPitchClasses = pitchClasses
                    )
                )
            }

            // Breathing room so the last line of a song can still scroll up
            // into the reading zone before the next banner arrives.
            repeat(2) {
                items.add(PromptItem.Lyric(songIndex, "", null, emptyList(), emptyList()))
            }
        }

        return items
    }

    // ---------------------------------------------------------------- helpers

    private fun trimTrailingBlanks(lines: List<ParsedLine>): List<ParsedLine> {
        var end = lines.size
        while (end > 0) {
            val candidate = lines[end - 1]
            if (candidate.text.isBlank() && candidate.sectionLabel == null && candidate.chords.isEmpty()) {
                end--
            } else {
                break
            }
        }
        return if (end == lines.size) lines else lines.subList(0, end).toList()
    }

    private fun cleanLyricText(line: String): String =
        INLINE_DIRECTIVE.replace(line, "").trimEnd()

    private fun extractInlineChords(line: String): ParsedLine {
        val text = StringBuilder(line.length)
        val chords = ArrayList<ChordOccurrence>(4)
        var i = 0
        while (i < line.length) {
            val c = line[i]
            if (c == '[') {
                val close = line.indexOf(']', i + 1)
                if (close > i) {
                    val token = line.substring(i + 1, close).trim()
                    if (token.isNotEmpty()) chords.add(ChordOccurrence(token, text.length))
                    i = close + 1
                    continue
                }
            }
            text.append(c)
            i++
        }
        return ParsedLine(
            text = INLINE_DIRECTIVE.replace(text.toString(), "").trimEnd(),
            chords = chords
        )
    }

    private fun transposeInline(line: String, semitones: Int, preferFlats: Boolean): String {
        val out = StringBuilder(line.length + 8)
        var i = 0
        while (i < line.length) {
            val c = line[i]
            if (c == '[') {
                val close = line.indexOf(']', i + 1)
                if (close > i) {
                    val token = line.substring(i + 1, close).trim()
                    out.append('[')
                        .append(ChordTransposer.transposeChord(token, semitones, preferFlats))
                        .append(']')
                    i = close + 1
                    continue
                }
            }
            out.append(c)
            i++
        }
        return out.toString()
    }

    /**
     * True when every whitespace-separated token on the line is a chord.
     * Requiring all tokens to be chords is what stops ordinary lyrics from
     * being swallowed as a chord row.
     */
    private fun isChordOnlyLine(line: String): Boolean {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) return false
        if (trimmed.contains('[')) return false
        val tokens = trimmed.split(WHITESPACE).filter { it.isNotBlank() && it != "|" && it != "-" }
        if (tokens.isEmpty() || tokens.size > 16) return false
        return tokens.all { ChordTransposer.isChordToken(it) }
    }

    private fun tokensWithColumns(line: String): List<ChordOccurrence> {
        val out = ArrayList<ChordOccurrence>(8)
        var i = 0
        while (i < line.length) {
            if (line[i].isWhitespace()) {
                i++
                continue
            }
            val start = i
            while (i < line.length && !line[i].isWhitespace()) i++
            val token = line.substring(start, i)
            if (ChordTransposer.isChordToken(token)) out.add(ChordOccurrence(token, start))
            i++
        }
        return out
    }

    private fun pairChordLineWithLyric(chordLine: String, lyricLine: String): ParsedLine {
        val lyric = cleanLyricText(lyricLine)
        val chords = tokensWithColumns(chordLine).map {
            ChordOccurrence(it.chord, it.column.coerceIn(0, maxOf(0, lyric.length)))
        }
        return ParsedLine(text = lyric, chords = chords)
    }

    private fun chordOnlyAsLine(chordLine: String): ParsedLine =
        ParsedLine(text = "", chords = tokensWithColumns(chordLine))

    private fun transposeChordOnlyLine(
        line: String,
        semitones: Int,
        preferFlats: Boolean
    ): String {
        val out = StringBuilder(line.length + 8)
        var i = 0
        while (i < line.length) {
            if (line[i].isWhitespace()) {
                out.append(line[i])
                i++
                continue
            }
            val start = i
            while (i < line.length && !line[i].isWhitespace()) i++
            val token = line.substring(start, i)
            out.append(ChordTransposer.transposeChord(token, semitones, preferFlats))
        }
        return out.toString()
    }
}
