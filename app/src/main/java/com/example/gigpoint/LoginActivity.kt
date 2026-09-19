package com.example.gigpoint

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import java.util.concurrent.Executors

class LoginActivity : AppCompatActivity() {

    private val executor =
        Executors.newSingleThreadExecutor()

    private lateinit var email: TextInputEditText
    private lateinit var password: TextInputEditText
    private lateinit var status: TextView
    private lateinit var login: MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        AppPreferences(this).apply()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        supportActionBar?.title =
            getString(R.string.app_name)

        email =
            findViewById(R.id.etLoginEmail)

        password =
            findViewById(R.id.etLoginPassword)

        status =
            findViewById(R.id.tvLoginStatus)

        login =
            findViewById(R.id.btnLogin)

        login.setOnClickListener {
            performLogin()
        }

        findViewById<TextView>(
            R.id.tvCreateAccount
        ).setOnClickListener {
            startActivity(
                Intent(
                    this,
                    SignupActivity::class.java
                )
            )
        }
    }

    private fun performLogin() {
        val emailText =
            email.text?.toString()?.trim().orEmpty()

        val passwordText =
            password.text?.toString().orEmpty()

        if (
            emailText.isBlank() ||
            passwordText.isBlank()
        ) {
            status.text =
                "Enter email and password."
            return
        }

        setLoading(true)

        executor.execute {
            try {
                val api = BackendClient(this)

                val session =
                    api.login(
                        emailText,
                        passwordText
                    )

                SessionManager(this)
                    .save(session)

                val context =
                    api.getMyContext(
                        session.accessToken
                    )

                val db =
                    DatabaseHelper(this)

                context.profile?.let {
                    db.saveMerchantProfile(it)

                    AppPreferences(this)
                        .setLanguage(
                            it.preferredLanguage
                        )

                    AppPreferences(this)
                        .setTheme(it.theme)
                }

                context.shop?.let {
                    db.saveShop(it)
                }

                db.close()

                runOnUiThread {
                    setLoading(false)

                    startActivity(
                        Intent(
                            this,
                            if (context.setupComplete)
                                MainActivity::class.java
                            else
                                BusinessSetupActivity::class.java
                        )
                    )

                    finish()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    setLoading(false)
                    status.text =
                        e.message ?: "Login failed."
                }
            }
        }
    }

    private fun setLoading(value: Boolean) {
        login.isEnabled = !value
        status.text =
            if (value)
                "Connecting to DhwaniMitra backend…"
            else
                ""
    }

    override fun onDestroy() {
        executor.shutdownNow()
        super.onDestroy()
    }
}
