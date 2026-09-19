package com.example.gigpoint

import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import java.util.UUID
import java.util.concurrent.Executors

class BusinessSetupActivity : AppCompatActivity() {

    private val executor =
        Executors.newSingleThreadExecutor()

    override fun onCreate(savedInstanceState: Bundle?) {
        AppPreferences(this).apply()
        super.onCreate(savedInstanceState)
        setContentView(
            R.layout.activity_business_setup
        )

        supportActionBar?.title =
            "Business Setup"

        val owner =
            findViewById<TextInputEditText>(
                R.id.etOwnerName
            )

        val shop =
            findViewById<TextInputEditText>(
                R.id.etShopName
            )

        val gstin =
            findViewById<TextInputEditText>(
                R.id.etGstin
            )

        val city =
            findViewById<TextInputEditText>(
                R.id.etCity
            )

        val area =
            findViewById<TextInputEditText>(
                R.id.etArea
            )

        val business =
            findViewById<Spinner>(
                R.id.spinnerBusinessType
            )

        val language =
            findViewById<Spinner>(
                R.id.spinnerLanguage
            )

        val theme =
            findViewById<Spinner>(
                R.id.spinnerTheme
            )

        val status =
            findViewById<TextView>(
                R.id.tvSetupStatus
            )

        val save =
            findViewById<MaterialButton>(
                R.id.btnSaveBusiness
            )

        business.adapter =
            ArrayAdapter(
                this,
                android.R.layout
                    .simple_spinner_dropdown_item,
                listOf(
                    "Grocery / Kirana",
                    "General Store",
                    "Medical Store",
                    "Bakery",
                    "Fruits & Vegetables",
                    "Hardware",
                    "Clothing",
                    "Restaurant / Food",
                    "Wholesale",
                    "Other"
                )
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

        val userId =
            SessionManager(this).userId()

        if (userId.isNullOrBlank()) {
            goToLogin()
            return
        }

        val db = DatabaseHelper(this)

        db.getMerchantProfile(userId)?.let {
            owner.setText(it.ownerName)

            language.setSelection(
                when (
                    it.preferredLanguage
                ) {
                    "te" -> 1
                    "hi" -> 2
                    else -> 0
                }
            )

            theme.setSelection(
                when (it.theme) {
                    "light" -> 1
                    "dark" -> 2
                    else -> 0
                }
            )
        }

        db.getShopForOwner(userId)?.let {
            shop.setText(it.shopName)
            gstin.setText(it.gstin.orEmpty())
            city.setText(it.city.orEmpty())
            area.setText(it.area.orEmpty())
        }

        db.close()

        save.setOnClickListener {
            val ownerName =
                owner.text?.toString()
                    ?.trim()
                    .orEmpty()

            val shopName =
                shop.text?.toString()
                    ?.trim()
                    .orEmpty()

            if (
                ownerName.isBlank() ||
                shopName.isBlank()
            ) {
                status.text =
                    "Owner name and shop name are required."
                return@setOnClickListener
            }

            val languageCode =
                when (
                    language.selectedItemPosition
                ) {
                    1 -> "te"
                    2 -> "hi"
                    else -> "en"
                }

            val themeCode =
                when (theme.selectedItemPosition) {
                    1 -> "light"
                    2 -> "dark"
                    else -> "system"
                }

            val existingDb =
                DatabaseHelper(this)

            val existingShop =
                existingDb.getShopForOwner(
                    userId
                )

            val profile =
                MerchantProfile(
                    userId = userId,
                    ownerName = ownerName,
                    phone = null,
                    preferredLanguage =
                        languageCode,
                    theme = themeCode,
                    syncStatus =
                        DatabaseHelper.SYNC_PENDING
                )

            val shopProfile =
                ShopProfile(
                    id =
                        existingShop?.id
                            ?: UUID.randomUUID()
                                .toString(),
                    ownerId = userId,
                    shopName = shopName,
                    gstin =
                        gstin.text?.toString()
                            ?.trim()
                            ?.takeIf {
                                it.isNotBlank()
                            },
                    businessType =
                        business.selectedItem
                            .toString(),
                    city =
                        city.text?.toString()
                            ?.trim()
                            ?.takeIf {
                                it.isNotBlank()
                            },
                    area =
                        area.text?.toString()
                            ?.trim()
                            ?.takeIf {
                                it.isNotBlank()
                            },
                    syncStatus =
                        DatabaseHelper.SYNC_PENDING
                )

            // Local first.
            existingDb.saveMerchantProfile(
                profile
            )

            existingDb.saveShop(
                shopProfile
            )

            existingDb.close()

            AppPreferences(this)
                .setLanguage(languageCode)

            AppPreferences(this)
                .setTheme(themeCode)

            save.isEnabled = false
            status.text =
                "Saving business details…"

            executor.execute {
                try {
                    val session =
                        SessionManager(this)

                    val token =
                        session.accessToken()
                            ?: error(
                                "Login session is missing."
                            )

                    BackendClient(this)
                        .completeBusinessSetup(
                            token,
                            profile,
                            shopProfile
                        )

                    val syncedDb =
                        DatabaseHelper(this)

                    syncedDb
                        .markMerchantSetupSynced(
                            userId
                        )

                    syncedDb.close()

                    runOnUiThread {
                        showWelcome(
                            ownerName,
                            shopName
                        )
                    }
                } catch (e: Exception) {
                    // Do not throw away merchant-entered data.
                    // It remains cached locally and can be synced later.
                    runOnUiThread {
                        save.isEnabled = true
                        status.text =
                            "Saved on this phone. Cloud backup failed: " +
                            (e.message ?: "unknown error")

                        showWelcome(
                            ownerName,
                            shopName
                        )
                    }
                }
            }
        }
    }

    private fun showWelcome(
        ownerName: String,
        shopName: String
    ) {
        AlertDialog.Builder(this)
            .setTitle(
                "Welcome to DhwaniMitra, $ownerName!"
            )
            .setMessage(
                "From now on, I'm your companion at $shopName. " +
                "I'll help you manage stock, purchases, sales and the items that need your attention."
            )
            .setCancelable(false)
            .setPositiveButton("Start") { _, _ ->
                startActivity(
                    Intent(
                        this,
                        MainActivity::class.java
                    )
                )
                finish()
            }
            .show()
    }

    private fun goToLogin() {
        SessionManager(this).clear()
        startActivity(
            Intent(
                this,
                LoginActivity::class.java
            )
        )
        finish()
    }

    override fun onDestroy() {
        executor.shutdownNow()
        super.onDestroy()
    }
}
