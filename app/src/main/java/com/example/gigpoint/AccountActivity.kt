package com.example.gigpoint

import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import java.util.concurrent.Executors

class AccountActivity : AppCompatActivity() {

    private val executor =
        Executors.newSingleThreadExecutor()

    override fun onCreate(savedInstanceState: Bundle?) {
        AppPreferences(this).apply()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_account)

        supportActionBar?.title =
            "Account & Settings"

        val session =
            SessionManager(this)

        val userId =
            session.userId()

        if (userId.isNullOrBlank()) {
            goToLogin()
            return
        }

        val db =
            DatabaseHelper(this)

        val profile =
            db.getMerchantProfile(userId)

        val shop =
            db.getShopForOwner(userId)

        db.close()

        findViewById<TextView>(
            R.id.tvAccountName
        ).text =
            profile?.ownerName ?: "Merchant"

        findViewById<TextView>(
            R.id.tvAccountShop
        ).text =
            shop?.shopName ?: "Shop"

        findViewById<TextView>(
            R.id.tvAccountEmail
        ).text =
            session.email().orEmpty()

        val language =
            findViewById<Spinner>(
                R.id.spinnerAccountLanguage
            )

        language.adapter =
            ArrayAdapter(
                this,
                android.R.layout
                    .simple_spinner_dropdown_item,
                listOf(
                    "English",
                    "Telugu",
                    "Hindi"
                )
            )

        language.setSelection(
            when (profile?.preferredLanguage) {
                "te" -> 1
                "hi" -> 2
                else -> 0
            }
        )

        val theme =
            findViewById<Spinner>(
                R.id.spinnerAccountTheme
            )

        theme.adapter =
            ArrayAdapter(
                this,
                android.R.layout
                    .simple_spinner_dropdown_item,
                listOf(
                    "System",
                    "Light",
                    "Dark"
                )
            )

        theme.setSelection(
            when (profile?.theme) {
                "light" -> 1
                "dark" -> 2
                else -> 0
            }
        )

        findViewById<MaterialButton>(
            R.id.btnApplyPreferences
        ).setOnClickListener {
            AppPreferences(this)
                .setLanguage(
                    when (
                        language.selectedItemPosition
                    ) {
                        1 -> "te"
                        2 -> "hi"
                        else -> "en"
                    }
                )

            AppPreferences(this)
                .setTheme(
                    when (
                        theme.selectedItemPosition
                    ) {
                        1 -> "light"
                        2 -> "dark"
                        else -> "system"
                    }
                )
        }

        findViewById<MaterialButton>(
            R.id.btnLogout
        ).setOnClickListener {
            logout()
        }

        findViewById<MaterialButton>(
            R.id.btnDeleteAccount
        ).setOnClickListener {
            confirmDelete()
        }
    }

    private fun logout() {
        val session =
            SessionManager(this)

        val token =
            session.accessToken()

        session.clear()

        executor.execute {
            try {
                if (!token.isNullOrBlank()) {
                    BackendClient(this)
                        .logout(token)
                }
            } catch (_: Exception) {
                // Local logout still succeeds.
            }
        }

        goToLogin()
    }

    private fun confirmDelete() {
        AlertDialog.Builder(this)
            .setTitle("Delete DhwaniMitra account?")
            .setMessage(
                "This permanently deletes the cloud account and its business data. This action cannot be undone."
            )
            .setNegativeButton("Cancel", null)
            .setPositiveButton(
                "Delete account"
            ) { _, _ ->
                deleteAccount()
            }
            .show()
    }

    private fun deleteAccount() {
        val session =
            SessionManager(this)

        val token =
            session.accessToken()

        if (token.isNullOrBlank()) {
            goToLogin()
            return
        }

        executor.execute {
            try {
                BackendClient(this)
                    .deleteAccount(token)

                session.clear()

                runOnUiThread {
                    goToLogin()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    AlertDialog.Builder(this)
                        .setTitle(
                            "Account deletion failed"
                        )
                        .setMessage(
                            e.message
                                ?: "Could not delete account."
                        )
                        .setPositiveButton(
                            "OK",
                            null
                        )
                        .show()
                }
            }
        }
    }

    private fun goToLogin() {
        startActivity(
            Intent(
                this,
                LoginActivity::class.java
            ).apply {
                flags =
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
        )
        finish()
    }

    override fun onDestroy() {
        executor.shutdownNow()
        super.onDestroy()
    }
}
