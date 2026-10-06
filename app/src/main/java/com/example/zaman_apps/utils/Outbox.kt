package com.example.zaman_apps.utils

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.io.IOException

/**
 * URL Web App Apps Script. Pakai konstanta INI saja di seluruh aplikasi
 * (ganti urlSheet di RingkasanActivity dan RELAY_URL di TelegramLogger),
 * supaya semua layar selalu menunjuk deployment yang sama.
 */
object ServerConfig {
    const val SCRIPT_URL =
        "https://script.google.com/macros/s/AKfycby3SAkaTgooCCX-4GnF3svjWTzW01RWx8XnV3c69pjaBHrtTixspCzzjKJeEomQGhI46A/exec"
}

/**
 * Antrean kirim (outbox) di penyimpanan privat aplikasi.
 *
 * Satu item = dua file di filesDir/outbox/:
 *   <jenis>_<siteId>.data  -> seluruh parameter submit (termasuk foto base64), bisa besar
 *   <jenis>_<siteId>.meta  -> info kecil (jenis, site_id, waktu, jumlah percobaan, siap)
 *
 * Satu site + satu jenis (input/update) hanya punya satu item; menyimpan lagi akan menimpa.
 * "siap" = false selama aplikasi masih mengirim sendiri secara langsung. Worker hanya
 * memproses item yang siap, supaya tidak bentrok dengan pengiriman yang sedang berjalan.
 */
object Outbox {
    private const val DIR = "outbox"
    private const val PREFIX_GAGAL = "gagal_"

    class Item(
        val base: String,
        val jenis: String,
        val siteId: String,
        val createdAt: Long,
        val attempts: Int
    )

    private fun dir(c: Context): File {
        val d = File(c.applicationContext.filesDir, DIR)
        if (!d.exists()) d.mkdirs()
        return d
    }

    private fun baseName(jenis: String, siteId: String): String =
        jenis + "_" + siteId.replace(Regex("[^A-Za-z0-9_-]"), "_")

    private fun metaFile(c: Context, base: String) = File(dir(c), "$base.meta")
    private fun dataFile(c: Context, base: String) = File(dir(c), "$base.data")

    private fun isAktif(f: File) =
        f.isFile && f.name.endsWith(".meta") && !f.name.startsWith(PREFIX_GAGAL)

    private fun tulisAtomik(target: File, teks: String) {
        val tmp = File(target.parentFile, target.name + ".tmp")
        tmp.writeText(teks, Charsets.UTF_8)
        if (!tmp.renameTo(target)) {
            target.delete()
            if (!tmp.renameTo(target)) throw IOException("Gagal rename ${tmp.name}")
        }
    }

    private fun bacaMeta(f: File): JSONObject? = try {
        JSONObject(f.readText(Charsets.UTF_8))
    } catch (e: Exception) {
        AppLog.e("Outbox", "Gagal baca ${f.name}: ${e.message}")
        null
    }

    @Synchronized
    fun simpan(context: Context, jenis: String, siteId: String, params: Map<String, String>): Boolean = try {
        val base = baseName(jenis, siteId)
        val p = JSONObject()
        for ((k, v) in params) p.put(k, v)
        tulisAtomik(dataFile(context, base), p.toString())

        val m = JSONObject()
        m.put("jenis", jenis)
        m.put("site_id", siteId)
        m.put("created_at", System.currentTimeMillis())
        m.put("attempts", 0)
        m.put("siap", false)
        tulisAtomik(metaFile(context, base), m.toString())

        AppLog.d("Outbox", "Disimpan $jenis $siteId (~${p.toString().length / 1024}KB)")
        true
    } catch (e: Exception) {
        AppLog.e("Outbox", "Gagal simpan $jenis $siteId: ${e.javaClass.simpleName}: ${e.message}")
        false
    }

    @Synchronized
    fun hapus(context: Context, jenis: String, siteId: String) {
        hapusBase(context, baseName(jenis, siteId))
    }

