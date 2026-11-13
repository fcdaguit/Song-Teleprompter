package com.chordflow.prompter.core

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** A chord the microphone is currently hearing. */
data class ChordDetection(
    val rootPc: Int,
    val quality: String,
    val bassPc: Int?,
    val confidence: Float,
    val level: Float
) {
    /** Sharp-spelled label. The UI re-spells this for the song's key. */
    val label: String
        get() {
            val base = ChordTransposer.SHARP_NAMES[rootPc] + quality
            return if (bassPc != null && bassPc != rootPc) {
                base + "/" + ChordTransposer.SHARP_NAMES[bassPc]
            } else {
                base
            }
        }
}

/** Iterative radix-2 Cooley-Tukey FFT, in place. */
object Fft {

    /**
     * Transforms [re]/[im] in place. Both arrays must be the same length and
     * that length must be a power of two.
     */
    fun transform(re: DoubleArray, im: DoubleArray) {
        val n = re.size
        if (n <= 1) return
        require(im.size == n) { "real and imaginary arrays must match" }
        require(n and (n - 1) == 0) { "FFT size must be a power of two" }

        // Bit-reversal permutation.
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (bit != 0 && (j and bit) != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j or bit
            if (i < j) {
                val tr = re[i]; re[i] = re[j]; re[j] = tr
                val ti = im[i]; im[i] = im[j]; im[j] = ti
            }
        }

        // Butterflies.
        var len = 2
        while (len <= n) {
            val angle = -2.0 * PI / len
            val wRe = cos(angle)
            val wIm = sin(angle)
            val half = len shr 1
            var base = 0
            while (base < n) {
                var curRe = 1.0
                var curIm = 0.0
                for (k in 0 until half) {
                    val a = base + k
                    val b = a + half
                    val bRe = re[b] * curRe - im[b] * curIm
                    val bIm = re[b] * curIm + im[b] * curRe
                    val aRe = re[a]
                    val aIm = im[a]
                    re[a] = aRe + bRe
                    im[a] = aIm + bIm
                    re[b] = aRe - bRe
                    im[b] = aIm - bIm
                    val nextRe = curRe * wRe - curIm * wIm
                    curIm = curRe * wIm + curIm * wRe
                    curRe = nextRe
                }
                base += len
            }
            len = len shl 1
        }
    }
}

/**
 * The chord templates the chromagram is correlated against: 10 qualities over
 * all 12 roots, 120 candidates in total.
 */
object ChordTemplates {

    private val QUALITY_INTERVALS: List<Pair<String, IntArray>> = listOf(
        "" to intArrayOf(0, 4, 7),
        "m" to intArrayOf(0, 3, 7),
        "7" to intArrayOf(0, 4, 7, 10),
        "m7" to intArrayOf(0, 3, 7, 10),
        "maj7" to intArrayOf(0, 4, 7, 11),
        "sus4" to intArrayOf(0, 5, 7),
        "sus2" to intArrayOf(0, 2, 7),
        "dim" to intArrayOf(0, 3, 6),
        "aug" to intArrayOf(0, 4, 8),
        "5" to intArrayOf(0, 7)
    )

    /** Root pitch class and quality for each template, in the same order as [vectors]. */
    val labels: List<Pair<Int, String>>

    /** L2-normalised 12-element template vectors. */
    val vectors: List<DoubleArray>

    init {
        val labelList = ArrayList<Pair<Int, String>>(120)
        val vectorList = ArrayList<DoubleArray>(120)
        for (root in 0 until 12) {
            for ((quality, intervals) in QUALITY_INTERVALS) {
                val v = DoubleArray(12)
                intervals.forEachIndexed { position, interval ->
                    // Root and fifth weighted a little higher: they dominate
                    // what a guitar or piano actually puts into the room.
                    val weight = if (position == 0) 1.25 else 1.0
                    v[(root + interval) % 12] = weight
                }
                normalise(v)
                labelList.add(root to quality)
                vectorList.add(v)
            }
        }
        labels = labelList
        vectors = vectorList
    }

