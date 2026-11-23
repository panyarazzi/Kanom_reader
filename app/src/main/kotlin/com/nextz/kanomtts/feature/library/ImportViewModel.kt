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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
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
            val (displayName, size) = readMetadata(uri)
            _events.send(importer.import(uri.toString(), size, displayName))
        }
    }
    fun importFolder(treeUri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val root = DocumentFile.fromTreeUri(context, treeUri) ?: return@launch
            suspend fun scan(folder: DocumentFile, relativePath: String) {
                folder.listFiles().forEach { child ->
                    if (child.isDirectory) {
                        val childPath = listOf(relativePath, child.name.orEmpty()).filter { it.isNotBlank() }.joinToString("/")
                        scan(child, childPath)
                    } else if (child.isFile && child.name?.endsWith(".epub", ignoreCase = true) == true) {
                        _events.send(importer.import(
                            uri = child.uri.toString(),
                            fileSize = child.length(),
                            displayName = child.name,
                            sourceFolder = listOf(root.name.orEmpty(), relativePath).filter { it.isNotBlank() }.joinToString("/"),
                        ))
                    }
                }
            }
            scan(root, "")
        }
    }
    private fun readMetadata(uri: Uri): Pair<String?, Long> {
        var displayName: String? = null
        var size = 0L
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
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
