package com.example.gigpoint

import com.example.gigpoint.data.DatabaseHelper

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL
import java.nio.charset.StandardCharsets

class SupabaseRestClient(private val context: Context) {

    private val baseUrl =
        context.getString(R.string.supabase_url).trim().trimEnd('/')

    private val publishableKey =
        context.getString(R.string.supabase_publishable_key).trim()

    fun isConfigured(): Boolean =
        baseUrl.startsWith("https://") &&
                publishableKey.isNotBlank() &&
                !publishableKey.startsWith("YOUR_")

    fun login(email: String, password: String): AuthSession {
        ensureConfigured()

        val payload = JSONObject()
            .put("email", email)
            .put("password", password)

        val response = request(
            method = "POST",
            path = "/auth/v1/token?grant_type=password",
            body = payload.toString()
        )

        if (response.code !in 200..299) {
            throw IllegalStateException(errorMessage(response.body))
        }

        val json = JSONObject(response.body)
        val user = json.getJSONObject("user")

        return AuthSession(
            userId = user.getString("id"),
            email = user.optString("email", email),
            accessToken = json.getString("access_token"),
            refreshToken = json.optString("refresh_token").takeIf { it.isNotBlank() }
        )
    }

    /**
     * For fastest prototype setup, disable "Confirm email" in Supabase Auth.
     * If email confirmation is enabled, this method returns null and the user
     * must verify the email, then use Login.
     */
    fun signUp(email: String, password: String): AuthSession? {
        ensureConfigured()

        val payload = JSONObject()
            .put("email", email)
            .put("password", password)

        val response = request(
            method = "POST",
            path = "/auth/v1/signup",
            body = payload.toString()
        )

        if (response.code !in 200..299) {
            throw IllegalStateException(errorMessage(response.body))
        }

        val json = JSONObject(response.body)
        val token = json.optString("access_token")
        if (token.isBlank()) return null

        val user = json.getJSONObject("user")

        return AuthSession(
            userId = user.getString("id"),
            email = user.optString("email", email),
            accessToken = token,
            refreshToken = json.optString("refresh_token").takeIf { it.isNotBlank() }
        )
    }

    fun fetchProfile(accessToken: String, userId: String): MerchantProfile? {
        ensureConfigured()

        val id = URLEncoder.encode(userId, StandardCharsets.UTF_8.toString())
        val response = request(
            method = "GET",
            path = "/rest/v1/profiles?id=eq.$id&select=*",
            accessToken = accessToken
        )

        if (response.code !in 200..299) {
            throw IllegalStateException(errorMessage(response.body))
        }

        val array = JSONArray(response.body)
        if (array.length() == 0) return null

        val row = array.getJSONObject(0)

        return MerchantProfile(
            userId = row.getString("id"),
            ownerName = row.optString("owner_name"),
            phone = row.optString("phone").takeIf { it.isNotBlank() },
            preferredLanguage = row.optString("preferred_language", "en"),
            theme = row.optString("theme", "system"),
            syncStatus = DatabaseHelper.SYNC_SYNCED
        )
    }

    fun fetchShop(accessToken: String, userId: String): ShopProfile? {
        ensureConfigured()

        val id = URLEncoder.encode(userId, StandardCharsets.UTF_8.toString())
        val response = request(
            method = "GET",
            path = "/rest/v1/shops?owner_id=eq.$id&select=*&limit=1",
            accessToken = accessToken
        )

        if (response.code !in 200..299) {
            throw IllegalStateException(errorMessage(response.body))
        }

        val array = JSONArray(response.body)
        if (array.length() == 0) return null

        val row = array.getJSONObject(0)

        return ShopProfile(
            id = row.getString("id"),
            ownerId = row.getString("owner_id"),
            shopName = row.optString("shop_name"),
            gstin = row.optString("gstin").takeIf { it.isNotBlank() },
            businessType = row.optString("business_type"),
            city = row.optString("city").takeIf { it.isNotBlank() },
            area = row.optString("area").takeIf { it.isNotBlank() },
            syncStatus = DatabaseHelper.SYNC_SYNCED
        )
    }

    fun upsertProfile(accessToken: String, profile: MerchantProfile) {
        ensureConfigured()

        val payload = JSONArray().put(
            JSONObject()
                .put("id", profile.userId)
                .put("owner_name", profile.ownerName)
                .put("phone", profile.phone ?: JSONObject.NULL)
                .put("preferred_language", profile.preferredLanguage)
                .put("theme", profile.theme)
        )

        val response = request(
            method = "POST",
            path = "/rest/v1/profiles?on_conflict=id",
            accessToken = accessToken,
            body = payload.toString(),
            prefer = "resolution=merge-duplicates,return=minimal"
        )

        if (response.code !in 200..299) {
            throw IllegalStateException(errorMessage(response.body))
        }
    }

    fun upsertShop(accessToken: String, shop: ShopProfile) {
        ensureConfigured()

        val payload = JSONArray().put(
            JSONObject()
                .put("id", shop.id)
                .put("owner_id", shop.ownerId)
                .put("shop_name", shop.shopName)
                .put("gstin", shop.gstin ?: JSONObject.NULL)
                .put("business_type", shop.businessType)
                .put("city", shop.city ?: JSONObject.NULL)
        )

        val response = request(
            method = "POST",
            path = "/rest/v1/shops?on_conflict=id",
            accessToken = accessToken,
            body = payload.toString(),
            prefer = "resolution=merge-duplicates,return=minimal"
        )

        if (response.code !in 200..299) {
            throw IllegalStateException(errorMessage(response.body))
        }
    }

    fun logout(accessToken: String) {
        if (!isConfigured()) return

        request(
            method = "POST",
            path = "/auth/v1/logout",
            accessToken = accessToken
        )
    }

    private fun ensureConfigured() {
        check(isConfigured()) {
            "Supabase is not configured. Add project URL and publishable key in res/values/supabase.xml."
        }
    }

    private data class HttpResponse(
        val code: Int,
        val body: String
    )

    private fun request(
        method: String,
        path: String,
        accessToken: String? = null,
        body: String? = null,
        prefer: String? = null
    ): HttpResponse {

        val connection =
            (URL(baseUrl + path).openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = 15_000
                readTimeout = 20_000

                setRequestProperty("apikey", publishableKey)
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/json")

                if (!accessToken.isNullOrBlank()) {
                    setRequestProperty(
                        "Authorization",
                        "Bearer $accessToken"
                    )
                }

                if (!prefer.isNullOrBlank()) {
                    setRequestProperty("Prefer", prefer)
                }

                if (body != null) {
                    doOutput = true
                    outputStream.bufferedWriter().use {
                        it.write(body)
                    }
                }
            }

        val code = connection.responseCode
        val stream =
            if (code in 200..299)
                connection.inputStream
            else
                connection.errorStream

        val responseBody =
            stream?.bufferedReader()?.use(BufferedReader::readText).orEmpty()

        connection.disconnect()

        return HttpResponse(code, responseBody)
    }

    private fun errorMessage(body: String): String {
        return try {
            val json = JSONObject(body)
            json.optString("msg")
                .ifBlank { json.optString("message") }
                .ifBlank { json.optString("error_description") }
                .ifBlank { "Request failed." }
        } catch (_: Exception) {
            body.ifBlank { "Request failed." }
        }
    }
}
