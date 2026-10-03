package com.example.data.download

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import androidx.core.content.FileProvider
import com.example.data.db.AppDatabase
import com.example.data.db.DownloadItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.RandomAccessFile
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

object DownloadManager {

    private val activeJobs = ConcurrentHashMap<Long, Job>()
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    fun getDownloadDir(context: Context): File {
        val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir, "ATP_Downloads")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun getCacheDir(context: Context): File {
        val dir = File(context.cacheDir, "download_cache")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun startDownload(
        context: Context,
        scope: CoroutineScope,
        url: String,
        suggestedFileName: String? = null
    ) {
        scope.launch(Dispatchers.IO) {
            val db = AppDatabase.getInstance(context).browserDao()
            val cleanName = resolveFileName(url, suggestedFileName)
            val cacheFile = File(getCacheDir(context), "part_${System.currentTimeMillis()}_$cleanName")
            val finalFile = File(getDownloadDir(context), cleanName)

            val downloadItem = DownloadItem(
                url = url,
                filename = cleanName,
                temporaryPath = cacheFile.absolutePath,
                finalPath = finalFile.absolutePath,
                downloadedBytes = 0,
                totalBytes = -1,
                status = "Downloading"
            )
            val id = db.insertDownload(downloadItem)
            val insertedItem = db.getDownloadById(id) ?: downloadItem.copy(id = id)

            executeDownload(context, insertedItem)
        }
    }

    fun resumeDownload(context: Context, scope: CoroutineScope, downloadId: Long) {
        scope.launch(Dispatchers.IO) {
            val db = AppDatabase.getInstance(context).browserDao()
            val item = db.getDownloadById(downloadId) ?: return@launch
            if (item.status == "Downloading") return@launch
            executeDownload(context, item)
        }
    }

    fun pauseDownload(context: Context, scope: CoroutineScope, downloadId: Long) {
        val job = activeJobs.remove(downloadId)
        job?.cancel()
        scope.launch(Dispatchers.IO) {
            val db = AppDatabase.getInstance(context).browserDao()
            val item = db.getDownloadById(downloadId) ?: return@launch
            db.updateDownload(item.copy(status = "Paused", speed = "0 KB/s"))
        }
    }

    fun cancelDownload(context: Context, scope: CoroutineScope, downloadId: Long) {
        val job = activeJobs.remove(downloadId)
        job?.cancel()
        scope.launch(Dispatchers.IO) {
            val db = AppDatabase.getInstance(context).browserDao()
            val item = db.getDownloadById(downloadId) ?: return@launch
            val partFile = File(item.temporaryPath)
            if (partFile.exists()) partFile.delete()
            db.deleteDownloadById(downloadId)
        }
    }

    private fun executeDownload(context: Context, item: DownloadItem) {
        val scope = CoroutineScope(Dispatchers.IO)
        val job = scope.launch {
            val db = AppDatabase.getInstance(context).browserDao()
            val partFile = File(item.temporaryPath)
            var currentBytes = if (partFile.exists()) partFile.length() else 0L

            try {
                db.updateDownload(item.copy(status = "Downloading", downloadedBytes = currentBytes))

                val requestBuilder = Request.Builder().url(item.url)
                if (currentBytes > 0) {
                    requestBuilder.header("Range", "bytes=$currentBytes-")
                    item.etag?.let { requestBuilder.header("If-Range", it) }
                }

                val response = client.newCall(requestBuilder.build()).execute()
                val isRangeSupported = response.code == 206
                val totalLength = response.body?.contentLength() ?: -1L
                val fullExpectedSize = if (isRangeSupported && totalLength > 0) currentBytes + totalLength else totalLength
                val newEtag = response.header("ETag") ?: item.etag

                if (!isRangeSupported && currentBytes > 0 && response.code == 200) {
                    // Range not supported, must restart from beginning
                    currentBytes = 0L
                    if (partFile.exists()) partFile.delete()
                }

                val inputStream: InputStream? = response.body?.byteStream()
                if (inputStream == null || !response.isSuccessful) {
                    db.updateDownload(item.copy(status = "Failed", errorMessage = "Server response: ${response.code}"))
                    return@launch
                }

                val raf = RandomAccessFile(partFile, "rw")
                if (isRangeSupported) {
                    raf.seek(currentBytes)
                } else {
                    raf.setLength(0)
                }

                val buffer = ByteArray(8192)
                var read: Int
                var lastProgressUpdate = System.currentTimeMillis()
                var bytesSinceLastUpdate = 0L

                while (inputStream.read(buffer).also { read = it } != -1) {
                    raf.write(buffer, 0, read)
                    currentBytes += read
                    bytesSinceLastUpdate += read

                    val now = System.currentTimeMillis()
                    if (now - lastProgressUpdate >= 500) {
                        val durationSec = (now - lastProgressUpdate) / 1000.0
                        val speedKbps = if (durationSec > 0) (bytesSinceLastUpdate / 1024.0 / durationSec).toInt() else 0
                        db.updateDownload(
                            item.copy(
                                downloadedBytes = currentBytes,
                                totalBytes = fullExpectedSize,
                                speed = "$speedKbps KB/s",
                                etag = newEtag,
                                status = "Downloading"
                            )
                        )
                        lastProgressUpdate = now
                        bytesSinceLastUpdate = 0L
                    }
                }
                raf.close()
                inputStream.close()

                // Finished! Move temporary part file to final path
                val finalFile = File(item.finalPath)
                if (finalFile.exists()) finalFile.delete()
                partFile.renameTo(finalFile)

                db.updateDownload(
                    item.copy(
                        status = "Completed",
                        downloadedBytes = currentBytes,
                        totalBytes = currentBytes,
                        speed = "0 KB/s",
                        finalPath = finalFile.absolutePath
                    )
                )
            } catch (e: CancellationException) {
                // User paused or cancelled
            } catch (e: Exception) {
                db.updateDownload(item.copy(status = "Failed", errorMessage = e.message ?: "Download failed"))
            } finally {
                activeJobs.remove(item.id)
            }
        }
        activeJobs[item.id] = job
    }

    private fun resolveFileName(url: String, suggested: String?): String {
        if (!suggested.isNullOrBlank()) return suggested
        val uri = try { Uri.parse(url) } catch (e: Exception) { null }
        val lastPath = uri?.lastPathSegment
        return if (!lastPath.isNullOrBlank() && lastPath.contains('.')) {
            lastPath
        } else {
            "download_${System.currentTimeMillis()}.bin"
        }
    }

    fun openFile(context: Context, item: DownloadItem) {
        val file = File(item.finalPath)
        if (!file.exists()) return

        val extension = file.extension.lowercase()
        val mimeType = when (extension) {
            "apk" -> "application/vnd.android.package-archive"
            "pdf" -> "application/pdf"
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "mp4" -> "video/mp4"
            "mp3" -> "audio/mpeg"
            "txt", "html", "htm", "js", "py", "sh" -> "text/plain"
            else -> "*/*"
        }

        val contentUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(contentUri, mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            // No app to handle file
        }
    }

    fun shareFile(context: Context, item: DownloadItem) {
        val file = File(item.finalPath)
        if (!file.exists()) return

        val contentUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "*/*"
            putExtra(Intent.EXTRA_STREAM, contentUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(shareIntent, "Share ${item.filename}").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }

    fun getDownloadCacheStats(context: Context): Pair<Int, Long> {
        val cacheFiles = getCacheDir(context).listFiles() ?: emptyArray()
        val totalBytes = cacheFiles.sumOf { it.length() }
        return Pair(cacheFiles.size, totalBytes)
    }

    fun clearDownloadCache(context: Context) {
        val cacheFiles = getCacheDir(context).listFiles() ?: emptyArray()
        for (f in cacheFiles) {
            f.delete()
        }
    }
}
