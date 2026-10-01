package com.example.zaman_apps.utils

import android.content.Context

object SessionHelper {
    fun getUsername(context: Context): String {
        val prefs = context.getSharedPreferences("session", Context.MODE_PRIVATE)
        return prefs.getString("username", "") ?: ""
    }

    fun saveUsername(context: Context, username: String) {
        val prefs = context.getSharedPreferences("session", Context.MODE_PRIVATE)
        prefs.edit().putString("username", username).apply()
    }

    fun getName(context: Context): String {
        val prefs = context.getSharedPreferences("session", Context.MODE_PRIVATE)
        return prefs.getString("name", "") ?: ""
    }

    fun getRole(context: Context): String {
        val prefs = context.getSharedPreferences("session", Context.MODE_PRIVATE)
        return prefs.getString("role", "") ?: ""
    }

    fun canEdit(context: Context): Boolean {
        val prefs = context.getSharedPreferences("session", Context.MODE_PRIVATE)
        return prefs.getBoolean("can_edit", false)
    }

    fun logout(context: Context) {
        val prefs = context.getSharedPreferences("session", Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
    }
}
