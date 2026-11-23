package com.nextz.kanomtts.data.db

import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@Config(sdk = [35])
@RunWith(RobolectricTestRunner::class)
class Migration6To7Test {
    @Test
    fun `migration adds nullable series metadata and preserves existing books`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context).name(null)
                .callback(object : SupportSQLiteOpenHelper.Callback(6) {
                    override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                        db.execSQL("CREATE TABLE books (id TEXT NOT NULL PRIMARY KEY, uri TEXT NOT NULL, title TEXT NOT NULL, author TEXT, lang TEXT, coverPath TEXT, fileSize INTEGER NOT NULL, addedAt INTEGER NOT NULL, lastOpenedAt INTEGER, sha256 TEXT, sourceFolder TEXT, sourceFileName TEXT)")
                        db.execSQL("INSERT INTO books (id,uri,title,fileSize,addedAt,sourceFileName) VALUES ('b1','content://old','Book',12,100,'original.epub')")
                    }
                    override fun onUpgrade(db: androidx.sqlite.db.SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
                }).build(),
        )
        val db = helper.writableDatabase
        MIGRATION_6_7.migrate(db)
        db.query("SELECT title, sourceFileName, seriesName, seriesPosition FROM books WHERE id='b1'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Book", cursor.getString(0))
            assertEquals("original.epub", cursor.getString(1))
            assertTrue(cursor.isNull(2))
            assertTrue(cursor.isNull(3))
        }
        db.close()
    }
}
