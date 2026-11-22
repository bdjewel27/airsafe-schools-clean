package com.airsafe.schools.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.airsafe.schools.R
import com.airsafe.schools.data.api.AirSafeApiClient
import com.airsafe.schools.data.model.SchoolAnalyticsSummary
import com.airsafe.schools.data.model.TrendPoint
import com.airsafe.schools.util.AppLog
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Modular Feature ViewModel for School Analytics, Metric Summaries, and Historical Trends.
 */
@HiltViewModel
class AnalyticsViewModel
    @Inject
    constructor(
        application: Application,
    ) : AndroidViewModel(application) {
        companion object {
            private const val TAG = "AnalyticsVM"
        }

        private val _trendData = MutableStateFlow<List<TrendPoint>>(emptyList())
        val trendData: StateFlow<List<TrendPoint>> = _trendData.asStateFlow()

        private val _isTrendsLoading = MutableStateFlow(false)
        val isTrendsLoading: StateFlow<Boolean> = _isTrendsLoading.asStateFlow()

        private val _trendsError = MutableStateFlow<String?>(null)
        val trendsError: StateFlow<String?> = _trendsError.asStateFlow()

        private val _selectedTrendRange = MutableStateFlow("24h")
        val selectedTrendRange: StateFlow<String> = _selectedTrendRange.asStateFlow()

        private val _compareSiteId = MutableStateFlow<String?>(null)
        val compareSiteId: StateFlow<String?> = _compareSiteId.asStateFlow()

        private val _trendPeriodLabel = MutableStateFlow("আজকের ২৪ ঘণ্টার ট্রেন্ড")
        val trendPeriodLabel: StateFlow<String> = _trendPeriodLabel.asStateFlow()

        private val _analyticsData =
            MutableStateFlow(
                SchoolAnalyticsSummary(
                    weekLabelBn = "ডেটা লোড হচ্ছে...",
                    weekLabelEn = "Loading Data...",
                    lostOutdoorMinutes = 0,
                    prevWeekLostMinutes = null,
                    hoursAboveThreshold = 0.0,
                    proceedCount = 0,
                    cautionCount = 0,
                    moveIndoorsCount = 0,
                    overridesCount = 0,
                    measuredRatioPct = 0,
                ),
            )
        val analyticsData: StateFlow<SchoolAnalyticsSummary> = _analyticsData.asStateFlow()

        fun setTrendRange(
            range: String,
            schoolId: String,
            isBengali: Boolean = true,
        ) {
            _selectedTrendRange.value = range
            loadTrends(schoolId, range, null, _compareSiteId.value, isBengali)
        }

        fun setComparisonSite(
            siteId: String?,
            schoolId: String,
            isBengali: Boolean = true,
        ) {
            _compareSiteId.value = siteId
            loadTrends(schoolId, _selectedTrendRange.value, null, siteId, isBengali)
        }

        fun loadTrends(
            schoolId: String,
            range: String = _selectedTrendRange.value,
            siteId: String? = null,
            compareSiteId: String? = _compareSiteId.value,
            isBengali: Boolean = true,
        ) {
            viewModelScope.launch {
                _isTrendsLoading.value = true
                _trendsError.value = null
                try {
                    AppLog.d(TAG, "Loading trends from schools/$schoolId/trends?range=$range&compare_site_id=$compareSiteId")
                    val resp = AirSafeApiClient.service.getTrends(schoolId, range, siteId, compareSiteId)
                    val app = getApplication<Application>()
                    val locale = java.util.Locale(if (isBengali) "bn" else "en")
                    val cfg = android.content.res.Configuration(app.resources.configuration).apply { setLocale(locale) }
                    val defaultPeriod = app.createConfigurationContext(cfg).getString(R.string.trends_default_period)
                    _trendPeriodLabel.value =
                        if (isBengali) resp.periodBn ?: defaultPeriod else resp.periodEn ?: defaultPeriod
                    val validPoints =
                        resp.points.mapNotNull { p ->
                            val hi = p.heatIndexC
                            val aqiVal = p.aqi
                            if (hi == null || aqiVal == null) {
                                null
                            } else {
                                TrendPoint(
                                    timeLabelBn = p.timeLabelBn ?: "",
                                    timeLabelEn = p.timeLabelEn ?: "",
                                    heatIndexC = hi,
                                    aqi = aqiVal,
                                    isDangerous = p.isDangerous ?: (hi >= 39.0 || aqiVal >= 151),
                                    isSchoolHours = p.isSchoolHours ?: false,
                                    isShaded = p.isShaded ?: false,
                                    comparisonHeatIndexC = p.comparisonHeatIndexC,
                                    comparisonAqi = p.comparisonAqi,
                                    deltaHeatIndexC = p.deltaHeatIndexC,
                                    deltaAqi = p.deltaAqi,
                                )
                            }
                        }
                    _trendData.value = validPoints
                    if (validPoints.isEmpty()) {
                        _trendsError.value = getApplication<Application>().getString(R.string.err_trends_empty)
                    }
                    AppLog.d(TAG, "Trends loaded successfully (${_trendData.value.size} points)")
                } catch (e: Exception) {
                    AppLog.e(TAG, "Error in loadTrends: ${e.message}", e)
                    _trendsError.value =
                        getApplication<Application>().getString(
                            R.string.err_trends_load_failed,
                            e.javaClass.simpleName,
                        )
                } finally {
                    _isTrendsLoading.value = false
                }
            }
        }

        fun loadAnalytics(schoolId: String) {
            viewModelScope.launch {
                try {
                    AppLog.d(TAG, "Loading analytics from schools/$schoolId/analytics")
                    val resp = AirSafeApiClient.service.getAnalytics(schoolId)
                    val dynamicLostMinutes =
                        if (resp.lostOutdoorMinutes > 0) {
                            resp.lostOutdoorMinutes
                        } else if (resp.totalIndoorHours > 0) {
                            resp.totalIndoorHours * 60
                        } else {
                            0
                        }

                    val priorWeekMinutes: Int? =
                        if (resp.priorWeek != null) {
                            (resp.priorWeek.totalIndoorHours ?: 0) * 60
                        } else {
                            null
                        }

                    _analyticsData.value =
                        SchoolAnalyticsSummary(
                            weekLabelBn = resp.periodBn ?: "গত ৭ দিন",
                            weekLabelEn = resp.periodEn ?: "Past 7 Days",
                            lostOutdoorMinutes = dynamicLostMinutes,
                            prevWeekLostMinutes = priorWeekMinutes,
                            hoursAboveThreshold = resp.totalIndoorHours.toDouble(),
                            proceedCount = resp.safeSessionsCount,
                            cautionCount = resp.cautionSessionsCount,
                            moveIndoorsCount = resp.moveIndoorsCount,
                            overridesCount = resp.overridesCount,
                            measuredRatioPct = resp.measuredRatioPct,
                        )
                    AppLog.d(
                        TAG,
                        "Analytics loaded successfully: dynamicLostMinutes=$dynamicLostMinutes, prevWeekLostMinutes=$priorWeekMinutes",
                    )
                } catch (e: Exception) {
                    AppLog.e(TAG, "Error in loadAnalytics: ${e.message}", e)
                    _analyticsData.value =
                        SchoolAnalyticsSummary(
                            weekLabelBn = "তথ্য অনুপলব্ধ",
                            weekLabelEn = "Data Unavailable",
                            lostOutdoorMinutes = 0,
                            prevWeekLostMinutes = null,
                            hoursAboveThreshold = 0.0,
                            proceedCount = 0,
                            cautionCount = 0,
                            moveIndoorsCount = 0,
                            overridesCount = 0,
                            measuredRatioPct = 0,
                        )
                }
            }
        }

        fun getWeeklyAnalyticsCsv(): String {
            val a = _analyticsData.value
            return buildString {
                appendLine("metric_key,metric_label_bn,metric_label_en,value,unit")
                appendLine("lost_outdoor_minutes,বাতিল হওয়া আউটডোর মিনিট,Lost Outdoor Minutes,${a.lostOutdoorMinutes},minutes")
                appendLine("hours_above_threshold,বিপদসীমার ওপরে সময়,Hours Above Safety Threshold,${a.hoursAboveThreshold},hours")
                appendLine("safe_sessions,নিরাপদ সেশন,Safe Sessions,${a.proceedCount},sessions")
                appendLine("caution_sessions,সতর্কতা সেশন,Caution Sessions,${a.cautionCount},sessions")
                appendLine("move_indoors_sessions,ভেতরে নেওয়া সেশন,Move Indoors Sessions,${a.moveIndoorsCount},sessions")
                appendLine("logged_overrides,রেকর্ড করা ওভাররাইড,Logged Overrides,${a.overridesCount},count")
                appendLine("measured_ratio,মাপা ডেটার অনুপাত,Measured Sensor Ratio,${a.measuredRatioPct},percent")
            }
        }
    }
