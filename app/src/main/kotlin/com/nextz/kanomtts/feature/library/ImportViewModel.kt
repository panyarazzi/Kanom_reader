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
        viewModelScope.launch(Dispatchers.IO) {
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
            suspend fun scan(folder: DocumentFile, relativePath: String) {
                currentCoroutineContext().ensureActive()
                val children = try {
                    folder.listFiles()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    _events.send(ImportResult.Unreadable)
                    return
                }
                for (child in children) {
                    currentCoroutineContext().ensureActive()
                    if (child.isDirectory) {
                        val childPath = listOf(relativePath, child.name.orEmpty())
                            .filter { it.isNotBlank() }
                            .joinToString("/")
                        scan(child, childPath)
                    } else if (child.isFile && child.name?.endsWith(".epub", ignoreCase = true) == true) {
                        epubCount++
                        val result = try {
                            importer.import(
                                uri = child.uri.toString(),
                                fileSize = child.length(),
                                displayName = child.name,
                                sourceFolder = listOf(root.name.orEmpty(), relativePath)
                                    .filter { it.isNotBlank() }
                                    .joinToString("/"),
                            )
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            ImportResult.Unreadable
                        }
                        _events.send(result)
                    }
                }
            }

            scan(root, "")
            if (epubCount == 0) _events.send(ImportResult.EmptyFolder)
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
