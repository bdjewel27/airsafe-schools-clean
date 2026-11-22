package com.airsafe.schools.di

import com.airsafe.schools.data.local.AirSafeLocalDataSource
import com.airsafe.schools.data.local.AirSafeLocalDataSourceImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    @Singleton
    abstract fun bindLocalDataSource(impl: AirSafeLocalDataSourceImpl): AirSafeLocalDataSource
}
