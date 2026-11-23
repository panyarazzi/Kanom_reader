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
class Migration5To6Test {
    @Test fun `migration adds original source filename and preserves existing books`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context).name(null)
                .callback(object : SupportSQLiteOpenHelper.Callback(5) {
                    override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                        db.execSQL("CREATE TABLE books (id TEXT NOT NULL PRIMARY KEY, uri TEXT NOT NULL, title TEXT NOT NULL, author TEXT, lang TEXT, coverPath TEXT, fileSize INTEGER NOT NULL, addedAt INTEGER NOT NULL, lastOpenedAt INTEGER, sha256 TEXT, sourceFolder TEXT)")
                        db.execSQL("INSERT INTO books (id,uri,title,fileSize,addedAt,sourceFolder) VALUES ('b1','content://old','Book',12,100,'Series')")
                    }
                    override fun onUpgrade(db: androidx.sqlite.db.SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
                }).build(),
        )
        val db = helper.writableDatabase
        MIGRATION_5_6.migrate(db)
        db.query("SELECT title, sourceFolder, sourceFileName FROM books WHERE id='b1'").use { cursor ->
            assertEquals(true, cursor.moveToFirst())
            assertEquals("Book", cursor.getString(0))
            assertEquals("Series", cursor.getString(1))
            assertEquals(true, cursor.isNull(2))
        }
        db.close()
    }
}
