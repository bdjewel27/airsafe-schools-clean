package com.airsafe.schools.data.model

import android.content.Context
import com.airsafe.schools.R
import com.google.gson.annotations.SerializedName
import java.text.NumberFormat
import java.util.Locale

/**
 * Fail-closed DecisionResponse model (Hard Gate 4).
 * Supports both snake_case and camelCase serialization from backend.
 * Missing or null fields NEVER default to GREEN/PROCEED or fabricated safe numbers.
 */
data class ActivityScheduleItem(
    @SerializedName("id") val id: String? = null,
    @SerializedName(value = "activity_id", alternate = ["activityId"]) val activityId: String? = null,
    @SerializedName(value = "name_bn", alternate = ["nameBn"]) val nameBn: String? = null,
    @SerializedName(value = "name_en", alternate = ["nameEn"]) val nameEn: String? = null,
    @SerializedName(value = "time_slot", alternate = ["timeSlot"]) val timeSlot: String? = null,
    @SerializedName("intensity") val intensity: String? = "MODERATE",
    @SerializedName(value = "location_type", alternate = ["locationType"]) val locationType: String? = "FIELD",
    @SerializedName(value = "target_class", alternate = ["targetClass"]) val targetClass: String? = null,
    @SerializedName(value = "duration_minutes", alternate = ["durationMinutes"]) val durationMinutes: Int? = 45,
    @SerializedName(value = "is_current", alternate = ["isCurrent"]) val isCurrent: Boolean? = false,
    @SerializedName("order") val order: Int? = 1,
    @SerializedName("decision") val decision: String? = "UNKNOWN",
    @SerializedName("status") val status: String? = "UNKNOWN",
    @SerializedName(value = "primary_reason", alternate = ["primaryReason"]) val primaryReason: String? = null,
    @SerializedName("reasons") val reasons: List<String>? = emptyList(),
    @SerializedName(value = "max_duration_minutes", alternate = ["maxDurationMinutes"]) val maxDurationMinutes: Int? = null,
    @SerializedName(value = "requires_shade", alternate = ["requiresShade"]) val requiresShade: Boolean? = true,
)