    @Synchronized
    fun serahkan(context: Context, jenis: String, siteId: String) {
        val f = metaFile(context, baseName(jenis, siteId))
        if (!f.exists()) return
        val m = bacaMeta(f) ?: return
        m.put("siap", true)
        try {
            tulisAtomik(f, m.toString())
        } catch (e: Exception) {
            AppLog.e("Outbox", "Gagal serahkan $siteId: ${e.message}")
        }
    }

    @Synchronized
    fun serahkanYatim(context: Context, batasMs: Long = 120_000L) {
        val now = System.currentTimeMillis()
        dir(context).listFiles()?.filter { isAktif(it) }?.forEach { f ->
            val m = bacaMeta(f) ?: return@forEach
            if (!m.optBoolean("siap", false) && now - m.optLong("created_at", now) > batasMs) {
                m.put("siap", true)
                try {
                    tulisAtomik(f, m.toString())
                } catch (_: Exception) {
                }
            }
        }
    }

    @Synchronized
    fun jumlahMenunggu(context: Context): Int =
        dir(context).listFiles()?.count { isAktif(it) } ?: 0

    @Synchronized
    fun antreanSiap(context: Context): List<Item> {
        val hasil = mutableListOf<Item>()
        dir(context).listFiles()?.filter { isAktif(it) }?.forEach { f ->
            val m = bacaMeta(f)
            if (m == null) {
                tandaiGagalBase(context, f.name.removeSuffix(".meta"))
                return@forEach
            }
            if (m.optBoolean("siap", false)) {
                hasil.add(
                    Item(
                        base = f.name.removeSuffix(".meta"),
                        jenis = m.optString("jenis"),
                        siteId = m.optString("site_id"),
                        createdAt = m.optLong("created_at"),
                        attempts = m.optInt("attempts")
                    )
                )
            }
        }
        return hasil.sortedBy { it.createdAt }
    }

    fun muatParams(context: Context, item: Item): Map<String, String>? = try {
        val o = JSONObject(dataFile(context, item.base).readText(Charsets.UTF_8))
        val map = HashMap<String, String>(o.length())
        val keys = o.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            map[k] = o.optString(k)
        }
        map
    } catch (e: Exception) {
        AppLog.e("Outbox", "Gagal muat data ${item.base}: ${e.message}")
        null
    }

    @Synchronized
    fun tambahPercobaan(context: Context, item: Item): Int {
        val f = metaFile(context, item.base)
        val m = bacaMeta(f) ?: return item.attempts + 1
        val baru = m.optInt("attempts") + 1
        m.put("attempts", baru)
        try {
            tulisAtomik(f, m.toString())
        } catch (_: Exception) {
        }
        return baru
    }

    @Synchronized
    fun hapus(context: Context, item: Item) = hapusBase(context, item.base)

    @Synchronized
    fun tandaiGagal(context: Context, item: Item) = tandaiGagalBase(context, item.base)

    @Synchronized
    fun bersihkanGagalLama(context: Context, hari: Int = 30) {
        val batas = System.currentTimeMillis() - hari * 24L * 3600L * 1000L
        dir(context).listFiles()?.filter { it.name.startsWith(PREFIX_GAGAL) && it.lastModified() < batas }
            ?.forEach { it.delete() }
    }

    private fun hapusBase(c: Context, base: String) {
        metaFile(c, base).delete()
        dataFile(c, base).delete()
    }

    private fun tandaiGagalBase(c: Context, base: String) {
        try {
            val d = dir(c)
            File(d, "$base.meta").renameTo(File(d, "$PREFIX_GAGAL$base.meta"))
            File(d, "$base.data").renameTo(File(d, "$PREFIX_GAGAL$base.data"))
        } catch (e: Exception) {
            AppLog.e("Outbox", "Gagal tandai gagal $base: ${e.message}")
        }
    }
}
