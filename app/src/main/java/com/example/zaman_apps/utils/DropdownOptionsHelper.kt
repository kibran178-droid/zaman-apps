package com.example.zaman_apps.utils

import android.content.Context
import com.example.zaman_apps.R
import org.json.JSONArray

object DropdownOptionsHelper {

    private const val PREF_NAME = "dropdown_options_prefs"

    const val KEY_EAS = "key_eas"
    const val KEY_MERK_GENSET = "key_merk_genset"
    const val KEY_STATUS_GENSET = "key_status_genset"
    const val KEY_TYPE_RECTIFIER = "key_type_rectifier"
    const val KEY_JENIS_BATERAI = "key_jenis_baterai"
    const val KEY_MERK_BATERAI = "key_merk_baterai"
    const val KEY_JUMLAH_BATTERAI = "key_jumlah_batterai"
    const val KEY_BALTING = "key_balting"
    const val KEY_JUMLAH_BANK_NOT_AKTIF = "key_jumlah_bank_not_aktif"

    fun getDefaultResId(key: String): Int {
        return when (key) {
            KEY_EAS -> R.array.status_options
            KEY_MERK_GENSET -> R.array.merk_genset_options
            KEY_STATUS_GENSET -> R.array.status_genset_options
            KEY_TYPE_RECTIFIER -> R.array.type_rectifier_options
            KEY_JENIS_BATERAI -> R.array.jenis_baterai_options
            KEY_MERK_BATERAI -> R.array.merk_baterai_options
            KEY_JUMLAH_BATTERAI -> R.array.jumlah_battery_options
            KEY_BALTING -> R.array.balting_options
            KEY_JUMLAH_BANK_NOT_AKTIF -> R.array.jumlah_bank_not_aktif_options
            else -> R.array.status_options
        }
    }

    fun getCategoryName(key: String): String {
        return when (key) {
            KEY_EAS -> "EAS (MainsFail, RectyFail, Stolen, LowBatt, EngRun)"
            KEY_MERK_GENSET -> "Merk Genset (Genset 1 & 2)"
            KEY_STATUS_GENSET -> "Status Genset (Genset 1 & 2)"
            KEY_TYPE_RECTIFIER -> "Type Rectifier (Recti 1 - 7)"
            KEY_JENIS_BATERAI -> "Jenis Baterai (Recti 1 - 7)"
            KEY_MERK_BATERAI -> "Merk Baterai (Recti 1 - 7)"
            KEY_JUMLAH_BATTERAI -> "Jumlah Baterai (Recti 1 - 7)"
            KEY_BALTING -> "Battery Recti Status (Balting Options)"
            KEY_JUMLAH_BANK_NOT_AKTIF -> "Jumlah Bank Not Aktif (Recti 1 - 7)"
            else -> "Dropdown Options"
        }
    }

    fun getMerkBateraiFiltered(jenisBaterai: String): List<String> {
        val jenisUpper = jenisBaterai.uppercase().trim()
        return when {
            jenisUpper.contains("LITHIUM") -> listOf("", "SACREDSUN", "ZTE", "SACREDSUN + ZTE")
            jenisUpper.contains("FLOATING") -> listOf("", "SACREDSUN", "MAXLIFE", "SONNENSCHEIN")
            jenisUpper.contains("SPS") -> listOf("", "SPS")
            jenisUpper.contains("NO BATTERY") || jenisUpper.contains("NOBATTERY") || jenisUpper == "NOL" -> listOf("", "NO BATTERY", "STOLEN", "-")
            else -> listOf("", "SACREDSUN", "ZTE", "MAXLIFE", "SONNENSCHEIN", "SACREDSUN + ZTE", "SPS", "NO BATTERY", "STOLEN", "-")
        }
    }

    fun getJumlahBatteryFiltered(jenisBaterai: String): List<String> {
        val jenisUpper = jenisBaterai.uppercase().trim()
        return when {
            jenisUpper.contains("LITHIUM") -> listOf("", "0", "1 pack", "2 pack", "3 pack", "4 pack", "5 pack", "6 pack", "7 pack", "8 pack", "9 pack", "10 pack")
            jenisUpper.contains("FLOATING") -> listOf("", "1 blok", "2 blok", "3 blok", "4 blok", "5 blok", "6 blok", "7 blok", "8 blok", "9 blok", "10 blok", "11 blok", "12 blok")
            jenisUpper.contains("SPS") -> listOf("", "SPS")
            jenisUpper.contains("NO BATTERY") || jenisUpper.contains("NOBATTERY") || jenisUpper == "NOL" -> listOf("", "NO BATTERY", "0", "STOLEN")
            else -> listOf("", "0", "1 pack", "2 pack", "3 pack", "4 pack", "5 pack", "6 pack", "7 pack", "8 pack", "9 pack", "10 pack", "1 blok", "2 blok", "3 blok", "4 blok", "5 blok", "6 blok", "7 blok", "8 blok", "9 blok", "10 blok", "11 blok", "12 blok", "SPS", "NO BATTERY", "STOLEN")
        }
    }

    fun getOptions(context: Context, key: String): List<String> {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val jsonStr = prefs.getString(key, null)

        if (!jsonStr.isNull_or_empty()) {
            try {
                val jsonArray = JSONArray(jsonStr)
                val list = mutableListOf<String>()
                for (i in 0 until jsonArray.length()) {
                    list.add(jsonArray.getString(i))
                }
                if (list.isNotEmpty()) return list
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        val resId = getDefaultResId(key)
        return context.resources.getStringArray(resId).toList()
    }

    fun saveOptions(context: Context, key: String, options: List<String>) {
        val jsonArray = JSONArray()
        options.forEach { jsonArray.put(it) }

        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(key, jsonArray.toString()).apply()
    }

    fun addOption(context: Context, key: String, newOption: String) {
        val current = getOptions(context, key).toMutableList()
        if (!current.contains(newOption.trim())) {
            current.add(newOption.trim())
            saveOptions(context, key, current)
        }
    }

    fun updateOption(context: Context, key: String, index: Int, newText: String) {
        val current = getOptions(context, key).toMutableList()
        if (index in 0 until current.size) {
            current[index] = newText.trim()
            saveOptions(context, key, current)
        }
    }

    fun deleteOption(context: Context, key: String, index: Int) {
        val current = getOptions(context, key).toMutableList()
        if (index in 0 until current.size) {
            current.removeAt(index)
            saveOptions(context, key, current)
        }
    }

    fun resetToDefault(context: Context, key: String) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove(key).apply()
    }

    private fun String?.isNull_or_empty(): Boolean = this == null || this.trim().isEmpty()
}
