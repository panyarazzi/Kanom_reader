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
class Migration1To2Test {
    @Test fun `migration adds showInReader defaulting to 0 and preserves rows`() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(ctx)
                .name(null) // in-memory
                .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                    override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                        db.execSQL("""CREATE TABLE replacement_rules (
                            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, scope TEXT NOT NULL,
                            bookId TEXT, pattern TEXT NOT NULL, replacement TEXT NOT NULL,
                            isRegex INTEGER NOT NULL, enabled INTEGER NOT NULL, position INTEGER NOT NULL)""")
                        db.execSQL("INSERT INTO replacement_rules (scope,bookId,pattern,replacement,isRegex,enabled,position) VALUES ('global',NULL,'a','b',0,1,0)")
                    }
                    override fun onUpgrade(db: androidx.sqlite.db.SupportSQLiteDatabase, o: Int, n: Int) {}
                }).build(),
        )
        val db = helper.writableDatabase
        MIGRATION_1_2.migrate(db)
        db.query("SELECT pattern, showInReader FROM replacement_rules").use { c ->
            assertEquals(true, c.moveToFirst())
            assertEquals("a", c.getString(0))
            assertEquals(0, c.getInt(1)) // default 0 = false, row preserved
        }
        db.close()
    }
}
