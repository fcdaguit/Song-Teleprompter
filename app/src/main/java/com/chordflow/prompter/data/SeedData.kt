package com.chordflow.prompter.data

/**
 * The starter library that ships with the app.
 *
 * All three hymns are in the public domain (text and tune published well
 * before 1900), so they can be bundled with the APK without any licence.
 *
 * Note on the lineup: "Holy, Holy, Holy" is stored in its own key of D but
 * added to the Sunday lineup in Bb. That is deliberate - it exercises the
 * transposition engine the first time you launch the teleprompter.
 */
object SeedData {

    const val DEFAULT_LINEUP_NAME = "Sunday Service Lineup"

    data class SeedSong(
        val title: String,
        val artist: String,
        val key: String,
        val tempo: String,
        val lineupKey: String,
        val slotLabel: String,
        val content: String
    )

    val songs: List<SeedSong> = listOf(
        SeedSong(
            title = "Amazing Grace",
            artist = "John Newton (1779) - public domain",
            key = "G",
            tempo = "72",
            lineupKey = "G",
            slotLabel = "Call to Worship",
            content = AMAZING_GRACE
        ),
        SeedSong(
            title = "Holy, Holy, Holy",
            artist = "Reginald Heber (1826) / John B. Dykes - public domain",
            key = "D",
            tempo = "84",
            lineupKey = "Bb",
            slotLabel = "Praise 1",
            content = HOLY_HOLY_HOLY
        ),
        SeedSong(
            title = "Blessed Assurance",
            artist = "Fanny J. Crosby (1873) / Phoebe P. Knapp - public domain",
            key = "D",
            tempo = "100",
            lineupKey = "D",
            slotLabel = "Worship / Altar Call",
            content = BLESSED_ASSURANCE
        )
    )
}

/** ChordPro inline-bracket format. */
private val AMAZING_GRACE: String = """
{title: Amazing Grace}
{artist: John Newton (1779) - public domain}
{key: G}
{tempo: 72}

{comment: Verse 1}
[G]Amazing grace, how [G7]sweet the [C]sound
That [G]saved a wretch like [D]me
I [G]once was lost, but [G7]now am [C]found
Was [G]blind, but [D]now I [G]see

{comment: Verse 2}
[G]'Twas grace that [G7]taught my [C]heart to fear
And [G]grace my fears re[D]lieved
How [G]precious did that [G7]grace ap[C]pear
The [G]hour I [D]first be[G]lieved

{comment: Verse 3}
Through [G]many dangers, [G7]toils and [C]snares
I [G]have already [D]come
'Tis [G]grace hath brought me [G7]safe thus [C]far
And [G]grace will [D]lead me [G]home

{comment: Verse 4}
When [G]we've been there ten [G7]thousand [C]years
Bright [G]shining as the [D]sun
We've [G]no less days to [G7]sing God's [C]praise
Than [G]when we'd [D]first be[G]gun
""".trimIndent()

/** ChordPro inline-bracket format. Stored in D, played in Bb in the lineup. */
private val HOLY_HOLY_HOLY: String = """
{title: Holy, Holy, Holy}
{artist: Reginald Heber (1826) / John B. Dykes - public domain}
{key: D}
{tempo: 84}

{comment: Verse 1}
[D]Holy, holy, [G]holy! [D]Lord God Al[A]mighty!
[D]Early in the [A]morning our [D]song shall rise to [A]Thee
[D]Holy, holy, [G]holy! [D]merciful and [A]mighty!
[D]God in three [G]Persons, [D]blessed [A]Trini[D]ty

{comment: Verse 2}
[D]Holy, holy, [G]holy! [D]all the saints a[A]dore Thee
[D]Casting down their [A]golden crowns a[D]round the glassy [A]sea
[D]Cherubim and [G]seraphim [D]falling down be[A]fore Thee
[D]Which wert, and [G]art, and [D]ever[A]more shalt [D]be

{comment: Verse 3}
[D]Holy, holy, [G]holy! [D]though the darkness [A]hide Thee
[D]Though the eye of [A]sinful man Thy [D]glory may not [A]see
[D]Only Thou art [G]holy; [D]there is none be[A]side Thee
[D]Perfect in [G]power, in [D]love, and [A]puri[D]ty

{comment: Verse 4}
[D]Holy, holy, [G]holy! [D]Lord God Al[A]mighty!
[D]All Thy works shall [A]praise Thy name, in [D]earth, and sky, and [A]sea
[D]Holy, holy, [G]holy! [D]merciful and [A]mighty!
[D]God in three [G]Persons, [D]blessed [A]Trini[D]ty
""".trimIndent()

/**
 * Two-line chord-over-lyric format, so the starter library exercises both
 * parser paths out of the box.
 */
private val BLESSED_ASSURANCE: String = """
{title: Blessed Assurance}
{artist: Fanny J. Crosby (1873) / Phoebe P. Knapp - public domain}
{key: D}
{tempo: 100}

{comment: Verse 1}
D              G        D
Blessed assurance, Jesus is mine
A            D       A          D
Oh, what a foretaste of glory divine
D          G          D
Heir of salvation, purchase of God
A        D        A           D
Born of His Spirit, washed in His blood

{comment: Chorus}
D        G         D
This is my story, this is my song
A          D        A        D
Praising my Savior all the day long
D        G         D
This is my story, this is my song
A          D        A        D
Praising my Savior all the day long

{comment: Verse 2}
D            G       D
Perfect submission, perfect delight
A          D         A          D
Visions of rapture now burst on my sight
D         G        D
Angels descending bring from above
A        D       A          D
Echoes of mercy, whispers of love

{comment: Verse 3}
D            G       D
Perfect submission, all is at rest
A        D        A          D
I in my Savior am happy and blest
D          G        D
Watching and waiting, looking above
A         D        A       D
Filled with His goodness, lost in His love
""".trimIndent()
