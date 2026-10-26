package com.martins.whisper.asr

import com.martins.whisper.core.ComputeBackend
import com.martins.whisper.core.asr.EngineState
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
    fun `unknown persisted id falls back to the default model`() {
        val repository = repository()
        repository.storage.ensureModelsDir()
        File(repository.modelsDir, "base.bin").writeBytes(ByteArray(4))
        val plan = EnginePlanner.plan("custom.tiny", ComputeBackend.CPU, repository)
        assertEquals("base", plan.spec.id)
        assertEquals("base.bin", plan.modelFile.name)
    }

    @Test
    fun `a persisted custom model resolves by its own id`() {
        val repository = repository()
        repository.storage.ensureModelsDir()
        File(repository.modelsDir, "custom-mine.bin").writeBytes(ByteArray(4))
        val custom = ModelCatalog.custom(id = "custom-mine", fileName = "mine.bin")
        val plan = EnginePlanner.plan("custom-mine", ComputeBackend.CPU, repository, listOf(custom))
        assertEquals(custom, plan.spec)
        assertEquals("custom-mine.bin", plan.modelFile.name)
    }

    @Test
    fun `missing model is a typed unavailability with a reason`() {
        val repository = repository()
        try {
            EnginePlanner.plan("base", ComputeBackend.AUTO, repository)
            fail("expected SpeechEngineUnavailableException")
        } catch (e: SpeechEngineUnavailableException) {
            assertEquals(EngineState.MODEL_MISSING, e.reason)
            assertTrue(e.message!!.contains("not installed"))
            assertTrue(e.message!!.contains("Settings"))
        }
    }
}
