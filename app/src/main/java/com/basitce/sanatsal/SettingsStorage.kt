package com.basitce.sanatsal

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson

class SettingsStorage(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("fluid_settings", Context.MODE_PRIVATE)
    private val gson = Gson()

    fun save(settings: FluidSettings) {
        val json = gson.toJson(settings)
        prefs.edit().putString("settings_json", json).apply()
    }

    fun load(): FluidSettings {
        val json = prefs.getString("settings_json", null)
        return if (json != null) {
            try {
                gson.fromJson(json, FluidSettings::class.java)
            } catch (e: Exception) {
                FluidSettings()
            }
        } else {
            FluidSettings()
        }
    }
}
