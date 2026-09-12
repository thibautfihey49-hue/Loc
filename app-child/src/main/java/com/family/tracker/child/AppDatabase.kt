package com.family.tracker.child; import androidx.room.Database; import androidx.room.RoomDatabase
@Database(entities = [PendingPhoto::class], version = 1) abstract class AppDatabase:RoomDatabase(){abstract fun photoDao():PhotoDao}
