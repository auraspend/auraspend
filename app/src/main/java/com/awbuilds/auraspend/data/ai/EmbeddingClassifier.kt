package com.awbuilds.auraspend.data.ai

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import com.awbuilds.auraspend.core.AuraLog
import com.awbuilds.auraspend.core.boundary
import com.awbuilds.auraspend.core.boundaryOrNull
import com.awbuilds.auraspend.domain.model.TransactionType
import java.io.File
import java.nio.LongBuffer
import org.json.JSONObject

/**
 * Sentence-encoder classifier: nearest-centroid over cosine similarity.
 *
 * This is the seam's first implementation that is not a text generator, and that
 * difference is the whole point. Five runtimes were measured against the golden
 * corpus before it, and every generative one failed the same way — it emitted a
 * well-formed `is_transaction: false` for real transactions, and
 * `AiSignalFusion` turns that into a *deletion*, not a mis-filing:
 *
 * | runtime             | size   | exact  | false vetoes |
 * |---------------------|--------|--------|--------------|
 * | SmolLM2-135M        | 100 MB | 29.2 % | 43 / 46      |
 * | FunctionGemma-270M  | 241 MB | 24.6 % |  5 / 46      |
 * | Qwen2.5-0.5B        | 468 MB | 29.2 % |  8 / 46      |
 * | MiniLM-L6 (this)    | 22 MB  | 18.5 % |  1 / 46      |
 *
 * An encoder cannot express that failure. It emits no text, so there is no
 * output in which a spurious verdict can be fabricated — only a similarity
 * score, which is low honestly rather than high falsely. It is also 20x smaller
 * than the model it replaces and runs in single-digit milliseconds.
 *
 * The calibrated score falls out of the architecture rather than being requested:
 * [isTransactionProbability] is a softmax over the transaction centroids, which
 * is the field that has existed since the seam was reshaped and that no runtime
 * before this could populate honestly.
 *
 * The verdict is used for *filling gaps only*. `AiSignalFusion` takes the regex
 * result on every conflict, so this can improve a category the parser left blank
 * and cannot corrupt one it resolved.
 */
