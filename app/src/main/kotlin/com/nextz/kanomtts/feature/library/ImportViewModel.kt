package com.nextz.kanomtts.feature.library

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import javax.inject.Inject

@HiltViewModel
class ImportViewModel @Inject constructor(
    private val importer: EpubImporter,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _events = Channel<ImportResult>(Channel.BUFFERED)
    val events: Flow<ImportResult> = _events.receiveAsFlow()

    fun importFrom(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            var displayName: String? = null
            var size: Long = 0L
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
                null,
                null,
                null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIdx >= 0) displayName = cursor.getString(nameIdx)
                    if (sizeIdx >= 0) size = cursor.getLong(sizeIdx)
                }
            }
            val result = importer.import(uri.toString(), size, displayName)
            _events.send(result)
        }
    }
}
