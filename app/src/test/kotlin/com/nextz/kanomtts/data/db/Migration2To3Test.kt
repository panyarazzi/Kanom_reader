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
class Migration2To3Test {
    @Test fun migrationCreatesHighlightsTableWithoutTouchingExistingRows() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(ctx).name(null)
                .callback(object : SupportSQLiteOpenHelper.Callback(2) {
                    override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                        db.execSQL("CREATE TABLE bookmarks (id INTEGER PRIMARY KEY NOT NULL, bookId TEXT NOT NULL, locator TEXT NOT NULL, snippet TEXT NOT NULL, createdAt INTEGER NOT NULL)")
                        db.execSQL("INSERT INTO bookmarks VALUES (1, 'book', 'locator-json', 'snippet', 10)")
                    }
                    override fun onUpgrade(db: androidx.sqlite.db.SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
                }).build(),
        )
        val db = helper.writableDatabase
        MIGRATION_2_3.migrate(db)
        db.query("SELECT bookId, snippet FROM bookmarks WHERE id = 1").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("book", cursor.getString(0))
            assertEquals("snippet", cursor.getString(1))
        }
        db.query("SELECT name FROM sqlite_master WHERE type='table' AND name='highlights'").use { cursor ->
            assertTrue(cursor.moveToFirst())
        }
        db.query("SELECT name FROM sqlite_master WHERE type='index' AND name='index_highlights_bookId'").use { cursor ->
            assertTrue(cursor.moveToFirst())
        }
        db.close()
    }
}