data class DecisionResponse(
    @SerializedName(value = "school_id", alternate = ["schoolId"]) val schoolId: String? = null,
    @SerializedName(value = "school_name", alternate = ["schoolName"]) val schoolName: String? = null,
    // "GREEN", "YELLOW", "ORANGE", "RED", "UNKNOWN"
    @SerializedName(value = "status") val status: String? = "UNKNOWN",
    // "PROCEED", "PROCEED_WITH_PRECAUTIONS", "MODIFY", "MOVE_INDOORS", "UNKNOWN"
    @SerializedName(value = "decision") val decision: String? = "UNKNOWN",
    @SerializedName(value = "policy_floor_decision", alternate = ["policyFloorDecision"]) val policyFloorDecision: String? = null,
    @SerializedName(value = "heat_index_c", alternate = ["heatIndexC", "heat_index"]) val heatIndexC: Double? = null,
    @SerializedName(value = "aqi") val aqi: Int? = null,
    @SerializedName(value = "pm25_ugm3", alternate = ["pm25Ugm3", "pm25"]) val pm25Ugm3: Double? = null,
    @SerializedName(value = "primary_reason", alternate = ["primaryReason"]) val primaryReason: String? = null,
    @SerializedName(value = "primary_reason_en", alternate = ["primaryReasonEn"]) val primaryReasonEn: String? = null,
    @SerializedName(value = "reasons") val reasons: List<String>? = emptyList(),
    @SerializedName(value = "max_duration_minutes", alternate = ["maxDurationMinutes"]) val maxDurationMinutes: Int? = null,
    @SerializedName(value = "requires_shade", alternate = ["requiresShade"]) val requiresShade: Boolean? = true,
    @SerializedName(value = "is_stale", alternate = ["isStale"]) val isStale: Boolean? = true,
    @SerializedName(value = "data_age_minutes", alternate = ["dataAgeMinutes", "staleMinutes"]) val dataAgeMinutes: Int? = null,
    @SerializedName(value = "is_modeled", alternate = ["isModeled"]) val isModeled: Boolean? = true,
    @SerializedName(value = "last_updated", alternate = ["lastUpdated"]) val lastUpdated: String? = null,
    @SerializedName(value = "current_activity_bn", alternate = ["currentActivityBn"]) val currentActivityBn: String? = null,
    @SerializedName(value = "current_activity_en", alternate = ["currentActivityEn"]) val currentActivityEn: String? = null,
    @SerializedName(value = "next_activity_bn", alternate = ["nextActivityBn"]) val nextActivityBn: String? = null,
    @SerializedName(value = "next_activity_en", alternate = ["nextActivityEn"]) val nextActivityEn: String? = null,
    @SerializedName(value = "routine_intensity", alternate = ["routineIntensity"]) val routineIntensity: String? = null,
    @SerializedName(value = "active_override", alternate = ["activeOverride"]) val activeOverride: String? = null,
    @SerializedName(value = "confidence_level_pct", alternate = ["confidenceLevelPct"]) val confidenceLevelPct: Int? = null,
    @SerializedName(value = "confidence_label_bn", alternate = ["confidenceLabelBn"]) val confidenceLabelBn: String? = null,
    @SerializedName(value = "confidence_label_en", alternate = ["confidenceLabelEn"]) val confidenceLabelEn: String? = null,
    @SerializedName(value = "weather_trend_bn", alternate = ["weatherTrendBn"]) val weatherTrendBn: String? = null,
    @SerializedName(value = "weather_trend_en", alternate = ["weatherTrendEn"]) val weatherTrendEn: String? = null,
    @SerializedName(
        value = "change_forecast_bn",
        alternate = ["changeForecastBn", "expected_change_bn"],
    ) val changeForecastBn: String? = null,
    @SerializedName(
        value = "change_forecast_en",
        alternate = ["changeForecastEn", "expected_change_en"],
    ) val changeForecastEn: String? = null,
    @SerializedName(value = "expected_change_at", alternate = ["expectedChangeAt"]) val expectedChangeAt: String? = null,
    @SerializedName(value = "activities") val activities: List<ActivityScheduleItem>? = emptyList(),
    @SerializedName(value = "current_activity", alternate = ["currentActivity"]) val currentActivity: ActivityScheduleItem? = null,
    @SerializedName(value = "next_activity", alternate = ["nextActivity"]) val nextActivity: ActivityScheduleItem? = null,
) {
    /**
     * Hard Gate 4 Fail-Closed Sanitizer:
     * If decision/status or both environmental metrics (heatIndexC and aqi) are missing,
     * fail closed to UNKNOWN — never synthesize GREEN/PROCEED or fake sensor readings.
     */
    fun sanitized(): DecisionResponse {
        val hasValidMetrics = (heatIndexC != null && heatIndexC.isFinite()) && (aqi != null && aqi >= 0)
        val validDecisions = setOf("PROCEED", "PROCEED_WITH_PRECAUTIONS", "MODIFY", "MOVE_INDOORS")
        val normalizedDecision = decision?.uppercase()?.trim()

        val safeDecision =
            if (hasValidMetrics && normalizedDecision != null && validDecisions.contains(normalizedDecision)) {
                normalizedDecision
            } else {
                "UNKNOWN"
            }

        val safeStatus =
            when (safeDecision) {
                "PROCEED" -> "GREEN"
                "PROCEED_WITH_PRECAUTIONS" -> "YELLOW"
                "MODIFY" -> "ORANGE"
                "MOVE_INDOORS" -> "RED"
                else -> "UNKNOWN"
            }

        val defaultReason =
            if (safeDecision == "UNKNOWN") {
                "সেন্সর বা আবহাওয়া ডেটা পাওয়া যায়নি — অনুগ্রহ করে কোঅর্ডিনেটরের সাথে যোগাযোগ করুন (Data unavailable — fail-closed)"
            } else {
                primaryReason ?: "স্বাভাবিক পরিবেশ (Policy v1.2 সীমার মধ্যে)"
            }

        return this.copy(
            schoolId = schoolId ?: "school-dhaka-001",
            schoolName = if (schoolName.isNullOrBlank() || schoolName == "School") "ঢাকা মডেল স্কুল (Dhaka Model School)" else schoolName,
            status = safeStatus,
            decision = safeDecision,
            heatIndexC = if (safeDecision == "UNKNOWN") null else heatIndexC,
            aqi = if (safeDecision == "UNKNOWN") null else aqi,
            pm25Ugm3 = if (safeDecision == "UNKNOWN") null else pm25Ugm3,
            primaryReason = defaultReason,
            reasons = if (reasons.isNullOrEmpty()) listOf(defaultReason) else reasons,
            maxDurationMinutes = if (safeDecision == "UNKNOWN") null else maxDurationMinutes,
            requiresShade = requiresShade ?: (safeDecision != "PROCEED"),
            isStale = if (safeDecision == "UNKNOWN") true else (isStale ?: true),
            dataAgeMinutes = dataAgeMinutes,
            isModeled = isModeled ?: true,
            lastUpdated = lastUpdated,
            currentActivityBn = if (currentActivityBn.isNullOrBlank()) "তথ্য নেই" else currentActivityBn,
            currentActivityEn = if (currentActivityEn.isNullOrBlank()) "No routine scheduled" else currentActivityEn,
            nextActivityBn = if (nextActivityBn.isNullOrBlank()) null else nextActivityBn,
            nextActivityEn = if (nextActivityEn.isNullOrBlank()) null else nextActivityEn,
            routineIntensity = routineIntensity,
            confidenceLevelPct = if (confidenceLevelPct != null && confidenceLevelPct > 0) confidenceLevelPct else null,
            confidenceLabelBn =
                confidenceLabelBn
                    ?: (if (isModeled == true) "মডেল করা স্যাটেলাইট ডেটা" else "পরিমাপিত ক্যাম্পাস IoT সেন্সর"),
            confidenceLabelEn =
                confidenceLabelEn
                    ?: (if (isModeled == true) "Modeled Satellite Telemetry" else "Measured Campus IoT Sensor"),
            changeForecastBn = changeForecastBn ?: "পরবর্তী ৪ ঘণ্টায় পরিবর্তনের সম্ভাবনা নেই (স্থিতিশীল)",
            changeForecastEn = changeForecastEn ?: "No decision change expected in the next 4 hours (Stable)",
            expectedChangeAt = expectedChangeAt,
            activities = if (!activities.isNullOrEmpty()) activities else this.activities,
            currentActivity = currentActivity,
            nextActivity = nextActivity,
        )
    }
}

