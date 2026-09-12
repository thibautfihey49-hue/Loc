package com.family.tracker.child; import android.content.Context; import androidx.room.Room
object DbProvider{@Volatile private var db:AppDatabase?=null;fun get(context:Context):AppDatabase{return db?:synchronized(this){val ctx=context.applicationContext;val i=Room.databaseBuilder(ctx,AppDatabase::class.java,"tracker_db").fallbackToDestructiveMigration().build();db=i;i}}}
