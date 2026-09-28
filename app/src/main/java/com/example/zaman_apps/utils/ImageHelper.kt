package com.example.zaman_apps.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.util.Log
import java.io.ByteArrayOutputStream

object ImageHelper {

    private const val MAX_DIMENSION = 1280
    private const val MIN_DIMENSION_TO_COMPRESS = 1024
    private const val SMALL_FILE_BYTES = 300 * 1024
    private const val TARGET_SIZE_BYTES = 500 * 1024 // 500 KB

    fun decodeThumbnailFromUri(context: Context, uri: Uri, reqWidth: Int = 300, reqHeight: Int = 300): Bitmap? {
        return try {
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
            }

            var inSampleSize = 1
            if (options.outHeight > reqHeight || options.outWidth > reqWidth) {
                val halfHeight = options.outHeight / 2
                val halfWidth = options.outWidth / 2
                while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                    inSampleSize *= 2
                }
            }

            val decodeOptions = BitmapFactory.Options().apply {
                this.inSampleSize = inSampleSize
                inJustDecodeBounds = false
            }

            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOptions)
            }
        } catch (e: Exception) {
            Log.e("ImageHelper", "Gagal memuat thumbnail", e)
            null
        }
    }

    fun uriToCompressedBase64(context: Context, uri: Uri): String? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            val original = BitmapFactory.decodeStream(inputStream) ?: return null
            inputStream.close()

            val sourceSize = context.contentResolver.openAssetFileDescriptor(uri, "r")
                ?.use { it.length }
                ?: -1L

            // Keep small images untouched; recompressing them often reduces readability.
            if (sourceSize in 1..SMALL_FILE_BYTES &&
                original.width <= MIN_DIMENSION_TO_COMPRESS &&
                original.height <= MIN_DIMENSION_TO_COMPRESS
            ) {
                val originalBytes = context.contentResolver.openInputStream(uri)
                    ?.use { it.readBytes() }
                    ?: return null
                return Base64.encodeToString(originalBytes, Base64.NO_WRAP)
            }

            val resized = resizeBitmap(original, MAX_DIMENSION)
            val compressedBytes = compressToTargetSize(resized, TARGET_SIZE_BYTES)

            Base64.encodeToString(compressedBytes, Base64.NO_WRAP)
        } catch (e: Exception) {
            Log.e("ImageHelper", "Gagal memproses gambar", e)
            null
        }
    }

    private fun resizeBitmap(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height

        if (width <= maxDimension && height <= maxDimension) return bitmap

        val ratio = width.toFloat() / height.toFloat()
        val newWidth: Int
        val newHeight: Int

        if (width > height) {
            newWidth = maxDimension
            newHeight = (maxDimension / ratio).toInt()
        } else {
            newHeight = maxDimension
            newWidth = (maxDimension * ratio).toInt()
        }

        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }

    private fun compressToTargetSize(bitmap: Bitmap, targetBytes: Int): ByteArray {
        var quality = 85
        var stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)

        while (stream.toByteArray().size > targetBytes && quality > 40) {
            stream = ByteArrayOutputStream()
            quality -= 10
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)
        }

        return stream.toByteArray()
    }
}