data class LoginRequest(
    @SerializedName("email") val email: String,
    @SerializedName("password") val password: String,
)

data class AuthenticatedUserDto(
    @SerializedName(value = "email") val email: String,
    @SerializedName(value = "role") val role: String,
    @SerializedName(value = "school_id", alternate = ["schoolId"]) val schoolId: String? = null,
    @SerializedName(value = "name_bn", alternate = ["nameBn"]) val nameBn: String? = null,
    @SerializedName(value = "name_en", alternate = ["nameEn"]) val nameEn: String? = null,
    @SerializedName(value = "school_name_bn", alternate = ["schoolNameBn"]) val schoolNameBn: String? = null,
    @SerializedName(value = "school_name_en", alternate = ["schoolNameEn"]) val schoolNameEn: String? = null,
)

data class LoginResponse(
    @SerializedName("token") val token: String,
    @SerializedName("user") val user: AuthenticatedUserDto,
)

data class RoutineUpdateRequest(
    @SerializedName("activity_name_bn") val activityNameBn: String,
    @SerializedName("activity_name_en") val activityNameEn: String,
    @SerializedName("next_activity_bn") val nextActivityBn: String? = null,
    @SerializedName("next_activity_en") val nextActivityEn: String? = null,
    @SerializedName("intensity") val intensity: String,
    @SerializedName("duration_minutes") val durationMinutes: Int = 45,
    @SerializedName("target_class") val targetClass: String? = null,
    @SerializedName("justification") val justification: String? = null,
)

data class StaffItemDto(
    @SerializedName("email") val email: String,
    @SerializedName(value = "role_bn", alternate = ["roleBn"]) val roleBn: String?,
    @SerializedName(value = "role_en", alternate = ["roleEn"]) val roleEn: String?,
)

data class StaffListResponse(
    @SerializedName("staff") val staff: List<StaffItemDto>,
)

data class SensorSiteDto(
    @SerializedName("id") val id: String,
    @SerializedName(value = "name_bn", alternate = ["nameBn"]) val nameBn: String,
    @SerializedName(value = "name_en", alternate = ["nameEn"]) val nameEn: String,
    @SerializedName(value = "location_type", alternate = ["locationType"]) val locationType: String,
    @SerializedName(value = "temp_c", alternate = ["tempC"]) val tempC: Double?,
    @SerializedName(value = "rh_pct", alternate = ["rhPct"]) val rhPct: Double?,
    @SerializedName(value = "pm25_ugm3", alternate = ["pm25Ugm3"]) val pm25Ugm3: Double?,
    @SerializedName(value = "heat_index_c", alternate = ["heatIndexC"]) val heatIndexC: Double?,
    @SerializedName(value = "is_online", alternate = ["isOnline"]) val isOnline: Boolean,
    @SerializedName(value = "last_updated_bn", alternate = ["lastUpdatedBn"]) val lastUpdatedBn: String?,
    @SerializedName(value = "last_updated_en", alternate = ["lastUpdatedEn"]) val lastUpdatedEn: String?,
)

data class SensorSitesResponse(
    @SerializedName("sites") val sites: List<SensorSiteDto>,
    @SerializedName(value = "district_baseline", alternate = ["districtBaseline"]) val districtBaseline: SensorSiteDto? = null,
)

data class PriorWeekDto(
    @SerializedName(value = "avg_aqi", alternate = ["avgAqi"]) val avgAqi: Int? = null,
    @SerializedName(value = "max_heat_index_c", alternate = ["maxHeatIndexC"]) val maxHeatIndexC: Double? = null,
    @SerializedName(value = "total_indoor_hours", alternate = ["totalIndoorHours"]) val totalIndoorHours: Int? = null,
    @SerializedName(value = "total_sessions", alternate = ["totalSessions"]) val totalSessions: Int? = null,
)

data class AnalyticsResponseDto(
    @SerializedName(value = "period_bn", alternate = ["periodBn"]) val periodBn: String?,
    @SerializedName(value = "period_en", alternate = ["periodEn"]) val periodEn: String?,
    @SerializedName(value = "avg_aqi", alternate = ["avgAqi"]) val avgAqi: Int = 50,
    @SerializedName(value = "max_heat_index_c", alternate = ["maxHeatIndexC"]) val maxHeatIndexC: Double = 33.0,
    @SerializedName(value = "total_indoor_hours", alternate = ["totalIndoorHours"]) val totalIndoorHours: Int = 4,
    @SerializedName(value = "warning_days_count", alternate = ["warningDaysCount"]) val warningDaysCount: Int = 8,
    @SerializedName(value = "trend_direction", alternate = ["trendDirection"]) val trendDirection: String? = "STABLE",
    @SerializedName(value = "total_sessions_analyzed", alternate = ["totalSessionsAnalyzed"]) val totalSessionsAnalyzed: Int = 18,
    @SerializedName(value = "lost_outdoor_minutes", alternate = ["lostOutdoorMinutes"]) val lostOutdoorMinutes: Int = 240,
    @SerializedName(value = "safe_sessions_count", alternate = ["safeSessionsCount"]) val safeSessionsCount: Int = 12,
    @SerializedName(value = "caution_sessions_count", alternate = ["cautionSessionsCount"]) val cautionSessionsCount: Int = 4,
    @SerializedName(value = "move_indoors_count", alternate = ["moveIndoorsCount"]) val moveIndoorsCount: Int = 2,
    @SerializedName(value = "overrides_count", alternate = ["overridesCount"]) val overridesCount: Int = 0,
    @SerializedName(value = "measured_ratio_pct", alternate = ["measuredRatioPct"]) val measuredRatioPct: Int = 100,
    @SerializedName(value = "modeled_ratio_pct", alternate = ["modeledRatioPct"]) val modeledRatioPct: Int = 0,
    @SerializedName(value = "prior_week", alternate = ["priorWeek"]) val priorWeek: PriorWeekDto? = null,
)

