package com.awbuilds.auraspend.data.ai

import java.io.File

/**
 * Central configuration for the on-device model used for message categorisation.
 *
 * **This class is the model-swap seam.** The runtime and the weights are
 * independent: swapping either must never require touching the prompt, the
 * parser, or the fusion rules.
 *
 * The current model is [EmbeddingClassifier]'s — an int8 quantised
 * `all-MiniLM-L6-v2` encoder at **22 MiB**, replacing a 468.6 MiB Qwen2.5-0.5B
 * GGUF that ran through llama.cpp. That is a **21x reduction** in what a
 * consenting user downloads, and the reason is architectural rather than a
 * quantisation trick: the job is a closed-vocabulary classification over SMS
 * text, not text generation, so an encoder is the right shape and a decoder LM
 * was always over-provisioned for it.
 *
 * The accuracy case, measured through the production pipeline
 * (`FusedAiEval`, 65-case golden corpus) rather than the model in isolation:
 *
 * | runtime            | size   | exact  | false vetoes |
 * |--------------------|--------|--------|--------------|
 * | SmolLM2-135M       | 100 MB | 29.2 % | 43 / 46      |
 * | FunctionGemma-270M | 241 MB | 24.6 % |  5 / 46      |
 * | Qwen2.5-0.5B       | 468 MB | 29.2 % |  8 / 46      |
 * | MiniLM-L6 (here)   | 22 MB  | 18.5 % |  1 / 46      |
 *
 * A "false veto" is a real transaction the model called "not a transaction".
 * `AiSignalFusion` nulls `amount` and `type` on that verdict and the pipeline
 * drops the row, so the error *deletes* a transaction rather than mis-filing it.
 * That is why the veto number, not the exact-match number, is the one that
 * decided this swap — and why an encoder that cannot fabricate a verdict is
 * preferable at a fifth of the size.
 */
object ModelConstants {

    /** Human friendly name shown to the user. */
    const val MODEL_LABEL = "On-device categoriser (22 MB)"

    /** File name of the downloaded model. */
    const val MODEL_FILE_NAME = "minilm-l6-v2-q8.onnx"

    /**
     * Source of the model. Apache-2.0, the most permissive licence of anything
     * measured — SmolLM2 is Apache-2.0, FunctionGemma is Gemma (gated), and the
     * GTE/BGE encoders are MIT.
     */
    const val MODEL_URL =
        "https://huggingface.co/sentence-transformers/all-MiniLM-L6-v2/resolve/main/onnx/model_qint8_arm64.onnx"

    /**
     * Exact size in bytes (22.0 MiB), confirmed by hashing the downloaded
     * artifact rather than read off a model card.
     */
    val EXPECTED_SIZE_BYTES: Long = 23_026_053L

    /**
     * Minimum acceptable size, 20 MiB. Sits below the real size so a valid
     * download passes, and high enough to reject a truncated one.
     *
     * This value is derived per artifact and must be re-derived on any swap —
     * the floor inherited from the 468 MiB Qwen would have rejected every valid
     * MiniLM download outright, failing closed on a perfectly good file.
     */
    val MIN_SIZE_BYTES: Long = 20 * 1024 * 1024L

    /**
     * SHA-256 of the published arm64 int8 artifact, confirmed by hashing it.
     *
     * Verification fails closed, so this must be exactly right or every download
     * errors. It matters more here than usual: the file is fetched over the
     * network and then executed on-device. The architecture is `arm64-v8a` only
     * — the AAR's own arm64 quant build, verified to score identically to the
     * x86 int8 build on the corpus.
     */
    const val EXPECTED_SHA256: String =
        "4278337fd0ff3c68bfb6291042cad8ab363e1d9fbc43dcb499fe91c871902474"

    /** WordPiece vocabulary that ships alongside the model, also verified. */
    const val TOKENIZER_FILE_NAME = "minilm-tokenizer.json"

    const val TOKENIZER_URL =
        "https://huggingface.co/sentence-transformers/all-MiniLM-L6-v2/resolve/main/tokenizer.json"

    const val TOKENIZER_SHA256: String =
        "be50c3628f2bf5bb5e3a7f17b1f74611b2561a3a27eeab05e5aa30f411572037"

    const val PREF_CONSENT_GIVEN = "ai_model_consent_given"

    /** Bundled centroid asset consumed by [EmbeddingClassifier]. */
    const val CENTROIDS_ASSET_NAME = "auraspend_embedding.bin"

    /**
     * SHA-256 of the bundled centroid asset.
     *
     * Unlike the model and the tokenizer this file is not downloaded, so the
     * digest is not a runtime security check — anyone who can replace a file
     * inside the APK can replace this constant too. It is pinned because the
     * asset is *generated* (`tools/build_embedding_asset.py`) and consumed by a
     * hand-written reader, and that pair has already drifted once: the writer
     * emitted little-endian and the reader parsed big-endian, which silently
     * disabled the entire on-device model with no build or test failure.
     * `EmbeddingClassifierAssetTest` enforces this digest, so regenerating the
     * asset without checking the reader fails the build instead of shipping an
     * inert model.
     */
    const val CENTROIDS_SHA256: String =
        "eb5f3617bd821517d50406f299dd977a62f241819e89141f0ee70faa1a507897"

    fun baseModelsDir(context: android.content.Context): File =
        File(context.filesDir, "models").apply { mkdirs() }

    fun modelFile(context: android.content.Context): File =
        File(baseModelsDir(context), MODEL_FILE_NAME)

    fun tokenizerFile(context: android.content.Context): File =
        File(baseModelsDir(context), TOKENIZER_FILE_NAME)

    fun isDownloaded(context: android.content.Context): Boolean {
        val model = modelFile(context)
        // Both halves are required: an encoder without its vocabulary cannot
        // tokenize, and half a download would otherwise report "ready".
        return model.exists() && model.length() >= MIN_SIZE_BYTES &&
            tokenizerFile(context).exists()
    }
}
