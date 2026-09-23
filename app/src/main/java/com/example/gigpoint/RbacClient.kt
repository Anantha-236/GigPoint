package com.example.gigpoint

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Retrieves shop-scoped access calculated by PostgreSQL. */
class RbacClient(private val context: Context) {

    private val baseUrl =
        context.getString(R.string.supabase_url).trim().trimEnd('/')

    private val apiKey =
        context.getString(R.string.supabase_publishable_key).trim()

    fun getMyAccess(accessToken: String, shopId: String): ShopAccess {
        require(accessToken.isNotBlank()) { "Access token is required." }
        require(shopId.isNotBlank()) { "Shop ID is required." }

        val text = post(
            path = "/rest/v1/rpc/get_my_access",
            accessToken = accessToken,
            body = JSONObject().put("p_shop_id", shopId).toString()
        )

        val json = JSONObject(text)
        val permissionsJson = json.optJSONArray("permissions") ?: JSONArray()
        val permissions = buildSet {
            for (index in 0 until permissionsJson.length()) {
                val value = permissionsJson.optString(index).trim()
                if (value.isNotBlank()) add(value)
            }
        }

        return ShopAccess(
            shopId = json.getString("shop_id"),
            membershipId = json.optString("membership_id")
                .takeIf { it.isNotBlank() && it != "null" },
            roleCode = json.optString("role", "UNKNOWN"),
            isOwner = json.optBoolean("is_owner", false),
            permissions = permissions
        )
    }

    private fun post(path: String, accessToken: String, body: String): String {
        val connection = URL(baseUrl + path).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 12_000
            connection.readTimeout = 20_000
            connection.doOutput = true
            connection.setRequestProperty("apikey", apiKey)
            connection.setRequestProperty("Authorization", "Bearer $accessToken")
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Content-Type", "application/json")
            connection.outputStream.bufferedWriter().use { it.write(body) }

            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val response = stream?.bufferedReader()?.use { it.readText() }.orEmpty()

            if (status !in 200..299) {
                throw IllegalStateException(parseError(response))
            }
            return response
        } finally {
            connection.disconnect()
        }
    }

    private fun parseError(body: String): String =
        try {
            val json = JSONObject(body)
            json.optString("message")
                .ifBlank { json.optString("error_description") }
                .ifBlank { json.optString("hint") }
                .ifBlank { body }
                .ifBlank { "RBAC request failed." }
        } catch (_: Exception) {
            body.ifBlank { "RBAC request failed." }
        }
}