class EmbeddingClassifier(
    private val modelFile: File,
    private val tokenizerDir: File,
    private val centroidsAsset: java.io.InputStream
) : OnDeviceClassifier {

    override val id: String = "minilm-centroid"

    private val env: OrtEnvironment? = boundaryOrNull(TAG) { OrtEnvironment.getEnvironment() }

    private val session: OrtSession? = boundaryOrNull(TAG) {
        // Level 2 (REQUIRED): ONNX Runtime throws if a graph input is not
        // supplied, and the failure would otherwise surface as a crash on the
        // first message rather than a clean "no opinion" from the regex layer.
        val e = env ?: return@boundaryOrNull null
        if (!modelFile.exists()) return@boundaryOrNull null
        e.createSession(modelFile.absolutePath, OrtSession.SessionOptions())
    }

    private val vocab: Map<String, Int> = boundaryOrNull(TAG) { readVocab() } ?: emptyMap()

    private val model: EmbeddingCentroids? = boundaryOrNull(TAG) {
        centroidsAsset.use { EmbeddingCentroidCodec.decode(it.readBytes()) }
    }

    private val tokenizer: BertTokenizer? = boundaryOrNull(TAG) {
        if (vocab.isEmpty()) null else BertTokenizer(vocab)
    }

    /**
     * True when every piece the encoder needs is actually loaded: the native
     * session, the tokenizer and the centroid asset.
     *
     * Exists because each of those degrades to null on failure — deliberately,
     * so a broken runtime can never crash the app — which used to be
     * indistinguishable from "ready". A byte-order mismatch in the centroid
     * reader made [extract] return null for every message while
     * `LocalLlmProvider` logged "on-device classifier ready" and the Settings
     * screen reported the model as available. [LocalLlmProvider.isReady] now
     * asks this, so the two can no longer disagree.
     */
    override val isOperational: Boolean
        get() = session != null && tokenizer != null && model != null

    /** Diagnostics only: whether the ONNX session loaded. */
    val hasSession: Boolean get() = session != null

    /** Diagnostics only: whether the bundled centroid asset decoded. */
    val hasCentroids: Boolean get() = model != null

    /** Diagnostics only: whether the vocabulary parsed into a tokenizer. */
    val hasTokenizer: Boolean get() = tokenizer != null

    override suspend fun extract(
        smsBody: String,
        categoryIdByName: Map<String, String>
    ): SmsExtraction? {
        val s = session ?: return null
        val m = model ?: return null
        val tok = tokenizer ?: return null
        val e = env ?: return null

        val vector = encode(e, s, tok, smsBody) ?: return null

        // Gate 1 — is this a transaction? The one decision this runtime must not
        // be trusted with alone, so the result is reported as a probability and
        // `AiSignalFusion` decides what to do with it.
        val txnSims = vector.cosine(m.transaction)
        val pTransaction = txnSims.softmax(TEMPERATURE)[1]
        val isTransaction = pTransaction >= TRANSACTION_FLOOR

        // Gate 2 — income or expense, only for messages the first gate accepted.
        val typeIdx = vector.cosine(m.type).argmax()
        val type = when (typeIdx) {
            0 -> TransactionType.INCOME
            else -> TransactionType.EXPENSE
        }

        // Gate 3 — category. An expense picks from the user's real category set
        // so a renamed or user-created category is honoured rather than mapped
        // back through a hard-coded id.
        val catIdx = vector.cosine(m.category).argmax()
        val category = m.categoryIds.getOrNull(catIdx)

        return SmsExtraction(
            // A null is honest here: below the floor, the runtime does not have
            // an opinion, and `AiSignalFusion` then keeps the regex verdict.
            isTransaction = isTransaction,
            type = type,
            category = category,
            // Deliberately null. A centroid model has no notion of a merchant
            // string, and inventing one from the nearest centroid's label would
            // be a fabricated value shown to the user as fact.
            merchant = null,
            isSubscription = category == CATEGORY_SUBSCRIPTION,
            rawModelOutput = null,
            confidence = pTransaction,
            // Confidence in the verdict actually emitted, not in the transaction
            // class. `AiSignalFusion` reads this as "how sure are you that your
            // answer is right", so reporting the transaction-class probability on
            // a *negative* verdict made the 0.9 discard gate arithmetically
            // unreachable: pTransaction is below 0.5 whenever isTransaction is
            // false. The gate was dead code, and the measured 0-false-veto result
            // came from it never firing rather than from the encoder being
            // trustworthy. See `SmsExtraction.verdictConfidence`.
            isTransactionProbability = SmsExtraction.verdictConfidence(isTransaction, pTransaction)
        )
    }

    private fun encode(
        env: OrtEnvironment,
        session: OrtSession,
        tok: BertTokenizer,
        text: String
    ): FloatArray? = boundaryOrNull(TAG) {
        val ids = tok.encode(text, MAX_TOKENS)
        val shape = longArrayOf(1, ids.size.toLong())
        val inputIds = OnnxTensor.createTensor(env, LongBuffer.wrap(LongArray(ids.size) { ids[it].toLong() }), shape)
        val maskIds = OnnxTensor.createTensor(
            env,
            LongBuffer.wrap(LongArray(ids.size) { 1L }),
            shape
        )
        // MiniLM's exported graph carries the full BERT input set, and ONNX
        // Runtime fails the *run* — not the session — when one is missing:
        // "Missing Input: token_type_ids". All zeros is the standard
        // single-segment encoding. This surfaced on the first inference ever
        // run on a device (2026-10-13, behind the tokenizer fix that finally
        // reached `encode`); a model swap must re-run the on-device smoke.
        val typeIds = OnnxTensor.createTensor(
            env,
            LongBuffer.wrap(LongArray(ids.size) { 0L }),
            shape
        )
        try {
            val results = session.run(
                mapOf(
                    "input_ids" to inputIds,
                    "attention_mask" to maskIds,
                    "token_type_ids" to typeIds
                )
            )
            // results[0] is OnnxValue; cast rather than assume, so a graph whose
            // first output is not a float tensor fails as a logged boundary
            // error instead of a ClassCastException on the caller's thread.
            val output = results[0] as? OnnxTensor
                ?: return@boundaryOrNull null
            val flat = output.floatBuffer
            val hidden = FloatArray(flat.capacity())
            flat.get(hidden)
            output.close()

            // Mean-pool over real tokens, then L2-normalise so a dot product is
            // cosine similarity. This must match `tools/build_embedding_asset.py`
            // exactly: a different pooling would silently misalign the shipped
            // centroids against the query vector, and the symptom would be
            // plausible-looking but meaningless scores rather than an error.
            val width = hidden.size / ids.size
            val pooled = FloatArray(width)
            for (t in 0 until ids.size) {
                val base = t * width
                for (j in 0 until width) pooled[j] += hidden[base + j]
            }
            for (j in 0 until width) pooled[j] /= ids.size
            var norm = 0f
            for (v in pooled) norm += v * v
            norm = kotlin.math.sqrt(norm)
            if (norm < 1e-9f) return@boundaryOrNull null
            for (i in pooled.indices) pooled[i] /= norm
            pooled
        } finally {
            inputIds.close(); maskIds.close(); typeIds.close()
        }
    }

    /** WordPiece tokenizer over the ONNX model's `tokenizer.json` vocabulary. */
    private class BertTokenizer(private val vocab: Map<String, Int>) {
        fun encode(text: String, maxTokens: Int): IntArray {
            val out = ArrayList<Int>(maxTokens)
            out.add(vocab["[CLS]"] ?: 101)
            val normalized = text.lowercase()
                .replace(Regex("[^a-z0-9\\s]"), " ")
                .split(Regex("\\s+"))
                .filter { it.isNotEmpty() }
            for (word in normalized) {
                if (out.size >= maxTokens - 1) break
                var start = 0
                val sub = StringBuilder()
                while (start < word.length) {
                    var end = word.length
                    var found = -1
                    while (start < end) {
                        val piece = if (start > 0) "##" + word.substring(start, end) else word.substring(start, end)
                        val id = vocab[piece]
                        if (id != null) { found = id; sub.append(piece); break }
                        end--
                    }
                    if (found == -1) { sub.setLength(0); break }
                    out.add(found)
                    start = end
                }
            }
            out.add(vocab["[SEP]"] ?: 102)
            return out.toIntArray()
        }
    }

    private fun readVocab(): Map<String, Int> = readWordPieceVocab(tokenizerDir)

    override fun close() {
        boundary(TAG, Unit) { session?.close() }
    }

    private companion object {
        const val TAG = "EmbeddingClassifier"
        const val MAX_TOKENS = 256
        const val CATEGORY_SUBSCRIPTION = "cat_subscription"

        /**
         * Softmax temperature. Only matters for the *reported* probability, not
         * the argmax, so this is a display/routing knob rather than an accuracy
         * one — the measured per-field numbers are identical from 0.02 to 0.15.
         */
        const val TEMPERATURE = 0.05f

        /**
         * Below this the runtime answers "not a transaction", but reports it with
         * the confidence of that answer (`SmsExtraction.verdictConfidence`), and
         * `AiSignalFusion` discards the row only above 0.9 — so a low score here
         * still leaves the regex verdict in place. Set from the measured
         * separation: the distributions overlap, so the honest reading of a score
         * near the middle is "I do not know" rather than a confident denial.
         */
        const val TRANSACTION_FLOOR = 0.5f
    }
}

