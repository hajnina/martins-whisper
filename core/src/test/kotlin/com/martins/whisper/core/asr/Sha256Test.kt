package com.martins.whisper.core.asr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class Sha256Test {

    @Test
    fun `known vector`() {
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            Sha256.hex("abc".toByteArray()),
        )
    }

    @Test
    fun `file hashing matches byte hashing`() {
        val dir = Files.createTempDirectory("e1-sha").toFile()
        try {
            val file = File(dir, "blob")
            val bytes = ByteArray(200_000) { (it % 251).toByte() }
            file.writeBytes(bytes)
            val expected = Sha256.hex(bytes)
            assertEquals(expected, Sha256.hex(file))
            assertTrue(Sha256.matches(file, expected.uppercase()))
            assertFalse(Sha256.matches(file, "0".repeat(64)))
        } finally {
            dir.deleteRecursively()
        }
    }
}
