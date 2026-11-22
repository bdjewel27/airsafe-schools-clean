package com.airsafe.schools.ui.viewmodel

import android.app.Application
import android.content.res.Configuration
import androidx.lifecycle.AndroidViewModel
import com.airsafe.schools.R
import com.airsafe.schools.data.local.AirSafeLocalDataSource
import com.airsafe.schools.data.model.DecisionResponse
import com.airsafe.schools.data.model.DecisionUiState
import com.airsafe.schools.data.model.OverrideAuditItemDto
import com.airsafe.schools.data.model.SchoolAnalyticsSummary
import com.airsafe.schools.data.model.SensorSite
import com.airsafe.schools.data.model.TrendPoint
import com.airsafe.schools.data.model.UserSession
import com.airsafe.schools.data.model.formatLocalizedNumber
import com.airsafe.schools.data.notification.AirSafeNotificationHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale
import javax.inject.Inject

enum class AppTab {
    HOME,
    TRENDS,
    ASSISTANT,
    STAFF_MANAGEMENT, // Coordinator only (F-12)
    ANALYTICS, // Coordinator only (F-13, F-14)
}

/**
 * Unified Coordinator ViewModel for AirSafe Schools.
 * Refactored into modular, feature-specific ViewModels with Hilt dependency injection:
 * 1. [DashboardViewModel] - Campus Telemetry, Decisions, Sensor Sites, Offline Mode & AppSync.
 * 2. [ScheduleViewModel] - Activity Notes, Routine Schedules, and Staff Management.
 * 3. [AnalyticsViewModel] - Metric Summaries, Analytics Charts, and Historical Trends.
 * 4. [OverrideViewModel] - Coordinator Overrides & Immutable Audit Trail.
 */