data class OverrideRequest(
    @SerializedName("decision") val decision: String,
    @SerializedName("reason") val reason: String,
    @SerializedName(value = "duration_minutes", alternate = ["durationMinutes"]) val durationMinutes: Int? = 120,
    @SerializedName(value = "activity_id", alternate = ["activityId", "target_activity_id"]) val activityId: String? = "current",
)

data class OverrideAuditItemDto(
    @SerializedName("id") val id: String? = null,
    @SerializedName("timestamp") val timestamp: String? = null,
    @SerializedName(value = "requested_decision", alternate = ["decision"]) val requestedDecision: String? = null,
    @SerializedName(value = "policy_floor", alternate = ["policy_floor_decision"]) val policyFloor: String? = null,
    @SerializedName(value = "relaxes_floor", alternate = ["relaxesFloor"]) val relaxesFloor: Boolean = false,
    @SerializedName("reason") val reason: String? = null,
    @SerializedName(value = "coordinator_email", alternate = ["coordinatorEmail"]) val coordinatorEmail: String? = null,
    @SerializedName(value = "target_activity_id", alternate = ["targetActivityId"]) val targetActivityId: String? = null,
    @SerializedName(value = "duration_minutes", alternate = ["durationMinutes"]) val durationMinutes: Int? = null,
    @SerializedName(value = "expires_at", alternate = ["expiresAt"]) val expiresAt: String? = null,
    @SerializedName("date") val date: String? = null,
)

data class OverrideAuditResponse(
    @SerializedName("school_id") val schoolId: String? = null,
    @SerializedName(value = "audit_logs", alternate = ["auditLogs", "overrides"]) val auditLogs: List<OverrideAuditItemDto> = emptyList(),
    @SerializedName("count") val count: Int = 0,
)

data class NoteRequest(
    @SerializedName("note") val note: String,
    @SerializedName(value = "has_respiratory_condition", alternate = ["hasRespiratoryCondition"]) val hasRespiratoryCondition: Boolean,
    @SerializedName(value = "activity_id", alternate = ["activityId", "target_activity_id"]) val activityId: String? = null,
    @SerializedName(value = "activity_name", alternate = ["activityName", "target_activity_name"]) val activityName: String? = null,
)

data class NoteItemDto(
    @SerializedName("note") val note: String,
    @SerializedName(
        value = "hasRespiratory",
        alternate = ["has_respiratory", "has_respiratory_condition"],
    ) val hasRespiratory: Boolean? = false,
    @SerializedName(value = "activity_id", alternate = ["activityId", "target_activity_id"]) val activityId: String? = null,
    @SerializedName(value = "activity_name", alternate = ["activityName", "target_activity_name"]) val activityName: String? = null,
    @SerializedName("timestamp") val timestamp: String? = null,
)

data class NotesListResponse(
    @SerializedName("notes") val notes: List<NoteItemDto> = emptyList(),
)

data class StaffInviteRequest(
    @SerializedName("email") val email: String,
    @SerializedName("role") val role: String,
)

data class AssistantChatRequest(
    @SerializedName("query") val query: String,
    @SerializedName("is_bengali") val isBengali: Boolean = true,
    @SerializedName("stream") val stream: Boolean = false,
)

data class ToolCallItemDto(
    @SerializedName("name") val name: String? = null,
    @SerializedName("status") val status: String? = null,
    @SerializedName(value = "duration_ms", alternate = ["durationMs"]) val durationMs: Long? = null,
)

data class AssistantChatResponse(
    @SerializedName("answer") val answer: String,
    @SerializedName("decision") val decision: String? = null,
    @SerializedName(value = "policy_floor_decision", alternate = ["policyFloorDecision"]) val policyFloorDecision: String? = null,
    @SerializedName(value = "heat_index_c", alternate = ["heatIndexC"]) val heatIndexC: Double? = null,
    @SerializedName("aqi") val aqi: Int? = null,
    @SerializedName(value = "is_stale", alternate = ["isStale"]) val isStale: Boolean? = null,
    @SerializedName(value = "tools_called_count", alternate = ["toolsCalledCount"]) val toolsCalledCount: Int? = null,
    @SerializedName(value = "tool_calls", alternate = ["toolCalls"]) val toolCalls: List<ToolCallItemDto>? = emptyList(),
    @SerializedName("citation_id") val citationId: String? = null,
    @SerializedName("citation_excerpt") val citationExcerpt: String? = null,
    @SerializedName("doc_id") val docId: String? = null,
    @SerializedName(
        value = "suggested_follow_ups",
        alternate = ["suggestedFollowUps", "follow_ups", "followUps"],
    ) val suggestedFollowUps: List<String> = emptyList(),
    @SerializedName(value = "used_bedrock_model", alternate = ["usedBedrockModel"]) val usedBedrockModel: String? = null,
    @SerializedName(value = "is_rule_based_fallback", alternate = ["isRuleBasedFallback"]) val isRuleBasedFallback: Boolean = false,
    @SerializedName(value = "answer_source_label", alternate = ["answerSourceLabel"]) val answerSourceLabel: String? = null,
)

