package com.nextz.kanomtts.feature.library

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

@HiltViewModel
class ImportViewModel @Inject constructor(
    private val importer: EpubImporter,
    @ApplicationContext private val context: Context,
) : ViewModel() {
    private val _events = Channel<ImportResult>(Channel.BUFFERED)
    val events: Flow<ImportResult> = _events.receiveAsFlow()
    private var activeFolderImport: Job? = null

    fun importFrom(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = try {
                val (displayName, size) = readMetadata(uri)
                importer.import(uri.toString(), size, displayName)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                ImportResult.Unreadable
            }
            _events.send(result)
        }
    }

    fun importFolder(treeUri: Uri) {
        if (activeFolderImport?.isActive == true) return
        activeFolderImport = viewModelScope.launch(Dispatchers.IO) {
            val root = try {
                DocumentFile.fromTreeUri(context, treeUri)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }
            if (root == null) {
                _events.send(ImportResult.Unreadable)
                return@launch
            }

            var epubCount = 0
            var importedCount = 0
            var duplicateCount = 0
            var relinkedCount = 0
            var unreadableCount = 0
            var scanFailed = false
            val rootName = try {
                root.name.orEmpty()
            } catch (_: Exception) {
                scanFailed = true
                unreadableCount++
                ""
            }

            suspend fun scan(folder: DocumentFile, relativePath: String) {
                currentCoroutineContext().ensureActive()
                val children = try {
                    folder.listFiles()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    scanFailed = true
                    unreadableCount++
                    return
                }
                for (child in children) {
                    currentCoroutineContext().ensureActive()
                    val isDirectory = try {
                        child.isDirectory
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        scanFailed = true
                        unreadableCount++
                        continue
                    }
                    if (isDirectory) {
                        val childName = try {
                            child.name.orEmpty()
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            scanFailed = true
                            unreadableCount++
                            ""
                        }
                        val childPath = listOf(relativePath, childName)
                            .filter { it.isNotBlank() }
                            .joinToString("/")
                        scan(child, childPath)
                        continue
                    }
                    val isFile = try {
                        child.isFile
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        scanFailed = true
                        unreadableCount++
                        continue
                    }
                    if (!isFile) continue
                    val displayName = try {
                        child.name
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        scanFailed = true
                        unreadableCount++
                        null
                    }
                    if (displayName == null) {
                        scanFailed = true
                        unreadableCount++
                        continue
                    }
                    if (!displayName.endsWith(".epub", ignoreCase = true)) continue
                    epubCount++
                    val result = try {
                        val size = runCatching { child.length() }.getOrDefault(0L)
                        importer.import(
                            uri = child.uri.toString(),
                            fileSize = size,
                            displayName = displayName,
                            sourceFolder = listOf(rootName, relativePath)
                                .filter { it.isNotBlank() }
                                .joinToString("/"),
                        )
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        ImportResult.Unreadable
                    }
                    when (result) {
                        is ImportResult.Imported -> importedCount++
                        is ImportResult.Relinked -> relinkedCount++
                        ImportResult.Duplicate -> duplicateCount++
                        ImportResult.Unreadable -> unreadableCount++
                        ImportResult.EmptyFolder, is ImportResult.FolderSummary -> Unit
                    }
                }
            }

            scan(root, "")
            when {
                epubCount == 0 && !scanFailed -> _events.send(ImportResult.EmptyFolder)
                epubCount == 0 -> _events.send(ImportResult.Unreadable)
                else -> _events.send(
                    ImportResult.FolderSummary(
                        scanned = epubCount,
                        imported = importedCount,
                        duplicates = duplicateCount,
                        relinked = relinkedCount,
                        unreadable = unreadableCount,
                    )
                )
            }
        }
    }

    private fun readMetadata(uri: Uri): Pair<String?, Long> {
        var displayName: String? = null
        var size = 0L
        context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
            null,
            null,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (nameIndex >= 0) displayName = cursor.getString(nameIndex)
                if (sizeIndex >= 0) size = cursor.getLong(sizeIndex)
            }
        }
        return displayName to size
    }
}
