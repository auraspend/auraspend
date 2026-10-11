package com.awbuilds.auraspend.data.ai

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import org.json.JSONObject

/**
 * The nearest-centroid tables bundled in `assets/auraspend_embedding.bin`.
 *
 * Vectors are already L2-normalised by the build tool, so a dot product against
 * them is cosine similarity.
 */
internal class EmbeddingCentroids(
    val dim: Int,
    val transaction: Array<FloatArray>,
    val type: Array<FloatArray>,
    val category: Array<FloatArray>,
    val categoryIds: List<String>
) {
    /** Total vectors in the asset, used by the asset test to pin the layout. */
    val vectorCount: Int get() = transaction.size + type.size + category.size
}

/**
 * Decoder for the centroid asset written by `tools/build_embedding_asset.py`.
 *
 * Layout, all integers little-endian (the writer's `struct.pack("<I")` and the
 * native float order):
 *
 * ```
 * "AURAEMB1" | u32 dim | u32 metaLen | metaLen bytes of JSON
 *            | u8 count | count * dim float32   (transaction)
 *            | u8 count | count * dim float32   (type)
 *            | u8 count | count * dim float32   (category)
 * ```
 *
 * **The byte order here is not cosmetic.** This reader used to be big-endian
 * while the writer is little-endian, so a 384-dimensional asset decoded as
 * `dim = 0x80010000`, the metadata slice ran off the end of a 23 KB file, and
 * `EmbeddingClassifier` swallowed the failure into `model = null` — the
 * flagship on-device model then returned null for *every* message while the
 * readiness UI and the log both said it was ready. Nothing caught it: no test
 * decoded the asset, and the build tool's round-trip check validated its own
 * little-endian writer rather than this reader. `EmbeddingClassifierAssetTest`
 * now decodes the real bundled asset, and [EmbeddingClassifier.isOperational]
 * makes a decoding failure visible instead of silent.
 */
internal object EmbeddingCentroidCodec {

    const val MAGIC = "AURAEMB1"

    /**
     * No embedding this app would ever ship is wider than this. A corrupt header
     * must fail loudly rather than drive a multi-gigabyte allocation.
     */
    private const val MAX_DIM = 4096

    /** The real metadata blob is ~300 bytes; anything larger is corruption. */
    private const val MAX_META_BYTES = 1 shl 20

    /** Bytes per float32. */
    private const val FLOAT_BYTES = 4

    fun decode(bytes: ByteArray): EmbeddingCentroids {
        require(bytes.size >= MAGIC.length + 8) { "centroid asset truncated: ${bytes.size} bytes" }

        val magic = String(bytes, 0, MAGIC.length, StandardCharsets.UTF_8)
        require(magic == MAGIC) { "bad centroids magic: '$magic'" }

        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        buf.position(MAGIC.length)
        val dim = buf.int
        val metaLen = buf.int
        require(dim in 1..MAX_DIM) { "bad centroid dimension: $dim" }
        require(metaLen in 1..MAX_META_BYTES && buf.remaining() >= metaLen) {
            "bad centroid metadata length: $metaLen"
        }

        val metaBytes = ByteArray(metaLen)
        buf.get(metaBytes)
        val json = JSONObject(String(metaBytes, StandardCharsets.UTF_8))

        fun group(name: String): Array<FloatArray> {
            require(buf.hasRemaining()) { "centroid group '$name' is missing" }
            val count = buf.get().toInt() and 0xFF
            require(count > 0) { "centroid group '$name' is empty" }
            require(buf.remaining() >= count * dim * FLOAT_BYTES) {
                "centroid group '$name' truncated: ${buf.remaining()} bytes left for " +
                    "$count x $dim floats"
            }
            return Array(count) {
                val vector = FloatArray(dim)
                for (i in 0 until dim) vector[i] = buf.getFloat()
                vector
            }
        }

        val transaction = group("transaction")
        val type = group("type")
        val category = group("category")
        require(!buf.hasRemaining()) { "trailing bytes after the last centroid group" }

        val categoryIds = json.getJSONArray("category").let { arr ->
            List(arr.length()) { arr.getString(it) }
        }
        require(categoryIds.isNotEmpty()) { "centroid asset carries no category ids" }
        require(category.size == categoryIds.size) {
            "category centroid/id count mismatch: ${category.size} vs ${categoryIds.size}"
        }

        return EmbeddingCentroids(dim, transaction, type, category, categoryIds)
    }
}
