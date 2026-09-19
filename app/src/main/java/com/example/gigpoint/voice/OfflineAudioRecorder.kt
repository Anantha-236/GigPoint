package com.example.gigpoint.voice

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlin.math.abs
import kotlin.math.max

class OfflineAudioRecorder {

    companion object {
        const val SAMPLE_RATE = 16_000

        // Keep voice commands short. This limits RAM usage, CPU time,
        // accidental background recording and Whisper latency.
        private const val MAX_SECONDS = 10
    }

    private val lock = Any()

    @Volatile
    private var recording = false

    private var audioRecord: AudioRecord? = null
    private var worker: Thread? = null

    private val samples =
        ArrayList<Short>(
            SAMPLE_RATE * 4
        )

    @SuppressLint("MissingPermission")
    fun start() {
        if (recording) {
            return
        }

        val minBuffer =
            AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )

        check(minBuffer > 0) {
            "AudioRecord configuration is not supported."
        }

        val recorder =
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                max(
                    minBuffer,
                    SAMPLE_RATE
                )
            )

        check(
            recorder.state ==
                AudioRecord.STATE_INITIALIZED
        ) {
            recorder.release()
            "Could not initialize microphone."
        }

        synchronized(lock) {
            samples.clear()
        }

        audioRecord = recorder
        recording = true

        recorder.startRecording()

        worker =
            Thread(
                {
                    val buffer =
                        ShortArray(
                            max(
                                1024,
                                minBuffer / 2
                            )
                        )

                    val maxSamples =
                        SAMPLE_RATE *
                            MAX_SECONDS

                    while (recording) {
                        val read =
                            recorder.read(
                                buffer,
                                0,
                                buffer.size
                            )

                        if (read <= 0) {
                            continue
                        }

                        synchronized(lock) {
                            val remaining =
                                maxSamples -
                                    samples.size

                            if (remaining <= 0) {
                                recording = false
                            } else {
                                val amount =
                                    minOf(
                                        read,
                                        remaining
                                    )

                                for (
                                    i in 0 until amount
                                ) {
                                    samples.add(
                                        buffer[i]
                                    )
                                }

                                if (
                                    samples.size >=
                                    maxSamples
                                ) {
                                    recording = false
                                }
                            }
                        }
                    }
                },
                "DhwaniVoiceRecorder"
            ).apply {
                start()
            }
    }

    fun stop(): FloatArray {
        recording = false

        try {
            worker?.join(1_000)
        } catch (_: InterruptedException) {
            Thread.currentThread()
                .interrupt()
        }

        val recorder =
            audioRecord

        try {
            if (
                recorder?.recordingState ==
                AudioRecord.RECORDSTATE_RECORDING
            ) {
                recorder.stop()
            }
        } catch (_: IllegalStateException) {
        }

        recorder?.release()

        audioRecord = null
        worker = null

        val copy =
            synchronized(lock) {
                samples.toShortArray()
            }

        return FloatArray(copy.size) {
            index ->
            copy[index] / 32768.0f
        }
    }

    fun isRecording(): Boolean =
        recording

    fun looksLikeUsefulAudio(
        data: FloatArray
    ): Boolean {

        // Reject very short captures.
        if (
            data.size <
            SAMPLE_RATE / 2
        ) {
            return false
        }

        var peak = 0f

        for (sample in data) {
            peak =
                maxOf(
                    peak,
                    abs(sample)
                )
        }

        // Simple silence guard. This is intentionally conservative;
        // tune it after testing on actual shop devices.
        return peak >= 0.01f
    }
}
