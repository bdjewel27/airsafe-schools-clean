package com.airsafe.schools.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.airsafe.schools.R
import com.airsafe.schools.data.api.AirSafeApiClient
import com.airsafe.schools.data.model.OverrideAuditItemDto
import com.airsafe.schools.data.model.OverrideRequest
import com.airsafe.schools.data.model.UserRole
import com.airsafe.schools.util.AppLog
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Modular Feature ViewModel for Coordinator Overrides and Immutable Audit Trail.
 */
@HiltViewModel
class OverrideViewModel
    @Inject
    constructor(
        application: Application,
    ) : AndroidViewModel(application) {
        companion object {
            private const val TAG = "OverrideVM"
        }

        private val _activeOverride = MutableStateFlow<String?>(null)
        val activeOverride: StateFlow<String?> = _activeOverride.asStateFlow()

        private val _overrideAuditLogs = MutableStateFlow<List<OverrideAuditItemDto>>(emptyList())
        val overrideAuditLogs: StateFlow<List<OverrideAuditItemDto>> = _overrideAuditLogs.asStateFlow()

        fun resetActiveOverride() {
            _activeOverride.value = null
        }

        fun loadOverrideAuditLogs(schoolId: String) {
            viewModelScope.launch {
                try {
                    val resp = AirSafeApiClient.service.getOverrideAuditLogs(schoolId)
                    if (resp.auditLogs.isNotEmpty()) {
                        _overrideAuditLogs.value = resp.auditLogs
                    }
                } catch (e: Exception) {
                    AppLog.d(TAG, "loadOverrideAuditLogs skipped or failed: ${e.message}")
                }
            }
        }

        fun applyCoordinatorOverride(
            decision: String,
            reason: String,
            userRole: UserRole?,
            schoolId: String,
            isBengali: Boolean = true,
            onLocalOverrideApplied: ((overrideFormatted: String) -> Unit)? = null,
            onSyncCompleted: (() -> Unit)? = null,
            onActionError: ((String) -> Unit)? = null,
        ) {
            if (userRole != UserRole.COORDINATOR && userRole != UserRole.PLATFORM_ADMIN) {
                val err = getApplication<Application>().getString(R.string.err_coordinator_auth_required)
                onActionError?.invoke(err)
                return
            }
            val cleanReason = reason.trim()
            if (cleanReason.length < 5) {
                val err = getApplication<Application>().getString(R.string.err_override_reason_min_length)
                onActionError?.invoke(err)
                return
            }
            AppLog.d(TAG, "applyCoordinatorOverride called: decision=$decision, reason=$cleanReason")

            val formattedOverride = "$decision: $cleanReason"
            _activeOverride.value = formattedOverride

            onLocalOverrideApplied?.invoke(formattedOverride)

            viewModelScope.launch {
                try {
                    AppLog.d(TAG, "Sending submitOverride to schools/$schoolId/overrides")
                    val resp =
                        AirSafeApiClient.service.submitOverride(
                            schoolId,
                            OverrideRequest(decision = decision, reason = cleanReason),
                        )
                    AppLog.d(TAG, "submitOverride API Success: $resp")
                    loadOverrideAuditLogs(schoolId)
                    onSyncCompleted?.invoke()
                } catch (e: Exception) {
                    AppLog.e(TAG, "Error in submitOverride API call: ${e.message}", e)
                    val errNotice =
                        getApplication<Application>().getString(
                            R.string.err_override_sync_warning,
                            e.javaClass.simpleName,
                            e.message ?: "",
                        )
                    onActionError?.invoke(errNotice)
                }
            }
        }
    }
