package com.chordflow.prompter.core

/**
 * A chord symbol broken into its parts.
 *
 * @param rootPc   pitch class of the root, 0 = C .. 11 = B
 * @param quality  everything after the root letter, e.g. "m7", "sus4", "maj7"
 * @param bassPc   pitch class of the slash bass note, or null when there is none
 * @param raw      the cleaned token this was parsed from
 */
data class ChordSymbol(
    val rootPc: Int,
    val quality: String,
    val bassPc: Int?,
    val raw: String
)

/**
 * Chord parsing, transposition and enharmonic spelling.
 *
 * Everything here is pure Kotlin with no Android dependencies, so it runs
 * identically on the audio thread, in the UI layer and in unit tests.
 */
object ChordTransposer {

    val SHARP_NAMES = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
    val FLAT_NAMES = listOf("C", "Db", "D", "Eb", "E", "F", "Gb", "G", "Ab", "A", "Bb", "B")

    /**
     * Keys offered in the key dropdown. The list is deliberately in pitch-class
     * order (index == pitch class) and uses the spelling worship teams normally
     * read, so [preferredKeyName] is a straight index lookup.
     */
    val SELECTABLE_KEYS = listOf(
        "C", "C#", "D", "Eb", "E", "F", "F#", "G", "Ab", "A", "Bb", "B"
    )

    /** Pitch classes whose preferred key spelling uses flats: F, Eb, Ab, Bb. */
    private val FLAT_KEY_PCS = setOf(5, 3, 8, 10)

    /**
     * Chord suffixes recognised as real chords. Anything whose suffix is not in
     * this set is treated as ordinary text, which is what keeps two-line
     * chord-over-lyric detection from mistaking words such as "Fade" or "Bed"
     * for chords.
     */
    private val KNOWN_SUFFIXES: Set<String> = setOf(
        "",
        "5", "6", "7", "9", "11", "13", "2", "4", "69", "6/9",
        "m", "mi", "min", "-",
        "M", "ma", "maj", "+",
        "aug", "dim", "o", "0", "°", "ø",
        "m6", "m7", "m9", "m11", "m13", "mi7", "mi9", "min7", "min9", "min11",
        "-7", "-9", "-11",
        "maj7", "maj9", "maj11", "maj13", "Maj7", "Maj9", "M7", "M9", "M13", "ma7",
        "sus", "sus2", "sus4", "sus9",
        "7sus", "7sus2", "7sus4", "9sus4", "13sus4", "m7sus4",
        "add2", "add4", "add9", "add11", "add13", "madd9",
        "dim7", "o7", "°7", "ø7",
        "m7b5", "m7-5", "mi7b5", "min7b5", "m9b5",
        "7b5", "7#5", "7b9", "7#9", "7#11", "7b13", "7alt",
        "9b5", "9#5", "9#11", "11b9", "13b9", "13#11",
        "mmaj7", "mM7", "minmaj7", "mmaj9",
        "aug7", "+7", "+5", "6sus4", "4sus"
    )

    // ------------------------------------------------------------------ notes

    /** Length in characters of the note name at the start of [text], or null. */
    private fun rootLength(text: String): Int? {
        if (text.isEmpty()) return null
        val c = text[0]
        if (c < 'A' || c > 'G') return null
        if (text.length > 1) {
            val a = text[1]
            if (a == '#' || a == 'b' || a == '♯' || a == '♭') return 2
        }
        return 1
    }

    /** Pitch class of a note name such as "C", "F#", "Bb". Null when unparsable. */
    fun pitchClassOf(note: String): Int? {
        val trimmed = note.trim()
        if (trimmed.isEmpty()) return null
        val letter = trimmed[0]
        var pc = when (letter) {
            'C', 'c' -> 0
            'D', 'd' -> 2
            'E', 'e' -> 4
            'F', 'f' -> 5
            'G', 'g' -> 7
            'A', 'a' -> 9
            'B', 'b' -> 11
            else -> return null
        }
        for (i in 1 until trimmed.length) {
            when (trimmed[i]) {
                '#', '♯' -> pc += 1
                'b', '♭' -> pc -= 1
                else -> return null
            }
        }
        return ((pc % 12) + 12) % 12
    }

    private fun normalizeSuffix(suffix: String): String {
        val sb = StringBuilder(suffix.length)
        for (ch in suffix) {
            if (ch == '(' || ch == ')' || ch == ' ' || ch == '\t') continue
            sb.append(ch)
        }
        return sb.toString()
    }

    // ----------------------------------------------------------------- chords

    /** Strip bar lines, punctuation and whitespace from a raw chord token. */
    private fun cleanToken(raw: String): String =
        raw.trim().trim('|', ',', '.', ';', ':', '*', '"', '\'', '!', '?')

