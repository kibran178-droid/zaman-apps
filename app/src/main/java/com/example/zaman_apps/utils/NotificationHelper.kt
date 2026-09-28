package com.example.zaman_apps.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.zaman_apps.MainActivity
import com.example.zaman_apps.R

class NotificationHelper(private val context: Context) {

    companion object {
        const val CHANNEL_ID = "zaman_apps_channel"
        const val CHANNEL_NAME = "Zaman Apps Notifications"
        private const val NOTIFICATION_ID = 1001
        private const val LARGE_ICON_SIZE_DP = 256 // Ukuran ideal untuk large icon (dp)
    }

    fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifikasi untuk input dan update data site"
                enableVibration(true)
                vibrationPattern = longArrayOf(100, 200, 100, 200)
                setShowBadge(true)
                importance = NotificationManager.IMPORTANCE_HIGH
            }

            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    fun showNotification(title: String, message: String, isSuccess: Boolean = true) {
        // ✅ PERBAIKI: HAPUS FLAG_ACTIVITY_CLEAR_TASK
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // ✅ BUAT LARGE ICON DARI DRAWABLE (LEBIH JELAS)
        val largeIcon = getLargeIcon()

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_logo_perusahaan)
            .setLargeIcon(largeIcon)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setVibrate(longArrayOf(100, 200, 100, 200))
            .setSound(android.provider.Settings.System.DEFAULT_NOTIFICATION_URI)

        // ✅ WARNA BERDASARKAN STATUS
        if (isSuccess) {
            builder.setColor(context.getColor(R.color.success))
            builder.setColorized(true)
        } else {
            builder.setColor(context.getColor(R.color.error))
            builder.setColorized(true)
        }

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, builder.build())
    }

    fun showNotificationWithId(title: String, message: String, id: Int, isSuccess: Boolean = true) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context, id, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val largeIcon = getLargeIcon()

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_logo_perusahaan)
            .setLargeIcon(largeIcon)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setVibrate(longArrayOf(100, 200, 100, 200))
            .setSound(android.provider.Settings.System.DEFAULT_NOTIFICATION_URI)

        if (isSuccess) {
            builder.setColor(context.getColor(R.color.success))
            builder.setColorized(true)
        } else {
            builder.setColor(context.getColor(R.color.error))
            builder.setColorized(true)
        }

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(id, builder.build())
    }

    /**
     * 🔥 FUNGSI UNTUK MEMBUAT LARGE ICON YANG JELAS
     */
    private fun getLargeIcon(): Bitmap? {
        return try {
            // Cara 1: Dari drawable (lebih jelas)
            val drawable = ContextCompat.getDrawable(context, R.mipmap.ic_launcher)
            if (drawable != null) {
                // Konversi drawable ke bitmap dengan ukuran yang sesuai
                val sizeInPx = dpToPx(LARGE_ICON_SIZE_DP)
                val bitmap = Bitmap.createBitmap(sizeInPx, sizeInPx, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                drawable.setBounds(0, 0, canvas.width, canvas.height)
                drawable.draw(canvas)
                bitmap
            } else {
                // Cara 2: Dari resource (fallback)
                BitmapFactory.decodeResource(context.resources, R.mipmap.ic_launcher)
            }
        } catch (e: Exception) {
            // Fallback terakhir
            BitmapFactory.decodeResource(context.resources, R.mipmap.ic_launcher)
        }
    }

    /**
     * 🔥 KONVERSI DP KE PIXEL
     */
    private fun dpToPx(dp: Int): Int {
        val density = context.resources.displayMetrics.density
        return (dp * density + 0.5f).toInt()
    }
}