    private fun normalise(v: DoubleArray) {
        var sum = 0.0
        for (x in v) sum += x * x
        val norm = sqrt(sum)
        if (norm > 0.0) {
            for (i in v.indices) v[i] = v[i] / norm
        }
    }
}

/**
 * Live chord recognition from the device microphone.
 *
 * Pipeline: AudioRecord (16-bit PCM mono 44.1 kHz) -> Hann window -> 4096-point
 * FFT -> magnitude spectrum -> pitch class profile (chromagram) -> cosine
 * correlation against 120 chord templates -> frame smoothing -> StateFlow.
 *
 * Nothing here touches the network or the disk.
 */
class AudioChordDetector {

    companion object {
        const val SAMPLE_RATE = 44100
        private const val FFT_SIZE = 4096
        private const val HOP_SIZE = 2048

        /** Lowest note considered, roughly D2. */
        private const val MIN_FREQ = 70.0

        /** Highest note considered, roughly C7. */
        private const val MAX_FREQ = 2200.0

        /** Band used to guess the bass note of a slash chord. */
        private const val BASS_MIN_FREQ = 70.0
        private const val BASS_MAX_FREQ = 210.0

        private const val LN2 = 0.6931471805599453
    }

    private val _detection = MutableStateFlow<ChordDetection?>(null)
    val detection: StateFlow<ChordDetection?> = _detection.asStateFlow()

    private val _inputLevel = MutableStateFlow(0f)
    val inputLevel: StateFlow<Float> = _inputLevel.asStateFlow()

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    /**
     * Noise gate, 0f = wide open, 1f = only loud, close signal passes.
     * Driven by the sensitivity slider on the teleprompter HUD.
     */
    @Volatile
    var gate: Float = 0.30f

    /** Minimum template correlation before a chord is reported. */
    @Volatile
    var minConfidence: Float = 0.78f

    private var job: Job? = null
    private var record: AudioRecord? = null

    private val window = DoubleArray(FFT_SIZE) {
        0.5 - 0.5 * cos(2.0 * PI * it / (FFT_SIZE - 1))
    }
    private val binFrequencies = DoubleArray(FFT_SIZE / 2) {
        it.toDouble() * SAMPLE_RATE / FFT_SIZE
    }
    private val binPitchClasses = IntArray(FFT_SIZE / 2) { bin ->
        val f = binFrequencies[bin]
        if (f < MIN_FREQ || f > MAX_FREQ) {
            -1
        } else {
            val midi = 69.0 + 12.0 * ln(f / 440.0) / LN2
            ((midi.roundToInt() % 12) + 12) % 12
        }
    }