    /**
     * Parse a chord token such as "G", "Am7", "Csus4", "D/F#".
     * Returns null when the token is not a chord.
     */
    fun parseChord(raw: String): ChordSymbol? {
        val token = cleanToken(raw)
        if (token.isEmpty()) return null

        val slash = token.indexOf('/')
        val mainPart = if (slash >= 0) token.substring(0, slash) else token
        val bassPart = if (slash >= 0) token.substring(slash + 1) else null

        val rootLen = rootLength(mainPart) ?: return null
        val rootPc = pitchClassOf(mainPart.substring(0, rootLen)) ?: return null
        val quality = mainPart.substring(rootLen)
        if (normalizeSuffix(quality) !in KNOWN_SUFFIXES) return null

        if (bassPart == null) {
            return ChordSymbol(rootPc, quality, null, token)
        }

        val bassLen = rootLength(bassPart)
        if (bassLen != null && bassLen == bassPart.length) {
            val bassPc = pitchClassOf(bassPart) ?: return null
            return ChordSymbol(rootPc, quality, bassPc, token)
        }

        // The part after the slash is not a bass note. It may still be part of
        // the quality, as in "C6/9", so try the whole token as root + quality.
        val wholeRootLen = rootLength(token) ?: return null
        val wholeRootPc = pitchClassOf(token.substring(0, wholeRootLen)) ?: return null
        val wholeQuality = token.substring(wholeRootLen)
        if (normalizeSuffix(wholeQuality) !in KNOWN_SUFFIXES) return null
        return ChordSymbol(wholeRootPc, wholeQuality, null, token)
    }

    fun isChordToken(raw: String): Boolean = parseChord(raw) != null

    /** Render a parsed chord using sharp or flat spelling. */
    fun format(symbol: ChordSymbol, preferFlats: Boolean): String {
        val names = if (preferFlats) FLAT_NAMES else SHARP_NAMES
        val sb = StringBuilder(names[symbol.rootPc]).append(symbol.quality)
        if (symbol.bassPc != null) sb.append('/').append(names[symbol.bassPc])
        return sb.toString()
    }

    /**
     * Transpose a single chord token by [semitones] (-11..+11).
     * Tokens that are not chords are returned untouched.
     */
    fun transposeChord(raw: String, semitones: Int, preferFlats: Boolean): String {
        val symbol = parseChord(raw) ?: return raw
        val shifted = ChordSymbol(
            rootPc = wrap(symbol.rootPc + semitones),
            quality = symbol.quality,
            bassPc = symbol.bassPc?.let { wrap(it + semitones) },
            raw = symbol.raw
        )
        return format(shifted, preferFlats)
    }

    /** Pitch classes a chord occupies for trigger matching: root first, then bass. */
    fun triggerPitchClasses(raw: String, semitones: Int): List<Int> {
        val symbol = parseChord(raw) ?: return emptyList()
        val out = ArrayList<Int>(2)
        out.add(wrap(symbol.rootPc + semitones))
        symbol.bassPc?.let { out.add(wrap(it + semitones)) }
        return out
    }

    private fun wrap(pc: Int): Int = ((pc % 12) + 12) % 12

    // -------------------------------------------------------------------- keys

    fun isMinorKey(key: String?): Boolean {
        val k = key?.trim() ?: return false
        val lower = k.lowercase()
        return lower.endsWith("m") && !lower.endsWith("dim") ||
            lower.endsWith("min") || lower.endsWith("minor")
    }

    /** Pitch class of a key name such as "G", "Bb", "F#m". */
    fun keyRootPc(key: String?): Int? {
        var k = key?.trim() ?: return null
        if (k.isEmpty()) return null
        for (suffix in listOf("minor", "major", "min", "maj", "m", "M")) {
            if (k.length > suffix.length && k.endsWith(suffix)) {
                k = k.dropLast(suffix.length).trim()
                break
            }
        }
        return pitchClassOf(k)
    }

    /** The spelling this app uses for a key at a given pitch class. */
    fun preferredKeyName(pc: Int): String = SELECTABLE_KEYS[wrap(pc)]

    /** True when chords in this key should be spelled with flats. */
    fun keyPrefersFlats(key: String?): Boolean {
        val k = key?.trim() ?: return false
        if (k.length > 1 && (k[1] == 'b' || k[1] == '♭')) return true
        val pc = keyRootPc(k) ?: return false
        return pc in FLAT_KEY_PCS
    }

    /**
     * Shortest signed semitone distance from [fromKey] to [toKey], in -6..+6.
     * Returns 0 when either key cannot be parsed.
     */
    fun semitonesBetween(fromKey: String?, toKey: String?): Int {
        val a = keyRootPc(fromKey) ?: return 0
        val b = keyRootPc(toKey) ?: return 0
        var d = ((b - a) % 12 + 12) % 12
        if (d > 6) d -= 12
        return d
    }

    /** Name of the key reached by shifting [key] up by [semitones]. */
    fun transposeKeyName(key: String?, semitones: Int): String {
        val pc = keyRootPc(key) ?: return key?.takeIf { it.isNotBlank() } ?: "C"
        val name = preferredKeyName(pc + semitones)
        return if (isMinorKey(key)) name + "m" else name
    }

    /** Clamp a transpose amount into the supported -11..+11 range. */
    fun clampSemitones(semitones: Int): Int = semitones.coerceIn(-11, 11)
}