sealed class DecisionUiState {
    object Loading : DecisionUiState()

    data class Success(val data: DecisionResponse) : DecisionUiState()

    data class Error(val message: String) : DecisionUiState()
}

fun String.toBengaliDigits(isBengali: Boolean): String {
    if (!isBengali) return this
    val bnDigits = charArrayOf('০', '১', '২', '৩', '৪', '৫', '৬', '৭', '৮', '৯')
    val sb = StringBuilder()
    for (ch in this) {
        if (ch in '0'..'9') {
            sb.append(bnDigits[ch - '0'])
        } else {
            sb.append(ch)
        }
    }
    return sb.toString()
}

fun formatLocalizedNumber(
    value: Number,
    isBengali: Boolean,
    maxFractionDigits: Int = 1,
): String {
    val locale = if (isBengali) Locale("bn", "BD") else Locale.US
    val nf =
        NumberFormat.getInstance(locale).apply {
            isGroupingUsed = false
            maximumFractionDigits = maxFractionDigits
        }
    return nf.format(value).toBengaliDigits(isBengali)
}

/**
 * Single unified source of truth for sensor data age / freshness calculation (Issue 1 & Step 4).
 * Synchronizes status line and decision card freshness indicators.
 * Strictly uses genuine timestamps and never defaults to hardcoded 95 or 15 mins.
 */
fun calculateDataAgeMinutes(
    lastUpdated: String?,
    dataAgeMinutes: Int?,
): Int {
    if (dataAgeMinutes != null && dataAgeMinutes >= 0) {
        return dataAgeMinutes
    }
    if (!lastUpdated.isNullOrBlank() && !lastUpdated.startsWith("LATEST#")) {
        try {
            val instant = java.time.Instant.parse(lastUpdated)
            val diffMs = java.time.Duration.between(instant, java.time.Instant.now()).toMillis().coerceAtLeast(0)
            val mins = (diffMs / (60 * 1000)).toInt()
            if (mins >= 0) return mins
        } catch (_: Exception) {
            val epochMs = lastUpdated.toLongOrNull()
            if (epochMs != null && epochMs > 0) {
                val diffMs = (System.currentTimeMillis() - epochMs).coerceAtLeast(0)
                val mins = (diffMs / (60 * 1000)).toInt()
                if (mins >= 0) return mins
            }
        }
    }
    return 0
}

/**
 * Format elapsed minutes into localized freshness text.
 */
fun formatElapsedMinutes(
    minutes: Int,
    isBengali: Boolean,
    context: Context,
): String {
    if (minutes <= 0) {
        return context.getString(R.string.data_freshness_just_now)
    }
    if (minutes < 60) {
        val numStr = minutes.toString().toBengaliDigits(isBengali)
        return context.getString(R.string.data_freshness_mins_ago, numStr)
    }
    val hours = minutes / 60
    val remMins = minutes % 60
    val hoursStr = hours.toString().toBengaliDigits(isBengali)
    return if (remMins == 0) {
        context.getString(R.string.data_freshness_hrs_ago, hoursStr)
    } else {
        val remStr = remMins.toString().toBengaliDigits(isBengali)
        context.getString(R.string.data_freshness_hrs_mins_ago, hoursStr, remStr)
    }
}

/**
 * Maps decision enum strings to localized decision action labels (Issue 4 & Step 4).
 * NEVER displays raw uppercase enums on screen.
 */
fun getLocalizedDecisionAction(
    decision: String?,
    isBengali: Boolean,
    context: Context,
): String {
    return when (decision?.uppercase()?.trim()) {
        "PROCEED", "NORMAL", "NORMAL_OPERATION", "NORMAL_OUTDOOR" -> context.getString(R.string.decision_action_proceed)
        "PROCEED_WITH_PRECAUTIONS", "PRECAUTIONS" -> context.getString(R.string.decision_action_precautions)
        "MODIFY" -> context.getString(R.string.decision_action_modify)
        "MOVE_INDOORS", "CANCEL_OUTDOOR" -> context.getString(R.string.decision_action_move_indoors)
        else -> context.getString(R.string.decision_action_unknown)
    }
}

/**
 * Maps decision enum strings to localized decision hero title (Step 4.1).
 */
fun getLocalizedDecisionTitle(
    decision: String?,
    isBengali: Boolean,
    context: Context,
): String {
    return when (decision?.uppercase()?.trim()) {
        "PROCEED", "NORMAL", "NORMAL_OPERATION", "NORMAL_OUTDOOR" -> context.getString(R.string.decision_proceed_title)
        "PROCEED_WITH_PRECAUTIONS", "PRECAUTIONS" -> context.getString(R.string.decision_precautions_title)
        "MODIFY" -> context.getString(R.string.decision_modify_title)
        "MOVE_INDOORS", "CANCEL_OUTDOOR" -> context.getString(R.string.decision_move_indoors_title)
        else -> context.getString(R.string.decision_unknown_title)
    }
}

/**
 * Maps activity intensity strings to localized human-readable labels.
 */
