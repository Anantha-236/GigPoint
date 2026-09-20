package com.example.gigpoint.voice

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Lightweight offline speech pre-processing for short inventory commands.
 *
 * It is deliberately conservative:
 * - removes DC / very-low-frequency rumble (wind / handling noise)
 * - estimates a local noise floor
 * - trims leading/trailing non-speech
 * - softly attenuates low-energy frames instead of hard muting them
 * - normalizes speech without excessive amplification
 *
 * This is NOT a speaker-separation system. It cannot guarantee that another
 * nearby person's speech is removed. That requires speaker verification /
 * beamforming / source separation, which is a separate feature.
 */
object VoiceAudioProcessor {

    data class Metrics(
        val durationSeconds: Float,
        val rms: Float,
        val peak: Float,
        val noiseFloor: Float,
        val speechThreshold: Float,
        val voicedFrameRatio: Float,
        val clippingRatio: Float
    )

    fun process(
        input: FloatArray,
        sampleRate: Int = 16_000
    ): FloatArray {

        if (input.isEmpty()) {
            return input
        }

        val filtered =
            highPass(
                input = input,
                sampleRate = sampleRate,
                cutoffHz = 90f
            )

        val frameSize =
            max(
                160,
                sampleRate / 50
            ) // ~20 ms

        val frameRms =
            calculateFrameRms(
                filtered,
                frameSize
            )

        if (frameRms.isEmpty()) {
            return filtered
        }

        val sorted =
            frameRms
                .copyOf()
                .apply {
                    sort()
                }

        // Use a low percentile instead of only the first frames because
        // merchants may start talking immediately after tapping the mic.
        val noiseFloor =
            percentile(
                sorted,
                0.20f
            )

        val speechThreshold =
            max(
                0.006f,
                noiseFloor * 2.2f
            )

        var firstSpeechFrame = -1
        var lastSpeechFrame = -1

        frameRms.forEachIndexed {
                index,
                value ->

            if (
                value >=
                speechThreshold
            ) {

                if (
                    firstSpeechFrame < 0
                ) {
                    firstSpeechFrame =
                        index
                }

                lastSpeechFrame =
                    index
            }
        }

        // Do not throw away the entire capture if the adaptive detector
        // is uncertain. MainActivity/looksLikeUsefulAudio will reject
        // truly unusable audio later.
        if (
            firstSpeechFrame < 0 ||
            lastSpeechFrame < 0
        ) {
            return normalize(filtered)
        }

        val padSamples =
            (sampleRate * 0.20f)
                .toInt()

        val start =
            max(
                0,
                firstSpeechFrame *
                    frameSize -
                    padSamples
            )

        val endExclusive =
            min(
                filtered.size,
                (lastSpeechFrame + 1) *
                    frameSize +
                    padSamples
            )

        val trimmed =
            filtered.copyOfRange(
                start,
                endExclusive
            )

        val gated =
            trimmed.copyOf()

        var offset = 0

        while (
            offset <
            gated.size
        ) {

            val end =
                min(
                    gated.size,
                    offset +
                        frameSize
                )

            val rms =
                rms(
                    gated,
                    offset,
                    end
                )

            // Soft gate only. A hard gate can cut consonants and hurt
            // Whisper recognition, especially for Telugu/Hindi speech.
            val gain =
                when {
                    rms <
                        noiseFloor *
                        1.20f ->
                        0.18f

                    rms <
                        speechThreshold ->
                        0.45f

                    else ->
                        1.0f
                }

            for (
                i in offset
                    until end
            ) {
                gated[i] *= gain
            }

            offset =
                end
        }

        return normalize(
            gated
        )
    }

