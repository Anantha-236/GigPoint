package com.example.gigpoint

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import java.util.concurrent.Executors

class SignupActivity : AppCompatActivity() {

    private val executor =
        Executors.newSingleThreadExecutor()

    override fun onCreate(savedInstanceState: Bundle?) {
        AppPreferences(this).apply()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_signup)

        supportActionBar?.title = "Create account"

        val email =
            findViewById<TextInputEditText>(
                R.id.etSignupEmail
            )

        val password =
            findViewById<TextInputEditText>(
                R.id.etSignupPassword
            )

        val confirm =
            findViewById<TextInputEditText>(
                R.id.etSignupConfirm
            )

        val status =
            findViewById<TextView>(
                R.id.tvSignupStatus
            )

        val button =
            findViewById<MaterialButton>(
                R.id.btnSignup
            )

        button.setOnClickListener {
            val emailText =
                email.text?.toString()
                    ?.trim()
                    .orEmpty()

            val passwordText =
                password.text?.toString()
                    .orEmpty()

            val confirmText =
                confirm.text?.toString()
                    .orEmpty()

            when {
                emailText.isBlank() ||
                passwordText.isBlank() -> {
                    status.text =
                        "Enter email and password."
                }

                passwordText.length < 8 -> {
                    status.text =
                        "Password must have at least 8 characters."
                }

                passwordText != confirmText -> {
                    status.text =
                        "Passwords do not match."
                }

                else -> {
                    button.isEnabled = false
                    status.text =
                        "Creating account…"

                    executor.execute {
                        try {
                            val session =
                                BackendClient(this)
                                    .signUp(
                                        emailText,
                                        passwordText
                                    )

                            runOnUiThread {
                                button.isEnabled = true

                                if (session == null) {
                                    status.text =
                                        "Verify your email, then login."
                                    return@runOnUiThread
                                }

                                SessionManager(this)
                                    .save(session)

                                startActivity(
                                    Intent(
                                        this,
                                        BusinessSetupActivity::class.java
                                    )
                                )

                                finish()
                            }
                        } catch (e: Exception) {
                            runOnUiThread {
                                button.isEnabled = true
                                status.text =
                                    e.message
                                        ?: "Signup failed."
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        executor.shutdownNow()
        super.onDestroy()
    }
}
