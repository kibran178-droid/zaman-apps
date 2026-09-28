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
    // ===== RECTIFIER 1 =====
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
    var mcbBatteryRecti1: String = "",
    var kondisiArester1: String = "",

    // ===== RECTIFIER 2 =====
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
    var mcbBatteryRecti2: String = "",
    var kondisiArester2: String = "",

    // ===== RECTIFIER 3 =====
    var typeRectifier3: String = "",
    var jumlahModul3: String = "",
    var jumlahModulRusak3: String = "",
    var jumlahBatteryP3: String = "",
    var batteryRecti3: String = "",
    var busVoltage3: String = "",
    var loadA3: String = "",
    var perangkatTerhubung3: String = "",
    var mcbBatteryRecti3: String = "",
    var kondisiArester3: String = "",

    // ===== RECTIFIER 4 =====
    var typeRectifier4: String = "",
    var jumlahModul4: String = "",
    var jumlahModulRusak4: String = "",
    var jumlahBatteryP4: String = "",
    var batteryRecti4: String = "",
    var busVoltage4: String = "",
    var loadA4: String = "",
    var perangkatTerhubung4: String = "",
    var mcbBatteryRecti4: String = "",
    var kondisiArester4: String = "",

    // ===== RECTIFIER 5 =====
    var typeRectifier5: String = "",
    var jumlahModul5: String = "",
    var jumlahModulRusak5: String = "",
    var jumlahBatteryP5: String = "",
    var batteryRecti5: String = "",
    var busVoltage5: String = "",
    var loadA5: String = "",
    var perangkatTerhubung5: String = "",
    var mcbBatteryRecti5: String = "",
    var kondisiArester5: String = "",

    // ===== RECTIFIER 6 =====
    var typeRectifier6: String = "",
    var jumlahModul6: String = "",
    var jumlahModulRusak6: String = "",
    var jumlahBatteryP6: String = "",
    var batteryRecti6: String = "",
    var busVoltage6: String = "",
    var loadA6: String = "",
    var perangkatTerhubung6: String = "",
    var mcbBatteryRecti6: String = "",
    var kondisiArester6: String = "",

    // ===== RECTIFIER 7 =====
    var typeRectifier7: String = "",
    var jumlahModul7: String = "",
    var jumlahModulRusak7: String = "",
    var jumlahBatteryP7: String = "",
    var batteryRecti7: String = "",
    var busVoltage7: String = "",
    var loadA7: String = "",
    var perangkatTerhubung7: String = "",
    var mcbBatteryRecti7: String = "",
    var kondisiArester7: String = "",

    // TOGGLE RECTIFIERS (2..7)
    var adaRecti2: Boolean = false,
    var adaRecti3: Boolean = false,
    var adaRecti4: Boolean = false,
    var adaRecti5: Boolean = false,
    var adaRecti6: Boolean = false,
    var adaRecti7: Boolean = false,

    // Input Gambar (sampai 10 foto)
    val imageUris: ArrayList<String> = arrayListOf()
) : Parcelable
