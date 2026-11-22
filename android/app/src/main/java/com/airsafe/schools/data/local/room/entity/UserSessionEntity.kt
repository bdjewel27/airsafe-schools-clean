package com.airsafe.schools.data.local.room.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cached_user_sessions")
data class UserSessionEntity(
    @PrimaryKey
    @ColumnInfo(name = "email")
    val email: String,
    @ColumnInfo(name = "json_data")
    val jsonData: String,
    @ColumnInfo(name = "updated_at_ms")
    val updatedAtMs: Long,
)
