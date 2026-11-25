package com.armbharat.plantcareai.core.llm

import android.content.Context
import android.net.Uri
import android.util.AtomicFile
import java.io.File
import java.io.InputStream

object ModelFileManager {

    private const val MODEL_ASSET = "llama/assistant.pte"
    private const val TOKENIZER_ASSET = "llama/tokenizer_m33.bin"

    private val bundledCopyLock = Any()

    private fun getModelDirectory(context: Context): File {
        return File(context.filesDir, "llama").apply {
            check(isDirectory || mkdirs() || isDirectory) {
                "Could not create the Assistant model directory."
            }
        }
    }

    // Call on Dispatchers.IO because the model is large.
    fun prepareBundledModel(context: Context): Pair<File, File> =
        synchronized(bundledCopyLock) {
            val directory = File(context.noBackupFilesDir, "bundled_llama")
            check(directory.isDirectory || directory.mkdirs() || directory.isDirectory) {
                "Could not create the bundled Assistant model directory."
            }

            val model = File(directory, "assistant.pte")
            val tokenizer = File(directory, "tokenizer_m33.bin")
            val marker = File(directory, "installed_apk.txt")

            // Recopy the pair after an APK update so its files stay in sync.
            val apk = File(context.applicationInfo.sourceDir)
            val apkIdentity = "${apk.path}:${apk.length()}:${apk.lastModified()}"
            val installedIdentity = runCatching { marker.readText() }.getOrNull()

            if (installedIdentity != apkIdentity || !isReady(model) || !isReady(tokenizer)) {
                context.assets.open(MODEL_ASSET).use { input ->
                    writeAtomically(model, input)
                }
                context.assets.open(TOKENIZER_ASSET).use { input ->
                    writeAtomically(tokenizer, input)
                }
                apkIdentity.byteInputStream().use { input ->
                    writeAtomically(marker, input)
                }
            }

            model to tokenizer
        }

    fun copyModelFile(
        context: Context,
        sourceUri: Uri,
        fileName: String
    ): File {
        val destination = File(getModelDirectory(context), fileName)
        val input = context.contentResolver.openInputStream(sourceUri)
            ?: error("Cannot open selected file.")

        input.use { writeAtomically(destination, it) }
        return destination
    }

    fun getSavedModelFile(context: Context): File? =
        File(getModelDirectory(context), "llama_model.pte").takeIf(::isReady)

    fun getSavedTokenizerFile(context: Context): File? =
        File(getModelDirectory(context), "tokenizer.bin").takeIf(::isReady)

    private fun isReady(file: File): Boolean =
        file.isFile && file.length() > 0L

    private fun writeAtomically(destination: File, input: InputStream) {
        val file = AtomicFile(destination)
        val output = file.startWrite()

        try {
            check(input.copyTo(output, bufferSize = 256 * 1024) > 0L) {
                "Model file is empty: ${destination.name}"
            }
            file.finishWrite(output)
        } catch (failure: Throwable) {
            file.failWrite(output)
            throw failure
        }
    }
}