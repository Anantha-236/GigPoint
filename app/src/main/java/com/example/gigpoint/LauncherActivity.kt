package com.example.gigpoint

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.gigpoint.data.DatabaseHelper

class LauncherActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        AppPreferences(this).apply()

        super.onCreate(savedInstanceState)

        val session = SessionManager(this)

        if (!session.hasSession()) {
            openLogin()
            return
        }

        val userId = session.userId()

        if (userId.isNullOrBlank()) {
            session.clear()
            openLogin()
            return
        }

        val db = DatabaseHelper(this)

        val setupComplete =
            try {
                db.isMerchantSetupComplete(userId)
            } finally {
                db.close()
            }

        val destination =
            if (setupComplete) {
                MainActivity::class.java
            } else {
                BusinessSetupActivity::class.java
            }

        startActivity(
            Intent(
                this,
                destination
            )
        )

        finish()
    }

    private fun openLogin() {
        startActivity(
            Intent(
                this,
                LoginActivity::class.java
            )
        )

        finish()
    }
}