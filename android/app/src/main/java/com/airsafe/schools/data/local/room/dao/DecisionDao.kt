package com.airsafe.schools.data.local.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.airsafe.schools.data.local.room.entity.DecisionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DecisionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDecision(decision: DecisionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertDecisionBlocking(decision: DecisionEntity)

    @Query("SELECT * FROM cached_decisions WHERE school_id = :schoolId LIMIT 1")
    suspend fun getDecision(schoolId: String): DecisionEntity?

    @Query("SELECT * FROM cached_decisions WHERE school_id = :schoolId LIMIT 1")
    fun getDecisionBlocking(schoolId: String): DecisionEntity?

    @Query("SELECT * FROM cached_decisions WHERE school_id = :schoolId LIMIT 1")
    fun observeDecision(schoolId: String): Flow<DecisionEntity?>

    @Query("DELETE FROM cached_decisions")
    suspend fun clearDecisions()

    @Query("DELETE FROM cached_decisions")
    fun clearDecisionsBlocking()
}
