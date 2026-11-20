package com.nextz.kanomtts.data.repository

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.nextz.kanomtts.data.db.BookDao
import com.nextz.kanomtts.data.db.BookmarkDao
import com.nextz.kanomtts.data.db.ProgressDao
import com.nextz.kanomtts.data.db.ReplacementRuleDao
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject

class DefaultBookDeleter @Inject constructor(
    private val bookDao: BookDao,
    private val progressDao: ProgressDao,
    private val bookmarkDao: BookmarkDao,
    private val replacementRuleDao: ReplacementRuleDao,
    @ApplicationContext private val context: Context,
) : BookDeleter {
    override suspend fun delete(bookId: String) {
        val book = bookDao.getById(bookId) ?: return
        runCatching {
            context.contentResolver.releasePersistableUriPermission(
                Uri.parse(book.uri), Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
        book.coverPath?.let { runCatching { File(it).delete() } }
        progressDao.deleteByBook(bookId)
        bookmarkDao.deleteByBook(bookId)
        replacementRuleDao.deleteByBook(bookId)
        bookDao.delete(bookId)
    }
}
