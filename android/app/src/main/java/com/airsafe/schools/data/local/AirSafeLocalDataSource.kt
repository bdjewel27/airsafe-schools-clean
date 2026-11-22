package com.airsafe.schools.data.local

import com.airsafe.schools.data.local.datastore.AirSafeDataStore
import com.airsafe.schools.data.local.room.dao.ActivityNoteDao
import com.airsafe.schools.data.local.room.dao.DecisionDao
import com.airsafe.schools.data.local.room.dao.SensorSiteDao
import com.airsafe.schools.data.local.room.dao.UserSessionDao
import com.airsafe.schools.data.local.room.entity.ActivityNoteEntity
import com.airsafe.schools.data.local.room.entity.DecisionEntity
import com.airsafe.schools.data.local.room.entity.SensorSiteEntity
import com.airsafe.schools.data.local.room.entity.UserSessionEntity
import com.airsafe.schools.data.model.DecisionResponse
import com.airsafe.schools.data.model.NoteItemDto
import com.airsafe.schools.data.model.SensorSite
import com.airsafe.schools.data.model.UserSession
import com.airsafe.schools.util.AppLog
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import javax.inject.Inject
import javax.inject.Singleton

interface AirSafeLocalDataSource {
    suspend fun saveDecision(decision: DecisionResponse)

    suspend fun getDecision(schoolId: String): DecisionResponse?

    suspend fun saveUserSession(session: UserSession)

    suspend fun getUserSession(email: String): UserSession?

    suspend fun saveSensorSites(
        schoolId: String,
        sites: List<SensorSite>,
    )

    suspend fun getSensorSites(schoolId: String): List<SensorSite>?

    suspend fun saveNotes(
        schoolId: String,
        notes: List<NoteItemDto>,
    )

    suspend fun getNotes(schoolId: String): List<NoteItemDto>?

    suspend fun clearUserSessions()

    suspend fun clearAll()
}

