package com.awbuilds.auraspend.data.ai

import android.content.Context
import android.util.Log

/**
 * Supplies the [OnDeviceClassifier] used by the classification flow.
 *
 * This is the single place a runtime is chosen, and the single place the app
 * degrades. When the native runtime cannot be linked it returns
 * [UnavailableClassifier] rather than throwing, so the app keeps working on
 * regex-only classification — the property that makes swapping runtimes safe.
 *
 * Cached permanently once built: a failed native link is a build/packaging fact,
 * not a transient one, so retrying it on every message would only add log noise.
 */
object LocalLlmProvider {

    private const val TAG = "LocalLlmProvider"

    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var delegate: OnDeviceClassifier? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    /**
     * True when a downloaded, usable model is present **and** the runtime actually
     * loaded. The second half matters: every runtime degrades to null internals
     * instead of throwing, so a runtime whose native session, vocabulary or
     * centroid asset failed to load still builds successfully. Asking
     * [OnDeviceClassifier.isOperational] is what keeps "ready" from being a lie.
     */
    fun isReady(context: Context): Boolean =
        ModelConstants.isDownloaded(context) && get().isOperational

    /** Returns the shared classifier, building it lazily. Never null. */
    fun get(): OnDeviceClassifier {
        delegate?.let { return it }
        synchronized(this) {
            delegate?.let { return it }
            val ctx = appContext
            if (ctx == null) {
                Log.w(TAG, "LocalLlmProvider used before init()")
                return UnavailableClassifier
            }
            val classifier = build(ctx)
            delegate = classifier
            return classifier
        }
    }

    private fun build(context: Context): OnDeviceClassifier = try {
        val assets = context.assets.open(ModelConstants.CENTROIDS_ASSET_NAME)
        val classifier = EmbeddingClassifier(
            modelFile = ModelConstants.modelFile(context),
            tokenizerDir = ModelConstants.baseModelsDir(context),
            centroidsAsset = assets
        )
        if (classifier.isOperational) {
            Log.i(TAG, "on-device classifier ready: ${classifier.id}")
        } else {
            // The model file exists but the runtime could not assemble itself —
            // typically a centroid asset or vocabulary that failed to decode. The
            // app keeps working on regex, but readiness must not claim otherwise.
            Log.w(
                TAG,
                "On-device runtime loaded but is not operational " +
                    "(session=${classifier.hasSession}, tokenizer=${classifier.hasTokenizer}, " +
                    "centroids=${classifier.hasCentroids}); " +
                    "falling back to regex-only classification"
            )
        }
        classifier
    } catch (e: UnsatisfiedLinkError) {
        Log.w(TAG, "Native runtime unavailable; using regex fallback", e)
        UnavailableClassifier
    } catch (e: Throwable) {
        Log.w(TAG, "Failed to initialise on-device runtime; using regex fallback", e)
        UnavailableClassifier
    }

    /** Test seam: swap the runtime without touching production wiring. */
    internal fun overrideForTest(classifier: OnDeviceClassifier?) {
        delegate = classifier
    }

    /**
     * Test seam: return the provider to a just-started state. The provider is a
     * process-wide object, so without this one test's `init()`/delegate would
     * leak into the next one and the "used before init" case could not be
     * written at all.
     */
    internal fun resetForTest() {
        synchronized(this) {
            appContext = null
            delegate = null
        }
    }
}
