package com.nextz.kanomtts.data.db
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
@Config(sdk = [35])
@RunWith(RobolectricTestRunner::class)
class Migration4To5Test {
    @Test fun `migration adds folder and annotation tables without losing books`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context).name(null)
                .callback(object : SupportSQLiteOpenHelper.Callback(4) {
                    override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                        db.execSQL("CREATE TABLE books (id TEXT NOT NULL PRIMARY KEY, uri TEXT NOT NULL, title TEXT NOT NULL, author TEXT, lang TEXT, coverPath TEXT, fileSize INTEGER NOT NULL, addedAt INTEGER NOT NULL, lastOpenedAt INTEGER, sha256 TEXT)")
                        db.execSQL("INSERT INTO books (id,uri,title,fileSize,addedAt) VALUES ('b1','content://old','Book',12,100)")
                    }
                    override fun onUpgrade(db: androidx.sqlite.db.SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
                }).build(),
        )
        val db = helper.writableDatabase
        MIGRATION_4_5.migrate(db)
        db.query("SELECT title, sourceFolder FROM books WHERE id='b1'").use { cursor ->
            assertEquals(true, cursor.moveToFirst())
            assertEquals("Book", cursor.getString(0))
            assertEquals(true, cursor.isNull(1))
        }
        db.execSQL("INSERT INTO book_tags (bookId,tag) VALUES ('b1','recommended')")
        db.execSQL("INSERT INTO book_notes (bookId,note,updatedAt) VALUES ('b1','Good book',200)")
        db.query("SELECT tag FROM book_tags WHERE bookId='b1'").use { cursor ->
            assertEquals(true, cursor.moveToFirst())
            assertEquals("recommended", cursor.getString(0))
        }
        db.query("SELECT note FROM book_notes WHERE bookId='b1'").use { cursor ->
            assertEquals(true, cursor.moveToFirst())
            assertEquals("Good book", cursor.getString(0))
        }
        db.close()
    }
}
