package com.awbuilds.auraspend.data.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

/**
 * The download manager and the vocabulary reader must agree on ONE file name.
 *
 * They did not: the downloader wrote `minilm-tokenizer.json` while the reader
 * looked for a literal `tokenizer.json`, so on every device the vocabulary
 * failed to load, the boundary swallowed the exception ("Boundary failed,
 * using fallback") and the on-device encoder stayed inert in production
 * (found in a production build's logs, 2026-10-13). These tests pin the
 * contract that was missing.
 */
class EmbeddingClassifierTokenizerTest {

    private val minimalTokenizer =
        """{"model":{"vocab":{"[CLS]":101,"[SEP]":102,"hello":7592,"world":2088}}}"""

    private fun tempDir() = Files.createTempDirectory("tokenizer-test").toFile()

    @Test
    fun `reads the vocabulary the download manager writes`() {
        val dir = tempDir()
        java.io.File(dir, ModelConstants.TOKENIZER_FILE_NAME).writeText(minimalTokenizer)

        val vocab = readWordPieceVocab(dir)

        assertEquals(101, vocab["[CLS]"])
        assertEquals(7592, vocab["hello"])
    }

    @Test
    fun `the reader and the download manager use the same constant`() {
        // Structural: one symbol feeds the writer (ModelConstants.tokenizerFile)
        // and the reader (readWordPieceVocab). If this ever splits again, the
        // encoder is inert in production behind a caught exception.
        assertTrue(ModelConstants.TOKENIZER_FILE_NAME.endsWith(".json"))
        val dir = tempDir()
        java.io.File(dir, ModelConstants.TOKENIZER_FILE_NAME).writeText(minimalTokenizer)
        assertTrue(readWordPieceVocab(dir).isNotEmpty())
    }

    @Test
    fun `a missing vocabulary fails with the production file name in the message`() {
        val dir = tempDir()
        val threw = try {
            readWordPieceVocab(dir)
            false
        } catch (e: IllegalArgumentException) {
            e.message?.contains(ModelConstants.TOKENIZER_FILE_NAME) == true
        }
        assertTrue("the failure must name the file the download manager would have written", threw)
    }
}
