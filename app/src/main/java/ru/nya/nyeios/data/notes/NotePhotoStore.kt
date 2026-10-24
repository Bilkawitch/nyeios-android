package ru.nya.nyeios.data.notes

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.UUID

/**
 * Owns note photos inside filesDir. Metadata only stores a relative path, so files survive
 * normal app updates and do not depend on a temporary Photo Picker grant.
 */
class NotePhotoStore(private val context: Context) {

    private val root: File
        get() = File(context.filesDir, ROOT_DIRECTORY)

    fun importUri(noteKey: String, uri: Uri): NotePhotoAttachment? {
        val mime = context.contentResolver.getType(uri).orEmpty()
        if (mime.isNotEmpty() && !mime.startsWith("image/")) return null
        val extension = extensionFor(mime)
        val directory = directoryFor(noteKey)
        if (!directory.exists() && !directory.mkdirs()) return null

        val file = File(directory, "${UUID.randomUUID()}.$extension")
        val temp = File(directory, "${file.name}.part")
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(temp).use { output -> input.copyTo(output) }
            } ?: return null
            if (!temp.renameTo(file)) {
                temp.copyTo(file, overwrite = true)
                temp.delete()
            }
            attachmentFor(noteKey, file, mime)
        } catch (_: Exception) {
            temp.delete()
            file.delete()
            null
        }
    }

    fun createCameraFile(noteKey: String): Pair<File, Uri>? {
        val directory = directoryFor(noteKey)
        if (!directory.exists() && !directory.mkdirs()) return null
        val file = File(directory, "capture-${UUID.randomUUID()}.jpg")
        val uri = try {
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
        } catch (_: Exception) {
            return null
        }
        return file to uri
    }

    fun attachmentForCamera(noteKey: String, file: File): NotePhotoAttachment? {
        if (!file.exists() || file.length() == 0L) {
            file.delete()
            return null
        }
        return try {
            attachmentFor(noteKey, file, "image/jpeg")
        } catch (_: Exception) {
            file.delete()
            null
        }
    }

    fun resolve(attachment: NotePhotoAttachment): File? {
        val relative = attachment.relativePath
        if (relative.isBlank()) return null
        return try {
            val filesRoot = context.filesDir.canonicalFile
            val file = File(filesRoot, relative).canonicalFile
            if (file.path.startsWith(filesRoot.path + File.separator)) file else null
        } catch (_: Exception) {
            null
        }
    }

    fun deleteAttachment(attachment: NotePhotoAttachment) {
        resolve(attachment)?.delete()
    }

    fun deleteNoteFiles(noteKey: String) {
        directoryFor(noteKey).deleteRecursively()
    }

    fun cleanupOrphans(notes: Collection<LessonNote>) {
        val referenced = notes.flatMap { note ->
            note.photoAttachments.orEmpty().map { it.relativePath }
        }.toSet()
        val filesRoot = context.filesDir
        val prefix = "$ROOT_DIRECTORY/"
        filesRoot.walkTopDown()
            .filter { it.isFile && it.relativeTo(filesRoot).invariantSeparatorsPath.startsWith(prefix) }
            .forEach { file ->
                val relative = file.relativeTo(filesRoot).invariantSeparatorsPath
                if (relative !in referenced) file.delete()
            }
    }

    private fun attachmentFor(
        noteKey: String,
        file: File,
        mime: String
    ): NotePhotoAttachment {
        val relative = file.relativeTo(context.filesDir).invariantSeparatorsPath
        return NotePhotoAttachment(
            id = UUID.randomUUID().toString(),
            relativePath = relative,
            mimeType = mime,
            addedAt = System.currentTimeMillis()
        )
    }

    private fun directoryFor(noteKey: String): File {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(noteKey.toByteArray(Charsets.UTF_8))
            .take(12)
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
        return File(root, digest)
    }

    private fun extensionFor(mime: String): String {
        val fromMime = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime)
        return when {
            !fromMime.isNullOrBlank() -> fromMime.lowercase()
            mime.contains("heic", true) || mime.contains("heif", true) -> "heic"
            else -> "jpg"
        }
    }

    companion object {
        const val ROOT_DIRECTORY = "note_photos"
        const val MAX_PHOTOS_PER_NOTE = 10
    }
}
