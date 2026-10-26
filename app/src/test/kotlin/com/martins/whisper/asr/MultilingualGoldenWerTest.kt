package com.martins.whisper.asr

import com.martins.whisper.core.AudioChunk
import com.martins.whisper.core.ChunkReason
import com.martins.whisper.core.ComputeBackend
import java.io.File
import java.text.Normalizer
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * V6 multilingual golden fixtures (agent/v6-multilang): every manifest clip (public FLEURS
 * validation clips, CC-BY, fetched by scripts/verify/fetch-golden-fleurs.sh into gitignored
 * recordings/golden-fleurs/) is transcribed with the real engine through the host JNI path,
 * the clip's language passed explicitly, WER per language (CER for ja/zh/th) measured and
 * written to $WHISPER_FLEURS_REPORT; docs/LANGUAGES.md records those measured numbers.
 * Thresholds are never asserted: recorded from measurement, not invented; the test stays
 * green while the table stays honest.
 */
@RunWith(RobolectricTestRunner::class)
class MultilingualGoldenWerTest {

    @Test
    fun measuresPerLanguageErrorRates() {
        val dir = envDir("WHISPER_FLEURS_DIR")?.takeIf { it.isDirectory }
        val report = System.getenv("WHISPER_FLEURS_REPORT")?.takeIf { it.isNotBlank() }?.let(::File)
        val model = System.getenv("WHISPER_HOST_MODEL")?.takeIf { it.isNotBlank() }?.let(::File)?.takeIf { it.isFile }
        assumeTrue(
            "set WHISPER_FLEURS_DIR/WHISPER_FLEURS_REPORT/WHISPER_HOST_MODEL and -Pwhisper.host.libDir (scripts/verify/fleurs-wer.sh)",
            dir != null && model != null,
        )
        assumeTrue(
            "host libmartins_whisper.so not loadable: ${WhisperCppEngine.nativeLoadError}",
            WhisperCppEngine.isNativeAvailable,
        )
        val clips = FleursManifest.parse()
        assertTrue("manifest has too few clips (${clips.size})", clips.size >= 20)
        for (clip in clips) {
            assertTrue(
                "missing audio for ${clip.language}: ${clip.audioName}; re-run scripts/verify/fetch-golden-fleurs.sh",
                File(dir!!, clip.audioName).isFile,
            )
        }

        val engine = WhisperCppEngine.create(model!!.absolutePath, ComputeBackend.CPU, threads = 4)
        val rows = ArrayList<String>()
        try {
            for (clip in clips) {
                val samples = HostWav.read16kMono(File(dir, clip.audioName))
                assertTrue("empty wav for ${clip.audioName}", samples.size > 4000)
                val chunk = AudioChunk(
                    index = 0,
                    samples = samples,
                    sampleRate = 16_000,
                    startMs = 0,
                    endMs = samples.size * 1_000L / 16_000,
                    reason = ChunkReason.FLUSH,
                )
                val transcript = runBlocking { engine.transcribe(chunk, language = clip.language) }
                assertFalse(
                    "native UTF-8 decode produced U+FFFD for ${clip.language}: '${transcript.text}'",
                    transcript.text.contains('\uFFFD'),
                )
                val metric = if (clip.cer) "CER" else "WER"
                val ref = if (clip.cer) chars(clip.expected) else words(clip.expected)
                val hyp = if (clip.cer) chars(transcript.text) else words(transcript.text)
                val error = if (ref.isEmpty()) {
                    if (hyp.isEmpty()) 0.0 else 1.0
                } else {
                    levenshtein(hyp, ref).toDouble() / ref.size
                }
                rows += "| ${clip.language} | $metric | ${clip.sourceId} | " +
                    "${clip.durationSeconds} s | %.2f |".format(error) +
                    " `${transcript.text.replace("\n", " ").take(90)}` |\n"
                println("FLEURS ${clip.language} ${clip.sourceId} $metric=${"%.2f".format(error)} text='${transcript.text.take(70)}'")
            }
        } finally {
            engine.close()
        }

        val reportFile = report ?: File("build/reports/fleurs-report.md")
        reportFile.parentFile?.mkdirs()
        reportFile.writeText(
            "# FLEURS golden fixtures vs. ${model.name} (measurements, not thresholds)\n\n" +
                "| language | metric | source id | duration | error | transcript |\n|---|---|---|---|---|---|\n" +
                rows.joinToString(""),
        )
        println("fleurs report written to ${reportFile.absolutePath}")
    }

    /** Word error rate for space-separating scripts: NFC-normalised, uncapitalised, punctuation stripped. */
    fun words(t: String): List<String> =
        Normalizer.normalize(
            normalize(t).lowercase().replace(Regex("[.,;:!?¡¿«»„“”\"'()\\[\\]<>()«»]"), " "),
            Normalizer.Form.NFC,
        ).trim().split(Regex("\\s+")).filter { it.isNotEmpty() }

    /** Character list for scripts without word spacing (ja/zh/th): punctuation stripped. */
    fun chars(t: String): List<String> {
        val norm = Normalizer.normalize(
            normalize(t).lowercase().replace(Regex("[\\s.,;:!?¡¿«»„“”\"'()\\[\\]<>-]"), ""),
            Normalizer.Form.NFC,
        )
        return norm.codePoints().mapToObj { String(Character.toChars(it)) }.toList()
    }

    private fun normalize(t: String): String =
        t.replace(Regex("https?://\\S+"), " ")

    fun levenshtein(hyp: List<String>, ref: List<String>): Int {
        var prev = IntArray(ref.size + 1) { it }
        for (i in hyp.indices) {
            val cur = IntArray(ref.size + 1)
            cur[0] = i + 1
            for (j in ref.indices) {
                cur[j + 1] =
                    minOf(prev[j] + if (hyp[i] == ref[j]) 0 else 1, prev[j + 1] + 1, cur[j] + 1)
            }
            prev = cur
        }
        return prev[ref.size]
    }

    private fun envDir(name: String): File? =
        System.getenv(name)?.takeIf { it.isNotBlank() }?.let(::File)
}
