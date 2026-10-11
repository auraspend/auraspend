package com.awbuilds.auraspend.data.ai

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The runtime-degradation and readiness-reporting safety properties.
 *
 * "Swap the runtime, degrade silently, keep working on regex" is what makes
 * every model change safe, and its readiness half is what the byte-order bug
 * (review finding #1) hid: `LocalLlmProvider` logged "on-device classifier
 * ready" and readiness UI agreed, while `extract()` returned null for every
 * message because the runtime had failed to assemble itself. `isReady` now
 * asks `isOperational`, and these tests pin the contract at the provider level
 * — including via the `overrideForTest` seam that previously had no callers.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = com.awbuilds.auraspend.TestApplication::class)
class LocalLlmProviderTest {

    private lateinit var context: Context

    /** Minimal stand-in so `isReady`'s boolean logic can be driven exactly. */
    private class FakeClassifier(
        private val operational: Boolean
    ) : OnDeviceClassifier {
        override val id: String = "fake"
        override val isOperational: Boolean get() = operational
        override suspend fun extract(
            smsBody: String,
            categoryIdByName: Map<String, String>
        ): SmsExtraction? = null
        override fun close() = Unit
    }

    @Before
    fun setUp() {
        LocalLlmProvider.resetForTest()
        context = ApplicationProvider.getApplicationContext()
        // Start from a clean model directory so "downloaded" state is explicit.
        ModelConstants.baseModelsDir(context).listFiles()?.forEach { it.delete() }
    }

    @After
    fun tearDown() {
        LocalLlmProvider.resetForTest()
    }

    private fun writeModelFiles() {
        // `isDownloaded` requires both halves; contents are irrelevant here
        // because the native runtime never runs on the JVM.
        ModelConstants.baseModelsDir(context).let { dir ->
            File(dir, ModelConstants.MODEL_FILE_NAME).apply {
                writeBytes(ByteArray(ModelConstants.MIN_SIZE_BYTES.toInt()))
            }
            File(dir, ModelConstants.TOKENIZER_FILE_NAME).apply { writeText("{}") }
        }
    }

    @Test
    fun `used before init, get degrades to UnavailableClassifier`() {
        val classifier = LocalLlmProvider.get()
        assertTrue(classifier is UnavailableClassifier)
    }

    @Test
    fun `a failed build is cached, not retried per message`() {
        LocalLlmProvider.init(context)
        val first = LocalLlmProvider.get()
        val second = LocalLlmProvider.get()
        assertTrue(first is UnavailableClassifier)
        assertTrue("a failed native link is a packaging fact, not a transient one", first === second)
    }

    @Test
    fun `a real build on the JVM degrades instead of throwing`() {
        LocalLlmProvider.init(context)
        // onnxruntime-android ships no JVM-loadable native library, so the
        // provider takes its degradation path — it must never reach the caller
        // as an UnsatisfiedLinkError.
        val classifier = runBlocking { LocalLlmProvider.get() }
        assertNotNull(classifier)
        assertTrue(classifier is UnavailableClassifier)
    }

    @Test
    fun `isReady is false when the model is not downloaded`() {
        LocalLlmProvider.overrideForTest(FakeClassifier(operational = true))
        assertFalse(LocalLlmProvider.isReady(context))
    }

    @Test
    fun `isReady is false when a downloaded model cannot run`() {
        // The #1 regression, pinned at the readiness level: model present, but
        // the runtime reported itself non-operational. A ready check that looks
        // only at the file is the lie that shipped.
        writeModelFiles()
        LocalLlmProvider.overrideForTest(FakeClassifier(operational = false))
        assertFalse(LocalLlmProvider.isReady(context))
    }

    @Test
    fun `isReady is true when the model is downloaded and operational`() {
        writeModelFiles()
        LocalLlmProvider.overrideForTest(FakeClassifier(operational = true))
        assertTrue(LocalLlmProvider.isReady(context))
    }

    @Test
    fun `an override is visible to get`() {
        val fake = FakeClassifier(operational = true)
        LocalLlmProvider.init(context)
        LocalLlmProvider.overrideForTest(fake)
        assertEquals(fake, LocalLlmProvider.get())
    }
}
