
package com.armbharat.plantcareai.core.common

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max

object ImagePreprocessor {

    fun processImage(context: Context, imageUri: Uri): File {

        val inputStream = context.contentResolver
            .openInputStream(imageUri)
            ?: throw IllegalArgumentException("Unable to open image")

        val originalBitmap = inputStream.use {
            BitmapFactory.decodeStream(it)
        } ?: throw IllegalArgumentException("Invalid image")

        val outputBitmap = Bitmap.createBitmap(
            640,
            640,
            Bitmap.Config.ARGB_8888
        )

        val canvas = Canvas(outputBitmap)
        canvas.drawColor(Color.WHITE)

        val scale = minOf(
            640f / originalBitmap.width,
            640f / originalBitmap.height
        )

        val scaledWidth = (originalBitmap.width * scale).toInt()
        val scaledHeight = (originalBitmap.height * scale).toInt()

        val scaledBitmap = Bitmap.createScaledBitmap(
            originalBitmap,
            scaledWidth,
            scaledHeight,
            true
        )

        val left = (640 - scaledWidth) / 2f
        val top = (640 - scaledHeight) / 2f

        canvas.drawBitmap(
            scaledBitmap,
            left,
            top,
            Paint(Paint.ANTI_ALIAS_FLAG)
        )

        val directory = File(context.cacheDir, "processed_scans")
        if (!directory.exists()) directory.mkdirs()

        val outputFile = File(
            directory,
            "scan_${System.currentTimeMillis()}.jpg"
        )

        FileOutputStream(outputFile).use { stream ->
            outputBitmap.compress(
                Bitmap.CompressFormat.JPEG,
                88,
                stream
            )
        }

        originalBitmap.recycle()
        if (scaledBitmap !== originalBitmap) scaledBitmap.recycle()
        outputBitmap.recycle()

        return outputFile
    }
}
