package com.example.gigpoint.voice

import com.example.gigpoint.domain.Product

import android.content.Context
import java.io.Closeable
import kotlin.math.max
import kotlin.math.min

class WhisperEngine(
    context: Context
) : Closeable {

    companion object {

        /**
         * Prefer the stronger multilingual base model when present.
         * Fall back to the existing tiny multilingual Q5_1 model.
         */
        private val MODEL_CANDIDATES =
            listOf(
                "models/ggml-base-q5_1.bin",
                "models/ggml-tiny-q5_1.bin"
            )
    }

    private val appContext =
        context
            .applicationContext

    @Volatile
    private var contextPtr:
        Long =
        0L

    @Volatile
    private var loadedModel:
        String =
        ""

    @Synchronized
    fun initialize() {

        if (
            contextPtr !=
            0L
        ) {
            return
        }

        loadedModel =
            MODEL_CANDIDATES
                .firstOrNull {
                    assetExists(
                        it
                    )
                }
                ?: error(
                    "No Whisper model was found in app/src/main/assets/models."
                )

        contextPtr =
            WhisperNative
                .initContextFromAsset(
                    appContext.assets,
                    loadedModel
                )

        check(
            contextPtr !=
            0L
        ) {
            "Could not load $loadedModel"
        }
    }

    /**
     * language should normally be "auto" for DhwaniMitra because shopkeepers
     * can code-switch between English, Telugu and Hindi in one command.
     */
    @Synchronized
    fun transcribe(
        samples: FloatArray,
        language: String = "auto",
        initialPrompt: String = ""
    ): String {

        initialize()

        require(
            samples.isNotEmpty()
        ) {
            "No audio samples were provided."
        }

        val cpuCount =
            Runtime
                .getRuntime()
                .availableProcessors()

        val threads =
            min(
                4,
                max(
                    2,
                    cpuCount -
                        2
                )
            )

        val normalizedLanguage =
            language
                .trim()
                .lowercase()
                .ifBlank {
                    "auto"
                }

        val rc =
            WhisperNative
                .fullTranscribe(
                    contextPtr,
                    threads,
                    samples,
                    normalizedLanguage,
                    initialPrompt
                )

        check(
            rc ==
            0
        ) {
            "Whisper transcription failed ($rc)."
        }

        val count =
            WhisperNative
                .getSegmentCount(
                    contextPtr
                )

        return buildString {

            for (
                i in 0
                    until count
            ) {

                append(
                    WhisperNative
                        .getSegmentText(
                            contextPtr,
                            i
                        )
                )

                append(
                    ' '
                )
            }
        }
            .trim()
    }

    fun detectedLanguage():
        String {

        if (
            contextPtr ==
            0L
        ) {
            return ""
        }

        return WhisperNative
            .getDetectedLanguage(
                contextPtr
            )
            .trim()
    }

    fun loadedModelAsset():
        String =
        loadedModel

    /**
     * Product names are valuable decoding context because Whisper otherwise
     * tends to transform unfamiliar local brand names into common words.
     *
     * Keep this short. Whisper accepts an initial prompt but overly long
     * prompts can bias decoding too strongly.
     */
    fun buildInventoryPrompt(
        productNames: List<String>
    ): String {

        val uniqueNames =
            productNames
                .asSequence()
                .map {
                    it.trim()
                }
                .filter {
                    it.isNotBlank()
                }
                .distinct()
                .take(
                    24
                )
                .toList()

        val products =
            if (
                uniqueNames.isEmpty()
            ) {
                ""
            } else {
                " Products: " +
                    uniqueNames
                        .joinToString(
                            ", "
                        ) +
                    "."
            }

        return (
            "Inventory command. " +
            "Speech may be English, Telugu, Hindi, or mixed. " +
            "Common units: kg, gram, litre, bag, packet, carton, box, bottle, piece." +
            products
            )
            .take(
                420
            )
    }

    @Synchronized
    override fun close() {

        if (
            contextPtr !=
            0L
        ) {

            WhisperNative
                .freeContext(
                    contextPtr
                )

            contextPtr =
                0L

            loadedModel =
                ""
        }
    }

    private fun assetExists(
        path: String
    ): Boolean {

        return try {

            appContext
                .assets
                .open(
                    path
                )
                .use {
                    true
                }

        } catch (
            _: Exception
        ) {

            false
        }
    }
}