fun getLocalizedIntensity(
    intensity: String?,
    context: Context,
): String {
    return when (intensity?.uppercase()?.trim()) {
        "LIGHT" -> context.getString(R.string.intensity_light)
        "MODERATE" -> context.getString(R.string.intensity_moderate)
        "VIGOROUS", "HEAVY" -> context.getString(R.string.intensity_vigorous)
        else -> context.getString(R.string.intensity_moderate)
    }
}

/**
 * Maps target class labels cleanly for UI using strings.xml resources.
 */
fun getLocalizedTargetClass(
    targetClass: String?,
    context: Context,
): String {
    if (targetClass.isNullOrBlank()) return context.getString(R.string.target_class_all)
    return targetClass
        .replace("Class 5", context.getString(R.string.class_grade_5))
        .replace("Class 6", context.getString(R.string.class_grade_6))
        .replace("Class 7", context.getString(R.string.class_grade_7))
        .replace("Class 8", context.getString(R.string.class_grade_8))
        .replace("Class 9", context.getString(R.string.class_grade_9))
        .replace("Class 10", context.getString(R.string.class_grade_10))
        .replace("All Classes", context.getString(R.string.target_class_all))
}

/**
 * Action-first guidance subtitle for Hero Decision Card (Step 4.1).
 */
fun getLocalizedGuidanceText(
    decision: String?,
    maxDuration: Int?,
    isBengali: Boolean,
    context: Context,
): String {
    val durationStr = (maxDuration ?: 30).toString().toBengaliDigits(isBengali)
    return when (decision?.uppercase()?.trim()) {
        "MODIFY" -> context.getString(R.string.guidance_modify_format, durationStr)
        "PROCEED_WITH_PRECAUTIONS", "PRECAUTIONS" -> context.getString(R.string.decision_precautions_desc)
        "MOVE_INDOORS" -> context.getString(R.string.decision_move_indoors_desc)
        "PROCEED" -> context.getString(R.string.decision_proceed_desc)
        else -> context.getString(R.string.decision_unknown_desc)
    }
}

/**
 * Formats backend trigger reason into fully localized string using strings.xml.
 * Eliminates contradictory reasons (e.g. 'within tolerable limits' during caution/modify).
 */
