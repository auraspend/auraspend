package com.awbuilds.auraspend.data.ai

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Decodes the **real** bundled centroid asset, the way the runtime does.
 *
 * This test exists because nothing did. `auraspend_embedding.bin` was written
 * little-endian by `tools/build_embedding_asset.py` and read big-endian by
 * `EmbeddingClassifier`, so the dimension decoded as `0x80010000`, the metadata
 * slice ran past the end of the 23 KB file, the boundary swallowed the failure
 * into `model = null`, and the flagship on-device encoder returned null for every
 * message in production — while the readiness UI and the log both said it was
 * ready. Two things made that invisible:
 *
 *  - the Python tool's own round-trip check verified its output against its own
 *    little-endian reader, never against the Kotlin one, and
 *  - the "shipped encoder" accuracy eval replays a *recorded* JSON report, so it
 *    never touched the asset either.
 *
 * So this asserts the bytes, not the behaviour: magic, dimensions, group counts,
 * category ids, unit-length vectors, and the pinned digest.
 */
class EmbeddingClassifierAssetTest {

    private val asset: File by lazy {
        val candidates = listOf(
            File("src/main/assets/${ModelConstants.CENTROIDS_ASSET_NAME}"),
            File("app/src/main/assets/${ModelConstants.CENTROIDS_ASSET_NAME}")
        )
        candidates.firstOrNull { it.exists() }
            ?: error(
                "centroid asset not found; unit tests run with the module directory as " +
                    "the working directory, tried ${candidates.map { it.absolutePath }}"
            )
    }

    @Test
    fun `the bundled asset matches its pinned digest`() {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(asset.readBytes())
            .joinToString("") { "%02x".format(it) }

        assertEquals(
            "auraspend_embedding.bin changed. If the asset was regenerated on purpose, " +
                "verify EmbeddingCentroidCodec decodes the new file (this test does exactly " +
                "that) and then update ModelConstants.CENTROIDS_SHA256.",
            ModelConstants.CENTROIDS_SHA256,
            digest
        )
    }

    @Test
    fun `the real asset decodes to the pinned centroid layout`() {
        val centroids = EmbeddingCentroidCodec.decode(asset.readBytes())

        assertEquals("embedding dimension", 384, centroids.dim)
        assertEquals("transaction gate centroids", 2, centroids.transaction.size)
        assertEquals("type gate centroids", 2, centroids.type.size)
        assertEquals("category gate centroids", centroids.categoryIds.size, centroids.category.size)

        // Pinned by id, not just by count: a regeneration that silently drops or
        // renames a category would otherwise mis-file every message in it.
        assertEquals(
            listOf(
                "cat_food", "cat_grocery", "cat_shopping", "cat_transport",
                "cat_subscription", "cat_healthcare", "cat_education", "cat_bills",
                "cat_transfer", "cat_salary", "cat_other"
            ),
            centroids.categoryIds
        )

        // Every category the encoder can emit must be a real category, or the
        // fusion layer files transactions into an id nothing renders.
        val known = com.awbuilds.auraspend.data.classification.defaultCategories.map { it.id }
        assertTrue(
            "asset categories that are not in the app's category set: " +
                centroids.categoryIds.filterNot { it in known },
            centroids.categoryIds.all { it in known }
        )

        // The build tool L2-normalises, which is what makes the dot product in
        // `FloatArray.cosine` a cosine similarity. An un-normalised regeneration
        // would leave the argmax plausible-looking and the scores meaningless.
        val vectors = centroids.transaction + centroids.type + centroids.category
        vectors.forEachIndexed { index, vector ->
            val norm = kotlin.math.sqrt(vector.fold(0f) { acc, v -> acc + v * v })
            assertTrue(
                "centroid #$index is not a unit vector (norm=$norm); " +
                    "tools/build_embedding_asset.py must L2-normalise every vector",
                kotlin.math.abs(norm - 1f) < 1e-4f
            )
        }
    }

    @Test
    fun `a big-endian header is rejected instead of silently decoded`() {
        // The exact regression. A file written big-endian is not a valid asset:
        // the reader must fail loudly (and `LocalLlmProvider` then reports the
        // runtime as not operational) rather than decode dim = 0x80010000.
        val body = asset.readBytes()
        val bigEndian = body.copyOf()
        ByteBuffer.wrap(bigEndian).order(ByteOrder.BIG_ENDIAN).putInt(8, 384)

        val error = assertThrows(IllegalArgumentException::class.java) {
            EmbeddingCentroidCodec.decode(bigEndian)
        }
        assertNotNull(error.message)
    }

    @Test
    fun `a truncated asset is rejected`() {
        val body = asset.readBytes()
        assertThrows(IllegalArgumentException::class.java) {
            EmbeddingCentroidCodec.decode(body.copyOf(body.size / 2))
        }
        assertThrows(IllegalArgumentException::class.java) {
            EmbeddingCentroidCodec.decode(body.copyOf(20))
        }
    }

    @Test
    fun `a wrong magic is rejected`() {
        val body = asset.readBytes()
        body[0] = 'B'.code.toByte()
        assertThrows(IllegalArgumentException::class.java) {
            EmbeddingCentroidCodec.decode(body)
        }
    }
}
