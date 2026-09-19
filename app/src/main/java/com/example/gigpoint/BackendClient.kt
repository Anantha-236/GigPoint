package com.example.gigpoint

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class BackendClient(private val context: Context) {

    private val baseUrl =
        context.getString(R.string.supabase_url).trim().trimEnd('/')

    private val apiKey =
        context.getString(R.string.supabase_publishable_key).trim()

    fun isConfigured(): Boolean =
        baseUrl.startsWith("http") &&
        apiKey.isNotBlank() &&
        !apiKey.startsWith("YOUR_")

    fun login(
        email: String,
        password: String
    ): AuthSession {

        val json = postJson(
            "/auth/v1/token?grant_type=password",
            JSONObject()
                .put("email", email)
                .put("password", password),
            null
        )

        return parseAuthSession(json, email)
    }

    fun signUp(
        email: String,
        password: String
    ): AuthSession? {

        val json = postJson(
            "/auth/v1/signup",
            JSONObject()
                .put("email", email)
                .put("password", password),
            null
        )

        val token = json.optString("access_token")
        if (token.isBlank()) return null

        return parseAuthSession(json, email)
    }

    fun refreshSession(
        refreshToken: String
    ): AuthSession {

        val json = postJson(
            "/auth/v1/token?grant_type=refresh_token",
            JSONObject()
                .put("refresh_token", refreshToken),
            null
        )

        val user = json.getJSONObject("user")

        return AuthSession(
            userId = user.getString("id"),
            email = user.optString("email"),
            accessToken = json.getString("access_token"),
            refreshToken =
                json.optString("refresh_token")
                    .takeIf { it.isNotBlank() }
        )
    }

    fun getMyContext(
        accessToken: String
    ): MerchantContext {

        val json = postJson(
            "/rest/v1/rpc/get_my_context",
            JSONObject(),
            accessToken
        )

        val profileJson =
            json.optJSONObject("profile")

        val shopJson =
            json.optJSONObject("shop")

        val profile =
            profileJson?.let {
                MerchantProfile(
                    userId = it.getString("id"),
                    ownerName = it.optString("owner_name"),
                    phone =
                        it.optString("phone")
                            .takeIf(String::isNotBlank),
                    preferredLanguage =
                        it.optString(
                            "preferred_language",
                            "en"
                        ),
                    theme =
                        it.optString(
                            "theme",
                            "system"
                        )
                )
            }

        val shop =
            shopJson?.let {
                ShopProfile(
                    id = it.getString("id"),
                    ownerId = it.getString("owner_id"),
                    shopName = it.optString("shop_name"),
                    gstin =
                        it.optString("gstin")
                            .takeIf(String::isNotBlank),
                    businessType =
                        it.optString("business_type"),
                    city =
                        it.optString("city")
                            .takeIf(String::isNotBlank),
                    area =
                        it.optString("area")
                            .takeIf(String::isNotBlank)
                )
            }

        return MerchantContext(
            profile = profile,
            shop = shop,
            setupComplete =
                json.optBoolean(
                    "setup_complete",
                    false
                )
        )
    }

    fun completeBusinessSetup(
        accessToken: String,
        profile: MerchantProfile,
        shop: ShopProfile
    ): String {

        val body = JSONObject()
            .put("p_owner_name", profile.ownerName)
            .put("p_shop_name", shop.shopName)
            .put("p_business_type", shop.businessType)
            .put(
                "p_preferred_language",
                profile.preferredLanguage
            )
            .put("p_theme", profile.theme)
            .put(
                "p_phone",
                profile.phone ?: JSONObject.NULL
            )
            .put(
                "p_gstin",
                shop.gstin ?: JSONObject.NULL
            )
            .put(
                "p_city",
                shop.city ?: JSONObject.NULL
            )
            .put(
                "p_area",
                shop.area ?: JSONObject.NULL
            )
            .put("p_shop_id", shop.id)

        val result = postRawJson(
            "/rest/v1/rpc/complete_business_setup",
            body,
            accessToken
        )

        // PostgreSQL UUID scalar is returned as a JSON string.
        return result.trim().trim('"')
    }

    fun getDashboardSummary(
        accessToken: String,
        shopId: String
    ): JSONObject {

        return postJson(
            "/rest/v1/rpc/get_dashboard_summary",
            JSONObject().put(
                "p_shop_id",
                shopId
            ),
            accessToken
        )
    }

    fun logout(accessToken: String) {
        request(
            "POST",
            "/auth/v1/logout",
            accessToken,
            "{}"
        )
    }

    fun deleteAccount(
        accessToken: String
    ) {
        val response = request(
            "POST",
            "/functions/v1/delete-account",
            accessToken,
            "{}"
        )

        if (response.code !in 200..299) {
            throw IllegalStateException(
                parseError(response.body)
            )
        }
    }

    private fun parseAuthSession(
        json: JSONObject,
        fallbackEmail: String
    ): AuthSession {

        val user = json.getJSONObject("user")

        return AuthSession(
            userId = user.getString("id"),
            email =
                user.optString(
                    "email",
                    fallbackEmail
                ),
            accessToken =
                json.getString("access_token"),
            refreshToken =
                json.optString("refresh_token")
                    .takeIf { it.isNotBlank() }
        )
    }

    private fun postJson(
        path: String,
        body: JSONObject,
        accessToken: String?
    ): JSONObject {

        val text = postRawJson(
            path,
            body,
            accessToken
        )

        return JSONObject(text)
    }

    private fun postRawJson(
        path: String,
        body: JSONObject,
        accessToken: String?
    ): String {

        val response = request(
            "POST",
            path,
            accessToken,
            body.toString()
        )

        if (response.code !in 200..299) {
            throw IllegalStateException(
                parseError(response.body)
            )
        }

        return response.body
    }

    private data class Response(
        val code: Int,
        val body: String
    )

    private fun request(
        method: String,
        path: String,
        accessToken: String?,
        body: String?
    ): Response {

        check(isConfigured()) {
            "Backend is not configured. Check supabase.xml."
        }

        val connection =
            (URL(baseUrl + path)
                .openConnection() as HttpURLConnection)

        connection.requestMethod = method
        connection.connectTimeout = 12_000
        connection.readTimeout = 20_000
        connection.setRequestProperty(
            "apikey",
            apiKey
        )
        connection.setRequestProperty(
            "Accept",
            "application/json"
        )
        connection.setRequestProperty(
            "Content-Type",
            "application/json"
        )

        if (!accessToken.isNullOrBlank()) {
            connection.setRequestProperty(
                "Authorization",
                "Bearer $accessToken"
            )
        }

        if (body != null) {
            connection.doOutput = true
            connection.outputStream
                .bufferedWriter()
                .use { it.write(body) }
        }

        val code = connection.responseCode

        val stream =
            if (code in 200..299)
                connection.inputStream
            else
                connection.errorStream

        val text =
            stream?.bufferedReader()
                ?.use { it.readText() }
                .orEmpty()

        connection.disconnect()

        return Response(code, text)
    }

    private fun parseError(body: String): String {
        return try {
            val json = JSONObject(body)

            json.optString("message")
                .ifBlank {
                    json.optString("msg")
                }
                .ifBlank {
                    json.optString(
                        "error_description"
                    )
                }
                .ifBlank {
                    body
                }
        } catch (_: Exception) {
            body.ifBlank {
                "Backend request failed."
            }
        }
    }
}
