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
class Migration7To8Test {
    @Test
    fun `migration creates independent saved tag library and preserves assigned tags`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context).name(null)
                .callback(object : SupportSQLiteOpenHelper.Callback(7) {
                    override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                        db.execSQL("CREATE TABLE books (id TEXT NOT NULL PRIMARY KEY)")
                        db.execSQL("CREATE TABLE book_tags (bookId TEXT NOT NULL, tag TEXT NOT NULL, PRIMARY KEY(bookId, tag))")
                        db.execSQL("INSERT INTO books (id) VALUES ('b1')")
                        db.execSQL("INSERT INTO book_tags (bookId, tag) VALUES ('b1', 'แนะนำ')")
                    }
                    override fun onUpgrade(db: androidx.sqlite.db.SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
                }).build(),
        )
        val db = helper.writableDatabase
        MIGRATION_7_8.migrate(db)
        db.execSQL("INSERT INTO book_tag_library (tag) VALUES ('สำนวนดี')")
        db.execSQL("DELETE FROM book_tag_library WHERE tag = 'สำนวนดี'")

        db.query("SELECT tag FROM book_tags WHERE bookId='b1'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("แนะนำ", cursor.getString(0))
        }
        db.query("SELECT COUNT(*) FROM book_tag_library").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
        }
        db.close()
    }
}
