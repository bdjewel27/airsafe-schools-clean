package com.airsafe.schools.ui.viewmodel

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.airsafe.schools.R
import com.airsafe.schools.data.api.AirSafeApiClient
import com.airsafe.schools.data.api.AppSyncEventsClient
import com.airsafe.schools.data.local.AirSafeLocalDataSource
import com.airsafe.schools.data.model.DecisionResponse
import com.airsafe.schools.data.model.DecisionUiState
import com.airsafe.schools.data.model.LoginRequest
import com.airsafe.schools.data.model.SensorSite
import com.airsafe.schools.data.model.UserRole
import com.airsafe.schools.data.model.UserSession
import com.airsafe.schools.data.notification.AirSafeNotificationHelper
import com.airsafe.schools.util.AppLog
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.util.Locale
import javax.inject.Inject

/**
 * Modular Feature ViewModel for Campus Dashboard & Environmental Telemetry.
 * Handles live decisions, sensor sites, offline mode, and WebSocket push sync.
 */
@HiltViewModel
class DashboardViewModel
    @Inject
    constructor(
        application: Application,
        private val localDataSource: AirSafeLocalDataSource,
    ) : AndroidViewModel(application) {
        companion object {
            private const val TAG = "DashboardVM"
        }

        private var lastAlertedStatus: String? = null
        private var lastRealtimeUpdateMs: Long = 0L
        private var autoRecalculationJob: Job? = null
        private var isForegrounded: Boolean = true
        private var cachedDecision: DecisionResponse? = null

        private val _uiState = MutableStateFlow<DecisionUiState>(DecisionUiState.Loading)
        val uiState: StateFlow<DecisionUiState> = _uiState.asStateFlow()

        private val _currentTab = MutableStateFlow(AppTab.HOME)
        val currentTab: StateFlow<AppTab> = _currentTab.asStateFlow()

        private val _isBengali = MutableStateFlow(java.util.Locale.getDefault().language == "bn")
        val isBengali: StateFlow<Boolean> = _isBengali.asStateFlow()

        private val _userSession = MutableStateFlow<UserSession?>(null)
        val userSession: StateFlow<UserSession?> = _userSession.asStateFlow()

        private val _loginError = MutableStateFlow<String?>(null)
        val loginError: StateFlow<String?> = _loginError.asStateFlow()

        private val _actionError = MutableStateFlow<String?>(null)
        val actionError: StateFlow<String?> = _actionError.asStateFlow()

        private val _showExplanationDialog = MutableStateFlow(false)
        val showExplanationDialog: StateFlow<Boolean> = _showExplanationDialog.asStateFlow()

        private val _sensorSites = MutableStateFlow<List<SensorSite>>(emptyList())
        val sensorSites: StateFlow<List<SensorSite>> = _sensorSites.asStateFlow()

        private val _selectedSchoolId = MutableStateFlow("school-dhaka-001")
        val selectedSchoolId: StateFlow<String> = _selectedSchoolId.asStateFlow()

        private val _isOffline = MutableStateFlow(false)
        val isOffline: StateFlow<Boolean> = _isOffline.asStateFlow()

        private val appSyncEventsClient =
            AppSyncEventsClient(
                onDecisionUpdated = { realtimeDecision ->
                    handleRealtimeDecision(realtimeDecision)
                },
                onConnectionStatusChanged = { connected ->
                    AppLog.d(TAG, "AppSync Events connection status: $connected")
                },
            )

        init {
            AirSafeApiClient.init(application)
            AppLog.d(TAG, "DashboardViewModel initialized.")
            loadInitialCachedData()
            setupNetworkMonitoring()
        }

        private fun loadInitialCachedData() {
            viewModelScope.launch {
                try {
                    val cached = localDataSource.getDecision(_selectedSchoolId.value)
                    if (cached != null) {
                        cachedDecision = cached
                        _uiState.value = DecisionUiState.Success(cached)
                    }
                } catch (e: Exception) {
                    AppLog.e(TAG, "Error reading initial cached decision: ", e)
                    cachedDecision = null
                }
                try {
                    val cachedSites = localDataSource.getSensorSites(_selectedSchoolId.value)
                    if (!cachedSites.isNullOrEmpty()) {
                        _sensorSites.value = cachedSites
                    }
                } catch (_: Exception) {
                }
            }
        }

        fun setTab(tab: AppTab) {
            _currentTab.value = tab
        }

        fun toggleLanguage() {
            _isBengali.value = !_isBengali.value
        }

        fun clearActionError() {
            _actionError.value = null
        }

        fun setActionError(error: String?) {
            _actionError.value = error
        }

        fun triggerExplanationFromNotification() {
            _currentTab.value = AppTab.HOME
            _showExplanationDialog.value = true
        }

        fun dismissExplanationDialog() {
            _showExplanationDialog.value = false
        }

        fun getCustomBaseUrl(): String = AirSafeApiClient.getBaseUrl()

        fun setCustomBaseUrl(
            url: String,
            onSchoolDataReload: ((String) -> Unit)? = null,
        ) {
            AirSafeApiClient.setBaseUrl(url, getApplication())
            val schoolId = _userSession.value?.schoolId ?: _selectedSchoolId.value
            onSchoolDataReload?.invoke(schoolId) ?: loadCurrentDecision(schoolId)
        }

        fun authenticateStaff(
            emailInput: String,
            passwordInput: String,
            onSuccess: ((String) -> Unit)? = null,
        ) {
            val normalizedEmail = emailInput.trim().lowercase()
            _loginError.value = null
            AppLog.d(TAG, "Attempting authentication...")

            stopForegroundSync()
            AirSafeApiClient.authToken = null
            _userSession.value = null
            viewModelScope.launch {
                localDataSource.clearUserSessions()
            }

            viewModelScope.launch {
                try {
                    val loginResp = AirSafeApiClient.service.login(LoginRequest(normalizedEmail, passwordInput))
                    AppLog.d(TAG, "Login successful: role=${loginResp.user.role}, school=${loginResp.user.schoolId}")
                    val resolvedRole =
                        when (loginResp.user.role.uppercase()) {
                            "COORDINATOR" -> UserRole.COORDINATOR
                            "PLATFORM_ADMIN" -> UserRole.PLATFORM_ADMIN
                            else -> UserRole.TEACHER
                        }
                    AirSafeApiClient.authToken = loginResp.token
                    val isCtgEmail =
                        normalizedEmail.contains("chittagong") ||
                            normalizedEmail.contains("chattogram") ||
                            normalizedEmail.contains("chs") ||
                            normalizedEmail.contains("ctg")
                    val resolvedSchoolId =
                        loginResp.user.schoolId
                            ?: (if (isCtgEmail) "school-chattogram-001" else "school-dhaka-001")
                    val userSessionObj =
                        UserSession(
                            email = loginResp.user.email,
                            nameBn = loginResp.user.nameBn ?: "স্কুল স্টাফ",
                            nameEn = loginResp.user.nameEn ?: "School Staff",
                            role = resolvedRole,
                            schoolId = resolvedSchoolId,
                            schoolNameBn = loginResp.user.schoolNameBn ?: "ঢাকা মডেল স্কুল",
                            schoolNameEn = loginResp.user.schoolNameEn ?: "Dhaka Model School",
                            token = loginResp.token,
                        )
                    localDataSource.saveUserSession(userSessionObj)
                    _userSession.value = userSessionObj
                    _selectedSchoolId.value = resolvedSchoolId
                    onSuccess?.invoke(resolvedSchoolId) ?: run {
                        loadCurrentDecision(resolvedSchoolId)
                        loadSensorSites(resolvedSchoolId)
                    }
                } catch (apiEx: Exception) {
                    AppLog.e(TAG, "Backend authentication error: ${apiEx.message}", apiEx)
                    stopForegroundSync()
                    AirSafeApiClient.authToken = null
                    _userSession.value = null
                    localDataSource.clearUserSessions()

                    val httpCode = (apiEx as? HttpException)?.code()
                    val msg =
                        when {
                            httpCode == 401 || httpCode == 403 ->
                                getLocalizedTitle(R.string.err_auth_invalid_credentials_format, httpCode)
                            httpCode == 429 ->
                                getLocalizedTitle(R.string.err_auth_rate_limited)
                            else ->
                                getLocalizedTitle(
                                    R.string.err_auth_network_failed_format,
                                    apiEx.javaClass.simpleName,
                                    AirSafeApiClient.getBaseUrl(),
                                )
                        }
                    _loginError.value = msg
                }
            }
        }

        fun logout() {
            stopForegroundSync()
            AirSafeApiClient.authToken = null
            _userSession.value = null
            _loginError.value = null
            _actionError.value = null
            _selectedSchoolId.value = "school-dhaka-001"
            cachedDecision = null
            viewModelScope.launch {
                localDataSource.clearAll()
            }
        }

        private fun getLocalizedTitle(
            resId: Int,
            vararg formatArgs: Any,
        ): String {
            val app = getApplication<Application>()
            val locale = Locale(if (_isBengali.value) "bn" else "en")
            val cfg = Configuration(app.resources.configuration).apply { setLocale(locale) }
            val ctx = app.createConfigurationContext(cfg)
            return if (formatArgs.isEmpty()) ctx.getString(resId) else ctx.getString(resId, *formatArgs)
        }

        fun handleRealtimeDecision(decision: DecisionResponse) {
            if (_isOffline.value) return
            val now = System.currentTimeMillis()
            if (now - lastRealtimeUpdateMs < 1000L) {
                AppLog.d(TAG, "U-4 Throttle: Live decision update rate-limited (< 1 sec)")
                return
            }
            lastRealtimeUpdateMs = now

            val sanitized = decision.sanitized()
            cachedDecision = sanitized
            viewModelScope.launch {
                localDataSource.saveDecision(sanitized)
            }
            _uiState.value = DecisionUiState.Success(sanitized)

            if (sanitized.status == "RED" && lastAlertedStatus != "RED") {
                lastAlertedStatus = "RED"
                val title = getLocalizedTitle(R.string.notif_urgent_move_indoors_title)
                AirSafeNotificationHelper.sendDangerAlert(getApplication(), title, sanitized.primaryReason ?: "")
            } else if (sanitized.status != "RED") {
                lastAlertedStatus = sanitized.status
            }
        }

        fun startForegroundSync(schoolId: String? = null) {
            if (_isOffline.value) {
                stopForegroundSync()
                return
            }
            isForegrounded = true
            val targetSchoolId = schoolId ?: _userSession.value?.schoolId ?: _selectedSchoolId.value

            appSyncEventsClient.connect(AirSafeApiClient.getBaseUrl(), targetSchoolId)

            autoRecalculationJob?.cancel()
            autoRecalculationJob =
                viewModelScope.launch {
                    while (isForegrounded && !_isOffline.value) {
                        delay(45000L)
                        if (isForegrounded && !_isOffline.value) {
                            recalculateDecisionSilently(targetSchoolId)
                        }
                    }
                }
        }

        fun stopForegroundSync() {
            isForegrounded = false
            appSyncEventsClient.disconnect()
            autoRecalculationJob?.cancel()
            autoRecalculationJob = null
        }

        fun recalculateDecisionSilently(schoolId: String) {
            if (_isOffline.value) return
            viewModelScope.launch {
                try {
                    AppLog.d(TAG, "[AUTO-RECALC] Running silent recalculation for $schoolId...")
                    val resp = AirSafeApiClient.service.getCurrentDecision(schoolId).sanitized()
                    cachedDecision = resp
                    localDataSource.saveDecision(resp)
                    _uiState.value = DecisionUiState.Success(resp)
                } catch (e: Exception) {
                    AppLog.d(TAG, "[AUTO-RECALC] Silent recalculation skipped: ${e.message}")
                }
            }
        }

        fun loadCurrentDecision(schoolId: String = _selectedSchoolId.value) {
            val sessionSchoolId = _userSession.value?.schoolId ?: schoolId
            val targetSchoolId = if (_userSession.value?.role == UserRole.PLATFORM_ADMIN) schoolId else sessionSchoolId

            viewModelScope.launch {
                _uiState.value = DecisionUiState.Loading
                try {
                    val rawResponse = AirSafeApiClient.service.getCurrentDecision(targetSchoolId)
                    val response = rawResponse.sanitized()
                    cachedDecision = response
                    localDataSource.saveDecision(response)
                    _uiState.value = DecisionUiState.Success(response)

                    if (response.status == "RED" && lastAlertedStatus != "RED") {
                        lastAlertedStatus = "RED"
                        val title = getLocalizedTitle(R.string.notif_urgent_move_indoors_title)
                        AirSafeNotificationHelper.sendDangerAlert(getApplication(), title, response.primaryReason ?: "")
                    } else if (response.status != "RED") {
                        lastAlertedStatus = response.status
                    }
                } catch (e: Exception) {
                    AppLog.e(TAG, "Error in loadCurrentDecision: ${e.message}", e)
                    val errorDetails =
                        when (e) {
                            is java.net.ConnectException ->
                                getLocalizedTitle(R.string.err_connect_exception_format, e.message ?: "")
                            is java.net.UnknownHostException ->
                                getLocalizedTitle(R.string.err_unknown_host_format, e.message ?: "")
                            is java.net.SocketTimeoutException ->
                                getLocalizedTitle(R.string.err_socket_timeout)
                            is HttpException ->
                                getLocalizedTitle(R.string.err_http_code_format, e.code(), e.message() ?: "")
                            else -> "${e.javaClass.simpleName}: ${e.message ?: "Unknown network error"}"
                        }
                    _uiState.value =
                        DecisionUiState.Error(
                            message = "$errorDetails\n\n[API URL: ${AirSafeApiClient.getBaseUrl()}]",
                        )
                }
            }
        }

        fun loadSensorSites(schoolId: String) {
            viewModelScope.launch {
                try {
                    val resp = AirSafeApiClient.service.getSensorSites(schoolId)
                    val allSites = resp.sites.toMutableList()
                    resp.districtBaseline?.let { baseline ->
                        val missing = allSites.none { it.locationType == "DISTRICT" || it.id == baseline.id }
                        if (missing) {
                            allSites.add(baseline)
                        }
                    }
                    if (allSites.isNotEmpty()) {
                        val mappedSites =
                            allSites.map { siteDto ->
                                SensorSite(
                                    id = siteDto.id,
                                    nameBn = siteDto.nameBn,
                                    nameEn = siteDto.nameEn,
                                    locationType = siteDto.locationType,
                                    tempC = siteDto.tempC,
                                    rhPct = siteDto.rhPct,
                                    pm25Ugm3 = siteDto.pm25Ugm3,
                                    heatIndexC = siteDto.heatIndexC,
                                    isOnline = siteDto.isOnline,
                                    lastUpdatedBn = siteDto.lastUpdatedBn ?: "এইমাত্র",
                                    lastUpdatedEn = siteDto.lastUpdatedEn ?: "Just now",
                                )
                            }
                        _sensorSites.value = mappedSites
                        localDataSource.saveSensorSites(schoolId, mappedSites)
                    } else {
                        _sensorSites.value = emptyList()
                    }
                } catch (e: Exception) {
                    AppLog.e(TAG, "Error in loadSensorSites: ${e.message}", e)
                    val cachedDbSites = localDataSource.getSensorSites(schoolId)
                    if (!cachedDbSites.isNullOrEmpty()) {
                        _sensorSites.value =
                            cachedDbSites.map { site ->
                                site.copy(
                                    isOnline = false,
                                    lastUpdatedBn = "অফলাইনে সংরক্ষিত",
                                    lastUpdatedEn = "Cached offline",
                                )
                            }
                    } else if (_sensorSites.value.isEmpty()) {
                        _sensorSites.value = emptyList()
                    }
                }
            }
        }

        fun toggleOfflineMode() {
            setOfflineMode(!_isOffline.value)
        }

        fun setOfflineMode(offline: Boolean) {
            if (_isOffline.value == offline) return
            _isOffline.value = offline
            AppLog.d(TAG, "Offline mode changed to: $offline")
            if (offline) {
                stopForegroundSync()
                viewModelScope.launch {
                    val cached =
                        cachedDecision
                            ?: localDataSource.getDecision(_selectedSchoolId.value)
                            ?: (_uiState.value as? DecisionUiState.Success)?.data
                    if (cached != null) {
                        val cappedDecision = if (cached.decision == "PROCEED") "PROCEED_WITH_PRECAUTIONS" else cached.decision
                        val cappedStatus = if (cappedDecision == "PROCEED_WITH_PRECAUTIONS") "YELLOW" else cached.status
                        val offlineCopy =
                            cached.copy(
                                decision = cappedDecision,
                                status = cappedStatus,
                                isStale = true,
                                dataAgeMinutes = cached.dataAgeMinutes ?: 15,
                            )
                        _uiState.value = DecisionUiState.Success(offlineCopy)
                    } else {
                        _uiState.value = DecisionUiState.Success(buildFailClosedUnknown(_userSession.value?.schoolId ?: "school-dhaka-001"))
                    }
                    val cachedDbSites = localDataSource.getSensorSites(_selectedSchoolId.value)
                    if (!cachedDbSites.isNullOrEmpty()) {
                        _sensorSites.value =
                            cachedDbSites.map { site ->
                                site.copy(
                                    isOnline = false,
                                    lastUpdatedBn = "অফলাইনে সংরক্ষিত",
                                    lastUpdatedEn = "Cached offline",
                                )
                            }
                    } else {
                        _sensorSites.value =
                            _sensorSites.value.map { site ->
                                site.copy(
                                    isOnline = false,
                                    lastUpdatedBn = "অফলাইনে সংরক্ষিত",
                                    lastUpdatedEn = "Cached offline",
                                )
                            }
                    }
                }
            } else {
                _sensorSites.value =
                    _sensorSites.value.map { site ->
                        site.copy(
                            isOnline = true,
                            lastUpdatedBn = "১ মিনিট আগে",
                            lastUpdatedEn = "1 min ago",
                        )
                    }
                val schoolId = _userSession.value?.schoolId ?: _selectedSchoolId.value
                loadCurrentDecision(schoolId)
                loadSensorSites(schoolId)
                startForegroundSync(schoolId)
            }
        }

        fun useOfflineFallback(schoolId: String? = null) {
            val target = schoolId ?: _userSession.value?.schoolId ?: _selectedSchoolId.value
            viewModelScope.launch {
                val cachedFromDb = localDataSource.getDecision(target)
                val fallback =
                    if (cachedFromDb != null) {
                        val cappedDecision = if (cachedFromDb.decision == "PROCEED") "PROCEED_WITH_PRECAUTIONS" else cachedFromDb.decision
                        val cappedStatus = if (cappedDecision == "PROCEED_WITH_PRECAUTIONS") "YELLOW" else cachedFromDb.status
                        cachedFromDb.copy(
                            decision = cappedDecision,
                            status = cappedStatus,
                            isStale = true,
                            dataAgeMinutes = cachedFromDb.dataAgeMinutes ?: 15,
                        )
                    } else {
                        buildFailClosedUnknown(target)
                    }
                cachedDecision = fallback
                localDataSource.saveDecision(fallback)
                _uiState.value = DecisionUiState.Success(fallback)
                _actionError.value = null
            }
        }

        fun triggerSimulationDeterioration() {
            val targetSchoolId = _userSession.value?.schoolId ?: _selectedSchoolId.value
            val severeDecision =
                DecisionResponse(
                    schoolId = targetSchoolId,
                    schoolName = _userSession.value?.schoolNameBn ?: "ঢাকা মডেল স্কুল",
                    status = "RED",
                    decision = "MOVE_INDOORS",
                    policyFloorDecision = "MOVE_INDOORS",
                    heatIndexC = 44.5,
                    aqi = 185,
                    pm25Ugm3 = 120.5,
                    primaryReason = getLocalizedTitle(R.string.local_severe_hazard_sim),
                    reasons =
                        listOf(
                            "US AQI 185 triggers MOVE_INDOORS",
                            "Heat Index 44.5°C triggers MOVE_INDOORS",
                        ),
                    maxDurationMinutes = 0,
                    requiresShade = true,
                    isStale = false,
                    isModeled = false,
                    lastUpdated = java.time.Instant.now().toString(),
                    currentActivityBn = "মাঠের খেলাধুলা",
                    currentActivityEn = "Outdoor Sports",
                    nextActivityBn = null,
                    nextActivityEn = null,
                    routineIntensity = "VIGOROUS",
                )
            cachedDecision = severeDecision
            viewModelScope.launch {
                localDataSource.saveDecision(severeDecision)
            }
            _uiState.value = DecisionUiState.Success(severeDecision)
            lastAlertedStatus = "RED"
            val title = getLocalizedTitle(R.string.notif_urgent_move_indoors_title)
            AirSafeNotificationHelper.sendDangerAlert(
                getApplication(),
                title,
                severeDecision.primaryReason ?: getLocalizedTitle(R.string.local_severe_hazard_fallback_body),
            )
        }

        fun updateSuccessDecision(decision: DecisionResponse) {
            cachedDecision = decision
            viewModelScope.launch {
                localDataSource.saveDecision(decision)
            }
            _uiState.value = DecisionUiState.Success(decision)
        }

        fun getCachedDecision(): DecisionResponse? = cachedDecision

        private fun buildFailClosedUnknown(schoolId: String): DecisionResponse {
            val schoolNameBn = _userSession.value?.schoolNameBn ?: "ঢাকা মডেল স্কুল"
            val schoolNameEn = _userSession.value?.schoolNameEn ?: "Dhaka Model School"
            val reason = getLocalizedTitle(R.string.local_fail_closed_reason)
            return DecisionResponse(
                schoolId = schoolId,
                schoolName = "$schoolNameBn ($schoolNameEn)",
                status = "UNKNOWN",
                decision = "UNKNOWN",
                policyFloorDecision = "UNKNOWN",
                heatIndexC = null,
                aqi = null,
                pm25Ugm3 = null,
                primaryReason = reason,
                reasons = listOf(reason),
                maxDurationMinutes = null,
                requiresShade = true,
                isStale = true,
                isModeled = true,
                lastUpdated = null,
                routineIntensity = "moderate",
            )
        }

        private fun setupNetworkMonitoring() {
            try {
                val cm = getApplication<Application>().getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
                if (cm != null) {
                    cm.registerDefaultNetworkCallback(
                        object : android.net.ConnectivityManager.NetworkCallback() {
                            override fun onAvailable(network: android.net.Network) {
                                val caps = cm.getNetworkCapabilities(network)
                                val isValidated = caps?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
                                AppLog.d(TAG, "Default network available (validated=$isValidated).")
                                if (isValidated) {
                                    setOfflineMode(false)
                                }
                            }

                            override fun onCapabilitiesChanged(
                                network: android.net.Network,
                                networkCapabilities: android.net.NetworkCapabilities,
                            ) {
                                val isValidated =
                                    networkCapabilities.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                                setOfflineMode(!isValidated)
                            }

                            override fun onLost(network: android.net.Network) {
                                val activeCaps = cm.getNetworkCapabilities(cm.activeNetwork)
                                val stillOnline =
                                    activeCaps?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
                                AppLog.d(TAG, "Network lost; stillOnline=$stillOnline")
                                setOfflineMode(!stillOnline)
                            }
                        },
                    )

                    val activeNetwork = cm.activeNetwork
                    val caps = cm.getNetworkCapabilities(activeNetwork)
                    val isOnline = caps?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
                    if (!isOnline) {
                        setOfflineMode(true)
                    }
                }
            } catch (e: Exception) {
                AppLog.e(TAG, "Network monitoring setup exception: ", e)
            }
        }

        override fun onCleared() {
            super.onCleared()
            stopForegroundSync()
        }
    }
