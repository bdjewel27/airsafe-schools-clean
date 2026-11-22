package com.airsafe.schools.data.local.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.airsafe.schools.data.local.room.entity.UserSessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UserSessionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: UserSessionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertSessionBlocking(session: UserSessionEntity)

    @Query("SELECT * FROM cached_user_sessions WHERE email = :email LIMIT 1")
    suspend fun getSession(email: String): UserSessionEntity?

    @Query("SELECT * FROM cached_user_sessions WHERE email = :email LIMIT 1")
    fun getSessionBlocking(email: String): UserSessionEntity?

    @Query("SELECT * FROM cached_user_sessions WHERE email = :email LIMIT 1")
    fun observeSession(email: String): Flow<UserSessionEntity?>

    @Query("DELETE FROM cached_user_sessions")
    suspend fun clearSessions()

    @Query("DELETE FROM cached_user_sessions")
    fun clearSessionsBlocking()
}