/** Cosine similarity against each centroid. Vectors are already L2-normalised. */
private fun FloatArray.cosine(centroids: Array<FloatArray>): FloatArray =
    FloatArray(centroids.size) { i ->
        var dot = 0f
        for (j in indices) dot += this[j] * centroids[i][j]
        dot
    }

private fun FloatArray.softmax(temperature: Float): FloatArray {
    val scaled = FloatArray(size) { this[it] / temperature }
    val max = scaled.max()
    var sum = 0f
    for (v in scaled) sum += kotlin.math.exp((v - max))
    return FloatArray(size) { kotlin.math.exp((scaled[it] - max)) / sum }
}

private fun FloatArray.argmax(): Int {
    var best = 0
    for (i in 1 until size) if (this[i] > this[best]) best = i
    return best
}

/**
 * Reads the WordPiece vocabulary of the downloaded `tokenizer.json` into a
 * wordpiece -> id map.
 *
 * Takes the directory the model lives in and reads
 * [ModelConstants.TOKENIZER_FILE_NAME] — the exact file
 * `ModelDownloadManager` writes. The two names were once different
 * ("tokenizer.json" here, `minilm-tokenizer.json` there), so on every device
 * the vocabulary failed the `require` below, the boundary caught it as
 * "Boundary failed, using fallback", and the encoder stayed inert behind a
 * caught exception (found in a production build's logs, 2026-10-13 — the
 * same inert-in-production class as the centroid byte-order bug, one layer
 * down). Single-sourced through the constant so they cannot drift again;
 * `EmbeddingClassifierTokenizerTest` pins the contract.
 */
internal fun readWordPieceVocab(dir: File): Map<String, Int> {
    val file = File(dir, ModelConstants.TOKENIZER_FILE_NAME)
    require(file.exists()) { "tokenizer vocabulary missing at ${file.absolutePath}" }
    val root = JSONObject(file.readText())
    val model = root.getJSONObject("model")
    val vocabNode = model.getJSONObject("vocab")
    val map = HashMap<String, Int>(vocabNode.length() * 2)
    val keys = vocabNode.keys()
    while (keys.hasNext()) {
        val k = keys.next()
        map[k] = vocabNode.getInt(k)
    }
    return map
}