@Singleton
class AirSafeLocalDataSourceImpl
    @Inject
    constructor(
        private val decisionDao: DecisionDao,
        private val userSessionDao: UserSessionDao,
        private val sensorSiteDao: SensorSiteDao,
        private val activityNoteDao: ActivityNoteDao,
        private val dataStore: AirSafeDataStore,
    ) : AirSafeLocalDataSource {
        companion object {
            private const val TAG = "AirSafeLocalDS"
        }

        private val gson = Gson()

        override suspend fun saveDecision(decision: DecisionResponse) {
            val sId = decision.schoolId ?: return
            try {
                val json = gson.toJson(decision)
                val entity =
                    DecisionEntity(
                        schoolId = sId,
                        jsonData = json,
                        status = decision.status,
                        decision = decision.decision,
                        dataAgeMinutes = decision.dataAgeMinutes ?: 0,
                        isStale = if (decision.isStale == true) 1 else 0,
                        updatedAtMs = System.currentTimeMillis(),
                    )
                decisionDao.insertDecision(entity)
                dataStore.saveStringPreference("cached_decision_$sId", json)
                AppLog.d(TAG, "Persisted decision for $sId to Room & DataStore.")
            } catch (e: Exception) {
                AppLog.e(TAG, "Failed to persist decision to Room/DataStore: ${e.message}", e)
            }
        }

        override suspend fun getDecision(schoolId: String): DecisionResponse? {
            return try {
                val entity = decisionDao.getDecision(schoolId)
                if (entity != null) {
                    gson.fromJson(entity.jsonData, DecisionResponse::class.java)
                } else {
                    val json = dataStore.getStringPreference("cached_decision_$schoolId")
                    if (!json.isNullOrBlank()) {
                        gson.fromJson(json, DecisionResponse::class.java)
                    } else {
                        null
                    }
                }
            } catch (e: Exception) {
                AppLog.e(TAG, "Failed to retrieve decision from Room/DataStore: ${e.message}", e)
                null
            }
        }

        override suspend fun saveUserSession(session: UserSession) {
            try {
                val json = gson.toJson(session)
                val entity =
                    UserSessionEntity(
                        email = session.email.lowercase(),
                        jsonData = json,
                        updatedAtMs = System.currentTimeMillis(),
                    )
                userSessionDao.insertSession(entity)
                dataStore.saveStringPreference("cached_user_session_${session.email.lowercase()}", json)
                AppLog.d(TAG, "Persisted session for ${session.email} to Room & DataStore.")
            } catch (e: Exception) {
                AppLog.e(TAG, "Failed to persist session to Room/DataStore: ${e.message}", e)
            }
        }

        override suspend fun getUserSession(email: String): UserSession? {
            return try {
                val entity = userSessionDao.getSession(email.lowercase())
                if (entity != null) {
                    gson.fromJson(entity.jsonData, UserSession::class.java)
                } else {
                    val json = dataStore.getStringPreference("cached_user_session_${email.lowercase()}")
                    if (!json.isNullOrBlank()) {
                        gson.fromJson(json, UserSession::class.java)
                    } else {
                        null
                    }
                }
            } catch (e: Exception) {
                AppLog.e(TAG, "Failed to retrieve session from Room/DataStore: ${e.message}", e)
                null
            }
        }

        override suspend fun saveSensorSites(
            schoolId: String,
            sites: List<SensorSite>,
        ) {
            try {
                val json = gson.toJson(sites)
                val entity =
                    SensorSiteEntity(
                        schoolId = schoolId,
                        jsonData = json,
                        updatedAtMs = System.currentTimeMillis(),
                    )
                sensorSiteDao.insertSites(entity)
                dataStore.saveStringPreference("cached_sensor_sites_$schoolId", json)
                AppLog.d(TAG, "Persisted ${sites.size} sensor sites for $schoolId to Room & DataStore.")
            } catch (e: Exception) {
                AppLog.e(TAG, "Failed to persist sensor sites to Room/DataStore: ${e.message}", e)
            }
        }

        override suspend fun getSensorSites(schoolId: String): List<SensorSite>? {
            return try {
                val entity = sensorSiteDao.getSites(schoolId)
                if (entity != null) {
                    val type = object : TypeToken<List<SensorSite>>() {}.type
                    gson.fromJson<List<SensorSite>>(entity.jsonData, type)
                } else {
                    val json = dataStore.getStringPreference("cached_sensor_sites_$schoolId")
                    if (!json.isNullOrBlank()) {
                        val type = object : TypeToken<List<SensorSite>>() {}.type
                        gson.fromJson<List<SensorSite>>(json, type)
                    } else {
                        null
                    }
                }
            } catch (e: Exception) {
                AppLog.e(TAG, "Failed to retrieve sensor sites from Room/DataStore: ${e.message}", e)
                null
            }
        }

        override suspend fun saveNotes(
            schoolId: String,
            notes: List<NoteItemDto>,
        ) {
            try {
                val json = gson.toJson(notes)
                val entity =
                    ActivityNoteEntity(
                        schoolId = schoolId,
                        jsonData = json,
                        updatedAtMs = System.currentTimeMillis(),
                    )
                activityNoteDao.insertNotes(entity)
                dataStore.saveStringPreference("cached_notes_$schoolId", json)
                AppLog.d(TAG, "Persisted ${notes.size} notes for $schoolId to Room & DataStore.")
            } catch (e: Exception) {
                AppLog.e(TAG, "Failed to persist notes to Room/DataStore: ${e.message}", e)
            }
        }

        override suspend fun getNotes(schoolId: String): List<NoteItemDto>? {
            return try {
                val entity = activityNoteDao.getNotes(schoolId)
                if (entity != null) {
                    val type = object : TypeToken<List<NoteItemDto>>() {}.type
                    gson.fromJson<List<NoteItemDto>>(entity.jsonData, type)
                } else {
                    val json = dataStore.getStringPreference("cached_notes_$schoolId")
                    if (!json.isNullOrBlank()) {
                        val type = object : TypeToken<List<NoteItemDto>>() {}.type
                        gson.fromJson<List<NoteItemDto>>(json, type)
                    } else {
                        null
                    }
                }
            } catch (e: Exception) {
                AppLog.e(TAG, "Failed to retrieve notes from Room/DataStore: ${e.message}", e)
                null
            }
        }

        override suspend fun clearUserSessions() {
            try {
                userSessionDao.clearSessions()
                AppLog.d(TAG, "Cleared user sessions from Room.")
            } catch (e: Exception) {
                AppLog.e(TAG, "Failed to clear user sessions from Room: ${e.message}", e)
            }
        }

        override suspend fun clearAll() {
            try {
                decisionDao.clearDecisions()
                userSessionDao.clearSessions()
                sensorSiteDao.clearSites()
                activityNoteDao.clearNotes()
                dataStore.clearAll()
                AppLog.d(TAG, "Cleared all Room and DataStore cache tables.")
            } catch (e: Exception) {
                AppLog.e(TAG, "Failed to clear Room/DataStore: ${e.message}", e)
            }
        }
    }
