package com.example.zaman_apps.utils

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Mengirim ulang data yang tertahan di Outbox (jaringan putus, timeout, server bermasalah).
 * - Hanya jalan saat ada koneksi internet (constraint CONNECTED).
 * - Gagal sementara -> WorkManager mengulang dengan jeda bertambah (30 dtk, 1 mnt, 2 mnt, ...).
 * - Percobaan hanya terhitung saat jaringan tersedia, jadi offline lama tidak menghabiskan jatah.
 */
class OutboxWorker(context: Context, workerParams: WorkerParameters) : Worker(context, workerParams) {

    override fun doWork(): Result {
        val ctx = applicationContext
        Outbox.bersihkanGagalLama(ctx)

        val antrean = Outbox.antreanSiap(ctx)
        if (antrean.isEmpty()) return Result.success()

        var perluUlang = false
        for (item in antrean) {
            val selesai = kirimSatu(ctx, item)
            if (!selesai) perluUlang = true
        }
        return if (perluUlang) Result.retry() else Result.success()
    }

    /** true = urusan item ini selesai (jangan diulang); false = coba lagi nanti. */
    private fun kirimSatu(ctx: Context, item: Outbox.Item): Boolean {
        val params = Outbox.muatParams(ctx, item)
        if (params == null) {
            Outbox.tandaiGagal(ctx, item)
            return true
        }

        val percobaan = Outbox.tambahPercobaan(ctx, item)
        AppLog.d("Outbox", "Kirim ${item.jenis} ${item.siteId} percobaan=$percobaan")

        val respons = try {
            post(params)
        } catch (e: Exception) {
            AppLog.e("Outbox", "Gagal jaringan ${item.siteId}: ${e.javaClass.simpleName}: ${e.message}")
            return gagalSementara(ctx, item, percobaan, "Jaringan: ${e.javaClass.simpleName}: ${e.message}")
        }

        val json = try {
            JSONObject(respons)
        } catch (e: Exception) {
            return gagalSementara(ctx, item, percobaan, "Respons bukan JSON: ${respons.take(300)}")
        }

        val status = json.optString("status")
        return when {
            status == "success" -> {
                Outbox.hapus(ctx, item)
                AppLog.d("Outbox", "Terkirim ${item.jenis} ${item.siteId}")
                notif(ctx, "✅ Data Terkirim", "Site ${item.siteId} berhasil dikirim otomatis.", true)
                true
            }

            // Data sudah ada di server (kemungkinan besar kiriman sebelumnya yang responsnya hilang)
            status == "duplicate" && item.jenis == "input" -> {
                Outbox.hapus(ctx, item)
                AppLog.d("Outbox", "Duplikat dianggap selesai: ${item.siteId}")
                true
            }

            // Ditolak validasi: percuma diulang, data disimpan agar tidak hilang
            status == "error" && json.optString("code") == "VALIDATION" -> {
                Outbox.tandaiGagal(ctx, item)
                TelegramLogger.sendErrorMsg(
                    context = ctx,
                    activeScreen = "OutboxWorker",
                    lastUserAction = "Mengirim ulang ${item.jenis} ${item.siteId}",
                    errorMessage = "Ditolak validasi server: ${json.optString("message")}",
                    relevantData = "SITE_ID: ${item.siteId}, percobaan=$percobaan"
                )
                notif(
                    ctx, "⚠️ Data Perlu Diperiksa",
                    "Site ${item.siteId} ditolak server. Silakan buka aplikasi dan isi ulang.", false
                )
                true
            }

            // "processing", error sistem, dll: coba lagi nanti
            else -> gagalSementara(ctx, item, percobaan, "Server: ${json.toString().take(300)}")
        }
    }

    private fun gagalSementara(ctx: Context, item: Outbox.Item, percobaan: Int, alasan: String): Boolean {
        if (percobaan < MAX_PERCOBAAN) return false

        Outbox.tandaiGagal(ctx, item)
        TelegramLogger.sendErrorMsg(
            context = ctx,
            activeScreen = "OutboxWorker",
            lastUserAction = "Mengirim ulang ${item.jenis} ${item.siteId}",
            errorMessage = "Menyerah setelah $percobaan percobaan: $alasan",
            relevantData = "SITE_ID: ${item.siteId}, dibuat=${item.createdAt}"
        )
        notif(
            ctx, "⚠️ Data Belum Terkirim",
            "Site ${item.siteId} gagal dikirim otomatis. Hubungi admin.", false
        )
        return true
    }

    /**
     * POST form-urlencoded. Sengaja TANPA setFixedLengthStreamingMode / chunked:
     * Apps Script membalas dengan redirect (302) dan mode streaming membuat redirect gagal.
     */
    private fun post(params: Map<String, String>): String {
        val body = StringBuilder()
        for ((k, v) in params) {
            if (body.isNotEmpty()) body.append('&')
            body.append(URLEncoder.encode(k, "UTF-8")).append('=').append(URLEncoder.encode(v, "UTF-8"))
        }
        var conn: HttpURLConnection? = null
        try {
            conn = URL(ServerConfig.SCRIPT_URL).openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.connectTimeout = 20_000
            conn.readTimeout = 90_000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=utf-8")
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }

            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val teks = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code !in 200..299) throw IOException("HTTP $code: ${teks.take(200)}")
            return teks
        } finally {
            conn?.disconnect()
        }
    }

    private fun notif(ctx: Context, judul: String, isi: String, sukses: Boolean) {
        try {
            val helper = NotificationHelper(ctx)
            helper.createNotificationChannel()
            helper.showNotification(judul, isi, sukses)
        } catch (e: Exception) {
            AppLog.e("Outbox", "Notifikasi gagal: ${e.message}")
        }
    }

    companion object {
        private const val UNIQUE_NAME = "outbox_kirim"
        private const val MAX_PERCOBAAN = 8

        /** Jadwalkan pengiriman; otomatis menunggu sampai ada internet. */
        fun jadwalkan(context: Context) {
            val req = OneTimeWorkRequestBuilder<OutboxWorker>()
                .setConstraints(
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context.applicationContext)
                .enqueueUniqueWork(UNIQUE_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, req)
        }

        /** Panggil sekali di startup aplikasi: lanjutkan sisa antrean dari sesi sebelumnya. */
        fun mulai(context: Context) {
            Outbox.serahkanYatim(context)
            if (Outbox.antreanSiap(context).isNotEmpty()) jadwalkan(context)
        }
    }
}
