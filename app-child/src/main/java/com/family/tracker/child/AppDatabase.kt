package com.family.tracker.child

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [PendingPhoto::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun photoDao(): PhotoDao
}
