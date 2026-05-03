package com.example.addsplayer

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputEditText
import com.example.addsplayer.BuildConfig

class SettingsActivity : AppCompatActivity() {

    companion object {
        const val PREFS_NAME = "addsplayer_settings"
        const val KEY_SERVER = "server"
        const val KEY_LOGIN = "login"
        const val KEY_PASSWORD = "password"
        const val KEY_POS_ID = "pos_id"

        fun getPrefs(context: Context): SharedPreferences =
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        /** Створення Basic Auth */
        fun createBasicAuth(login: String, password: String): String {
            if (login.isBlank() || password.isBlank()) return ""
            val credentials = "$login:$password"
            val encoded = android.util.Base64.encodeToString(credentials.toByteArray(), android.util.Base64.NO_WRAP)
            return "Basic $encoded"
        }
    }

    private lateinit var editServer: TextInputEditText
    private lateinit var editLogin: TextInputEditText
    private lateinit var editPassword: TextInputEditText
    private lateinit var editPosId: TextInputEditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        setContentView(R.layout.activity_settings)

        editServer = findViewById(R.id.edit_server_url)
        editLogin = findViewById(R.id.edit_login)
        editPassword = findViewById(R.id.edit_password)
        editPosId = findViewById(R.id.edit_pos_id)

        loadSettings()

        findViewById<Button>(R.id.btn_save).setOnClickListener {
            saveSettings()
        }
    }

    private fun loadSettings() {
        val prefs = getPrefs(this)

        // Використовуємо BuildConfig замість пошуку файлів по дисках
        editServer.setText(prefs.getString(KEY_SERVER, BuildConfig.DEFAULT_SERVER))
        editLogin.setText(prefs.getString(KEY_LOGIN, BuildConfig.DEFAULT_LOGIN))
        editPassword.setText(prefs.getString(KEY_PASSWORD, BuildConfig.DEFAULT_PASSWORD))
        editPosId.setText(prefs.getString(KEY_POS_ID, BuildConfig.DEFAULT_POS_ID))
    }

    private fun saveSettings() {
        val server = editServer.text.toString().trim()
        val login = editLogin.text.toString().trim()
        val password = editPassword.text.toString().trim()
        val posId = editPosId.text.toString().trim()

        getPrefs(this).edit()
            .putString(KEY_SERVER, server)
            .putString(KEY_LOGIN, login)
            .putString(KEY_PASSWORD, password)
            .putString(KEY_POS_ID, posId)
            .apply()

        Toast.makeText(this, "✅ Налаштування збережено", Toast.LENGTH_SHORT).show()
        finish()
    }
}