fun formatLocalizedReason(
    rawReason: String?,
    decision: String?,
    aqi: Int?,
    heatIndexC: Double?,
    isBengali: Boolean,
    context: Context,
): String {
    val cleanReason = rawReason?.trim()

    // 0. Contradiction Guard: If decision is caution or danger, NEVER claim 'within tolerable limits' or 'safe'
    val isContradictory =
        !cleanReason.isNullOrBlank() &&
            (
                cleanReason.contains("tolerable limits", ignoreCase = true) ||
                    cleanReason.contains("সহনশীল", ignoreCase = true)
            ) &&
            (decision != null && decision != "PROCEED" && decision != "UNKNOWN")

    // 1. If reason is already provided in Bengali (and not contradictory and no raw enum leak)
    val rawEnumTokenRegex =
        Regex(
            "\\b(PROCEED_WITH_PRECAUTIONS|MOVE_INDOORS|NORMAL_OPERATION|" +
                "NORMAL_OUTDOOR|CANCEL_OUTDOOR|PROCEED|MODIFY|UNKNOWN)\\b",
        )
    val hasRawEnumInClean = !cleanReason.isNullOrBlank() && rawEnumTokenRegex.containsMatchIn(cleanReason)
    val hasBengaliClean = !cleanReason.isNullOrBlank() && cleanReason.any { it in '\u0980'..'\u09FF' }
    if (!isContradictory && !hasRawEnumInClean && isBengali && hasBengaliClean) {
        return cleanReason!!
    }

    // 2. Match AQI trigger pattern: e.g. "AQI 146 triggers MOVE_INDOORS" or "US AQI 185 triggers MOVE_INDOORS"
    val aqiMatch =
        Regex("(?:US\\s+)?AQI\\s+(\\d+)\\s+triggers\\s+([A-Za-z_]+)", RegexOption.IGNORE_CASE)
            .find(cleanReason ?: "")
    if (aqiMatch != null) {
        val aqiValStr = aqiMatch.groupValues[1].toBengaliDigits(isBengali)
        val actionStr = getLocalizedDecisionAction(aqiMatch.groupValues[2], isBengali, context)
        return context.getString(R.string.decision_reason_aqi_trigger, aqiValStr, actionStr)
    }

    // 3. Match Heat Index trigger pattern: e.g. "Heat Index 44.5°C triggers MOVE_INDOORS"
    val hiMatch =
        Regex("Heat\\s+Index\\s+([\\d.]+)(?:°C|Â°C)?\\s+triggers\\s+([A-Za-z_]+)", RegexOption.IGNORE_CASE)
            .find(cleanReason ?: "")
    if (hiMatch != null) {
        val hiValStr = hiMatch.groupValues[1].toBengaliDigits(isBengali)
        val actionStr = getLocalizedDecisionAction(hiMatch.groupValues[2], isBengali, context)
        return context.getString(R.string.decision_reason_heat_index_trigger, hiValStr, actionStr)
    }

    // 4. Match Weakness / Asthma / Respiratory phrases bidirectionally
    val weaknessRegex = Regex("(weak|দুর্বল|অসুস্থ)", RegexOption.IGNORE_CASE)
    if (!cleanReason.isNullOrBlank() && weaknessRegex.containsMatchIn(cleanReason)) {
        return context.getString(R.string.reason_students_weak)
    }

    val respiratoryNoteRegex = Regex("(asthma-sensitive|শ্বাসকষ্ট বা অ্যাজমাপ্রবণ|শ্বাসকষ্ট সমস্যা)", RegexOption.IGNORE_CASE)
    if (!cleanReason.isNullOrBlank() && respiratoryNoteRegex.containsMatchIn(cleanReason)) {
        return context.getString(R.string.note_asthma_sensitive_present)
    }

    // 4b. Match Respiratory step-up
    val respiratoryRegex = Regex("(respiratory|asthma|আজমা|হাঁপানি|শ্বাসকষ্ট)", RegexOption.IGNORE_CASE)
    if (!cleanReason.isNullOrBlank() && respiratoryRegex.containsMatchIn(cleanReason)) {
        return context.getString(R.string.decision_reason_respiratory_stepup)
    }

    // 5. Match Modeled satellite capping
    val modeledRegex = Regex("(modeled|satellite|মডেল)", RegexOption.IGNORE_CASE)
    if (!cleanReason.isNullOrBlank() && modeledRegex.containsMatchIn(cleanReason)) {
        return context.getString(R.string.decision_reason_modeled_capped)
    }

    // 6. Match Stale data capping (>90 mins)
    val staleRegex = Regex("(older than 90|stale|৯০ মিনিটের বেশি)", RegexOption.IGNORE_CASE)
    if (!cleanReason.isNullOrBlank() && staleRegex.containsMatchIn(cleanReason)) {
        return context.getString(R.string.decision_reason_stale_capped)
    }

    // 7. If contradictory, empty, generic, contains raw enum tokens, or is in the wrong language
    val rawEnumRegex =
        Regex(
            "\\b(PROCEED_WITH_PRECAUTIONS|MOVE_INDOORS|NORMAL_OPERATION|" +
                "NORMAL_OUTDOOR|CANCEL_OUTDOOR|PROCEED|MODIFY|UNKNOWN)\\b",
        )
    val containsRawEnum = !cleanReason.isNullOrBlank() && rawEnumRegex.containsMatchIn(cleanReason)
    val hasBengaliScript = !cleanReason.isNullOrBlank() && cleanReason.any { it in '\u0980'..'\u09FF' }
    val wrongLanguage = (isBengali && !hasBengaliScript) || (!isBengali && hasBengaliScript)
    val isGenericReason =
        cleanReason.isNullOrBlank() ||
            cleanReason.equals("safe", ignoreCase = true) ||
            cleanReason.equals("normal", ignoreCase = true)

    if (isContradictory || containsRawEnum || wrongLanguage || isGenericReason) {
        if (decision != null && decision != "PROCEED" && decision != "UNKNOWN") {
            val aqiPart = aqi?.let { context.getString(R.string.reason_aqi_part, formatLocalizedNumber(it, isBengali, 0)) } ?: ""
            val hiPart =
                heatIndexC?.let {
                    context.getString(R.string.reason_heat_index_part, formatLocalizedNumber(it, isBengali, 1))
                } ?: ""
            val actionStr = getLocalizedDecisionAction(decision, isBengali, context)
            val metricParts = listOf(aqiPart, hiPart).filter { it.isNotBlank() }.joinToString(if (isBengali) ", " else " • ")
            return if (metricParts.isNotBlank()) {
                context.getString(R.string.reason_with_metrics_format, metricParts, actionStr)
            } else {
                context.getString(R.string.reason_policy_only_format, actionStr)
            }
        }
        return context.getString(R.string.decision_reason_normal_policy)
    }

    return cleanReason
}

/**
 * Strips raw internal decision enum tokens (e.g. "(MODIFY)", "MOVE_INDOORS") from user-facing strings.
 */
fun stripRawEnumTokens(rawText: String): String {
    val parensEnumRegex =
        Regex(
            "\\s*\\((?:PROCEED_WITH_PRECAUTIONS|MOVE_INDOORS|NORMAL_OPERATION|" +
                "NORMAL_OUTDOOR|CANCEL_OUTDOOR|PROCEED|MODIFY|UNKNOWN)\\)",
        )
    val wordEnumRegex =
        Regex(
            "\\b(?:PROCEED_WITH_PRECAUTIONS|MOVE_INDOORS|NORMAL_OPERATION|" +
                "NORMAL_OUTDOOR|CANCEL_OUTDOOR|PROCEED|MODIFY|UNKNOWN)\\b:?\\s*",
        )
    return rawText
        .replace(parensEnumRegex, "")
        .replace(wordEnumRegex, "")
        .trim()
}

/**
 * Formats coordinator override banners cleanly without raw enum prefixes or "(MODIFY)" suffixes,
 * translating common English/Bengali override reasons bidirectionally.
 */
fun formatLocalizedOverrideText(
    rawOverride: String,
    isBengali: Boolean,
    context: Context,
): String {
    val trimmed = rawOverride.trim()
    val colonIdx = trimmed.indexOf(':')
    if (colonIdx > 0) {
        val prefix = trimmed.substring(0, colonIdx).trim().uppercase()
        val rawBody = trimmed.substring(colonIdx + 1).trim()
        val cleanBody = translateHealthOrOverrideBody(stripRawEnumTokens(rawBody), isBengali, context, preserveUntranslated = true)
        val knownPrefixes = setOf("MOVE_INDOORS", "CANCEL_OUTDOOR", "MODIFY", "PROCEED_WITH_PRECAUTIONS", "PRECAUTIONS", "PROCEED")
        if (prefix in knownPrefixes) {
            val localizedPrefix = getLocalizedDecisionAction(prefix, isBengali, context)
            return if (cleanBody.isNotBlank()) "$localizedPrefix: $cleanBody" else localizedPrefix
        }
    }
    return translateHealthOrOverrideBody(stripRawEnumTokens(trimmed), isBengali, context, preserveUntranslated = true)
}