    @SuppressLint("MissingPermission")
    fun start(scope: CoroutineScope) {
        if (job?.isActive == true) return
        _error.value = null
        job = scope.launch(Dispatchers.Default) {
            val minBuffer = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            if (minBuffer <= 0) {
                _error.value = "This device reported no usable microphone buffer."
                return@launch
            }
            val bufferSize = maxOf(minBuffer, HOP_SIZE * 2 * 4)

            val recorder = try {
                AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize
                )
            } catch (t: Throwable) {
                _error.value = "Microphone unavailable: ${t.message ?: "unknown error"}"
                return@launch
            }

            if (recorder.state != AudioRecord.STATE_INITIALIZED) {
                recorder.release()
                _error.value = "Microphone could not be initialised. Check the permission."
                return@launch
            }

            record = recorder
            val ring = ShortArray(FFT_SIZE)
            val chunk = ShortArray(HOP_SIZE)
            val re = DoubleArray(FFT_SIZE)
            val im = DoubleArray(FFT_SIZE)
            val chroma = DoubleArray(12)

            var pendingRoot = -1
            var pendingQuality = ""
            var pendingFrames = 0

            try {
                recorder.startRecording()
                _running.value = true

                while (isActive) {
                    var read = 0
                    while (read < HOP_SIZE && isActive) {
                        val n = recorder.read(chunk, read, HOP_SIZE - read)
                        if (n <= 0) break
                        read += n
                    }
                    if (read <= 0) continue

                    // Slide the analysis window along by one hop.
                    System.arraycopy(ring, read, ring, 0, FFT_SIZE - read)
                    System.arraycopy(chunk, 0, ring, FFT_SIZE - read, read)

                    val rms = rmsOf(ring)
                    _inputLevel.value = rms.toFloat()

                    val threshold = 0.0015 + gate.coerceIn(0f, 1f) * 0.055
                    if (rms < threshold) {
                        pendingFrames = 0
                        pendingRoot = -1
                        if (_detection.value != null) _detection.value = null
                        continue
                    }

                    for (i in 0 until FFT_SIZE) {
                        re[i] = ring[i] / 32768.0 * window[i]
                        im[i] = 0.0
                    }
                    Fft.transform(re, im)

                    java.util.Arrays.fill(chroma, 0.0)
                    var bassPc = -1
                    var bassMag = 0.0
                    for (bin in 1 until FFT_SIZE / 2) {
                        val pc = binPitchClasses[bin]
                        if (pc < 0) continue
                        val mag = sqrt(re[bin] * re[bin] + im[bin] * im[bin])
                        chroma[pc] += mag
                        val f = binFrequencies[bin]
                        if (f in BASS_MIN_FREQ..BASS_MAX_FREQ && mag > bassMag) {
                            bassMag = mag
                            bassPc = pc
                        }
                    }

                    // Mild compression keeps a loud bass fundamental from
                    // swamping the upper voices of the chord.
                    for (i in 0 until 12) chroma[i] = sqrt(chroma[i])
                    if (!normaliseInPlace(chroma)) {
                        pendingFrames = 0
                        continue
                    }

                    var bestIndex = -1
                    var bestScore = 0.0
                    for (t in ChordTemplates.vectors.indices) {
                        val template = ChordTemplates.vectors[t]
                        var dot = 0.0
                        for (i in 0 until 12) dot += chroma[i] * template[i]
                        if (dot > bestScore) {
                            bestScore = dot
                            bestIndex = t
                        }
                    }

                    if (bestIndex < 0 || bestScore < minConfidence) {
                        pendingFrames = 0
                        pendingRoot = -1
                        continue
                    }

                    val (root, quality) = ChordTemplates.labels[bestIndex]

                    // Require the same chord on two consecutive frames before
                    // publishing it. At a 2048-sample hop that is about 46 ms
                    // of agreement, which removes most single-frame flicker.
                    if (root == pendingRoot && quality == pendingQuality) {
                        pendingFrames++
                    } else {
                        pendingRoot = root
                        pendingQuality = quality
                        pendingFrames = 1
                    }
                    if (pendingFrames < 2) continue

                    _detection.value = ChordDetection(
                        rootPc = root,
                        quality = quality,
                        bassPc = if (bassPc >= 0) bassPc else null,
                        confidence = bestScore.toFloat(),
                        level = rms.toFloat()
                    )
                }
            } catch (t: Throwable) {
                _error.value = t.message ?: "Audio capture stopped unexpectedly."
            } finally {
                try {
                    if (recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                        recorder.stop()
                    }
                } catch (_: Throwable) {
                    // Nothing useful to do if stop() throws during teardown.
                }
                recorder.release()
                record = null
                _running.value = false
                _inputLevel.value = 0f
                _detection.value = null
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    private fun rmsOf(samples: ShortArray): Double {
        var sum = 0.0
        for (s in samples) {
            val v = s / 32768.0
            sum += v * v
        }
        return sqrt(sum / samples.size)
    }

    private fun normaliseInPlace(v: DoubleArray): Boolean {
        var sum = 0.0
        for (x in v) sum += x * x
        val norm = sqrt(sum)
        if (norm <= 1e-9 || !norm.isFinite()) return false
        for (i in v.indices) v[i] = v[i] / norm
        return abs(norm) > 0.0
    }
}
