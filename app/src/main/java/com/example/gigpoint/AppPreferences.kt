package com.example.gigpoint

import com.example.gigpoint.actions.VoiceActionPolicy

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

class AppPreferences(
    context: Context
) {

    private val prefs =
        context.getSharedPreferences(
            PREFS_NAME,
            Context.MODE_PRIVATE
        )

    // =========================================================
    // APP LANGUAGE
    // =========================================================

    /**
     * UI language.
     *
     * Supported:
     * en = English
     * te = Telugu
     * hi = Hindi
     */
    fun language(): String =
        prefs.getString(
            KEY_LANGUAGE,
            DEFAULT_LANGUAGE
        ) ?: DEFAULT_LANGUAGE

    fun setLanguage(
        value: String
    ) {

        val normalized =
            when (value) {
                "te" -> "te"
                "hi" -> "hi"
                else -> "en"
            }

        prefs.edit()
            .putString(
                KEY_LANGUAGE,
                normalized
            )
            .apply()

        AppCompatDelegate
            .setApplicationLocales(
                LocaleListCompat
                    .forLanguageTags(
                        normalized
                    )
            )
    }

    // =========================================================
    // THEME
    // =========================================================

    /**
     * system
     * light
     * dark
     */
    fun theme(): String =
        prefs.getString(
            KEY_THEME,
            DEFAULT_THEME
        ) ?: DEFAULT_THEME

    fun setTheme(
        value: String
    ) {

        val normalized =
            when (value) {
                "light" -> "light"
                "dark" -> "dark"
                else -> "system"
            }

        prefs.edit()
            .putString(
                KEY_THEME,
                normalized
            )
            .apply()

        applyTheme(
            normalized
        )
    }

    private fun applyTheme(
        value: String
    ) {

        val mode =
            when (value) {

                "light" ->
                    AppCompatDelegate
                        .MODE_NIGHT_NO

                "dark" ->
                    AppCompatDelegate
                        .MODE_NIGHT_YES

                else ->
                    AppCompatDelegate
                        .MODE_NIGHT_FOLLOW_SYSTEM
            }

        AppCompatDelegate
            .setDefaultNightMode(
                mode
            )
    }

    // =========================================================
    // WHISPER / SPEECH RECOGNITION
    // =========================================================

    /**
     * Voice recognition language.
     *
     * auto = multilingual / mixed-language mode
     * en   = English
     * te   = Telugu
     * hi   = Hindi
     *
     * Default is AUTO because merchants may naturally mix
     * Telugu/Hindi/English in the same sentence.
     */
    fun voiceRecognitionLanguage():
            String =
        prefs.getString(
            KEY_VOICE_RECOGNITION_LANGUAGE,
            DEFAULT_VOICE_RECOGNITION_LANGUAGE
        )
            ?: DEFAULT_VOICE_RECOGNITION_LANGUAGE

    fun setVoiceRecognitionLanguage(
        value: String
    ) {

        val normalized =
            when (value) {

                "en" -> "en"

                "te" -> "te"

                "hi" -> "hi"

                else -> "auto"
            }

        prefs.edit()
            .putString(
                KEY_VOICE_RECOGNITION_LANGUAGE,
                normalized
            )
            .apply()
    }

    // =========================================================
    // VOICE REPLIES / TTS
    // =========================================================

    /**
     * DhwaniMitra speaks answers back to the merchant.
     *
     * Default = ON.
     */
    fun voiceReplyEnabled():
            Boolean =
        prefs.getBoolean(
            KEY_VOICE_REPLY_ENABLED,
            true
        )

    fun setVoiceReplyEnabled(
        enabled: Boolean
    ) {

        prefs.edit()
            .putBoolean(
                KEY_VOICE_REPLY_ENABLED,
                enabled
            )
            .apply()
    }

    /**
     * app = use current app language
     * en  = English
     * te  = Telugu
     * hi  = Hindi
     */
    fun voiceReplyLanguage():
            String =
        prefs.getString(
            KEY_VOICE_REPLY_LANGUAGE,
            "app"
        ) ?: "app"

    fun setVoiceReplyLanguage(
        value: String
    ) {

        val normalized =
            when (value) {

                "en" -> "en"

                "te" -> "te"

                "hi" -> "hi"

                else -> "app"
            }

        prefs.edit()
            .putString(
                KEY_VOICE_REPLY_LANGUAGE,
                normalized
            )
            .apply()
    }

    /**
     * Resolves "app" into the actual language code.
     */
    fun effectiveVoiceReplyLanguage():
            String {

        return when (
            val configured =
                voiceReplyLanguage()
        ) {

            "app" ->
                language()

            else ->
                configured
        }
    }

    // =========================================================
    // SPEECH RATE
    // =========================================================

    /**
     * Android TTS normal speech rate is approximately 1.0.
     *
     * Supported range:
     * 0.5 - 1.5
     */
    fun speechRate():
            Float =
        prefs.getFloat(
            KEY_SPEECH_RATE,
            DEFAULT_SPEECH_RATE
        )

    fun setSpeechRate(
        value: Float
    ) {

        prefs.edit()
            .putFloat(
                KEY_SPEECH_RATE,
                value.coerceIn(
                    0.5f,
                    1.5f
                )
            )
            .apply()
    }

    // =========================================================
    // VOICE SAFETY
    // =========================================================

    /**
     * General voice confirmation preference.
     *
     * IMPORTANT:
     * destructive / stock-changing operations should still be
     * forced through VoiceActionPolicy regardless of this flag.
     */
    fun confirmVoiceActions():
            Boolean =
        prefs.getBoolean(
            KEY_CONFIRM_VOICE_ACTIONS,
            true
        )

    fun setConfirmVoiceActions(
        enabled: Boolean
    ) {

        prefs.edit()
            .putBoolean(
                KEY_CONFIRM_VOICE_ACTIONS,
                enabled
            )
            .apply()
    }

    /**
     * Speak alerts such as:
     *
     * "Rice is now low on stock."
     */
    fun speakAlerts():
            Boolean =
        prefs.getBoolean(
            KEY_SPEAK_ALERTS,
            true
        )

    fun setSpeakAlerts(
        enabled: Boolean
    ) {

        prefs.edit()
            .putBoolean(
                KEY_SPEAK_ALERTS,
                enabled
            )
            .apply()
    }

    /**
     * Speak answers to read-only queries such as:
     *
     * "How much rice is available?"
     */
    fun speakQueryAnswers():
            Boolean =
        prefs.getBoolean(
            KEY_SPEAK_QUERY_ANSWERS,
            true
        )

    fun setSpeakQueryAnswers(
        enabled: Boolean
    ) {

        prefs.edit()
            .putBoolean(
                KEY_SPEAK_QUERY_ANSWERS,
                enabled
            )
            .apply()
    }

    // =========================================================
    // INVENTORY ALERT SETTINGS
    // =========================================================

    /**
     * Future StockBatch alert engine.
     *
     * Example:
     * product received 30+ days ago and still remaining
     * -> OLD_STOCK.
     */
    fun oldStockThresholdDays():
            Int =
        prefs.getInt(
            KEY_OLD_STOCK_DAYS,
            DEFAULT_OLD_STOCK_DAYS
        )

    fun setOldStockThresholdDays(
        days: Int
    ) {

        prefs.edit()
            .putInt(
                KEY_OLD_STOCK_DAYS,
                days.coerceIn(
                    1,
                    3650
                )
            )
            .apply()
    }

    /**
     * Number of days before expiry when DhwaniMitra should warn.
     *
     * Default = 7 days.
     */
    fun nearExpiryWarningDays():
            Int =
        prefs.getInt(
            KEY_NEAR_EXPIRY_DAYS,
            DEFAULT_NEAR_EXPIRY_DAYS
        )

    fun setNearExpiryWarningDays(
        days: Int
    ) {

        prefs.edit()
            .putInt(
                KEY_NEAR_EXPIRY_DAYS,
                days.coerceIn(
                    1,
                    365
                )
            )
            .apply()
    }

    // =========================================================
    // APPLY SAVED UI PREFERENCES
    // =========================================================

    /**
     * Call before setContentView() in Activities.
     */
    fun apply() {

        applyTheme(
            theme()
        )

        AppCompatDelegate
            .setApplicationLocales(
                LocaleListCompat
                    .forLanguageTags(
                        language()
                    )
            )
    }

    companion object {

        private const val
                PREFS_NAME =
            "dhwani_mitra_preferences"

        private const val
                KEY_LANGUAGE =
            "language"

        private const val
                KEY_THEME =
            "theme"

        private const val
                KEY_VOICE_RECOGNITION_LANGUAGE =
            "voice_recognition_language"

        private const val
                KEY_VOICE_REPLY_ENABLED =
            "voice_reply_enabled"

        private const val
                KEY_VOICE_REPLY_LANGUAGE =
            "voice_reply_language"

        private const val
                KEY_SPEECH_RATE =
            "speech_rate"

        private const val
                KEY_CONFIRM_VOICE_ACTIONS =
            "confirm_voice_actions"

        private const val
                KEY_SPEAK_ALERTS =
            "speak_alerts"

        private const val
                KEY_SPEAK_QUERY_ANSWERS =
            "speak_query_answers"

        private const val
                KEY_OLD_STOCK_DAYS =
            "old_stock_threshold_days"

        private const val
                KEY_NEAR_EXPIRY_DAYS =
            "near_expiry_warning_days"

        private const val
                DEFAULT_LANGUAGE =
            "en"

        private const val
                DEFAULT_THEME =
            "system"

        private const val
                DEFAULT_VOICE_RECOGNITION_LANGUAGE =
            "auto"

        private const val
                DEFAULT_SPEECH_RATE =
            1.0f

        private const val
                DEFAULT_OLD_STOCK_DAYS =
            30

        private const val
                DEFAULT_NEAR_EXPIRY_DAYS =
            7
    }
}