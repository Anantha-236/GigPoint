package com.example.gigpoint.voice

import android.content.Context
import java.io.Closeable
import kotlin.math.max
import kotlin.math.min

class WhisperEngine(
    context: Context
) : Closeable {

    companion object {
        const val MODEL_ASSET =
            "models/ggml-tiny-q5_1.bin"
    }

    private val appContext =
        context.applicationContext

    @Volatile
    private var contextPtr: Long = 0L

    @Synchronized
    fun initialize() {
        if (contextPtr != 0L) {
            return
        }

        contextPtr =
            WhisperNative.initContextFromAsset(
                appContext.assets,
                MODEL_ASSET
            )

        check(contextPtr != 0L) {
            "Could not load $MODEL_ASSET"
        }
    }

    @Synchronized
    fun transcribe(
        samples: FloatArray,
        language: String
    ): String {

        initialize()

        require(samples.isNotEmpty()) {
            "No audio samples were provided."
        }

        val cpuCount =
            Runtime.getRuntime()
                .availableProcessors()

        // Tiny is small; avoid driving every CPU core and heating
        // low-cost phones unnecessarily.
        val threads =
            min(
                4,
                max(2, cpuCount - 2)
            )

        val rc =
            WhisperNative.fullTranscribe(
                contextPtr,
                threads,
                samples,
                language
            )

        check(rc == 0) {
            "Whisper transcription failed ($rc)."
        }

        val count =
            WhisperNative.getSegmentCount(
                contextPtr
            )

        return buildString {
            for (i in 0 until count) {
                append(
                    WhisperNative.getSegmentText(
                        contextPtr,
                        i
                    )
                )
                append(' ')
            }
        }.trim()
    }

    @Synchronized
    override fun close() {
        if (contextPtr != 0L) {
            WhisperNative.freeContext(
                contextPtr
            )
            contextPtr = 0L
        }
    }
}
