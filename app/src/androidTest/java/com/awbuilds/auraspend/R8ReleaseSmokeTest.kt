package com.awbuilds.auraspend

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.awbuilds.auraspend.data.ai.EmbeddingClassifier
import com.awbuilds.auraspend.data.ai.ModelConstants
import com.awbuilds.auraspend.data.local.AppDatabase
import com.awbuilds.auraspend.data.local.entities.TransactionEntity
import java.nio.FloatBuffer
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Device tests for the JNI and Room paths — the two that cannot be exercised on
 * the JVM.
 *
 * Run with:
 * ```
 * ./gradlew :app:connectedDevDebugAndroidTest
 * ```
 *
 * ## What this does and does not cover
 *
 * This runs against the **debug** build. It covers the native library load, a
 * real encoder inference, and Room's generated code on a real device — none of
 * which Robolectric can do.
 *
 * It deliberately does **not** claim to cover R8. Targeting a minified build
 * type needs `testBuildType`, and that flag renames the *unit* test task too:
 * `testDevDebugUnitTest` disappears and is replaced by
 * `testDevBenchmarkUnitTest`, which would run the whole unit-test suite against
 * a shrunk build. That trades the documented gate and CI for nothing, so it was
 * reverted.
 *
 * The R8 question is therefore answered statically instead, by
 * `tools/verify_r8_release.sh`, which checks the release `mapping.txt` for the
 * classes ONNX Runtime resolves by name at `JNI_OnLoad`. That is
 * deterministic, needs no device, and is a stronger answer to the specific R8
 * risk than "the app launched and nothing obvious broke".
 *
 * ## The assertion that matters
 *
 * The obvious version of a test like this — "nothing threw" — would pass even
 * if the encoder were completely dead. `LocalLlmProvider.build()` catches
 * `UnsatisfiedLinkError` and every `Throwable`, logs, and returns
 * `UnavailableClassifier`, and `EmbeddingClassifier` routes each of its
 * initialisation steps through `boundaryOrNull`. So a total JNI failure
 * degrades to the regex classifier *silently*: no crash, no exception, a
 * working-looking app that has quietly lost its AI path.
 *
 * Every test below therefore asserts that work actually **happened** — a
 * non-null extraction, a populated probability — rather than that it didn't
 * crash. `assertNotNull` on the result is the load-bearing line.
 *
 * ## Model provisioning
 *
 * The encoder weights are downloaded at runtime, not bundled, so a fresh
 * install has no model and the test would have nothing to run. The test
 * provisions it itself, over the same URL and against the same pinned SHA-256
 * digests that `ModelDownloadManager` uses, so it exercises the real
 * download-and-verify path rather than trusting a sideloaded file. If the
 * download or the digest check fails, the test **fails loudly** rather than
 * skipping: a skip would be indistinguishable from the silent degradation this
 * class exists to catch.
 */
@RunWith(AndroidJUnit4::class)
class R8ReleaseSmokeTest {

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun provisionModel() {
        val model = ModelConstants.modelFile(context)
        val tokenizer = ModelConstants.tokenizerFile(context)

        if (ModelConstants.isDownloaded(context)) {
            assertTrue(
                "Existing model failed its size floor — delete the app's files dir and re-run",
                model.length() >= ModelConstants.MIN_SIZE_BYTES
            )
            return
        }

        fetchVerified(ModelConstants.MODEL_URL, model, ModelConstants.EXPECTED_SHA256, "model")
        fetchVerified(
            ModelConstants.TOKENIZER_URL,
            tokenizer,
            ModelConstants.TOKENIZER_SHA256,
            "tokenizer"
        )
        assertTrue("Model still not reported as downloaded", ModelConstants.isDownloaded(context))
    }

