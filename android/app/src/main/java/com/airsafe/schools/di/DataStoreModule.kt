package com.airsafe.schools.di

import android.content.Context
import com.airsafe.schools.data.local.datastore.AirSafeDataStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataStoreModule {
    @Provides
    @Singleton
    fun provideAirSafeDataStore(
        @ApplicationContext context: Context,
    ): AirSafeDataStore {
        return AirSafeDataStore(context)
    }
}
