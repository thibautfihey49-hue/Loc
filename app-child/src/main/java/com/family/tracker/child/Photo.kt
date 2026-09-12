package com.family.tracker.child; import androidx.room.Entity; import androidx.room.PrimaryKey
@Entity(tableName = "pending_photos") data class PendingPhoto(@PrimaryKey(autoGenerate = true) val id:Long=0,val photoPath:String,val timestamp:Long,val sent:Boolean=false)
