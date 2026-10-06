package com.example.zaman_apps.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.util.Log
import com.android.volley.VolleyError
import org.json.JSONArray
import org.json.JSONObject
import java.io.PrintWriter
import java.io.StringWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Pelapor error ke Telegram admin.
 *
 * PERUBAHAN PENTING:
 * - Token bot TIDAK ada lagi di aplikasi. Laporan dikirim ke Apps Script (action=client_log),
 *   lalu Apps Script yang meneruskan ke Telegram admin (ADMIN_CHAT_ID di server).
 * - Laporan yang gagal terkirim (offline / app crash) disimpan lokal dan dikirim ulang
 *   saat aplikasi dibuka berikutnya (panggil flushPending sekali di startup).
 * - Nama fungsi publik sama seperti sebelumnya, jadi pemanggil lama tidak perlu diubah.
 */
object TelegramLogger {

    private const val TAG = "TelegramLogger"

    // URL Web App Apps Script (sama dengan SCRIPT_URL).
    private val RELAY_URL = ServerConfig.SCRIPT_URL

    private const val PREF_NAME = "telegram_logger_pending"
    private const val PREF_KEY = "queue"
    private const val MAX_PENDING = 20

    private val FIELDS = listOf(
        "device", "username", "screen", "last_action",
        "error", "stack", "data", "logs", "extra"
    )

    // ------------------------------------------------------------------
    // API PUBLIK
    // ------------------------------------------------------------------

    fun sendError(
        context: Context,
        activeScreen: String,
        lastUserAction: String,
        error: Throwable,
        relevantData: String = ""
    ) {
        val report = buildReport(
            context, activeScreen, lastUserAction,
            "${error.javaClass.name}: ${error.message}",
            fullStack(error), relevantData
        )
        sendReportAsync(context, report)
    }

    fun sendErrorMsg(
        context: Context,
        activeScreen: String,
        lastUserAction: String,
        errorMessage: String,
        relevantData: String = ""
    ) {
        val report = buildReport(context, activeScreen, lastUserAction, errorMessage, "-", relevantData)
        sendReportAsync(context, report)
    }

    /** Ringkasan teknis VolleyError: jenis, kode HTTP, header Location, isi body. */
    fun describeVolley(error: VolleyError): String {
        val nr = error.networkResponse
        val body = nr?.data?.let { String(it, Charsets.UTF_8).take(400) } ?: "-"
        val headers = nr?.headers
            ?.filterKeys { it.equals("Location", true) || it.equals("Content-Type", true) }
            ?.toString() ?: "-"
        return "TYPE=${error.javaClass.simpleName}, MSG=${error.message}, CAUSE=${error.cause}, " +
                "HTTP=${nr?.statusCode ?: "-"}, NET_MS=${error.networkTimeMs}, HEADERS=$headers, BODY=[$body]"
    }

