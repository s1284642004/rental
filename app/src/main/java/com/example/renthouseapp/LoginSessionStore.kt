package com.example.renthouseapp

import android.content.Context

data class LoginSession(
    val loginName: String,
    val phoneNumber: String
)

class LoginSessionStore(context: Context) {
    private val prefs = context.getSharedPreferences("login_session", Context.MODE_PRIVATE)

    fun save(session: LoginSession) {
        prefs.edit()
            .putString(KEY_LOGIN_NAME, session.loginName)
            .putString(KEY_PHONE_NUMBER, session.phoneNumber)
            .apply()
    }

    fun get(): LoginSession? {
        val name = prefs.getString(KEY_LOGIN_NAME, null)?.trim().orEmpty()
        val phone = prefs.getString(KEY_PHONE_NUMBER, null)?.trim().orEmpty()
        if (name.isBlank() || phone.isBlank()) return null
        return LoginSession(name, phone)
    }

    companion object {
        private const val KEY_LOGIN_NAME = "login_name"
        private const val KEY_PHONE_NUMBER = "phone_number"
    }
}