    /**
     * Downloads [url] to [target] and fails unless the SHA-256 matches [expected].
     *
     * Mirrors `ModelDownloadManager`, including the fail-closed behaviour: a
     * digest mismatch deletes the file rather than leaving a corrupt artifact
     * where the next run would find it "present".
     */
    private fun fetchVerified(url: String, target: java.io.File, expected: String, label: String) {
        val partial = java.io.File(target.parentFile, target.name + ".part")
        val connection = (java.net.URL(url).openConnection() as java.net.HttpURLConnection)
        try {
            connection.connectTimeout = 30_000
            connection.readTimeout = 120_000
            connection.instanceFollowRedirects = true
            assertEquals("HTTP status for $label", 200, connection.responseCode)

            val digest = java.security.MessageDigest.getInstance("SHA-256")
            connection.inputStream.use { input ->
                partial.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        digest.update(buffer, 0, read)
                        output.write(buffer, 0, read)
                    }
                }
            }
            val actual = digest.digest().joinToString("") { "%02x".format(it) }
            if (actual != expected) {
                partial.delete()
                fail(
                    "$label digest mismatch — expected $expected but the download hashed " +
                        "to $actual. Failing closed rather than running an unverified model."
                )
            }
            assertTrue("Could not move $label into place", partial.renameTo(target))
        } finally {
            connection.disconnect()
        }
    }

    /**
     * The JNI surface itself. `OrtEnvironment.getEnvironment()` is what triggers
     * `System.loadLibrary` and then `JNI_OnLoad` → `RegisterNatives`, binding
     * every native method in the runtime by class and method name. If R8 had
     * renamed the ONNX classes, `FindClass` inside `JNI_OnLoad` would fail and
     * this would surface as `UnsatisfiedLinkError` or `NoClassDefFoundError`.
     *
     * Tensor creation is included because marshalling crosses into native code
     * with a different signature than plain calls do.
     */
    @Test
    fun onnxRuntimeNativeLibraryLoads() {
        val env = OrtEnvironment.getEnvironment()
        assertNotNull("OrtEnvironment.getEnvironment() returned null", env)

        val tensor = OnnxTensor.createTensor(
            env,
            FloatBuffer.wrap(floatArrayOf(1f, 2f, 3f)),
            longArrayOf(1, 3)
        )
        assertNotNull("tensor creation failed — native marshalling is broken", tensor)
        tensor.close()
    }

    /**
     * End-to-end inference through the shipped runtime.
     *
     * This is the test that would have caught a rename-breaking JNI strip: it
     * builds the real [EmbeddingClassifier] exactly as `LocalLlmProvider` does,
     * against the same bundled centroid asset, and runs a real bank SMS
     * through it.
     */
    @Test
    fun encoderProducesAnExtractionUnderMinification() = runBlocking {
        val modelFile = ModelConstants.modelFile(context)
        assertTrue(
            "Model absent at ${modelFile.absolutePath} after provisioning",
            modelFile.exists()
        )
        assertTrue("Tokenizer absent", ModelConstants.tokenizerFile(context).exists())

        val classifier = EmbeddingClassifier(
            modelFile = modelFile,
            tokenizerDir = ModelConstants.baseModelsDir(context),
            centroidsAsset = context.assets.open("auraspend_embedding.bin")
        )
        try {
            val extraction = classifier.extract(
                "Rs 450.00 debited from your account at SWIGGY on 12 Sep 2026. UPI ref 4471882.",
                mapOf("cat_food" to "Food & Dining", "cat_transport" to "Transport")
            )

            // The load-bearing assertion. A null here means the session, the
            // centroids or the tokenizer failed to initialise — and production
            // would have swallowed it and fallen back to regex.
            assertNotNull(
                "EmbeddingClassifier returned null. The encoder silently degraded — " +
                    "in a minified build the usual cause is R8 breaking JNI. " +
                    "Check that AGP's default proguard-android-optimize.txt (with its " +
                    "native <methods> rule) is still applied.",
                extraction
            )

            // A centroid runtime has no merchant, and reports its type and
            // category; it must not fabricate a merchant string.
            assertNotNull(
                "encoder must populate isTransactionProbability",
                extraction!!.isTransactionProbability
            )
            assertNotNull("encoder should report a transaction type", extraction.type)
        } finally {
            classifier.close()
        }
    }

    /**
     * Room's generated code under minification.
     *
     * KSP-generated DAOs and entities are ordinary R8 inputs, and this is the
     * cheapest way to prove the generated classes survived: if the DAO
     * implementation had been stripped or its `_Impl` renamed, this read would
     * return empty rather than throw.
     */
    @Test
    fun roomRoundTripsUnderMinification() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val dao = db.transactionDao()
            dao.insertTransaction(
                TransactionEntity(
                    id = "r8-smoke-1",
                    amount = 450.0,
                    categoryId = "cat_food",
                    note = "R8 smoke test",
                    dateTimestamp = 1_757_000_000_000L,
                    type = "EXPENSE"
                )
            )
            val rows = dao.getAllTransactions().first()
            assertEquals("expected exactly one row back from Room", 1, rows.size)
            assertEquals("round-tripped amount differs", 450.0, rows[0].amount, 0.001)
        } finally {
            db.close()
        }
    }
}
