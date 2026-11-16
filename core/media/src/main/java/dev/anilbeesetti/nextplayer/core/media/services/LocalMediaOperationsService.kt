package dev.anilbeesetti.nextplayer.core.media.services

import android.Manifest
import android.app.Activity
import android.app.PendingIntent
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Process
import android.provider.BaseColumns
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.system.Os
import android.webkit.MimeTypeMap
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import dev.anilbeesetti.nextplayer.core.common.extensions.deleteMedia
import dev.anilbeesetti.nextplayer.core.common.extensions.getFilenameFromUri
import dev.anilbeesetti.nextplayer.core.common.extensions.getPath
import dev.anilbeesetti.nextplayer.core.common.extensions.scanPaths
import dev.anilbeesetti.nextplayer.core.common.extensions.updateMedia
import dev.anilbeesetti.nextplayer.core.common.service.SuspendActivityResultLauncher
import dev.anilbeesetti.nextplayer.core.common.service.registerForSuspendActivityResult
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Single

@Single
class LocalMediaOperationsService(private val context: Context) : MediaOperationsService {

    private lateinit var activity: Activity
    private val contentResolver = context.contentResolver
    private var mediaRequestLauncher:
        SuspendActivityResultLauncher<IntentSenderRequest, ActivityResult>? = null
    private var storagePermissionLauncher: SuspendActivityResultLauncher<String, Boolean>? = null

    override fun initialize(activity: ComponentActivity) {
        this.activity = activity
        mediaRequestLauncher = activity.registerForSuspendActivityResult(
            contract = ActivityResultContracts.StartIntentSenderForResult(),
        )
        storagePermissionLauncher = activity.registerForSuspendActivityResult(
            contract = ActivityResultContracts.RequestPermission(),
        )
    }

