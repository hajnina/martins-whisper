package com.martins.whisper.core.asr

import com.martins.whisper.core.ModelSpec
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class ModelRepositoryTest {

    private val payload = ByteArray(512) { (it * 7).toByte() }
    private val hash = Sha256.hex(payload)

    @Test
    fun `storage paths use id under models directory`() = withTempDir { tmp ->
        val storage = ModelStorage(tmp)
        val spec = spec()
        assertEquals(File(tmp, "models"), storage.modelsDir)
        assertEquals(File(tmp, "models/test.bin"), storage.modelFile(spec))
        assertEquals(File(tmp, "models/test.bin.part"), storage.partFile(spec))
        assertEquals("weird_id", storage.safeId("weird/id"))
        assertEquals("weird_id", storage.safeId("weird\\id"))
    }

    @Test
    fun `import verifies and installs`() = withTempDir { tmp ->
        val repo = ModelRepository(ModelStorage(tmp))
        val source = File(tmp, "downloaded.bin").apply { writeBytes(payload) }
        val progress = mutableListOf<DownloadProgress>()

        val result = repo.importFile(spec(), source) { progress += it }

        assertEquals(hash, result.sha256)
        assertArrayEquals(payload, repo.modelFile(spec()).readBytes())
        assertTrue(repo.isInstalled(spec()))
        assertTrue(repo.isInstalled(spec(), verifyHash = true))
        assertFalse(repo.storage.partFile(spec()).exists())
        assertEquals(payload.size.toLong(), progress.last().bytesDownloaded)
    }

    @Test
    fun `import with bad hash leaves no installed model`() = withTempDir { tmp ->
        val repo = ModelRepository(ModelStorage(tmp))
        val source = File(tmp, "downloaded.bin").apply { writeBytes(payload) }
        val bad = spec(sha256 = "f".repeat(64))

        try {
            repo.importFile(bad, source)
            throw AssertionError("expected ModelVerificationException")
        } catch (_: ModelVerificationException) {
        }
        assertFalse(repo.modelFile(bad).exists())
        assertFalse(repo.storage.partFile(bad).exists())
    }

    @Test
    fun `installed listing filters missing files`() = withTempDir { tmp ->
        val repo = ModelRepository(ModelStorage(tmp))
        repo.storage.ensureModelsDir()
        repo.modelFile(spec()).writeBytes(payload)
        assertEquals(listOf(spec()), repo.installed(listOf(spec(), spec("other"))))
    }

    @Test
    fun `delete removes model and partial`() = withTempDir { tmp ->
        val repo = ModelRepository(ModelStorage(tmp))
        repo.storage.ensureModelsDir()
        repo.modelFile(spec()).writeBytes(payload)
        repo.storage.partFile(spec()).writeBytes(byteArrayOf(1))
        assertTrue(repo.delete(spec()))
        assertFalse(repo.modelFile(spec()).exists())
        assertFalse(repo.storage.partFile(spec()).exists())
    }

    @Test
    fun `install without url is rejected`() = withTempDir { tmp ->
        val repo = ModelRepository(ModelStorage(tmp))
        try {
            repo.install(ModelCatalog.custom(id = "custom", fileName = "custom.bin"))
            throw AssertionError("expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
        }
    }

    private fun spec(id: String = "test", sha256: String? = hash): ModelSpec = ModelSpec(
        id = id,
        displayName = id,
        fileName = "$id.bin",
        url = if (sha256 != null) "https://example.invalid/$id.bin" else null,
        sha256 = sha256,
        sizeBytes = payload.size.toLong(),
        multilingual = true,
    )

    private fun withTempDir(block: (File) -> Unit) {
        val dir = Files.createTempDirectory("e1-repo").toFile()
        try {
            block(dir)
        } finally {
            dir.deleteRecursively()
        }
    }
}
