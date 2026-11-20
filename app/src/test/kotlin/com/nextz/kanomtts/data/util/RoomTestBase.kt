package com.nextz.kanomtts.data.util

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.nextz.kanomtts.data.db.KanomDatabase
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// compileSdk is 36 but Robolectric's android-all jars only go up to SDK 35 (as of 4.16.1).
// @Config(sdk=[35]) pins Robolectric to SDK 35 to avoid "No such manifest" / unknown SDK errors.
@Config(sdk = [35])
@RunWith(RobolectricTestRunner::class)
abstract class RoomTestBase {
    protected lateinit var db: KanomDatabase

    @Before fun createDb() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, KanomDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After fun closeDb() { db.close() }
}
