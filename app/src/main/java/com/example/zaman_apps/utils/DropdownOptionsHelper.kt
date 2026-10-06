package com.example.zaman_apps.utils

import android.content.Context
import com.android.volley.Request
import com.android.volley.toolbox.StringRequest
import com.example.zaman_apps.R
import org.json.JSONArray
import org.json.JSONObject

object DropdownOptionsHelper {

    private const val PREF_NAME = "dropdown_options_prefs"
    private val URL_SHEET = ServerConfig.SCRIPT_URL

    const val KEY_EAS = "key_eas"
    const val KEY_MERK_GENSET = "key_merk_genset"
    const val KEY_STATUS_GENSET = "key_status_genset"
    const val KEY_TYPE_RECTIFIER = "key_type_rectifier"
    const val KEY_JENIS_BATERAI = "key_jenis_baterai"
    const val KEY_MERK_BATERAI = "key_merk_baterai"
    const val KEY_JUMLAH_BATTERAI = "key_jumlah_batterai"
    const val KEY_BALTING = "key_balting"
    const val KEY_JUMLAH_BANK_NOT_AKTIF = "key_jumlah_bank_not_aktif"

    // Custom Mapping Keys
    const val KEY_BATERAI_MAPPING = "key_baterai_mapping"
    const val KEY_MAPPING_MERK_BATTERAI = "key_mapping_merk_baterai"
    const val KEY_MAPPING_JUMLAH_BATTERAI = "key_mapping_jumlah_baterai"

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
            KEY_BATERAI_MAPPING -> "Relasi Baterai (Jenis ➔ Merk & Jumlah)"
            else -> "Dropdown Options"
        }
    }

    fun getMerkBateraiFiltered(jenisBaterai: String): List<String> {
        val jenisUpper = jenisBaterai.uppercase().trim()
        return when {
            jenisUpper.contains("LITHIUM") -> listOf("", "SACREDSUN", "ZTE", "SACREDSUN+ZTE")
            jenisUpper.contains("FLOATING") -> listOf("", "SACREDSUN", "MAXLIFE", "SONENSCHEIN")
            jenisUpper.contains("SPS") -> listOf("", "SPS")
            jenisUpper.contains("NO BATTERY") || jenisUpper.contains("NOBATTERY") || jenisUpper == "NOL" -> listOf("", "No Battery")
            else -> listOf("", "SACREDSUN", "ZTE", "SACREDSUN+ZTE", "MAXLIFE", "SPS", "SONENSCHEIN", "No Battery")
        }
    }

    fun getJumlahBatteryFiltered(jenisBaterai: String): List<String> {
        val jenisUpper = jenisBaterai.uppercase().trim()
        return when {
            jenisUpper.contains("LITHIUM") -> listOf("", "0", "1 pack", "2 pack", "3 pack", "4 pack", "5 pack", "6 pack", "7 pack", "8 pack", "9 pack", "10 pack")
            jenisUpper.contains("FLOATING") -> listOf("", "1 blok", "2 blok", "3 blok", "4 blok", "5 blok", "6 blok", "7 blok", "8 blok", "9 blok", "10 blok", "11 blok", "12 blok")
            jenisUpper.contains("SPS") -> listOf("", "SPS")
            jenisUpper.contains("NO BATTERY") || jenisUpper.contains("NOBATTERY") || jenisUpper == "NOL" -> listOf("", "0")
            else -> listOf("", "0", "1 pack", "2 pack", "3 pack", "4 pack", "5 pack", "6 pack", "7 pack", "8 pack", "9 pack", "10 pack", "1 blok", "2 blok", "3 blok", "4 blok", "5 blok", "6 blok", "7 blok", "8 blok", "9 blok", "10 blok", "11 blok", "12 blok", "SPS")
        }
    }

    fun getMerkBateraiFiltered(context: Context, jenisBaterai: String): List<String> {
        val jenisTrimmed = jenisBaterai.trim()
        if (jenisTrimmed.isEmpty()) return getOptions(context, KEY_MERK_BATERAI)

        val customMapping = getMerkMappingForJenis(context, jenisTrimmed)
        if (customMapping.isNotEmpty()) {
            return listOf("") + customMapping
        }
        return getMerkBateraiFiltered(jenisTrimmed)
    }

    fun getJumlahBatteryFiltered(context: Context, jenisBaterai: String): List<String> {
        val jenisTrimmed = jenisBaterai.trim()
        if (jenisTrimmed.isEmpty()) return getOptions(context, KEY_JUMLAH_BATTERAI)

        val customMapping = getJumlahMappingForJenis(context, jenisTrimmed)
        if (customMapping.isNotEmpty()) {
            return listOf("") + customMapping
        }
        return getJumlahBatteryFiltered(jenisTrimmed)
    }

    fun getMerkMappingForJenis(context: Context, jenisBaterai: String): List<String> {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val jsonStr = prefs.getString(KEY_MAPPING_MERK_BATTERAI, null) ?: return emptyList()
        return try {
            val obj = JSONObject(jsonStr)
            val array = obj.optJSONArray(jenisBaterai.trim().uppercase()) ?: return emptyList()
            val list = mutableListOf<String>()
            for (i in 0 until array.length()) list.add(array.getString(i))
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun getJumlahMappingForJenis(context: Context, jenisBaterai: String): List<String> {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val jsonStr = prefs.getString(KEY_MAPPING_JUMLAH_BATTERAI, null) ?: return emptyList()
        return try {
            val obj = JSONObject(jsonStr)
            val array = obj.optJSONArray(jenisBaterai.trim().uppercase()) ?: return emptyList()
            val list = mutableListOf<String>()
            for (i in 0 until array.length()) list.add(array.getString(i))
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveMappingForJenis(
        context: Context,
        jenisBaterai: String,
        selectedMerkList: List<String>,
        selectedJumlahList: List<String>
    ) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val keyUpper = jenisBaterai.trim().uppercase()

        // 1. Save Merk Mapping
        val merkJsonStr = prefs.getString(KEY_MAPPING_MERK_BATTERAI, "{}") ?: "{}"
        val merkObj = try { JSONObject(merkJsonStr) } catch (_: Exception) { JSONObject() }
        val merkArr = JSONArray()
        selectedMerkList.forEach { merkArr.put(it) }
        merkObj.put(keyUpper, merkArr)
        prefs.edit().putString(KEY_MAPPING_MERK_BATTERAI, merkObj.toString()).apply()

        // 2. Save Jumlah Mapping
        val jmlJsonStr = prefs.getString(KEY_MAPPING_JUMLAH_BATTERAI, "{}") ?: "{}"
        val jmlObj = try { JSONObject(jmlJsonStr) } catch (_: Exception) { JSONObject() }
        val jmlArr = JSONArray()
        selectedJumlahList.forEach { jmlArr.put(it) }
        jmlObj.put(keyUpper, jmlArr)
        prefs.edit().putString(KEY_MAPPING_JUMLAH_BATTERAI, jmlObj.toString()).apply()

        // 3. Sync Mappings to Cloud
        syncOptionToCloud(context, KEY_MAPPING_MERK_BATTERAI, listOf(merkObj.toString()))
        syncOptionToCloud(context, KEY_MAPPING_JUMLAH_BATTERAI, listOf(jmlObj.toString()))
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

        syncOptionToCloud(context, key, options)
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

        // Sync reset to cloud
        val resId = getDefaultResId(key)
        val defaultList = context.resources.getStringArray(resId).toList()
        syncOptionToCloud(context, key, defaultList)
    }

    fun syncCloudOptions(context: Context, onComplete: (() -> Unit)? = null) {
        val queue = VolleyHelper.getRequestQueue(context)
        val url = "$URL_SHEET?action=get_dropdown_options"

        val request = StringRequest(
            Request.Method.GET, url,
            { response ->
                try {
                    val json = JSONObject(response)
                    if (json.optString("status") == "success") {
                        val optionsObj = json.optJSONObject("options")
                        if (optionsObj != null) {
                            val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit()
                            val keys = optionsObj.keys()
                            while (keys.hasNext()) {
                                val key = keys.next()
                                val array = optionsObj.getJSONArray(key)
                                prefs.putString(key, array.toString())
                            }
                            prefs.apply()
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    onComplete?.invoke()
                }
            },
            {
                onComplete?.invoke()
            }
        )
        VolleyHelper.apply30sTimeout(request)
        queue.add(request)
    }

    private fun syncOptionToCloud(context: Context, key: String, options: List<String>) {
        val queue = VolleyHelper.getRequestQueue(context)
        val request = object : StringRequest(
            Method.POST, URL_SHEET,
            { _ -> },
            { _ -> }
        ) {
            override fun getParams(): MutableMap<String, String> {
                val params = HashMap<String, String>()
                params["action"] = "save_dropdown_options"
                params["key"] = key
                val jsonArray = JSONArray()
                options.forEach { jsonArray.put(it) }
                params["options"] = jsonArray.toString()
                return params
            }
        }
        VolleyHelper.apply30sTimeout(request)
        queue.add(request)
    }

    private fun String?.isNull_or_empty(): Boolean = this == null || this.trim().isEmpty()
}
