package com.example.zaman_apps.utils

import android.util.Log
import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale

/**
 * Catatan jejak (breadcrumbs) di memori.
 * Dipakai sebagai pengganti Log.d/Log.e di alur penting (submit, kompres foto, dll).
 * Isi terakhirnya otomatis ikut dikirim ke Telegram admin saat terjadi error,
 * jadi admin bisa melihat "apa yang terjadi sebelum error" tanpa Logcat.
 */
object AppLog {
    private const val MAX_ENTRIES = 80
    private val buffer = ArrayDeque<String>()
    private val fmt = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    @Synchronized
    fun d(tag: String, msg: String) {
        Log.d(tag, msg)
        add("D", tag, msg)
    }

    @Synchronized
    fun e(tag: String, msg: String) {
        Log.e(tag, msg)
        add("E", tag, msg)
    }

    private fun add(level: String, tag: String, msg: String) {
        buffer.addLast("${fmt.format(Date())} $level/$tag: ${msg.take(300)}")
        while (buffer.size > MAX_ENTRIES) buffer.removeFirst()
    }

    @Synchronized
    fun dump(last: Int = 40): String =
        buffer.toList().takeLast(last).joinToString("\n").ifEmpty { "-" }
}