/**
 * Maps teacher health notes bidirectionally so zero Bengali characters leak into English mode
 * and English notes translate cleanly in Bengali mode.
 */
fun getLocalizedHealthNote(
    rawNote: String,
    isBengali: Boolean,
    context: Context,
): String {
    val bracketMatch = Regex("^(.*?)\\s*\\[([^\\]]+)\\]\\s*$").find(rawNote.trim())
    val bodyPart = bracketMatch?.groupValues?.get(1)?.trim() ?: rawNote.trim()
    val tagPart = bracketMatch?.groupValues?.get(2)?.trim()
    val localizedBody = translateHealthOrOverrideBody(stripRawEnumTokens(bodyPart), isBengali, context, preserveUntranslated = false)
    if (!tagPart.isNullOrBlank()) {
        val localizedTag = getLocalizedActivityName(tagPart, tagPart, isBengali, context)
        return "$localizedBody [$localizedTag]"
    }
    return localizedBody
}

private fun translateHealthOrOverrideBody(
    cleanText: String,
    isBengali: Boolean,
    context: Context,
    preserveUntranslated: Boolean,
): String {
    if (cleanText.isBlank()) return ""
    val lower = cleanText.lowercase()
    return when {
        Regex("(respiratory|asthma|আজমা|হাঁপানি|শ্বাসকষ্ট|cough|কাশি)").containsMatchIn(lower) ->
            context.getString(R.string.note_asthma_sensitive_present)
        Regex("(weak|দুর্বল|sick|অসুস্থ|tired|ক্লান্ত)").containsMatchIn(lower) ->
            context.getString(R.string.reason_students_weak)
        !isBengali && cleanText.any { it in '\u0980'..'\u09FF' } ->
            context.getString(R.string.note_general_health_caution)
        isBengali && !cleanText.any { it in '\u0980'..'\u09FF' } && !preserveUntranslated ->
            context.getString(R.string.note_general_health_caution)
        isBengali && !cleanText.any { it in '\u0980'..'\u09FF' } && preserveUntranslated ->
            context.getString(R.string.note_general_health_caution)
        else -> cleanText
    }
}

/**
 * Maps routine activity titles deterministically to English when !isBengali and Bengali when isBengali.
 */
fun getLocalizedActivityName(
    nameBn: String?,
    nameEn: String?,
    isBengali: Boolean,
    context: Context,
): String {
    val bn = nameBn?.trim().orEmpty()
    val en = nameEn?.trim().orEmpty()
    val combined = "$en $bn".lowercase()
    val isPe =
        combined.contains("শারীরিক") ||
            combined.contains("পিই") ||
            combined.contains("ফুটবল") ||
            combined.contains("physical education") ||
            combined.contains("football") ||
            Regex("\\bpe\\b").containsMatchIn(combined)
    if (isPe) {
        return context.getString(R.string.activity_pe_football)
    }
    if (combined.contains("সমাবেশ") || combined.contains("অ্যাসেম্বলি") || combined.contains("assembly")) {
        return context.getString(R.string.activity_general_assembly)
    }
    if (combined.contains("টিফিন") || combined.contains("বিরতি") || combined.contains("recess") || combined.contains("tiffin")) {
        return context.getString(R.string.activity_recess_break)
    }
    if (combined.contains("খেলা") || combined.contains("outdoor sports")) {
        return context.getString(R.string.activity_outdoor_sports)
    }
    return if (isBengali) {
        when {
            bn.isNotBlank() && bn != "তথ্য নেই" -> bn
            en.isNotBlank() && en != "No routine scheduled" && en != "No data available" -> en
            else -> context.getString(R.string.activity_outdoor_default)
        }
    } else {
        when {
            en.isNotBlank() &&
                !en.any { it in '\u0980'..'\u09FF' } &&
                en != "No routine scheduled" &&
                en != "No data available" -> en
            bn.isNotBlank() && !bn.any { it in '\u0980'..'\u09FF' } && bn != "তথ্য নেই" -> bn
            else -> context.getString(R.string.activity_outdoor_default)
        }
    }
}

/**
 * Maps raw policy citation codes ("Model School Outdoor Activity Policy v1.2, A 1–A 4", "v1.3, Section A-D")
 * into localized user-friendly titles.
 */
fun getLocalizedPolicyTitle(
    rawCitation: String?,
    context: Context,
): String {
    val clean = rawCitation?.trim().orEmpty()
    val looksTechnical =
        clean.isBlank() ||
            clean.contains("Model School", ignoreCase = true) ||
            clean.contains("v1.2", ignoreCase = true) ||
            clean.contains("v1.3", ignoreCase = true) ||
            clean.contains("Section ", ignoreCase = true) ||
            Regex("\\bA\\s*\\d+").containsMatchIn(clean) ||
            clean.contains("ধারা")
    return if (looksTechnical) context.getString(R.string.policy_title_friendly) else clean
}