    /** Pasang sekali di startup (Application.onCreate atau Activity pertama). */
    fun setupGlobalCrashHandler(context: Context) {
        val app = context.applicationContext
        val screenName = context.javaClass.simpleName
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val report = buildReport(
                    app,
                    "GlobalCrashHandler ($screenName)",
                    "App crash di thread ${thread.name}",
                    "${throwable.javaClass.name}: ${throwable.message}",
                    fullStack(throwable, 40),
                    "CRASH_THREAD: ${thread.name}"
                )
                enqueue(app, report) // simpan dulu secara sinkron supaya tidak hilang
                val sender = Thread {
                    if (postReport(report)) remove(app, report.optLong("_id"))
                }
                sender.start()
                sender.join(2500) // beri waktu singkat sebelum proses dimatikan
            } catch (e: Exception) {
                Log.e(TAG, "Error di crash handler", e)
            } finally {
                defaultHandler?.uncaughtException(thread, throwable)
            }
        }
    }

    /** Kirim ulang laporan yang tertunda (offline / crash sebelumnya). Panggil sekali saat app dibuka. */
    fun flushPending(context: Context) {
        val app = context.applicationContext
        Thread {
            try {
                val prefs = app.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                val arr = JSONArray(prefs.getString(PREF_KEY, "[]"))
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    if (postReport(o)) remove(app, o.optLong("_id"))
                }
            } catch (e: Exception) {
                Log.e(TAG, "flushPending gagal", e)
            }
        }.start()
    }

    // ------------------------------------------------------------------
    // INTERNAL
    // ------------------------------------------------------------------

    private fun sendReportAsync(context: Context, report: JSONObject) {
        val app = context.applicationContext
        Thread {
            if (!postReport(report)) enqueue(app, report)
        }.start()
    }

    private fun buildReport(
        context: Context,
        screen: String,
        action: String,
        error: String,
        stack: String,
        data: String
    ): JSONObject = JSONObject().apply {
        put("device", getDeviceInfo(context))
        put("username", safeUsername(context))
        put("screen", screen.ifBlank { "Unknown" })
        put("last_action", action.ifBlank { "Unknown" })
        put("error", error.take(1500))
        put("stack", stack.take(3000))
        put("data", data.ifBlank { "-" }.take(3000))
        put("logs", AppLog.dump(40).take(5000))
        put("extra", buildExtra(context))
    }

    private fun postReport(report: JSONObject): Boolean {
        var conn: HttpURLConnection? = null
        return try {
            val body = StringBuilder("action=client_log")
            for (k in FIELDS) {
                body.append('&').append(k).append('=')
                    .append(URLEncoder.encode(report.optString(k, "-"), "UTF-8"))
            }
            conn = URL(RELAY_URL).openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.instanceFollowRedirects = false // Apps Script membalas 302 setelah doPost selesai
            conn.connectTimeout = 15000
            conn.readTimeout = 30000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=utf-8")
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            Log.d(TAG, "Relay response code: $code")
            code in 200..399
        } catch (e: Exception) {
            Log.e(TAG, "Gagal kirim laporan ke relay", e)
            false
        } finally {
            conn?.disconnect()
        }
    }

    @Synchronized
    private fun enqueue(context: Context, report: JSONObject) {
        try {
            if (!report.has("_id")) report.put("_id", System.nanoTime())
            val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            val lama = JSONArray(prefs.getString(PREF_KEY, "[]"))
            val baru = JSONArray()
            val mulai = maxOf(0, lama.length() - (MAX_PENDING - 1))
            for (i in mulai until lama.length()) baru.put(lama.get(i))
            baru.put(report)
            prefs.edit().putString(PREF_KEY, baru.toString()).commit()
        } catch (e: Exception) {
            Log.e(TAG, "Gagal simpan antrean laporan", e)
        }
    }

    @Synchronized
    private fun remove(context: Context, id: Long) {
        try {
            val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            val lama = JSONArray(prefs.getString(PREF_KEY, "[]"))
            val baru = JSONArray()
            for (i in 0 until lama.length()) {
                val o = lama.getJSONObject(i)
                if (o.optLong("_id") != id) baru.put(o)
            }
            prefs.edit().putString(PREF_KEY, baru.toString()).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Gagal hapus dari antrean", e)
        }
    }

    private fun fullStack(t: Throwable, maxLines: Int = 30): String {
        val sw = StringWriter()
        t.printStackTrace(PrintWriter(sw))
        return sw.toString().lineSequence().take(maxLines).joinToString("\n")
    }

    private fun safeUsername(context: Context): String = try {
        SessionHelper.getUsername(context).toString()
    } catch (_: Exception) {
        "-"
    }

    private fun buildExtra(context: Context): String {
        val rt = Runtime.getRuntime()
        val usedMb = (rt.totalMemory() - rt.freeMemory()) / 1048576
        val maxMb = rt.maxMemory() / 1048576
        val waktu = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        return "TIME=$waktu, THREAD=${Thread.currentThread().name}, " +
                "NET=${networkType(context)}, HEAP=${usedMb}/${maxMb}MB"
    }

    private fun networkType(context: Context): String = try {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork)
        when {
            caps == null -> "OFFLINE"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WIFI"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "SELULER"
            else -> "LAINNYA"
        }
    } catch (_: Exception) {
        "UNKNOWN"
    }

    private fun getDeviceInfo(context: Context): String {
        val manufacturer = Build.MANUFACTURER.replaceFirstChar { it.uppercase() }
        val model = Build.MODEL
        val androidVer = Build.VERSION.RELEASE
        val sdkVer = Build.VERSION.SDK_INT
        val appVersion = try {
            val p = context.packageManager.getPackageInfo(context.packageName, 0)
            val code = if (Build.VERSION.SDK_INT >= 28) p.longVersionCode else @Suppress("DEPRECATION") p.versionCode.toLong()
            "${p.versionName} (build $code)"
        } catch (_: Exception) {
            "1.0.0"
        }
        return "$manufacturer $model / Android $androidVer (API $sdkVer) / v$appVersion"
    }
}