    override suspend fun deleteMedia(uris: List<Uri>, permanently: Boolean): Boolean =
        withContext(Dispatchers.IO) {
            return@withContext if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                if (permanently) deleteMediaR(uris = uris) else trashMediaR(uris = uris)
            } else {
                deleteMediaBelowR(uris = uris)
            }
        }

    override suspend fun restoreMedia(uris: List<Uri>): Boolean = withContext(Dispatchers.IO) {
        return@withContext Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
            trashMediaR(
                uris = uris,
                trashed = false,
            )
    }

    override suspend fun renameMedia(uri: Uri, to: String): Boolean = withContext(Dispatchers.IO) {
        return@withContext if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            renameMediaR(
                uri = uri,
                to = to,
            )
        } else {
            renameMediaBelowR(
                uri = uri,
                to = to,
            )
        }
    }

    override suspend fun renameFolder(
        oldPath: String,
        newName: String,
        uris: List<Uri>,
    ): Boolean = withContext(Dispatchers.IO) {
        val cleanName = newName.trim()
        if (cleanName.isEmpty() ||
            cleanName == "." ||
            cleanName == ".." ||
            cleanName.contains('/') ||
            cleanName.contains('\\')
        ) {
            return@withContext false
        }

        val oldDir = File(oldPath)
        val parentDir = oldDir.parentFile ?: return@withContext false
        if (cleanName == oldDir.name) return@withContext true

        val newDir = File(parentDir, cleanName)
        if (newDir.exists()) return@withContext false

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val mediaStoreUris = uris.filter { it.authority == MediaStore.AUTHORITY }
            if (mediaStoreUris.isEmpty()) {
                return@withContext runCatching { oldDir.renameTo(newDir) }.getOrDefault(false)
            }
            val granted = try {
                requestWriteAccessR(uris = mediaStoreUris)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                false
            }
            if (!granted) return@withContext false

            var anyUpdated = false
            for (uri in mediaStoreUris) {
                val loc = mediaStoreLocation(uri = uri) ?: continue
                val fileParent = File(loc.path).parent ?: continue
                val subSuffix = if (fileParent == oldDir.path) {
                    ""
                } else if (fileParent.startsWith(oldDir.path + File.separator)) {
                    fileParent.removePrefix(oldDir.path + File.separator)
                } else {
                    continue
                }
                val oldTrail = if (subSuffix.isEmpty()) {
                    "${oldDir.name}/"
                } else {
                    "${oldDir.name}/$subSuffix/"
                }
                val newTrail = if (subSuffix.isEmpty()) {
                    "$cleanName/"
                } else {
                    "$cleanName/$subSuffix/"
                }
                val currentRel = loc.folder.relativePath
                if (!currentRel.endsWith(oldTrail)) continue
                val newRel = currentRel.removeSuffix(oldTrail) + newTrail

                val updated = contentResolver.updateMedia(
                    uri = uri,
                    contentValues = ContentValues().apply {
                        put(
                            MediaStore.MediaColumns.RELATIVE_PATH,
                            newRel,
                        )
                    },
                )
                if (updated) anyUpdated = true
            }

            if (anyUpdated) {
                runCatching {
                    oldDir.walkBottomUp().forEach { dir ->
                        if (dir.isDirectory && dir.listFiles().isNullOrEmpty()) {
                            dir.delete()
                        }
                    }
                }
            }
            return@withContext anyUpdated
        } else {
            val granted = context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
                PackageManager.PERMISSION_GRANTED ||
                storagePermissionLauncher?.launch(
                    input = Manifest.permission.WRITE_EXTERNAL_STORAGE,
                ) == true
            if (!granted) return@withContext false

            val renamed = runCatching { oldDir.renameTo(newDir) }.getOrDefault(false)
            if (!renamed) return@withContext false

            val pathsToScan = mutableListOf<String>()
            for (uri in uris) {
                val oldFilePath = context.getPath(uri = uri) ?: continue
                if (oldFilePath.startsWith(oldDir.path + File.separator)) {
                    val newFilePath = newDir.path + oldFilePath.removePrefix(oldDir.path)
                    pathsToScan.add(oldFilePath)
                    pathsToScan.add(newFilePath)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        val loc = mediaStoreLocation(uri = uri)
                        val fileParent = File(oldFilePath).parent
                        val subSuffix = if (fileParent == oldDir.path) {
                            ""
                        } else if (fileParent != null &&
                            fileParent.startsWith(oldDir.path + File.separator)
                        ) {
                            fileParent.removePrefix(oldDir.path + File.separator)
                        } else {
                            null
                        }
                        if (loc != null && subSuffix != null) {
                            val oldTrail = if (subSuffix.isEmpty()) {
                                "${oldDir.name}/"
                            } else {
                                "${oldDir.name}/$subSuffix/"
                            }
                            val newTrail = if (subSuffix.isEmpty()) {
                                "$cleanName/"
                            } else {
                                "$cleanName/$subSuffix/"
                            }
                            if (loc.folder.relativePath.endsWith(oldTrail)) {
                                contentResolver.updateMedia(
                                    uri = uri,
                                    contentValues = ContentValues().apply {
                                        put(
                                            MediaStore.MediaColumns.RELATIVE_PATH,
                                            loc.folder.relativePath.removeSuffix(oldTrail) +
                                                newTrail,
                                        )
                                    },
                                )
                            }
                        }
                    } else {
                        contentResolver.updateMedia(
                            uri = uri,
                            contentValues = ContentValues().apply {
                                put(
                                    MediaStore.Files.FileColumns.DATA,
                                    newFilePath,
                                )
                            },
                        )
                    }
                }
            }
            if (pathsToScan.isNotEmpty()) {
                context.scanPaths(paths = pathsToScan)
            }
            return@withContext true
        }
    }

    override suspend fun shareMedia(uris: List<Uri>) {
        val intent = Intent.createChooser(
            Intent().apply {
                type = "video/*"
                action = Intent.ACTION_SEND_MULTIPLE
                putParcelableArrayListExtra(
                    Intent.EXTRA_STREAM,
                    ArrayList(uris),
                )
            },
            null,
        )
        activity.startActivity(intent)
    }

    override suspend fun moveMedia(targets: Map<Uri, File>): Map<Uri, File?> = moveMedia(
        targets = targets,
        onMoveCommitted = null,
    )

    /**
     * The internal callback is a deterministic synchronization seam for commit-boundary tests.
     * Production calls use [moveMedia] and pay no callback cost.
     */
    internal suspend fun moveMedia(
        targets: Map<Uri, File>,
        onMoveCommitted: (suspend (Uri) -> Unit)?,
    ): Map<Uri, File?> {
        val uris = targets.keys.toList()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val mediaStoreUris = uris.filter { it.authority == MediaStore.AUTHORITY }
            if (mediaStoreUris.isNotEmpty()) {
                val granted = try {
                    requestWriteAccessR(uris = mediaStoreUris)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    false
                }
                if (!granted) return targets.keys.associateWith { null }
            }
        }

        val callerJob = currentCoroutineContext()[Job]
        return withContext(context = NonCancellable) {
            withContext(Dispatchers.IO) io@{
                val movedFiles = linkedMapOf<Uri, File?>()
                val entries = targets.entries.iterator()
                var moveCommitted = false
                while (entries.hasNext()) {
                    val (uri, destination) = entries.next()
                    val movedFile = try {
                        callerJob?.ensureActive()
                        val sourceFile = context.getPath(uri = uri)?.let { File(it) }
                        if (sourceFile == null) {
                            null
                        } else {
                            destination.parentFile?.mkdirs()
                            moveFileWithoutOverwrite(
                                source = sourceFile,
                                destination = destination,
                            ) { callerJob?.ensureActive() }
                        }
                    } catch (e: CancellationException) {
                        if (!moveCommitted) throw e
                        movedFiles[uri] = null
                        while (entries.hasNext()) movedFiles[entries.next().key] = null
                        return@io movedFiles
                    } catch (e: Exception) {
                        null
                    }
                    movedFiles[uri] = movedFile
                    moveCommitted = moveCommitted || movedFile != null
                    if (movedFile != null) onMoveCommitted?.invoke(uri)
                }
                movedFiles
            }
        }
    }

    /** Copies into an exclusively-created destination before atomically committing source deletion. */
    internal fun moveFileWithoutOverwrite(
        source: File,
        destination: File,
        ensureActive: () -> Unit,
    ): File? {
        if (!source.exists() || !destination.createNewFile()) return null
        val expectedBytes = source.length()

        val copiedBytes = try {
            source.inputStream().use { input ->
                FileOutputStream(destination).use { output ->
                    val buffer = ByteArray(size = DEFAULT_BUFFER_SIZE)
                    var copied = 0L
                    while (true) {
                        ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(
                            buffer,
                            0,
                            read,
                        )
                        copied += read
                    }
                    output.fd.sync()
                    ensureActive()
                    copied
                }
            }
        } catch (e: CancellationException) {
            destination.delete()
            throw e
        } catch (e: Exception) {
            destination.delete()
            return null
        }

        // Cancellation before this point rolls back the destination. From here, the move is committed.
        if (copiedBytes != expectedBytes || !source.delete()) {
            destination.delete()
            return null
        }
        return destination
    }

    override fun transferMedia(
        uris: List<Uri>,
        folderUri: Uri,
        mode: TransferMode,
    ): Flow<TransferEvent> = flow {
        val destinationDir = folderUri.toTreeDocumentUri()

        // Moving a file into the folder it already lives in is a no-op, so keep those aside.
        val (sameFolder, sources) = if (mode == TransferMode.MOVE) {
            uris.partition {
                isInFolder(
                    uri = it,
                    folderDocUri = destinationDir,
                )
            }
        } else {
            emptyList<Uri>() to uris
        }

        if (mode == TransferMode.MOVE && sources.isNotEmpty()) {
            val granted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                requestWriteAccessR(uris = sources.filter { it.authority == MediaStore.AUTHORITY })
            } else {
                context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
                    PackageManager.PERMISSION_GRANTED ||
                    storagePermissionLauncher?.launch(
                        input = Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    ) == true
            }
            if (!granted) {
                emit(
                    TransferEvent.Completed(
                        result = TransferResult(
                            succeeded = 0,
                            failed = sources.size,
                            sameFolderSkipped = sameFolder.size,
                        ),
                    ),
                )
                return@flow
            }
        }

        val sizes = sources.map { sizeOf(uri = it) }
        val overallTotal = sizes.sum()

        val createdPaths = mutableListOf<String>()
        var originalsNotDeleted = false
        var overallCopied = 0L
        var succeeded = 0

        sources.forEachIndexed { index, source ->
            val name = context.getFilenameFromUri(uri = source).takeIf { it.isNotBlank() }
            val currentTotal = sizes[index]

            fun progress(copied: Long) = TransferProgress(
                totalFiles = sources.size,
                currentIndex = index,
                currentName = name,
                currentBytesCopied = copied,
                currentBytesTotal = currentTotal,
                overallBytesCopied = overallCopied + copied,
                overallBytesTotal = overallTotal,
            )

            emit(TransferEvent.Progress(progress = progress(copied = 0)))
            if (name == null) return@forEachIndexed

            if (mode == TransferMode.MOVE) {
                when (
                    moveWithinVolume(
                        source = source,
                        destinationDir = destinationDir,
                    )
                ) {
                    DirectMoveResult.MOVED -> {
                        succeeded++
                        emit(TransferEvent.Progress(progress = progress(copied = currentTotal)))
                        overallCopied += currentTotal
                        return@forEachIndexed
                    }

                    DirectMoveResult.FAILED -> return@forEachIndexed

                    DirectMoveResult.FALLBACK -> Unit
                }
            }

            val copied =
                copyToDocument(
                    source = source,
                    destinationDir = destinationDir,
                    name = name,
                    expectedSize = currentTotal,
                ) { copiedSoFar ->
                    emit(TransferEvent.Progress(progress = progress(copied = copiedSoFar)))
                } ?: return@forEachIndexed

            succeeded++
            overallCopied += copied.bytes
            context.getPath(copied.uri)?.let { createdPaths.add(element = it) }
            if (mode == TransferMode.MOVE) {
                // Finish each committed move even if cancellation arrives after the copy.
                val deleted = withContext(NonCancellable) {
                    contentResolver.deleteMedia(uri = source)
                }
                originalsNotDeleted = originalsNotDeleted || !deleted
            }
        }

        if (createdPaths.isNotEmpty()) context.scanPaths(paths = createdPaths)

        emit(
            TransferEvent.Completed(
                result = TransferResult(
                    succeeded = succeeded,
                    failed = sources.size - succeeded,
                    sameFolderSkipped = sameFolder.size,
                    originalsNotDeleted = originalsNotDeleted,
                ),
            ),
        )
    }.flowOn(Dispatchers.IO)

    private fun isInFolder(uri: Uri, folderDocUri: Uri): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val target = folderDocUri.mediaStoreFolder()
            val source = mediaStoreLocation(uri = uri)
            if (target != null && source != null) return target == source.folder
        }
        val folderPath =
            runCatching { context.getPath(uri = folderDocUri) }.getOrNull() ?: return false
        val filePath = runCatching { context.getPath(uri = uri) }.getOrNull() ?: return false
        return File(filePath).parent == folderPath
    }

    private enum class DirectMoveResult { MOVED, FALLBACK, FAILED }

    private data class StorageFolder(val volume: String, val relativePath: String)
    private data class MediaStoreLocation(val folder: StorageFolder, val path: String)

    private fun Uri.mediaStoreFolder(): StorageFolder? {
        if (authority != "com.android.externalstorage.documents") return null
        val parts = DocumentsContract.getDocumentId(this).split(
            ':',
            limit = 2,
        )
        if (parts.size != 2) return null
        val path = parts[1].trimEnd('/')
        if (path.isEmpty() || path.split('/').any { it.isEmpty() || it == "." || it == ".." }) {
            return null
        }
        return StorageFolder(
            volume = if (parts[0].equals(
                    "primary",
                    ignoreCase = true,
                )
            ) {
                "external_primary"
            } else {
                parts[0].lowercase(Locale.ROOT)
            },
            relativePath = "$path/",
        )
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun mediaStoreLocation(uri: Uri): MediaStoreLocation? {
        if (uri.authority != MediaStore.AUTHORITY) return null
        return runCatching {
            contentResolver.query(
                uri,
                arrayOf(
                    MediaStore.MediaColumns.VOLUME_NAME,
                    MediaStore.MediaColumns.RELATIVE_PATH,
                    MediaStore.MediaColumns.DATA,
                ),
                null,
                null,
                null,
            )?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                val volume = cursor.getString(0) ?: return@use null
                val folder = cursor.getString(1) ?: return@use null
                val path = cursor.getString(2) ?: return@use null
                MediaStoreLocation(
                    folder = StorageFolder(
                        volume = volume,
                        relativePath = folder,
                    ),
                    path = path,
                )
            }
        }.getOrNull()
    }

    private suspend fun moveWithinVolume(source: Uri, destinationDir: Uri): DirectMoveResult {
        if (source.authority != MediaStore.AUTHORITY) return DirectMoveResult.FALLBACK
        val target = destinationDir.mediaStoreFolder() ?: return DirectMoveResult.FALLBACK
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val before = mediaStoreLocation(uri = source) ?: return DirectMoveResult.FALLBACK
            if (before.folder.volume != target.volume || !File(before.path).isFile) {
                return DirectMoveResult.FALLBACK
            }
            currentCoroutineContext().ensureActive()
            return withContext(NonCancellable) {
                val updated = contentResolver.updateMedia(
                    uri = source,
                    contentValues = ContentValues().apply {
                        put(
                            MediaStore.MediaColumns.RELATIVE_PATH,
                            target.relativePath,
                        )
                    },
                )
                if (updated) {
                    DirectMoveResult.MOVED
                } else {
                    // A provider failure can happen after the file has already moved.
                    val after = mediaStoreLocation(uri = source)
                    when {
                        after?.folder == target && File(after.path).isFile -> DirectMoveResult.MOVED
                        after == before && File(before.path).isFile -> DirectMoveResult.FALLBACK
                        else -> DirectMoveResult.FAILED
                    }
                }
            }
        }

        if (target.volume != "external_primary") return DirectMoveResult.FALLBACK
        val sourceFile = context.getPath(uri = source)?.let { File(it) }
            ?: return DirectMoveResult.FALLBACK
        val folder = File(
            Environment.getExternalStorageDirectory(),
            target.relativePath,
        )
        val sameFilesystem = runCatching {
            Os.stat(sourceFile.
