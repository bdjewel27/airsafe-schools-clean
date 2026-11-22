package com.airsafe.schools.di

import android.content.Context
import com.airsafe.schools.data.local.room.AirSafeRoomDatabase
import com.airsafe.schools.data.local.room.dao.ActivityNoteDao
import com.airsafe.schools.data.local.room.dao.DecisionDao
import com.airsafe.schools.data.local.room.dao.SensorSiteDao
import com.airsafe.schools.data.local.room.dao.UserSessionDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideRoomDatabase(
        @ApplicationContext context: Context,
    ): AirSafeRoomDatabase {
        return AirSafeRoomDatabase.getInstance(context)
    }

    @Provides
    fun provideDecisionDao(database: AirSafeRoomDatabase): DecisionDao {
        return database.decisionDao()
    }

    @Provides
    fun provideUserSessionDao(database: AirSafeRoomDatabase): UserSessionDao {
        return database.userSessionDao()
    }

    @Provides
    fun provideSensorSiteDao(database: AirSafeRoomDatabase): SensorSiteDao {
        return database.sensorSiteDao()
    }

    @Provides
    fun provideActivityNoteDao(database: AirSafeRoomDatabase): ActivityNoteDao {
        return database.activityNoteDao()
    }
}
