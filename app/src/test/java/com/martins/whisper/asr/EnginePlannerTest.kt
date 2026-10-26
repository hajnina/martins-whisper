package com.martins.whisper.asr

import com.martins.whisper.core.ComputeBackend
import com.martins.whisper.core.asr.ModelCatalog
import com.martins.whisper.core.asr.ModelRepository
import com.martins.whisper.core.asr.ModelStorage
import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class EnginePlannerTest {

    private fun repository(): ModelRepository {
        val root = Files.createTempDirectory("engine-planner-test").toFile()
        root.deleteOnExit()
        return ModelRepository(ModelStorage(root))
    }

    @Test
    fun `catalog model files use the id-based storage layout`() {
        val repository = repository()
        repository.storage.ensureModelsDir()
        File(repository.modelsDir, "base.bin").writeBytes(ByteArray(4))
        val plan = EnginePlanner.plan("base", ComputeBackend.AUTO, repository)
        assertEquals("base", plan.spec.id)
        assertEquals("base.bin", plan.modelFile.name)
        assertTrue(plan.modelFile.isFile)
        assertEquals(ComputeBackend.AUTO, plan.backend)
    }

    @Test
    fun `unknown persisted id is treated as an imported model`() {
        val repository = repository()
        repository.storage.ensureModelsDir()
        File(repository.modelsDir, "custom.tiny.bin").writeBytes(ByteArray(4))
        val plan = EnginePlanner.plan("custom.tiny", ComputeBackend.CPU, repository)
        assertEquals("custom.tiny", plan.spec.id)
        assertEquals("custom.tiny.bin", plan.modelFile.name)
    }

    @Test
    fun `missing model is a typed unavailability`() {
        val repository = repository()
        try {
            EnginePlanner.plan("base", ComputeBackend.AUTO, repository)
            fail("expected SpeechEngineUnavailableException")
        } catch (e: SpeechEngineUnavailableException) {
            assertTrue(e.message!!.contains("not installed"))
        }
    }

    @Test
    fun `catalog custom specs reject path traversal`() {
        try {
            ModelCatalog.custom(id = "../evil", fileName = "evil.bin")
            fail("expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
        }
    }
}
