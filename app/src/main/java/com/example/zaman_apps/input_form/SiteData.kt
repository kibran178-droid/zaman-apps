package com.example.zaman_apps.input_form

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class SiteData(
    var siteId: String = "",
    var siteName: String = "",
    var mainsFail: String = "",
    var rectyFail: String = "",
    var stolen: String = "",
    var lowBatt: String = "",
    var engRun: String = "",
    var rV: String = "",
    var sV: String = "",
    var tV: String = "",
    var rA: String = "",
    var sA: String = "",
    var tA: String = "",
    // ===== GENSET 1 & 2 =====
    var merkGenset1: String = "",
    var capacityGenset1: String = "",
    var statusGenset1: String = "",
    var remarkGenset1: String = "",
    var merkGenset2: String = "",
    var capacityGenset2: String = "",
    var statusGenset2: String = "",
    var remarkGenset2: String = "",
    var adaGenset2: Boolean = false,
    // Rectifier 1
    var typeRectifier1: String = "",
    var jumlahModul1: String = "",
    var jumlahModulRusak1: String = "",
    var jenisBaterai1: String = "",
    var merkBaterai1: String = "",
    var jumlahBatteryP1: String = "",
    var batteryRecti1: String = "",
    var busVoltage1: String = "",
    var loadA1: String = "",
    var perangkatTerhubung1: String = "",
    var remark1: String = "",
    // Rectifier 2
    var typeRectifier2: String = "",
    var jumlahModul2: String = "",
    var jumlahModulRusak2: String = "",
    var jenisBaterai2: String = "",
    var merkBaterai2: String = "",
    var jumlahBatteryP2: String = "",
    var batteryRecti2: String = "",
    var busVoltage2: String = "",
    var loadA2: String = "",
    var perangkatTerhubung2: String = "",
    var remark2: String = "",
    // Rectifier 3
    var typeRectifier3: String = "",
    var jumlahModul3: String = "",
    var jumlahModulRusak3: String = "",
    var jumlahBatteryP3: String = "",
    var batteryRecti3: String = "",
    var busVoltage3: String = "",
    var loadA3: String = "",
    var perangkatTerhubung3: String = "",
    var adaRecti2: Boolean = false,
    var adaRecti3: Boolean = false,
    // Input Gambar (sampai 10 foto)
    val imageUris: ArrayList<String> = arrayListOf()
) : Parcelable
