package com.example.gigpoint

import android.content.Context
import org.json.JSONObject
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URI
import java.net.UnknownHostException
import java.net.URL
import javax.net.ssl.SSLHandshakeException

class BackendClient(
    private val context: Context
) {

    private val baseUrl =
        context.getString(
            R.string.supabase_url
        )
            .trim()
            .trimEnd('/')

    private val apiKey =
        context.getString(
            R.string.supabase_publishable_key
        )
            .trim()

    data class HealthResult(
        val ok: Boolean,
        val endpoint: String,
        val message: String
    )

    fun isConfigured(): Boolean {
        if (
            baseUrl.isBlank() ||
            apiKey.isBlank() ||
            apiKey.startsWith("YOUR_")
        ) {
            return false
        }

        val uri =
            try {
                URI(baseUrl)
            } catch (_: Exception) {
                return false
            }

        val scheme =
            uri.scheme
                ?.lowercase()
                ?: return false

        val host =
            uri.host
                ?.lowercase()
                ?: return false

        val remoteHttps =
            scheme == "https"

        val localDebug =
            BuildConfig.DEBUG &&
                scheme == "http" &&
                host in setOf(
                    "127.0.0.1",
                    "localhost",
                    "10.0.2.2"
                )

        return (
            remoteHttps ||
                localDebug
            ) &&
            apiKey.startsWith(
                "sb_publishable_"
            )
    }

    fun endpointDescription():
        String =
        baseUrl.ifBlank {
            "not configured"
        }

    fun healthCheck():
        HealthResult {
        if (!isConfigured()) {
            return HealthResult(
                ok = false,
                endpoint =
                    endpointDescription(),
                message =
                    "Backend configuration is invalid. Use a hosted HTTPS Supabase URL with an sb_publishable_ key, or local loopback only in a debug build."
            )
        }

        return try {
            val response =
                request(
                    method = "GET",
                    path = "/auth/v1/health",
                    accessToken = null,
                    body = null
                )

            if (
                response.code in
                200..299
            ) {
                HealthResult(
                    true,
                    baseUrl,
                    "Supabase Auth is reachable."
                )
            } else {
                HealthResult(
                    false,
                    baseUrl,
                    "Backend responded with HTTP ${response.code}: ${parseError(response.body)}"
                )
            }
        } catch (
            e: Exception
        ) {
            HealthResult(
                false,
                baseUrl,
                e.message
                    ?: "Backend health check failed."
            )
        }
    }

    fun login(
        email: String,
        password: String
    ): AuthSession {
        val json =
            postJson(
                "/auth/v1/token?grant_type=password",
                JSONObject()
                    .put(
                        "email",
                        email
                    )
                    .put(
                        "password",
                        password
                    ),
                null
            )

        return parseAuthSession(
            json,
            email
        )
    }

    fun signUp(
        email: String,
        password: String
    ): AuthSession? {
        val json =
            postJson(
                "/auth/v1/signup",
                JSONObject()
                    .put(
                        "email",
                        email
                    )
                    .put(
                        "password",
                        password
                    ),
                null
            )

        val token =
            json.optString(
                "access_token"
            )

        if (
            token.isBlank()
        ) {
            return null
        }

        return parseAuthSession(
            json,
            email
        )
    }

    fun refreshSession(
        refreshToken: String
    ): AuthSession {
        val json =
            postJson(
                "/auth/v1/token?grant_type=refresh_token",
                JSONObject()
                    .put(
                        "refresh_token",
                        refreshToken
                    ),
                null
            )

        val user =
            json.getJSONObject(
                "user"
            )

        return AuthSession(
            userId =
                user.getString(
                    "id"
                ),
            email =
                user.optString(
                    "email"
                ),
            accessToken =
                json.getString(
                    "access_token"
                ),
            refreshToken =
                json.optString(
                    "refresh_token"
                )
                    .takeIf {
                        it.isNotBlank()
                    }
        )
    }

    fun getMyContext(
        accessToken: String
    ): MerchantContext {
        val json =
            postJson(
                "/rest/v1/rpc/get_my_context",
                JSONObject(),
                accessToken
            )

        val profileJson =
            json.optJSONObject(
                "profile"
            )

        val shopJson =
            json.optJSONObject(
                "shop"
            )

        val profile =
            profileJson
                ?.let {
                    MerchantProfile(
                        userId =
                            it.getString(
                                "id"
                            ),
                        ownerName =
                            it.optString(
                                "owner_name"
                            ),
                        phone =
                            it.optString(
                                "phone"
                            )
                                .takeIf(
                                    String::isNotBlank
                                ),
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
            shopJson
                ?.let {
                    ShopProfile(
                        id =
                            it.getString(
                                "id"
                            ),
                        ownerId =
                            it.getString(
                                "owner_id"
                            ),
                        shopName =
                            it.optString(
                                "shop_name"
                            ),
                        gstin =
                            it.optString(
                                "gstin"
                            )
                                .takeIf(
                                    String::isNotBlank
                                ),
                        businessType =
                            it.optString(
                                "business_type"
                            ),
                        city =
                            it.optString(
                                "city"
                            )
                                .takeIf(
                                    String::isNotBlank
                                ),
                        area =
                            it.optString(
                                "area"
                            )
                                .takeIf(
                                    String::isNotBlank
                                )
                    )
                }

        return MerchantContext(
            profile =
                profile,
            shop =
                shop,
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
        val body =
            JSONObject()
                .put(
                    "p_owner_name",
                    profile.ownerName
                )
                .put(
                    "p_shop_name",
                    shop.shopName
                )
                .put(
                    "p_business_type",
                    shop.businessType
                )
                .put(
                    "p_preferred_language",
                    profile.preferredLanguage
                )
                .put(
                    "p_theme",
                    profile.theme
                )
                .put(
                    "p_phone",
                    profile.phone
                        ?: JSONObject.NULL
                )
                .put(
                    "p_gstin",
                    shop.gstin
                        ?: JSONObject.NULL
                )
                .put(
                    "p_city",
                    shop.city
                        ?: JSONObject.NULL
                )
                .put(
                    "p_area",
                    shop.area
                        ?: JSONObject.NULL
                )
                .put(
                    "p_shop_id",
                    shop.id
                )

        return postRawJson(
            "/rest/v1/rpc/complete_business_setup",
            body,
            accessToken
        )
            .trim()
            .trim('"')
    }

    fun getDashboardSummary(
        accessToken: String,
        shopId: String
    ): JSONObject =
        postJson(
            "/rest/v1/rpc/get_dashboard_summary",
            JSONObject()
                .put(
                    "p_shop_id",
                    shopId
                ),
            accessToken
        )

    fun logout(
        accessToken: String
    ) {
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
        val response =
            request(
                "POST",
                "/functions/v1/delete-account",
                accessToken,
                "{}"
            )

        if (
            response.code !in
            200..299
        ) {
            throw IllegalStateException(
                parseError(
                    response.body
                )
            )
        }
    }

    private fun parseAuthSession(
        json: JSONObject,
        fallbackEmail: String
    ): AuthSession {
        val user =
            json.getJSONObject(
                "user"
            )

        return AuthSession(
            userId =
                user.getString(
                    "id"
                ),
            email =
                user.optString(
                    "email",
                    fallbackEmail
                ),
            accessToken =
                json.getString(
                    "access_token"
                ),
            refreshToken =
                json.optString(
                    "refresh_token"
                )
                    .takeIf {
                        it.isNotBlank()
                    }
        )
    }

    private fun postJson(
        path: String,
        body: JSONObject,
        accessToken: String?
    ): JSONObject =
        JSONObject(
            postRawJson(
                path,
                body,
                accessToken
            )
        )

    private fun postRawJson(
        path: String,
        body: JSONObject,
        accessToken: String?
    ): String {
        val response =
            request(
                "POST",
                path,
                accessToken,
                body.toString()
            )

        if (
            response.code !in
            200..299
        ) {
            throw IllegalStateException(
                parseError(
                    response.body
                )
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
        check(
            isConfigured()
        ) {
            "Backend is not configured. The app is currently pointing to '$baseUrl'. Configure the hosted Supabase Project URL and publishable key."
        }

        val target =
            baseUrl +
                path

        val connection =
            try {
                (
                    URL(
                        target
                    )
                        .openConnection()
                    as
                    HttpURLConnection
                    )
            } catch (
                e: Exception
            ) {
                throw backendException(
                    target,
                    e
                )
            }

        try {
            connection.requestMethod =
                method

            connection.connectTimeout =
                12_000

            connection.readTimeout =
                20_000

            connection.instanceFollowRedirects =
                false

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

            if (
                !accessToken
                    .isNullOrBlank()
            ) {
                connection.setRequestProperty(
                    "Authorization",
                    "Bearer $accessToken"
                )
            }

            if (
                body !=
                null
            ) {
                connection.doOutput =
                    true

                connection
                    .outputStream
                    .bufferedWriter()
                    .use {
                        it.write(
                            body
                        )
                    }
            }

            val code =
                connection.responseCode

            val stream =
                if (
                    code in
                    200..299
                ) {
                    connection.inputStream
                } else {
                    connection.errorStream
                }

            val text =
                stream
                    ?.bufferedReader()
                    ?.use {
                        it.readText()
                    }
                    .orEmpty()

            return Response(
                code,
                text
            )

        } catch (
            e: Exception
        ) {
            throw backendException(
                target,
                e
            )

        } finally {
            connection.disconnect()
        }
    }

    private fun backendException(
        target: String,
        cause: Exception
    ): IllegalStateException {
        val message =
            when (
                cause
            ) {
                is ConnectException ->
                    if (
                        baseUrl.contains(
                            "127.0.0.1"
                        ) ||
                        baseUrl.contains(
                            "localhost"
                        )
                    ) {
                        "Cannot reach local Supabase at $baseUrl. On a physical phone, 127.0.0.1 is the phone itself. Use the hosted Supabase URL for normal app use, or create an ADB reverse tunnel for local development."
                    } else {
                        "Could not connect to $baseUrl."
                    }

                is UnknownHostException ->
                    "The Supabase host could not be resolved. Check the Project URL and internet connection."

                is SocketTimeoutException ->
                    "The backend timed out while connecting to $baseUrl."

                is SSLHandshakeException ->
                    "Secure connection to Supabase failed. Check that the Project URL uses HTTPS and the device date/time is correct."

                else ->
                    cause.message
                        ?: "Backend request failed for $target."
            }

        return IllegalStateException(
            message,
            cause
        )
    }

    private fun parseError(
        body: String
    ): String =
        try {
            val json =
                JSONObject(
                    body
                )

            json.optString(
                "message"
            )
                .ifBlank {
                    json.optString(
                        "msg"
                    )
                }
                .ifBlank {
                    json.optString(
                        "error_description"
                    )
                }
                .ifBlank {
                    json.optString(
                        "error"
                    )
                }
                .ifBlank {
                    body
                }
        } catch (
            _: Exception
        ) {
            body.ifBlank {
                "Backend request failed."
            }
        }
}
