package com.airsafe.schools.data.local.room.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cached_decisions")
data class DecisionEntity(
    @PrimaryKey
    @ColumnInfo(name = "school_id")
    val schoolId: String,
    @ColumnInfo(name = "json_data")
    val jsonData: String,
    @ColumnInfo(name = "status")
    val status: String?,
    @ColumnInfo(name = "decision")
    val decision: String?,
    @ColumnInfo(name = "data_age_minutes")
    val dataAgeMinutes: Int?,
    @ColumnInfo(name = "is_stale", defaultValue = "0")
    val isStale: Int = 0,
    @ColumnInfo(name = "updated_at_ms")
    val updatedAtMs: Long,
)
