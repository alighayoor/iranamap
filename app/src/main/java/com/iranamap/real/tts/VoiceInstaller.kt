package com.iranamap.real.tts

import android.content.Context
import android.util.Log
import com.iranamap.real.core.VoiceModel
import com.iranamap.real.core.VoiceCatalog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

/**
 * نصب و مدیریت صداهای فارسی:
 * ۱) صدای همراه برنامه از پوشه‌ی assets استخراج می‌شود.
 * ۲) صداهای دیگر به‌صورت tar.bz2 دانلود و باز می‌شوند.
 */
object VoiceInstaller {

    private const val TAG = "VoiceInstaller"
    private lateinit var appContext: Context

    private val http: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    private fun baseDir(): File = File(appContext.filesDir, "tts").apply { mkdirs() }

    fun modelDir(model: VoiceModel): File = File(baseDir(), model.folder)

    fun modelFileFor(dir: File): File? =
        dir.listFiles()?.firstOrNull { it.isFile && it.name.endsWith(".onnx") && !it.name.endsWith(".json") }

    fun isInstalled(model: VoiceModel): Boolean {
        val dir = modelDir(model)
        val hasModel = modelFileFor(dir) != null
        val hasTokens = File(dir, "tokens.txt").exists()
        val hasEspeak = File(dir, "espeak-ng-data").isDirectory
        return hasModel && hasTokens && hasEspeak
    }

    fun installedVoices(): List<VoiceModel> = VoiceCatalog.all.filter { isInstalled(it) }

    fun installedSizeBytes(): Long {
        var total = 0L
        baseDir().walkTopDown().filter { it.isFile }.forEach { total += it.length() }
        return total
    }

    fun delete(model: VoiceModel): Boolean {
        val dir = modelDir(model)
        return dir.exists() && dir.deleteRecursively()
    }

    /** اطمینان از نصب بودن صدا؛ در صورت لزوم صدای همراه برنامه را از assets خارج می‌کند. */
    suspend fun ensureInstalled(
        model: VoiceModel,
        onProgress: (Float) -> Unit = {}
    ): Result<File> = withContext(Dispatchers.IO) {
        if (isInstalled(model)) return@withContext Result.success(modelDir(model))
        if (model.bundled) {
            return@withContext extractBundled(model, onProgress)
        }
        Result.failure(IllegalStateException("این صدا نصب نشده است؛ ابتدا آن را دانلود کنید."))
    }

    private fun extractBundled(model: VoiceModel, onProgress: (Float) -> Unit): Result<File> {
        val assetRoot = "tts/${model.folder}"
        val target = modelDir(model)
        return try {
            val files = listAssetFiles(assetRoot)
            if (files.isEmpty()) {
                return Result.failure(IllegalStateException("صدای همراه برنامه در assets یافت نشد"))
            }
            target.mkdirs()
            var done = 0
            for (relative in files) {
                val assetPath = "$assetRoot/$relative"
                val outFile = File(target, relative)
                outFile.parentFile?.mkdirs()
                appContext.assets.open(assetPath).use { input ->
                    FileOutputStream(outFile).use { output -> input.copyTo(output) }
                }
                done++
                onProgress(done.toFloat() / files.size)
            }
            Result.success(target)
        } catch (e: Exception) {
            Log.e(TAG, "استخراج صدای همراه برنامه ناموفق بود", e)
            Result.failure(e)
        }
    }

    private fun listAssetFiles(root: String): List<String> {
        val out = ArrayList<String>()
        fun walk(path: String, prefix: String) {
            val children = appContext.assets.list(path) ?: return
            for (child in children) {
                val childPath = "$path/$child"
                val childPrefix = if (prefix.isEmpty()) child else "$prefix/$child"
                val grand = appContext.assets.list(childPath)
                if (grand.isNullOrEmpty()) {
                    out.add(childPrefix)
                } else {
                    walk(childPath, childPrefix)
                }
            }
        }
        walk(root, "")
        return out
    }

    /** دانلود و نصب یک صدا از اینترنت (با پیشرفت درصدی). */
    suspend fun download(
        model: VoiceModel,
        onProgress: (Float) -> Unit = {}
    ): Result<File> = withContext(Dispatchers.IO) {
        val archive = File(appContext.cacheDir, "${model.id}.tar.bz2")
        try {
            val request = Request.Builder()
                .url(model.downloadUrl)
                .header("User-Agent", "IranAmap/2.0 (Android)")
                .build()
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(IOExceptionWithMessage("کد پاسخ سرور: ${response.code}"))
                }
                val body = response.body ?: return@withContext Result.failure(
                    IOExceptionWithMessage("پاسخ خالی از سرور")
                )
                val total = body.contentLength()
                body.byteStream().use { input ->
                    FileOutputStream(archive).use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var read: Int
                        var written = 0L
                        while (input.read(buffer).also { read = it } > 0) {
                            output.write(buffer, 0, read)
                            written += read
                            if (total > 0) onProgress((written.toFloat() / total).coerceIn(0f, 0.9f))
                        }
                    }
                }
            }
            val target = baseDir()
            extractTarBz2(archive, target, onProgress = { p -> onProgress(0.9f + p * 0.1f) })
            archive.delete()
            if (isInstalled(model)) Result.success(modelDir(model))
            else Result.failure(IOExceptionWithMessage("فایل‌های صدا پس از استخراج کامل نبودند"))
        } catch (e: Exception) {
            Log.e(TAG, "دانلود صدا ناموفق بود", e)
            archive.delete()
            Result.failure(e)
        }
    }

    private fun extractTarBz2(archive: File, targetRoot: File, onProgress: (Float) -> Unit) {
        targetRoot.mkdirs()
        TarArchiveInputStream(BZip2CompressorInputStream(BufferedInputStream(archive.inputStream()))).use { tar ->
            var entry = tar.nextEntry
            while (entry != null) {
                val name = entry.name.replace('\\', '/')
                if (name.contains("..")) {
                    entry = tar.nextEntry
                    continue
                }
                val outFile = File(targetRoot, name)
                if (entry.isDirectory) {
                    outFile.mkdirs()
                } else {
                    outFile.parentFile?.mkdirs()
                    FileOutputStream(outFile).use { output -> tar.copyTo(output) }
                }
                entry = tar.nextEntry
            }
        }
        onProgress(1f)
    }

    class IOExceptionWithMessage(message: String) : Exception(message)
}
