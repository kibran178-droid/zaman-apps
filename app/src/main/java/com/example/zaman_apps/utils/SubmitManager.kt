package com.example.zaman_apps.utils

import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.appcompat.app.AppCompatActivity
import com.android.volley.DefaultRetryPolicy
import com.android.volley.Request
import com.android.volley.VolleyError
import com.android.volley.toolbox.StringRequest
import org.json.JSONObject

interface SubmitCallback {
    fun onMulai() {}
    fun onSukses(isRetry: Boolean)
    fun onDuplikat()
    fun onValidasi(pesan: String)
    fun onDiantrekan(pesan: String)
}

object SubmitManager {

    private const val TAG = "SubmitManager"

    fun kirim(
        activity: AppCompatActivity,
        jenis: String,
        kunci: String,
        params: MutableMap<String, String>,
        callback: SubmitCallback
    ) {
        kirimInternal(activity, jenis, kunci, params, callback, sudahKirimUlang = false)
    }

    private fun kirimInternal(
        activity: AppCompatActivity,
        jenis: String,
        kunci: String,
        params: MutableMap<String, String>,
        callback: SubmitCallback,
        sudahKirimUlang: Boolean
    ) {
        callback.onMulai()

        // a. Outbox.simpan(...) SEBELUM request dikirim.
        if (!sudahKirimUlang) {
            if (!Outbox.simpan(activity, jenis, kunci, params)) {
                AppLog.e(TAG, "Outbox gagal menyimpan (penyimpanan penuh?)")
            }
        }

        val startTime = SystemClock.elapsedRealtime()
        val payloadKb = (params.toString().length) / 1024

        val queue = VolleyHelper.getRequestQueue(activity)

        val request = object : StringRequest(
            Method.POST, ServerConfig.SCRIPT_URL,
            { response ->
                val durationMs = SystemClock.elapsedRealtime() - startTime
                tanganiRespons(
                    activity = activity,
                    jenis = jenis,
                    kunci = kunci,
                    params = params,
                    response = response,
                    durationMs = durationMs,
                    payloadKb = payloadKb,
                    sudahKirimUlang = sudahKirimUlang,
                    callback = callback
                )
            },
            { error ->
                val durationMs = SystemClock.elapsedRealtime() - startTime
                tanganiGagalJaringan(
                    activity = activity,
                    jenis = jenis,
                    kunci = kunci,
                    params = params,
                    error = error,
                    durationMs = durationMs,
                    payloadKb = payloadKb,
                    sudahKirimUlang = sudahKirimUlang,
                    callback = callback
                )
            }
        ) {
            override fun getHeaders(): MutableMap<String, String> {
                val headers = HashMap<String, String>()
                headers["Connection"] = "close"
                return headers
            }

            override fun getBodyContentType(): String {
                return "application/x-www-form-urlencoded; charset=utf-8"
            }

            override fun getParamsEncoding(): String {
                return "UTF-8"
            }

            override fun getParams(): MutableMap<String, String> {
                return params
            }
        }

        // b. POST Volley, timeout 60 detik, maxRetries = 0 (tidak ada retry otomatis)
        request.retryPolicy = DefaultRetryPolicy(60_000, 0, DefaultRetryPolicy.DEFAULT_BACKOFF_MULT)
        queue.add(request)
    }

    private fun tanganiRespons(
        activity: AppCompatActivity,
        jenis: String,
        kunci: String,
        params: MutableMap<String, String>,
        response: String,
        durationMs: Long,
        payloadKb: Int,
        sudahKirimUlang: Boolean,
        callback: SubmitCallback
    ) {
        val json = try {
            JSONObject(response)
        } catch (e: Exception) {
            AppLog.e(TAG, "Respons bukan JSON valid: ${e.message}")
            TelegramLogger.sendError(
                context = activity,
                activeScreen = "${activity.javaClass.simpleName} ($jenis)",
                lastUserAction = "Kirim $jenis $kunci",
                error = e,
                relevantData = "KUNCI: $kunci, DURASI: ${durationMs}ms, PAYLOAD: ${payloadKb}KB, RESPONSE: ${response.take(300)}"
            )
            tanganiAmbigu(activity, jenis, kunci, params, "respons bukan JSON", sudahKirimUlang, callback)
            return
        }

        val status = json.optString("status")
        val message = json.optString("message", "")
        val code = json.optString("code", "")

        when {
            status == "success" -> {
                Outbox.hapus(activity, jenis, kunci)
                AppLog.d(TAG, "Sukses $jenis $kunci (${durationMs}ms)")
                callback.onSukses(sudahKirimUlang)
            }

            status == "duplicate" -> {
                if (jenis == "input") {
                    if (sudahKirimUlang) {
                        AppLog.d(TAG, "Duplikat pada kiriman ulang -> dianggap sukses $kunci")
                        Outbox.hapus(activity, jenis, kunci)
                        callback.onSukses(true)
                    } else {
                        Outbox.hapus(activity, jenis, kunci)
                        AppLog.d(TAG, "Duplikat kiriman pertama -> notify duplikat $kunci")
                        callback.onDuplikat()
                    }
                } else {
                    Outbox.hapus(activity, jenis, kunci)
                    AppLog.d(TAG, "Duplikat $jenis -> dianggap sukses $kunci")
                    callback.onSukses(sudahKirimUlang)
                }
            }

            status == "error" && code == "VALIDATION" -> {
                Outbox.hapus(activity, jenis, kunci)
                AppLog.e(TAG, "Ditolak validasi server $jenis $kunci: $message")
                callback.onValidasi(message.ifEmpty { "Data ditolak oleh aturan validasi server." })
            }

            status == "processing" -> {
                AppLog.d(TAG, "Server masih memproses (processing) $jenis $kunci")
                tanganiAmbigu(activity, jenis, kunci, params, "server processing", sudahKirimUlang, callback)
            }

            else -> {
                AppLog.e(TAG, "Server error $jenis $kunci: status=$status, msg=$message")
                TelegramLogger.sendErrorMsg(
                    context = activity,
                    activeScreen = "${activity.javaClass.simpleName} ($jenis)",
                    lastUserAction = "Kirim $jenis $kunci",
                    errorMessage = "Server Error: status=$status, message=$message",
                    relevantData = "KUNCI: $kunci, DURASI: ${durationMs}ms, PAYLOAD: ${payloadKb}KB, RESPONSE: $json"
                )
                tanganiAmbigu(activity, jenis, kunci, params, "server error ($status)", sudahKirimUlang, callback)
            }
        }
    }

