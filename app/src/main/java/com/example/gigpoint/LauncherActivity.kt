package com.example.gigpoint

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class LauncherActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        AppPreferences(this).apply()
        super.onCreate(savedInstanceState)

        val session = SessionManager(this)

        if (!session.hasSession()) {
            startActivity(
                Intent(
                    this,
                    LoginActivity::class.java
                )
            )
            finish()
            return
        }

        val userId = session.userId()

        if (userId.isNullOrBlank()) {
            session.clear()
            startActivity(
                Intent(
                    this,
                    LoginActivity::class.java
                )
            )
            finish()
            return
        }

        // Offline-first:
        // if we already know this authenticated merchant locally,
        // enter immediately even when the PC/cloud is unavailable.
        val db = DatabaseHelper(this)

        val destination =
            if (db.isMerchantSetupComplete(userId))
                MainActivity::class.java
            else
                BusinessSetupActivity::class.java

        db.close()

        startActivity(
            Intent(this, destination)
        )

        finish()
    }
}