@HiltViewModel
class DecisionViewModel
    @Inject
    constructor(
        application: Application,
        localDataSource: AirSafeLocalDataSource,
    ) : AndroidViewModel(application) {
        val dashboardViewModel: DashboardViewModel = DashboardViewModel(application, localDataSource)
        val scheduleViewModel: ScheduleViewModel = ScheduleViewModel(application, localDataSource)
        val analyticsViewModel: AnalyticsViewModel = AnalyticsViewModel(application)
        val overrideViewModel: OverrideViewModel = OverrideViewModel(application)

        companion object {
            private const val TAG = "DecisionVM"
        }

        // --- StateFlow Delegation to Feature ViewModels ---
        val uiState: StateFlow<DecisionUiState> get() = dashboardViewModel.uiState
        val currentTab: StateFlow<AppTab> get() = dashboardViewModel.currentTab
        val isBengali: StateFlow<Boolean> get() = dashboardViewModel.isBengali
        val userSession: StateFlow<UserSession?> get() = dashboardViewModel.userSession
        val loginError: StateFlow<String?> get() = dashboardViewModel.loginError
        val actionError: StateFlow<String?> get() = dashboardViewModel.actionError
        val staffError: StateFlow<String?> get() = scheduleViewModel.staffError
        val showExplanationDialog: StateFlow<Boolean> get() = dashboardViewModel.showExplanationDialog
        val sensorSites: StateFlow<List<SensorSite>> get() = dashboardViewModel.sensorSites
        val isOffline: StateFlow<Boolean> get() = dashboardViewModel.isOffline
        val selectedSchoolId: StateFlow<String> get() = dashboardViewModel.selectedSchoolId

        val activityNotes: StateFlow<List<String>> get() = scheduleViewModel.activityNotes
        val invitedStaff: StateFlow<List<StaffItem>> get() = scheduleViewModel.invitedStaff

        val trendData: StateFlow<List<TrendPoint>> get() = analyticsViewModel.trendData
        val isTrendsLoading: StateFlow<Boolean> get() = analyticsViewModel.isTrendsLoading
        val trendsError: StateFlow<String?> get() = analyticsViewModel.trendsError
        val selectedTrendRange: StateFlow<String> get() = analyticsViewModel.selectedTrendRange
        val compareSiteId: StateFlow<String?> get() = analyticsViewModel.compareSiteId
        val trendPeriodLabel: StateFlow<String> get() = analyticsViewModel.trendPeriodLabel
        val analyticsData: StateFlow<SchoolAnalyticsSummary> get() = analyticsViewModel.analyticsData

        val activeOverride: StateFlow<String?> get() = overrideViewModel.activeOverride
        val overrideAuditLogs: StateFlow<List<OverrideAuditItemDto>> get() = overrideViewModel.overrideAuditLogs

        // --- UI Controls & Navigation ---
        fun clearStaffError() = scheduleViewModel.clearStaffError()

        fun clearActionError() = dashboardViewModel.clearActionError()

        fun triggerExplanationFromNotification() = dashboardViewModel.triggerExplanationFromNotification()

        fun dismissExplanationDialog() = dashboardViewModel.dismissExplanationDialog()

        fun setTab(tab: AppTab) = dashboardViewModel.setTab(tab)

        fun toggleLanguage() = dashboardViewModel.toggleLanguage()

        fun getCustomBaseUrl(): String = dashboardViewModel.getCustomBaseUrl()

        fun setCustomBaseUrl(url: String) {
            dashboardViewModel.setCustomBaseUrl(url) { schoolId ->
                loadSchoolData(schoolId)
            }
        }

        // --- Authentication & Tenant Context ---
        fun authenticateStaff(
            emailInput: String,
            passwordInput: String,
        ) {
            overrideViewModel.resetActiveOverride()
            dashboardViewModel.authenticateStaff(emailInput, passwordInput) { schoolId ->
                loadSchoolData(schoolId)
            }
        }

        fun logout() {
            overrideViewModel.resetActiveOverride()
            dashboardViewModel.logout()
        }

        fun loadSchoolData(schoolId: String) {
            dashboardViewModel.startForegroundSync(schoolId)
            dashboardViewModel.loadCurrentDecision(schoolId)
            scheduleViewModel.loadNotes(schoolId, isBengali.value)
            dashboardViewModel.loadSensorSites(schoolId)
            analyticsViewModel.loadAnalytics(schoolId)
            scheduleViewModel.loadStaff(schoolId)
            analyticsViewModel.loadTrends(schoolId, isBengali = isBengali.value)
            overrideViewModel.loadOverrideAuditLogs(schoolId)
        }

        // --- Telemetry & Dashboard Actions ---
        fun handleRealtimeDecision(decision: DecisionResponse) = dashboardViewModel.handleRealtimeDecision(decision)

        fun startForegroundSync(schoolId: String? = null) = dashboardViewModel.startForegroundSync(schoolId)

        fun stopForegroundSync() = dashboardViewModel.stopForegroundSync()

        fun recalculateDecisionSilently(schoolId: String) = dashboardViewModel.recalculateDecisionSilently(schoolId)

        fun loadCurrentDecision(schoolId: String = selectedSchoolId.value) = dashboardViewModel.loadCurrentDecision(schoolId)

        fun loadSensorSites(schoolId: String) = dashboardViewModel.loadSensorSites(schoolId)

        fun toggleOfflineMode() = dashboardViewModel.toggleOfflineMode()

        fun setOfflineMode(offline: Boolean) = dashboardViewModel.setOfflineMode(offline)

        fun useOfflineFallback(schoolId: String? = null) = dashboardViewModel.useOfflineFallback(schoolId)

        fun triggerSimulationDeterioration() = dashboardViewModel.triggerSimulationDeterioration()

        // --- Activity Notes & Routine Schedule ---
        fun loadNotes(schoolId: String) = scheduleViewModel.loadNotes(schoolId, isBengali.value)

        fun addActivityNote(
            note: String,
            activityId: String? = null,
            activityName: String? = null,
        ) {
            val schoolId = userSession.value?.schoolId ?: selectedSchoolId.value
            scheduleViewModel.addActivityNote(
                note = note,
                activityId = activityId,
                activityName = activityName,
                schoolId = schoolId,
                isBengali = isBengali.value,
                onLocalNoteAdded = {
                    val updatedDecision = computeLocalAuthoritativeDecision(schoolId)
                    dashboardViewModel.updateSuccessDecision(updatedDecision)
                    dashboardViewModel.clearActionError()
                },
                onSyncCompleted = {
                    dashboardViewModel.loadCurrentDecision(schoolId)
                    scheduleViewModel.loadNotes(schoolId, isBengali.value)
                },
                onActionError = { errorMsg ->
                    dashboardViewModel.setActionError(errorMsg)
                },
            )
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
        ) {
            val schoolId = userSession.value?.schoolId ?: selectedSchoolId.value
            scheduleViewModel.updateRoutineActivity(
                activityNameBn = activityNameBn,
                activityNameEn = activityNameEn,
                intensity = intensity,
                durationMinutes = durationMinutes,
                targetClass = targetClass,
                nextActivityNameBn = nextActivityNameBn,
                nextActivityNameEn = nextActivityNameEn,
                timeSlot = timeSlot,
                justification = justification,
                schoolId = schoolId,
                isBengali = isBengali.value,
                onLocalUpdated = {
                    val updatedDecision = computeLocalAuthoritativeDecision(schoolId)
                    dashboardViewModel.updateSuccessDecision(updatedDecision)
                    dashboardViewModel.clearActionError()
                },
                onSyncCompleted = {
                    dashboardViewModel.loadCurrentDecision(schoolId)
                },
                onActionError = { errorMsg ->
                    dashboardViewModel.setActionError(errorMsg)
                },
            )
        }

        fun loadStaff(schoolId: String) = scheduleViewModel.loadStaff(schoolId)

        fun inviteTeacher(email: String) {
            val schoolId = userSession.value?.schoolId ?: return
            scheduleViewModel.inviteTeacher(
                email = email,
                schoolId = schoolId,
                userRole = userSession.value?.role,
                isBengali = isBengali.value,
                onActionError = { errorMsg ->
                    dashboardViewModel.setActionError(errorMsg)
                },
            )
        }

        // --- Analytics & Trends ---
        fun setTrendRange(range: String) = analyticsViewModel.setTrendRange(range, selectedSchoolId.value, isBengali.value)

        fun setComparisonSite(siteId: String?) = analyticsViewModel.setComparisonSite(siteId, selectedSchoolId.value, isBengali.value)

        fun loadTrends(
            schoolId: String = selectedSchoolId.value,
            range: String = selectedTrendRange.value,
            siteId: String? = null,
            compareSiteId: String? = this.compareSiteId.value,
        ) = analyticsViewModel.loadTrends(schoolId, range, siteId, compareSiteId, isBengali.value)

        fun loadAnalytics(schoolId: String) = analyticsViewModel.loadAnalytics(schoolId)

        fun getWeeklyAnalyticsCsv(): String = analyticsViewModel.getWeeklyAnalyticsCsv()

        // --- Coordinator Overrides ---
        fun loadOverrideAuditLogs(schoolId: String) = overrideViewModel.loadOverrideAuditLogs(schoolId)

        fun applyCoordinatorOverride(
            decision: String,
            reason: String,
        ) {
            val schoolId = userSession.value?.schoolId ?: selectedSchoolId.value
            overrideViewModel.applyCoordinatorOverride(
                decision = decision,
                reason = reason,
                userRole = userSession.value?.role,
                schoolId = schoolId,
                isBengali = isBengali.value,
                onLocalOverrideApplied = {
                    val updatedDecision = computeLocalAuthoritativeDecision(schoolId)
                    dashboardViewModel.updateSuccessDecision(updatedDecision)
                    dashboardViewModel.clearActionError()

                    if (updatedDecision.status == "RED") {
                        val title = getLocalizedString(R.string.notif_urgent_move_indoors_title)
                        AirSafeNotificationHelper.sendDangerAlert(
                            getApplication(),
                            title,
                            updatedDecision.primaryReason ?: "",
                        )
                    }
                },
                onSyncCompleted = {
                    dashboardViewModel.loadCurrentDecision(schoolId)
                    overrideViewModel.loadOverrideAuditLogs(schoolId)
                },
                onActionError = { errorMsg ->
                    dashboardViewModel.setActionError(errorMsg)
                },
            )
        }

        // --- Local Authoritative Decision Calculation ---
        private fun getLocalizedString(
            resId: Int,
            vararg formatArgs: Any,
        ): String {
            val app = getApplication<Application>()
            val locale = Locale(if (isBengali.value) "bn" else "en")
            val cfg = Configuration(app.resources.configuration).apply { setLocale(locale) }
            val ctx = app.createConfigurationContext(cfg)
            return if (formatArgs.isEmpty()) ctx.getString(resId) else ctx.getString(resId, *formatArgs)
        }

        private data class LocalDecisionPlan(
            val status: String,
            val decision: String,
            val primaryReason: String,
            val maxDuration: Int,
            val requiresShade: Boolean,
        )

        fun computeLocalAuthoritativeDecision(schoolId: String): DecisionResponse {
            val verifiedBase =
                (uiState.value as? DecisionUiState.Success)?.data
                    ?: dashboardViewModel.getCachedDecision()
            val heatIndex = verifiedBase?.heatIndexC
            val aqiVal = verifiedBase?.aqi
            val pm25Val = verifiedBase?.pm25Ugm3
            if (heatIndex == null || aqiVal == null) {
                val sNameBn = userSession.value?.schoolNameBn ?: "ঢাকা মডেল স্কুল"
                val sNameEn = userSession.value?.schoolNameEn ?: "Dhaka Model School"
                val failReason = getLocalizedString(R.string.local_fail_closed_reason)
                return DecisionResponse(
                    schoolId = schoolId,
                    schoolName = "$sNameBn ($sNameEn)",
                    status = "UNKNOWN",
                    decision = "UNKNOWN",
                    policyFloorDecision = "UNKNOWN",
                    heatIndexC = null,
                    aqi = null,
                    pm25Ugm3 = null,
                    primaryReason = failReason,
                    reasons = listOf(failReason),
                    maxDurationMinutes = null,
                    requiresShade = true,
                    isStale = true,
                    isModeled = true,
                    lastUpdated = null,
                    routineIntensity = scheduleViewModel.currentRoutineIntensity,
                )
            }
            val isChittagong = schoolId.contains("chittagong") || schoolId.contains("chs")
            val schoolNameBn = if (isChittagong) "চট্টগ্রাম হাই স্কুল" else "ঢাকা মডেল স্কুল"
            val schoolNameEn = if (isChittagong) "Chittagong High School" else "Dhaka Model School"

            val override = activeOverride.value
            val isSensorRed = heatIndex >= 40.0 || aqiVal >= 201 || (pm25Val != null && pm25Val >= 150.4)
            val isSensorOrange = heatIndex >= 38.0 || aqiVal >= 151 || (pm25Val != null && pm25Val >= 55.4)
            val hiFormatted = formatLocalizedNumber(heatIndex, isBengali.value, 1)
            val aqiFormatted = formatLocalizedNumber(aqiVal, isBengali.value, 0)
            val hasRespNote = scheduleViewModel.hasRespiratoryNote
            val isVigorous = scheduleViewModel.currentRoutineIntensity.equals("VIGOROUS", ignoreCase = true)

            val plan: LocalDecisionPlan =
                when {
                    isSensorRed || (override != null && override.startsWith("MOVE_INDOORS", ignoreCase = true)) -> {
                        val reasonText =
                            if (override != null && override.startsWith("MOVE_INDOORS", ignoreCase = true)) {
                                override.substringAfter(":", "").trim().ifBlank {
                                    getLocalizedString(R.string.local_override_emergency_default)
                                }
                            } else if (heatIndex >= 40.0) {
                                getLocalizedString(R.string.local_heat_extreme_format, hiFormatted)
                            } else {
                                getLocalizedString(R.string.local_aqi_hazardous_format, aqiFormatted)
                            }
                        LocalDecisionPlan(
                            status = "RED",
                            decision = "MOVE_INDOORS",
                            primaryReason = getLocalizedString(R.string.local_mandatory_move_indoors_format, reasonText),
                            maxDuration = 0,
                            requiresShade = true,
                        )
                    }
                    override != null && override.startsWith("MODIFY", ignoreCase = true) -> {
                        val reasonText =
                            override.substringAfter(":", "").trim().ifBlank {
                                getLocalizedString(R.string.local_override_modify_default)
                            }
                        LocalDecisionPlan(
                            status = "ORANGE",
                            decision = "MODIFY",
                            primaryReason = getLocalizedString(R.string.local_override_modify_format, reasonText),
                            maxDuration = 30,
                            requiresShade = true,
                        )
                    }
                    isSensorOrange || hasRespNote || isVigorous -> {
                        val reasonText =
                            when {
                                isSensorOrange && heatIndex >= 38.0 ->
                                    getLocalizedString(R.string.local_high_heat_modify_format, hiFormatted)
                                isSensorOrange ->
                                    getLocalizedString(R.string.local_unhealthy_aqi_modify_format, aqiFormatted)
                                hasRespNote ->
                                    getLocalizedString(R.string.local_respiratory_modify)
                                else ->
                                    getLocalizedString(R.string.local_vigorous_modify)
                            }
                        LocalDecisionPlan(
                            status = "ORANGE",
                            decision = "MODIFY",
                            primaryReason = reasonText,
                            maxDuration = 30,
                            requiresShade = true,
                        )
                    }
                    heatIndex < 32.0 && aqiVal <= 50 -> {
                        LocalDecisionPlan(
                            status = "GREEN",
                            decision = "PROCEED",
                            primaryReason = getLocalizedString(R.string.local_proceed_safe),
                            maxDuration = 60,
                            requiresShade = false,
                        )
                    }
                    else -> {
                        LocalDecisionPlan(
                            status = "YELLOW",
                            decision = "PROCEED_WITH_PRECAUTIONS",
                            primaryReason = getLocalizedString(R.string.local_precautions_tolerable),
                            maxDuration = 45,
                            requiresShade = true,
                        )
                    }
                }

            return DecisionResponse(
                schoolId = schoolId,
                schoolName = "$schoolNameBn ($schoolNameEn)",
                status = plan.status,
                decision = plan.decision,
                policyFloorDecision = plan.decision,
                heatIndexC = heatIndex,
                aqi = aqiVal,
                pm25Ugm3 = pm25Val,
                primaryReason = plan.primaryReason,
                reasons =
                    listOf(
                        plan.primaryReason,
                        getLocalizedString(R.string.local_mandatory_shade_break),
                    ),
                maxDurationMinutes = plan.maxDuration,
                requiresShade = plan.requiresShade,
                isStale = false,
                isModeled = true,
                lastUpdated = getLocalizedString(R.string.local_just_now_updated),
                routineIntensity = scheduleViewModel.currentRoutineIntensity,
                currentActivityBn = getLocalizedString(R.string.local_no_activity_data),
                currentActivityEn = "No data available",
                nextActivityBn = null,
                nextActivityEn = null,
                confidenceLevelPct = 95,
                confidenceLabelBn = getLocalizedString(R.string.local_measured_iot_label),
                confidenceLabelEn = "Measured Campus IoT Sensor",
                changeForecastBn = getLocalizedString(R.string.local_forecast_stable_4h),
                changeForecastEn = "No decision change expected in the next 4 hours (Stable)",
                activities = emptyList(),
                currentActivity = null,
                nextActivity = null,
            )
        }

        override fun onCleared() {
            super.onCleared()
            dashboardViewModel.stopForegroundSync()
        }
    }
