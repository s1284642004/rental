package com.example.renthouseapp

import android.content.Context

class LoginSessionStore(context: Context) {
    private val prefs = context.getSharedPreferences("login_session", Context.MODE_PRIVATE)

    fun save(identity: LoginIdentity) {
        prefs.edit()
            .putString(KEY_LOGIN_NAME, identity.loginName)
            .putString(KEY_PHONE_NUMBER, identity.phoneNumber)
            .apply()
    }

    fun load(): LoginIdentity? {
        val loginName = prefs.getString(KEY_LOGIN_NAME, null).orEmpty()
        val phoneNumber = prefs.getString(KEY_PHONE_NUMBER, null).orEmpty()
        if (loginName.isBlank() || phoneNumber.isBlank()) return null
        return LoginIdentity(loginName = loginName, phoneNumber = phoneNumber)
    }

    companion object {
        private const val KEY_LOGIN_NAME = "login_name"
        private const val KEY_PHONE_NUMBER = "phone_number"
    }
}
