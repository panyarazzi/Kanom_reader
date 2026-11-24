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
class Migration9To10Test {
    @Test
    fun `migration adds nullable description and preserves existing books`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context).name(null)
                .callback(object : SupportSQLiteOpenHelper.Callback(9) {
                    override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                        db.execSQL("CREATE TABLE books (id TEXT NOT NULL PRIMARY KEY, title TEXT NOT NULL)")
                        db.execSQL("INSERT INTO books (id, title) VALUES ('b1', 'Existing book')")
                    }
                    override fun onUpgrade(db: androidx.sqlite.db.SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
                }).build(),
        )
        val db = helper.writableDatabase
        MIGRATION_9_10.migrate(db)

        db.query("SELECT title, description FROM books WHERE id='b1'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Existing book", cursor.getString(0))
            assertTrue(cursor.isNull(1))
        }
        db.close()
    }
}
