package com.airsafe.schools.data.local.room

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.airsafe.schools.data.local.room.dao.ActivityNoteDao
import com.airsafe.schools.data.local.room.dao.DecisionDao
import com.airsafe.schools.data.local.room.dao.SensorSiteDao
import com.airsafe.schools.data.local.room.dao.UserSessionDao
import com.airsafe.schools.data.local.room.entity.ActivityNoteEntity
import com.airsafe.schools.data.local.room.entity.DecisionEntity
import com.airsafe.schools.data.local.room.entity.SensorSiteEntity
import com.airsafe.schools.data.local.room.entity.UserSessionEntity

@Database(
    entities = [
        DecisionEntity::class,
        UserSessionEntity::class,
        SensorSiteEntity::class,
        ActivityNoteEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class AirSafeRoomDatabase : RoomDatabase() {
    abstract fun decisionDao(): DecisionDao

    abstract fun userSessionDao(): UserSessionDao

    abstract fun sensorSiteDao(): SensorSiteDao

    abstract fun activityNoteDao(): ActivityNoteDao

    companion object {
        const val DATABASE_NAME = "airsafe_offline_cache.db"

        @Volatile
        private var instance: AirSafeRoomDatabase? = null

        fun getInstance(context: Context): AirSafeRoomDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AirSafeRoomDatabase::class.java,
                    DATABASE_NAME,
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { instance = it }
            }
        }
    }
}
