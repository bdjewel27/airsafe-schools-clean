package com.airsafe.schools.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.airsafe.schools.R
import com.airsafe.schools.data.api.AirSafeApiClient
import com.airsafe.schools.data.local.AirSafeLocalDataSource
import com.airsafe.schools.data.model.NoteRequest
import com.airsafe.schools.data.model.RoutineUpdateRequest
import com.airsafe.schools.data.model.StaffInviteRequest
import com.airsafe.schools.data.model.UserRole
import com.airsafe.schools.util.AppLog
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Modular Feature ViewModel for Routine Schedule, Activity Notes, and Staff Management.
 */
@HiltViewModel
class ScheduleViewModel
    @Inject
    constructor(
        application: Application,
        private val localDataSource: AirSafeLocalDataSource,
    ) : AndroidViewModel(application) {
        companion object {
            private const val TAG = "ScheduleVM"
        }

        private val _activityNotes = MutableStateFlow<List<String>>(emptyList())
        val activityNotes: StateFlow<List<String>> = _activityNotes.asStateFlow()

        private var _hasRespiratoryNote: Boolean = false
        val hasRespiratoryNote: Boolean get() = _hasRespiratoryNote

        private var _currentRoutineIntensity: String = "moderate"
        val currentRoutineIntensity: String get() = _currentRoutineIntensity

        private val _invitedStaff = MutableStateFlow<List<StaffItem>>(emptyList())
        val invitedStaff: StateFlow<List<StaffItem>> = _invitedStaff.asStateFlow()

        private val _staffError = MutableStateFlow<String?>(null)
        val staffError: StateFlow<String?> = _staffError.asStateFlow()

        fun clearStaffError() {
            _staffError.value = null
        }

        fun loadNotes(
            schoolId: String,
            isBengali: Boolean = true,
        ) {
            viewModelScope.launch {
                try {
                    val resp = AirSafeApiClient.service.getNotes(schoolId)
                    if (resp.notes.isNotEmpty()) {
                        localDataSource.saveNotes(schoolId, resp.notes)
                        _activityNotes.value =
                            resp.notes.map { n ->
                                val safeNote =
                                    if (n.note.contains("???")) {
                                        getApplication<Application>().getString(R.string.note_respiratory_caution)
                                    } else {
                                        n.note
                                    }
                                val actLabel =
                                    if (!n.activityName.isNullOrBlank()) {
                                        " [" + n.activityName + "]"
                                    } else if (!n.activityId.isNullOrBlank() && n.activityId != "current") {
                                        " [" + n.activityId + "]"
                                    } else {
                                        ""
                                    }
                                safeNote + actLabel
                            }
                        if (resp.notes.any { it.hasRespiratory == true }) {
                            _hasRespiratoryNote = true
                        }
                    }
                } catch (e: Exception) {
                    AppLog.d(TAG, "loadNotes skipped or failed: ${e.message}")
                    try {
                        val cachedNotes = localDataSource.getNotes(schoolId)
                        if (!cachedNotes.isNullOrEmpty()) {
                            _activityNotes.value =
                                cachedNotes.map { n ->
                                    val safeNote =
                                        if (n.note.contains("???")) {
                                            getApplication<Application>().getString(R.string.note_respiratory_caution)
                                        } else {
                                            n.note
                                        }
                                    val actLabel =
                                        if (!n.activityName.isNullOrBlank()) {
                                            " [" + n.activityName + "]"
                                        } else if (!n.activityId.isNullOrBlank() && n.activityId != "current") {
                                            " [" + n.activityId + "]"
                                        } else {
                                            ""
                                        }
                                    safeNote + actLabel
                                }
                            if (cachedNotes.any { it.hasRespiratory == true }) {
                                _hasRespiratoryNote = true
                            }
                        }
                    } catch (_: Exception) {
                    }
                }
            }
        }

        fun addActivityNote(
            note: String,
            activityId: String? = null,
            activityName: String? = null,
            schoolId: String,
            isBengali: Boolean = true,
            onLocalNoteAdded: ((hasRespiratory: Boolean) -> Unit)? = null,
            onSyncCompleted: (() -> Unit)? = null,
            onActionError: ((String) -> Unit)? = null,
        ) {
            val cleanNote = note.trim()
            if (cleanNote.isBlank()) return
            AppLog.d(TAG, "addActivityNote called: $cleanNote (activity: $activityId)")

            val triggersRespiratoryStepUp =
                Regex(
                    "(asthma|respiratory|breathing|cough|wheez|আজমা|অ্যাজমা|হাঁপানি|শ্বাসকষ্ট|কাশি|সংবেদনশীল|sensitive|ajma|ajmao)",
                    RegexOption.IGNORE_CASE,
                ).containsMatchIn(cleanNote)

            val displayLabel =
                if (!activityName.isNullOrBlank()) {
                    " [" + activityName + "]"
                } else if (!activityId.isNullOrBlank() && activityId != "current") {
                    " [" + activityId + "]"
                } else {
                    ""
                }
            _activityNotes.value = _activityNotes.value + (cleanNote + displayLabel)
            if (triggersRespiratoryStepUp) {
                _hasRespiratoryNote = true
            }

            onLocalNoteAdded?.invoke(triggersRespiratoryStepUp)

            viewModelScope.launch {
                try {
                    AppLog.d(TAG, "Sending submitNote to schools/$schoolId/notes with activityId=$activityId")
                    val resp =
                        AirSafeApiClient.service.submitNote(
                            schoolId,
                            NoteRequest(
                                note = cleanNote,
                                hasRespiratoryCondition = triggersRespiratoryStepUp,
                                activityId = activityId ?: "current",
                                activityName = activityName,
                            ),
                        )
                    AppLog.d(TAG, "submitNote API Success: $resp")
                    loadNotes(schoolId, isBengali)
                    onSyncCompleted?.invoke()
                } catch (e: Exception) {
                    AppLog.e(TAG, "Error in submitNote API call: ${e.message}", e)
                    val errNotice =
                        getApplication<Application>().getString(
                            R.string.err_note_sync_warning,
                            e.javaClass.simpleName,
                            e.message ?: "",
                        )
                    onActionError?.invoke(errNotice)
                }
            }
        }

        fun updateRoutineActivity(
            activityNameBn: String,
            activityNameEn: String,
            intensity: String = "MODERATE",
            durationMinutes: Int = 45,
            targetClass: String? = null,
            nextActivityNameBn: String? = null,
            nextActivityNameEn: String? = null,
            timeSlot: String? = "০৮:৪৫ – ০৯:৩০",
            justification: String? = null,
            schoolId: String,
            isBengali: Boolean = true,
            onLocalUpdated: ((normalizedIntensity: String) -> Unit)? = null,
            onSyncCompleted: (() -> Unit)? = null,
            onActionError: ((String) -> Unit)? = null,
        ) {
            val cleanIntensity = intensity.trim().uppercase()
            val normalizedIntensity =
                when (cleanIntensity) {
                    "LIGHT" -> "LIGHT"
                    "VIGOROUS" -> "VIGOROUS"
                    else -> "MODERATE"
                }
            _currentRoutineIntensity = normalizedIntensity.lowercase()
            AppLog.d(TAG, "updateRoutineActivity: $activityNameBn, intensity=$normalizedIntensity, duration=$durationMinutes")

            onLocalUpdated?.invoke(normalizedIntensity)

            viewModelScope.launch {
                try {
                    AppLog.d(TAG, "Sending updateRoutine to schools/$schoolId/routines")
                    val resp =
                        AirSafeApiClient.service.updateRoutine(
                            schoolId,
                            RoutineUpdateRequest(
                                activityNameBn = activityNameBn,
                                activityNameEn = activityNameEn,
                                nextActivityBn = nextActivityNameBn,
                                nextActivityEn = nextActivityNameEn,
                                intensity = normalizedIntensity,
                                durationMinutes = durationMinutes,
                                targetClass = targetClass,
                                justification = justification,
                            ),
                        )
                    AppLog.d(TAG, "updateRoutine API Success: $resp")
                    onSyncCompleted?.invoke()
                } catch (e: Exception) {
                    AppLog.e(TAG, "Error in updateRoutine API call: ${e.message}", e)
                    val errNotice =
                        getApplication<Application>().getString(
                            R.string.err_routine_sync_warning,
                            e.javaClass.simpleName,
                            e.message ?: "",
                        )
                    onActionError?.invoke(errNotice)
                }
            }
        }

        fun loadStaff(schoolId: String) {
            viewModelScope.launch {
                try {
                    AppLog.d(TAG, "Loading staff from schools/$schoolId/staff")
                    val resp = AirSafeApiClient.service.getStaff(schoolId)
                    if (resp.staff.isNotEmpty()) {
                        _invitedStaff.value =
                            resp.staff.map {
                                StaffItem(
                                    email = it.email,
                                    roleBn = it.roleBn ?: "শিক্ষক",
                                    roleEn = it.roleEn ?: "Teacher",
                                )
                            }
                        AppLog.d(TAG, "Staff loaded: ${_invitedStaff.value.size} members")
                    } else {
                        _invitedStaff.value = emptyList()
                    }
                } catch (e: Exception) {
                    AppLog.e(TAG, "Error in loadStaff: ${e.message}", e)
                    if (_invitedStaff.value.isEmpty()) {
                        _invitedStaff.value = emptyList()
                    }
                }
            }
        }

        fun inviteTeacher(
            email: String,
            schoolId: String,
            userRole: UserRole?,
            isBengali: Boolean = true,
            onActionError: ((String) -> Unit)? = null,
        ) {
            if (userRole != UserRole.COORDINATOR && userRole != UserRole.PLATFORM_ADMIN) {
                val err = getApplication<Application>().getString(R.string.err_invite_coordinator_only)
                _staffError.value = err
                onActionError?.invoke(err)
                return
            }
            val cleanEmail = email.trim().lowercase()
            if (cleanEmail.isBlank() || !cleanEmail.contains("@")) {
                val err = getApplication<Application>().getString(R.string.err_invite_valid_email)
                _staffError.value = err
                onActionError?.invoke(err)
                return
            }

            viewModelScope.launch {
                try {
                    AppLog.d(TAG, "Inviting teacher via schools/$schoolId/staff/invite")
                    AirSafeApiClient.service.inviteStaff(
                        schoolId,
                        StaffInviteRequest(email = cleanEmail, role = "TEACHER"),
                    )
                    _invitedStaff.value = _invitedStaff.value + StaffItem(cleanEmail, "শিক্ষক", "Teacher")
                    AppLog.d(TAG, "Staff invite API Success")
                } catch (e: Exception) {
                    AppLog.e(TAG, "Error in inviteStaff API: ${e.message}", e)
                    val app = getApplication<Application>()
                    val err =
                        app.getString(
                            R.string.err_invite_send_failed,
                            e.javaClass.simpleName,
                            e.message ?: app.getString(R.string.err_server_generic),
                        )
                    _staffError.value = err
                    onActionError?.invoke(err)
                }
            }
        }
    }

data class StaffItem(
    val email: String,
    val roleBn: String,
    val roleEn: String,
)
