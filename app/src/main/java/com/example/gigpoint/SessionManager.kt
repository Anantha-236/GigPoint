package com.example.gigpoint

import android.content.Context

class SessionManager(context: Context) {

    private val prefs = context.getSharedPreferences(
        "dhwani_mitra_session",
        Context.MODE_PRIVATE
    )

    fun save(session: AuthSession) {
        prefs.edit()
            .putString("user_id", session.userId)
            .putString("email", session.email)
            .putString("access_token", session.accessToken)
            .putString("refresh_token", session.refreshToken)
            .apply()
    }

    fun userId(): String? = prefs.getString("user_id", null)
    fun email(): String? = prefs.getString("email", null)
    fun accessToken(): String? = prefs.getString("access_token", null)
    fun refreshToken(): String? = prefs.getString("refresh_token", null)

    fun hasSession(): Boolean =
        !userId().isNullOrBlank() &&
        !accessToken().isNullOrBlank()

    fun clear() {
        prefs.edit().clear().apply()
    }
}
