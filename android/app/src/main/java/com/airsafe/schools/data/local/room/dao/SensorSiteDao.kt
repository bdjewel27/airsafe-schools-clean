package com.airsafe.schools.data.local.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.airsafe.schools.data.local.room.entity.SensorSiteEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SensorSiteDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSites(site: SensorSiteEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertSitesBlocking(site: SensorSiteEntity)

    @Query("SELECT * FROM cached_sensor_sites WHERE school_id = :schoolId LIMIT 1")
    suspend fun getSites(schoolId: String): SensorSiteEntity?

    @Query("SELECT * FROM cached_sensor_sites WHERE school_id = :schoolId LIMIT 1")
    fun getSitesBlocking(schoolId: String): SensorSiteEntity?

    @Query("SELECT * FROM cached_sensor_sites WHERE school_id = :schoolId LIMIT 1")
    fun observeSites(schoolId: String): Flow<SensorSiteEntity?>

    @Query("DELETE FROM cached_sensor_sites")
    suspend fun clearSites()

    @Query("DELETE FROM cached_sensor_sites")
    fun clearSitesBlocking()
}
