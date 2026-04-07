package com.example.renthouseapp

import android.content.Context

data class LoginContext(val loginName: String, val phoneNumber: String)

object LoginSessionStore {
    private const val PREF = "rent_house_login"
    private const val KEY_NAME = "login_name"
    private const val KEY_PHONE = "phone_number"

    fun save(context: Context, loginName: String, phoneNumber: String) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_NAME, loginName)
            .putString(KEY_PHONE, phoneNumber)
            .apply()
    }

    fun load(context: Context): LoginContext? {
        val sp = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val name = sp.getString(KEY_NAME, null)
        val phone = sp.getString(KEY_PHONE, null)
        return if (name.isNullOrBlank() || phone.isNullOrBlank()) null else LoginContext(name, phone)
    }
}
