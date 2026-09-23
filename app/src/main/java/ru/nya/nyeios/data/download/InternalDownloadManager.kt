package ru.nya.nyeios.data.download

import android.content.ActivityNotFoundException
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.Call
import okhttp3.Request
import ru.nya.nyeios.data.repository.EiosRepository
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap

class InternalDownloadManager private constructor(private val context: Context) {

    private val repository = EiosRepository.getInstance(context)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val gson = Gson()

    private val downloadDir: File by lazy {
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)?.resolve("NyEIOS_Files")
            ?: File(context.filesDir, "downloads")
        if (!dir.exists()) dir.mkdirs()
        dir
    }

    private val metadataFile: File by lazy {
        File(context.filesDir, "download_registry.json")
    }

    private val _downloadStates = MutableStateFlow<Map<String, DownloadState>>(emptyMap())
    val downloadStates: StateFlow<Map<String, DownloadState>> = _downloadStates.asStateFlow()

    private val _downloadedFiles = MutableStateFlow<List<DownloadedFile>>(emptyList())
    val downloadedFiles: StateFlow<List<DownloadedFile>> = _downloadedFiles.asStateFlow()

    private val activeCalls = ConcurrentHashMap<String, Call>()
    private val activeJobs = ConcurrentHashMap<String, Job>()

    init {
        loadSavedDownloads()
    }

    fun sanitizeFileName(name: String): String {
        val sanitized = name.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim()
        return sanitized.ifEmpty { "file_${System.currentTimeMillis()}" }
    }

    fun getMimeType(file: File): String {
        val ext = file.extension.lowercase()
        val mapped = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
        if (!mapped.isNullOrEmpty()) return mapped

        return when (ext) {
            "pdf" -> "application/pdf"
            "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            "doc" -> "application/msword"
            "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            "xls" -> "application/vnd.ms-excel"
            "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
            "ppt" -> "application/vnd.ms-powerpoint"
            "zip" -> "application/zip"
            "rar" -> "application/x-rar-compressed"
            "7z" -> "application/x-7z-compressed"
            "txt" -> "text/plain"
            "rtf" -> "application/rtf"
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            else -> "application/octet-stream"
        }
    }

    fun formatFileSize(bytes: Long): String {
        if (bytes <= 0) return "0 Б"
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        return when {
            mb >= 1.0 -> String.format(java.util.Locale.US, "%.1f МБ", mb)
            kb >= 1.0 -> String.format(java.util.Locale.US, "%.1f КБ", kb)
            else -> "$bytes Б"
        }
    }

    fun getDownloadState(url: String, fileName: String? = null): DownloadState {
        val state = _downloadStates.value[url]
        if (state != null && state !is DownloadState.Idle) {
            return state
        }

        // Check if file exists on disk
        if (fileName != null) {
            val localFile = File(downloadDir, sanitizeFileName(fileName))
            if (localFile.exists() && localFile.length() > 0) {
                val completed = DownloadState.Completed(localFile, localFile.length())
                _downloadStates.update { it + (url to completed) }
                return completed
            }
        }

        return DownloadState.Idle
    }

    fun getFileForAttachment(url: String, fileName: String): File? {
        val downloaded = _downloadedFiles.value.firstOrNull { it.url == url }
        if (downloaded != null && downloaded.file.exists() && downloaded.file.length() > 0) {
            return downloaded.file
        }
        val fileByName = File(downloadDir, sanitizeFileName(fileName))
        if (fileByName.exists() && fileByName.length() > 0) {
            return fileByName
        }
        return null
    }

    fun downloadAttachment(url: String, fileName: String) {
        if (_downloadStates.value[url] is DownloadState.Downloading) {
            return
        }

        val cleanName = sanitizeFileName(fileName)
        val targetFile = File(downloadDir, cleanName)
        val tempFile = File(downloadDir, "${cleanName}.tmp")

        val job = scope.launch {
            _downloadStates.update {
                it + (url to DownloadState.Downloading(
                    progress = 0f,
                    bytesDownloaded = 0,
                    totalBytes = -1
                ))
            }

            try {
                if (tempFile.exists()) tempFile.delete()

                val cookies = repository.getActiveCookieString()
                val reqBuilder = Request.Builder()
                    .url(url)
                    .header("User-Agent", EiosRepository.BROWSER_USER_AGENT)

                if (cookies.isNotEmpty()) {
                    reqBuilder.header("Cookie", cookies)
                }

                val call = repository.okHttpClient.newCall(reqBuilder.build())
                activeCalls[url] = call

                val response = call.execute()
                if (!response.isSuccessful) {
                    throw Exception("Ошибка сервера при скачивании: HTTP ${response.code}")
                }

                val finalUrl = response.request.url.toString()
                if (finalUrl.contains("login=yes") || response.header("X-Bitrix-Ajax-Status") == "Authorize") {
                    throw Exception("Для скачивания требуется авторизация в ЭИОС")
                }

                val body = response.body ?: throw Exception("Пустой ответ от сервера")
                val totalBytes = body.contentLength()

                var bytesRead = 0L
                var lastProgressUpdate = 0L

                body.byteStream().use { input ->
                    FileOutputStream(tempFile).use { output ->
                        val buffer = ByteArray(8192)
                        var read: Int
                        while (input.read(buffer).also { read = it } != -1) {
                            output.write(buffer, 0, read)
                            bytesRead += read

                            val now = System.currentTimeMillis()
                            if (now - lastProgressUpdate > 120 || bytesRead == totalBytes) {
                                val progress = if (totalBytes > 0) bytesRead.toFloat() / totalBytes else -1f
                                _downloadStates.update {
                                    it + (url to DownloadState.Downloading(
                                        progress = progress,
                                        bytesDownloaded = bytesRead,
                                        totalBytes = totalBytes
                                    ))
                                }
                                lastProgressUpdate = now
                            }
                        }
                        output.flush()
                    }
                }

                if (targetFile.exists()) targetFile.delete()
                if (!tempFile.renameTo(targetFile)) {
                    tempFile.copyTo(targetFile, overwrite = true)
                    tempFile.delete()
                }

                val downloadedItem = DownloadedFile(
                    id = url,
                    name = cleanName,
                    url = url,
                    file = targetFile,
                    sizeBytes = targetFile.length(),
                    downloadedAt = System.currentTimeMillis(),
                    mimeType = getMimeType(targetFile)
                )

                addOrUpdateDownloadedRecord(downloadedItem)

                val completedState = DownloadState.Completed(targetFile, targetFile.length())
                _downloadStates.update { it + (url to completedState) }

            } catch (e: CancellationException) {
                if (tempFile.exists()) tempFile.delete()
                _downloadStates.update { it - url }
            } catch (e: Exception) {
                if (tempFile.exists()) tempFile.delete()
                val errorMsg = e.localizedMessage ?: e.message ?: "Ошибка скачивания файла"
                _downloadStates.update { it + (url to DownloadState.Failed(errorMsg)) }
            } finally {
                activeCalls.remove(url)
                activeJobs.remove(url)
            }
        }

        activeJobs[url] = job
    }

    fun cancelDownload(url: String) {
        activeCalls[url]?.cancel()
        activeJobs[url]?.cancel()
        activeCalls.remove(url)
        activeJobs.remove(url)

        _downloadStates.update { it - url }
    }

    fun openDownloadedFile(context: Context, file: File): Result<Unit> {
        if (!file.exists() || file.length() == 0L) {
            return Result.failure(Exception("Файл не найден на диске. Попробуйте скачать его заново."))
        }

        return try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val mimeType = getMimeType(file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val chooser = Intent.createChooser(intent, "Открыть: ${file.name}").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
            Result.success(Unit)
        } catch (e: ActivityNotFoundException) {
            Result.failure(Exception("Не найдено приложение для открытия файла .${file.extension}"))
        } catch (e: Exception) {
            Result.failure(Exception("Ошибка при открытии файла: ${e.localizedMessage ?: e.message}"))
        }
    }

    fun shareDownloadedFile(context: Context, file: File): Result<Unit> {
        if (!file.exists() || file.length() == 0L) {
            return Result.failure(Exception("Файл не найден для отправки"))
        }

        return try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val mimeType = getMimeType(file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, file.name)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(intent, "Поделиться: ${file.name}").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception("Не удалось отправить файл: ${e.localizedMessage ?: e.message}"))
        }
    }

    fun saveToPublicDownloads(context: Context, file: File): Result<String> {
        if (!file.exists() || file.length() == 0L) {
            return Result.failure(Exception("Исходный файл не найден"))
        }

        return try {
            val mimeType = getMimeType(file)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, file.name)
                    put(MediaStore.Downloads.MIME_TYPE, mimeType)
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val resolver = context.contentResolver
                val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                val uri = resolver.insert(collection, values)
                    ?: return Result.failure(Exception("Не удалось создать запись в системных Загрузках"))

                resolver.openOutputStream(uri)?.use { output ->
                    file.inputStream().use { input ->
                        input.copyTo(output)
                    }
                }
                values.clear()
                values.put(MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                Result.success("Файл сохранен в системную папку 'Загрузки'")
            } else {
                @Suppress("DEPRECATION")
                val pubDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (!pubDir.exists()) pubDir.mkdirs()
                val target = File(pubDir, file.name)
                file.copyTo(target, overwrite = true)
                Result.success("Файл сохранен в Загрузки: ${target.name}")
            }
        } catch (e: Exception) {
            Result.failure(Exception("Не удалось сохранить в Загрузки: ${e.localizedMessage ?: e.message}"))
        }
    }

    fun deleteDownloadedFile(file: File) {
        scope.launch {
            if (file.exists()) {
                file.delete()
            }
            val currentList = _downloadedFiles.value.toMutableList()
            val removedItems = currentList.filter { it.file.absolutePath == file.absolutePath }
            currentList.removeAll { it.file.absolutePath == file.absolutePath }
            _downloadedFiles.value = currentList
            saveDownloadsMetadata(currentList)

            _downloadStates.update { states ->
                val newStates = states.toMutableMap()
                for (item in removedItems) {
                    newStates.remove(item.url)
                }
                newStates
            }
        }
    }

    fun clearAllDownloads() {
        scope.launch {
            activeCalls.values.forEach { it.cancel() }
            activeCalls.clear()
            activeJobs.values.forEach { it.cancel() }
            activeJobs.clear()

            downloadDir.listFiles()?.forEach {
                try {
                    it.delete()
                } catch (e: Exception) {
                    // Ignore delete failure
                }
            }

            _downloadedFiles.value = emptyList()
            _downloadStates.value = emptyMap()
            if (metadataFile.exists()) metadataFile.delete()
        }
    }

    private fun addOrUpdateDownloadedRecord(item: DownloadedFile) {
        val currentList = _downloadedFiles.value.toMutableList()
        currentList.removeAll { it.file.absolutePath == item.file.absolutePath || it.url == item.url }
        currentList.add(0, item)
        _downloadedFiles.value = currentList
        saveDownloadsMetadata(currentList)
    }

    private fun saveDownloadsMetadata(list: List<DownloadedFile>) {
        try {
            val records = list.map {
                DownloadRecordDto(
                    id = it.id,
                    name = it.name,
                    url = it.url,
                    filePath = it.file.absolutePath,
                    sizeBytes = it.sizeBytes,
                    downloadedAt = it.downloadedAt,
                    mimeType = it.mimeType
                )
            }
            metadataFile.writeText(gson.toJson(records))
        } catch (e: Exception) {
            // Ignore metadata write error
        }
    }

    private fun loadSavedDownloads() {
        scope.launch {
            val list = mutableListOf<DownloadedFile>()
            val type = object : TypeToken<List<DownloadRecordDto>>() {}.type

            if (metadataFile.exists()) {
                try {
                    val records: List<DownloadRecordDto>? = gson.fromJson(metadataFile.readText(), type)
                    records?.forEach { r ->
                        val f = File(r.filePath)
                        if (f.exists() && f.length() > 0) {
                            list.add(
                                DownloadedFile(
                                    id = r.id,
                                    name = r.name,
                                    url = r.url,
                                    file = f,
                                    sizeBytes = f.length(),
                                    downloadedAt = r.downloadedAt,
                                    mimeType = r.mimeType
                                )
                            )
                        }
                    }
                } catch (e: Exception) {
                    // Ignore corrupt metadata
                }
            }

            // Also check for existing unindexed files in downloadDir
            downloadDir.listFiles()?.forEach { diskFile ->
                if (!diskFile.name.endsWith(".tmp") && diskFile.isFile && diskFile.length() > 0) {
                    if (list.none { it.file.absolutePath == diskFile.absolutePath }) {
                        list.add(
                            DownloadedFile(
                                id = diskFile.name,
                                name = diskFile.name,
                                url = "",
                                file = diskFile,
                                sizeBytes = diskFile.length(),
                                downloadedAt = diskFile.lastModified(),
                                mimeType = getMimeType(diskFile)
                            )
                        )
                    }
                }
            }

            _downloadedFiles.value = list

            val stateUpdates = mutableMapOf<String, DownloadState>()
            for (item in list) {
                if (item.url.isNotEmpty()) {
                    stateUpdates[item.url] = DownloadState.Completed(item.file, item.sizeBytes)
                }
            }
            _downloadStates.value = stateUpdates
        }
    }

    private data class DownloadRecordDto(
        val id: String,
        val name: String,
        val url: String,
        val filePath: String,
        val sizeBytes: Long,
        val downloadedAt: Long,
        val mimeType: String
    )

    companion object {
        @Volatile
        private var INSTANCE: InternalDownloadManager? = null

        fun getInstance(context: Context): InternalDownloadManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: InternalDownloadManager(context.applicationContext).also { INSTANCE = it }
            }
        }

        fun sanitizeFileName(name: String): String {
            val sanitized = name.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim()
            return sanitized.ifEmpty { "file_${System.currentTimeMillis()}" }
        }

        fun formatFileSize(bytes: Long): String {
            if (bytes <= 0) return "0 Б"
            val kb = bytes / 1024.0
            val mb = kb / 1024.0
            return when {
                mb >= 1.0 -> String.format(java.util.Locale.US, "%.1f МБ", mb)
                kb >= 1.0 -> String.format(java.util.Locale.US, "%.1f КБ", kb)
                else -> "$bytes Б"
            }
        }
    }
}
