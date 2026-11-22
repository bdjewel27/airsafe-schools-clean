package com.airsafe.schools.data.local.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.airsafe.schools.data.local.room.entity.ActivityNoteEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ActivityNoteDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotes(notes: ActivityNoteEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertNotesBlocking(notes: ActivityNoteEntity)

    @Query("SELECT * FROM cached_activity_notes WHERE school_id = :schoolId LIMIT 1")
    suspend fun getNotes(schoolId: String): ActivityNoteEntity?

    @Query("SELECT * FROM cached_activity_notes WHERE school_id = :schoolId LIMIT 1")
    fun getNotesBlocking(schoolId: String): ActivityNoteEntity?

    @Query("SELECT * FROM cached_activity_notes WHERE school_id = :schoolId LIMIT 1")
    fun observeNotes(schoolId: String): Flow<ActivityNoteEntity?>

    @Query("DELETE FROM cached_activity_notes")
    suspend fun clearNotes()

    @Query("DELETE FROM cached_activity_notes")
    fun clearNotesBlocking()
}
