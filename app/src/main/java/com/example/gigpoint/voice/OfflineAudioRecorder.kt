package com.example.gigpoint.voice

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import kotlin.math.max

class OfflineAudioRecorder {

    companion object {

        const val SAMPLE_RATE =
            16_000

        /**
         * Short push-to-talk commands are safer and significantly
         * easier for offline Whisper to understand in a shop.
         */
        private const val
            MAX_SECONDS =
            12
    }

    private val lock =
        Any()

    @Volatile
    private var recording =
        false

    private var audioRecord:
        AudioRecord? =
        null

    private var worker:
        Thread? =
        null

    private var noiseSuppressor:
        NoiseSuppressor? =
        null

    private var echoCanceler:
        AcousticEchoCanceler? =
        null

    private val samples =
        ArrayList<Short>(
            SAMPLE_RATE *
                5
        )

    @SuppressLint(
        "MissingPermission"
    )
    fun start() {

        if (
            recording
        ) {
            return
        }

        val minBuffer =
            AudioRecord
                .getMinBufferSize(
                    SAMPLE_RATE,
                    AudioFormat
                        .CHANNEL_IN_MONO,
                    AudioFormat
                        .ENCODING_PCM_16BIT
                )

        check(
            minBuffer >
                0
        ) {
            "AudioRecord configuration is not supported."
        }

        /**
         * VOICE_RECOGNITION requests an Android capture path tuned for
         * speech recognition. Device vendors may already enable some
         * preprocessing for this source.
         */
        val recorder =
            AudioRecord(
                MediaRecorder
                    .AudioSource
                    .VOICE_RECOGNITION,

                SAMPLE_RATE,

                AudioFormat
                    .CHANNEL_IN_MONO,

                AudioFormat
                    .ENCODING_PCM_16BIT,

                max(
                    minBuffer *
                        2,
                    SAMPLE_RATE
                )
            )

        check(
            recorder.state ==
                AudioRecord
                    .STATE_INITIALIZED
        ) {

            recorder.release()

            "Could not initialize microphone."
        }

        attachAudioEffects(
            recorder
        )

        synchronized(
            lock
        ) {
            samples.clear()
        }

        audioRecord =
            recorder

        recording =
            true

        recorder
            .startRecording()

        check(
            recorder.recordingState ==
                AudioRecord
                    .RECORDSTATE_RECORDING
        ) {
            releaseEffects()
            recorder.release()
            audioRecord = null
            recording = false
            "The microphone did not start recording."
        }

        worker =
            Thread(
                {
                    val buffer =
                        ShortArray(
                            max(
                                1024,
                                minBuffer /
                                    2
                            )
                        )

                    val maxSamples =
                        SAMPLE_RATE *
                            MAX_SECONDS

                    while (
                        recording
                    ) {

                        val read =
                            recorder.read(
                                buffer,
                                0,
                                buffer.size
                            )

                        if (
                            read <=
                            0
                        ) {
                            continue
                        }

                        synchronized(
                            lock
                        ) {

                            val remaining =
                                maxSamples -
                                    samples.size

                            if (
                                remaining <=
                                0
                            ) {

                                recording =
                                    false

                            } else {

                                val amount =
                                    minOf(
                                        read,
                                        remaining
                                    )

                                for (
                                    i in 0
                                        until amount
                                ) {

                                    samples.add(
                                        buffer[i]
                                    )
                                }

                                if (
                                    samples.size >=
                                    maxSamples
                                ) {

                                    recording =
                                        false
                                }
                            }
                        }
                    }
                },

                "DhwaniVoiceRecorder"
            )
                .apply {
                    start()
                }
    }

    fun stop():
        FloatArray {

        recording =
            false

        try {

            worker
                ?.join(
                    1_200
                )

        } catch (
            _: InterruptedException
        ) {

            Thread
                .currentThread()
                .interrupt()
        }

        val recorder =
            audioRecord

        try {

            if (
                recorder
                    ?.recordingState ==
                AudioRecord
                    .RECORDSTATE_RECORDING
            ) {

                recorder.stop()
            }

        } catch (
            _: IllegalStateException
        ) {
        }

        releaseEffects()

        recorder
            ?.release()

        audioRecord =
            null

        worker =
            null

        val copy =
            synchronized(
                lock
            ) {
                samples
                    .toShortArray()
            }

        val raw =
            FloatArray(
                copy.size
            ) {
                    index ->

                copy[index] /
                    32768.0f
            }

        return VoiceAudioProcessor
            .process(
                raw,
                SAMPLE_RATE
            )
    }

    fun isRecording():
        Boolean =
        recording

    fun looksLikeUsefulAudio(
        data: FloatArray
    ): Boolean {

        val metrics =
            VoiceAudioProcessor
                .analyze(
                    data,
                    SAMPLE_RATE
                )

        if (
            metrics.durationSeconds <
            0.55f
        ) {
            return false
        }

        if (
            metrics.peak <
            0.020f
        ) {
            return false
        }

        if (
            metrics.rms <
            0.006f
        ) {
            return false
        }

        if (
            metrics.voicedFrameRatio <
            0.06f
        ) {
            return false
        }

        // Very high clipping usually means the mic is overloaded,
        // rubbing against something, or being hit by a strong impulse.
        if (
            metrics.clippingRatio >
            0.08f
        ) {
            return false
        }

        return true
    }

    private fun attachAudioEffects(
        recorder: AudioRecord
    ) {

        val sessionId =
            recorder
                .audioSessionId

        if (
            NoiseSuppressor
                .isAvailable()
        ) {

            try {

                noiseSuppressor =
                    NoiseSuppressor
                        .create(
                            sessionId
                        )
                        ?.apply {
                            enabled =
                                true
                        }

            } catch (
                _: Throwable
            ) {

                noiseSuppressor =
                    null
            }
        }

        /**
         * AEC is primarily for removing audio coming from the device
         * speaker (for example DhwaniMitra TTS) from the microphone.
         * It is not a general wind-noise remover.
         */
        if (
            AcousticEchoCanceler
                .isAvailable()
        ) {

            try {

                echoCanceler =
                    AcousticEchoCanceler
                        .create(
                            sessionId
                        )
                        ?.apply {
                            enabled =
                                true
                        }

            } catch (
                _: Throwable
            ) {

                echoCanceler =
                    null
            }
        }
    }

    private fun releaseEffects() {

        try {
            noiseSuppressor
                ?.release()
        } catch (
            _: Throwable
        ) {
        }

        try {
            echoCanceler
                ?.release()
        } catch (
            _: Throwable
        ) {
        }

        noiseSuppressor =
            null

        echoCanceler =
            null
    }
}
