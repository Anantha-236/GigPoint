package com.example.gigpoint.voice

import android.content.res.AssetManager

object WhisperNative {

    init {
        System.loadLibrary(
            "dhwani_whisper"
        )
    }

    external fun initContextFromAsset(
        assetManager: AssetManager,
        assetPath: String
    ): Long

    external fun freeContext(
        contextPtr: Long
    )

    external fun fullTranscribe(
        contextPtr: Long,
        numThreads: Int,
        audioData: FloatArray,
        language: String,
        initialPrompt: String
    ): Int

    external fun getSegmentCount(
        contextPtr: Long
    ): Int

    external fun getSegmentText(
        contextPtr: Long,
        index: Int
    ): String

    /**
     * Primary language selected by Whisper for the latest transcription.
     * Mixed-language speech can still contain words from several languages;
     * Whisper exposes one primary detected language for the segment/context.
     */
    external fun getDetectedLanguage(
        contextPtr: Long
    ): String
}
