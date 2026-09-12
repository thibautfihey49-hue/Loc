package com.family.tracker.child; import androidx.room.Dao; import androidx.room.Insert; import androidx.room.Query; import androidx.room.Update
@Dao interface PhotoDao{@Insert suspend fun addPhoto(photo:PendingPhoto);@Query("SELECT * FROM pending_photos WHERE sent=0 ORDER BY timestamp ASC") suspend fun getUnsent():List<PendingPhoto>;@Update suspend fun markAsSent(photo:PendingPhoto)}
