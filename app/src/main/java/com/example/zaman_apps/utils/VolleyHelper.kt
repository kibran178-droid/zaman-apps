package com.example.zaman_apps.utils

import android.content.Context
import com.android.volley.DefaultRetryPolicy
import com.android.volley.RequestQueue
import com.android.volley.toolbox.HurlStack
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import java.net.HttpURLConnection
import java.net.URL

object VolleyHelper {

    private var requestQueue: RequestQueue? = null

    /**
     * Mengembalikan Volley RequestQueue yang dikonfigurasi khusus dengan:
     * 1. Connection Timeout = 60 Detik
     * 2. Read Timeout = 60 Detik
     * 3. Header 'Connection: close' untuk mencegah 'SocketException: Software caused connection lost'
     *    pada koneksi Google Apps Script Web App akibat penutupan socket prematur selama proses 302 Redirect.
     */
    @Synchronized
    fun getRequestQueue(context: Context): RequestQueue {
        if (requestQueue == null) {
            val hurlStack = object : HurlStack() {
                override fun createConnection(url: URL): HttpURLConnection {
                    val connection = super.createConnection(url)
                    connection.connectTimeout = 60000 // 60 detik timeout koneksi
                    connection.readTimeout = 60000    // 60 detik timeout membaca data
                    connection.setRequestProperty("Connection", "close") // Hindari reutilisasi socket yang terputus
                    return connection
                }
            }
            requestQueue = Volley.newRequestQueue(context.applicationContext, hurlStack)
        }
        return requestQueue!!
    }

    /**
     * Menerapkan Retry Policy 30 detik secara eksplisit pada Volley StringRequest (Max Retries = 0).
     */
    fun apply30sTimeout(request: StringRequest): StringRequest {
        request.retryPolicy = DefaultRetryPolicy(
            30000, // 30,000 ms = 30 detik
            0,     // 0 retry untuk mencegah pengiriman data ganda ke Google Sheets
            DefaultRetryPolicy.DEFAULT_BACKOFF_MULT
        )
        return request
    }

    /**
     * Menerapkan Retry Policy 60 detik secara eksplisit pada Volley StringRequest (Max Retries = 0).
     */
    fun apply60sTimeout(request: StringRequest): StringRequest {
        request.retryPolicy = DefaultRetryPolicy(
            60000, // 60,000 ms = 60 detik
            0,     // 0 retry untuk mencegah pengiriman data ganda ke Google Sheets
            DefaultRetryPolicy.DEFAULT_BACKOFF_MULT
        )
        return request
    }
}
