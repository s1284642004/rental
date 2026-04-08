package com.example.renthouseapp

import android.content.Context

data class CurrentLoginUser(
    val loginName: String,
    val phoneNumber: String
)

class LoginSessionStore(context: Context) {
    private val prefs = context.getSharedPreferences("login_session", Context.MODE_PRIVATE)

    fun saveUser(user: CurrentLoginUser) {
        prefs.edit()
            .putString(KEY_LOGIN_NAME, user.loginName)
            .putString(KEY_PHONE_NUMBER, user.phoneNumber)
            .apply()
    }

    fun getUser(): CurrentLoginUser? {
        val loginName = prefs.getString(KEY_LOGIN_NAME, null)
        val phoneNumber = prefs.getString(KEY_PHONE_NUMBER, null)
        if (loginName.isNullOrBlank() || phoneNumber.isNullOrBlank()) return null
        return CurrentLoginUser(loginName = loginName, phoneNumber = phoneNumber)
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val KEY_LOGIN_NAME = "key_login_name"
        private const val KEY_PHONE_NUMBER = "key_phone_number"
    }
}