    fun analyze(
        data: FloatArray,
        sampleRate: Int = 16_000
    ): Metrics {

        if (data.isEmpty()) {
            return Metrics(
                durationSeconds = 0f,
                rms = 0f,
                peak = 0f,
                noiseFloor = 0f,
                speechThreshold = 0.006f,
                voicedFrameRatio = 0f,
                clippingRatio = 0f
            )
        }

        val frameSize =
            max(
                160,
                sampleRate / 50
            )

        val frames =
            calculateFrameRms(
                data,
                frameSize
            )

        val sorted =
            frames
                .copyOf()
                .apply {
                    sort()
                }

        val noiseFloor =
            if (
                sorted.isEmpty()
            ) {
                0f
            } else {
                percentile(
                    sorted,
                    0.20f
                )
            }

        val threshold =
            max(
                0.006f,
                noiseFloor * 2.2f
            )

        val voiced =
            if (
                frames.isEmpty()
            ) {
                0
            } else {
                frames.count {
                    it >=
                        threshold
                }
            }

        var peak = 0f
        var sumSquares = 0.0
        var clipped = 0

        for (
            sample in data
        ) {

            val a =
                abs(sample)

            peak =
                max(
                    peak,
                    a
                )

            sumSquares +=
                sample *
                    sample

            if (
                a >=
                0.985f
            ) {
                clipped++
            }
        }

        return Metrics(
            durationSeconds =
                data.size.toFloat() /
                    sampleRate.toFloat(),

            rms =
                sqrt(
                    sumSquares /
                        data.size
                ).toFloat(),

            peak =
                peak,

            noiseFloor =
                noiseFloor,

            speechThreshold =
                threshold,

            voicedFrameRatio =
                if (
                    frames.isEmpty()
                ) {
                    0f
                } else {
                    voiced.toFloat() /
                        frames.size.toFloat()
                },

            clippingRatio =
                clipped.toFloat() /
                    data.size.toFloat()
        )
    }

    private fun highPass(
        input: FloatArray,
        sampleRate: Int,
        cutoffHz: Float
    ): FloatArray {

        if (
            input.size < 2
        ) {
            return input.copyOf()
        }

        val output =
            FloatArray(
                input.size
            )

        val dt =
            1.0f /
                sampleRate.toFloat()

        val rc =
            1.0f /
                (
                    2.0f *
                    PI.toFloat() *
                    cutoffHz
                )

        val alpha =
            rc /
                (
                    rc +
                    dt
                )

        var previousInput =
            input[0]

        var previousOutput =
            0f

        output[0] =
            0f

        for (
            i in 1
                until input.size
        ) {

            val current =
                alpha *
                    (
                        previousOutput +
                        input[i] -
                        previousInput
                    )

            output[i] =
                current

            previousOutput =
                current

            previousInput =
                input[i]
        }

        return output
    }

    private fun normalize(
        input: FloatArray
    ): FloatArray {

        if (
            input.isEmpty()
        ) {
            return input
        }

        var peak = 0f

        for (
            sample in input
        ) {
            peak =
                max(
                    peak,
                    abs(sample)
                )
        }

        if (
            peak <
            0.01f
        ) {
            return input
        }

        // Never amplify aggressively. Excessive gain raises fan/wind/shop
        // background noise along with the user's speech.
        val gain =
            min(
                3.0f,
                0.88f /
                    peak
            )

        return FloatArray(
            input.size
        ) {
                index ->

            (
                input[index] *
                    gain
                )
                .coerceIn(
                    -1.0f,
                    1.0f
                )
        }
    }

    private fun calculateFrameRms(
        data: FloatArray,
        frameSize: Int
    ): FloatArray {

        if (
            data.isEmpty()
        ) {
            return FloatArray(0)
        }

        val count =
            (
                data.size +
                    frameSize -
                    1
                ) /
                frameSize

        return FloatArray(
            count
        ) {
                frame ->

            val start =
                frame *
                    frameSize

            val end =
                min(
                    data.size,
                    start +
                        frameSize
                )

            rms(
                data,
                start,
                end
            )
        }
    }

    private fun rms(
        data: FloatArray,
        start: Int,
        end: Int
    ): Float {

        if (
            end <=
            start
        ) {
            return 0f
        }

        var sum = 0.0

        for (
            i in start
                until end
        ) {
            sum +=
                data[i] *
                    data[i]
        }

        return sqrt(
            sum /
                (
                    end -
                    start
                )
        ).toFloat()
    }

    private fun percentile(
        sorted: FloatArray,
        fraction: Float
    ): Float {

        if (
            sorted.isEmpty()
        ) {
            return 0f
        }

        val index =
            (
                (
                    sorted.size -
                    1
                    ) *
                    fraction
                )
                .toInt()
                .coerceIn(
                    0,
                    sorted.lastIndex
                )

        return sorted[
            index
        ]
    }
}