    private fun tanganiGagalJaringan(
        activity: AppCompatActivity,
        jenis: String,
        kunci: String,
        params: MutableMap<String, String>,
        error: VolleyError,
        durationMs: Long,
        payloadKb: Int,
        sudahKirimUlang: Boolean,
        callback: SubmitCallback
    ) {
        val descVolley = TelegramLogger.describeVolley(error)
        AppLog.e(TAG, "Gagal jaringan $jenis $kunci: $descVolley")

        TelegramLogger.sendError(
            context = activity,
            activeScreen = "${activity.javaClass.simpleName} ($jenis)",
            lastUserAction = "Kirim $jenis $kunci",
            error = error,
            relevantData = "KUNCI: $kunci, DURASI: ${durationMs}ms, PAYLOAD: ${payloadKb}KB, VOLLEY: $descVolley"
        )

        tanganiAmbigu(activity, jenis, kunci, params, "gagal jaringan", sudahKirimUlang, callback)
    }

    private fun tanganiAmbigu(
        activity: AppCompatActivity,
        jenis: String,
        kunci: String,
        params: MutableMap<String, String>,
        alasan: String,
        sudahKirimUlang: Boolean,
        callback: SubmitCallback
    ) {
        if (jenis != "input") {
            serahkanKeAntrean(activity, jenis, kunci, callback)
            return
        }

        AppLog.d(TAG, "AMBIGU ($alasan) -> verifikasi get_detail $kunci")

        verifikasiTersimpan(activity, kunci, 1) { ada, catatan ->
            AppLog.d(TAG, "VERIFIKASI $kunci: ada=$ada ($catatan)")
            when {
                ada -> {
                    TelegramLogger.sendErrorMsg(
                        context = activity,
                        activeScreen = "${activity.javaClass.simpleName} (Verifikasi)",
                        lastUserAction = "Verifikasi $jenis $kunci",
                        errorMessage = "Respons ambigu ($alasan) tetapi data TERNYATA TERSIMPAN",
                        relevantData = "KUNCI: $kunci, $catatan"
                    )
                    Outbox.hapus(activity, jenis, kunci)
                    callback.onSukses(isRetry = true)
                }
                !sudahKirimUlang -> {
                    AppLog.d(TAG, "Kirim ulang ONCE $jenis $kunci")
                    kirimInternal(activity, jenis, kunci, params, callback, sudahKirimUlang = true)
                }
                else -> {
                    serahkanKeAntrean(activity, jenis, kunci, callback)
                }
            }
        }
    }

    private fun verifikasiTersimpan(
        activity: AppCompatActivity,
        siteId: String,
        percobaan: Int,
        onHasil: (Boolean, String) -> Unit
    ) {
        val queue = VolleyHelper.getRequestQueue(activity)
        val url = "${ServerConfig.SCRIPT_URL}?action=get_detail&site_id=${Uri.encode(siteId)}"

        val request = StringRequest(
            Request.Method.GET, url,
            { response ->
                try {
                    val json = JSONObject(response)
                    val status = json.optString("status")
                    if (status == "success" && json.has("data")) {
                        onHasil(true, "ketemu di percobaan $percobaan")
                    } else if (percobaan < 4) {
                        Handler(Looper.getMainLooper()).postDelayed({
                            verifikasiTersimpan(activity, siteId, percobaan + 1, onHasil)
                        }, 5000)
                    } else {
                        onHasil(false, "tidak ketemu setelah $percobaan percobaan")
                    }
                } catch (e: Exception) {
                    if (percobaan < 4) {
                        Handler(Looper.getMainLooper()).postDelayed({
                            verifikasiTersimpan(activity, siteId, percobaan + 1, onHasil)
                        }, 5000)
                    } else {
                        onHasil(false, "error JSON: ${e.message}")
                    }
                }
            },
            { error ->
                if (percobaan < 4) {
                    Handler(Looper.getMainLooper()).postDelayed({
                        verifikasiTersimpan(activity, siteId, percobaan + 1, onHasil)
                    }, 5000)
                } else {
                    onHasil(false, "error network: ${error.message}")
                }
            }
        )
        VolleyHelper.apply30sTimeout(request)
        queue.add(request)
    }

    private fun serahkanKeAntrean(
        activity: AppCompatActivity,
        jenis: String,
        kunci: String,
        callback: SubmitCallback
    ) {
        Outbox.serahkan(activity, jenis, kunci)
        OutboxWorker.jadwalkan(activity)
        AppLog.d(TAG, "Dialihkan ke antrean offline: $jenis $kunci")

        val pesan = "Data $jenis $kunci telah tersimpan di perangkat dan akan dikirim otomatis begitu koneksi kembali."
        callback.onDiantrekan(pesan)
    }
}
