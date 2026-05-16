package com.prometheus.android.service

import android.content.Context
import android.content.SharedPreferences

object InjectionSettings {
    private const val PREFS_NAME = "prometheus_injection"
    private const val KEY_ENABLED = "injection_enabled"
    private const val KEY_IP = "injection_ip"
    private const val KEY_PORT = "injection_port"

    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    var enabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_ENABLED, value).apply()

    var ip: String
        get() = prefs.getString(KEY_IP, "") ?: ""
        set(value) = prefs.edit().putString(KEY_IP, value).apply()

    var port: Int
        get() = prefs.getInt(KEY_PORT, 8080)
        set(value) = prefs.edit().putInt(KEY_PORT, value).apply()

    val baseUrl: String?
        get() {
            if (!enabled || ip.isBlank()) return null
            return "http://$ip:$port"
        }
}
