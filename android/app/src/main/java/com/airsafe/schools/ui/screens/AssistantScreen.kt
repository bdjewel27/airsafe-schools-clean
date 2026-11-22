package com.airsafe.schools.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.airsafe.schools.R
import com.airsafe.schools.data.api.AirSafeApiClient
import com.airsafe.schools.data.model.AssistantChatRequest
import com.airsafe.schools.data.model.AssistantChatResponse
import com.airsafe.schools.data.model.DecisionUiState
import com.airsafe.schools.data.model.getLocalizedDecisionAction
import com.airsafe.schools.data.model.getLocalizedPolicyTitle
import com.airsafe.schools.data.model.stripRawEnumTokens
import com.airsafe.schools.data.model.toBengaliDigits
import com.airsafe.schools.ui.theme.CautionText
import com.airsafe.schools.ui.theme.CautionYellowBackground
import com.airsafe.schools.ui.theme.DangerRed
import com.airsafe.schools.ui.theme.DangerRedBackground
import com.airsafe.schools.ui.theme.SafeGreen
import com.airsafe.schools.ui.theme.SafeGreenBackground
import com.airsafe.schools.ui.theme.SurfaceCard
import com.airsafe.schools.ui.theme.TextPrimary
import com.airsafe.schools.ui.theme.TextSecondary
import com.airsafe.schools.ui.viewmodel.DecisionViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AssistantScreen(
    viewModel: DecisionViewModel,
    modifier: Modifier = Modifier,
) {
    val isBengali by viewModel.isBengali.collectAsState()
    val isOffline by viewModel.isOffline.collectAsState()
    val currentDecisionState by viewModel.uiState.collectAsState()
    val userSession by viewModel.userSession.collectAsState()

    var queryText by remember { mutableStateOf("") }
    var isThinking by remember { mutableStateOf(false) }
    var responseAnswer by remember { mutableStateOf<String?>(null) }
    var verifiedHi by remember { mutableStateOf<Double?>(null) }
    var verifiedAqi by remember { mutableStateOf<Int?>(null) }
    var verifiedDecision by remember { mutableStateOf<String?>(null) }
    var activeCitation by remember { mutableStateOf<PolicyCitation?>(null) }
    var showCitationDialog by remember { mutableStateOf(false) }
    var followUpSuggestions by remember { mutableStateOf<List<String>>(emptyList()) }
    var assistantError by remember { mutableStateOf<String?>(null) }
    var usedBedrockModel by remember { mutableStateOf<String?>(null) }
    var isRuleBasedFallback by remember { mutableStateOf(false) }
    var answerSourceLabel by remember { mutableStateOf<String?>(null) }

    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val decisionData = (currentDecisionState as? DecisionUiState.Success)?.data

    val defaultGreeting = stringResource(R.string.assistant_greeting)

    val isRefusal =
        responseAnswer?.contains("only answer questions related to school", ignoreCase = true) == true ||
            responseAnswer?.contains("বায়ুমান, তাপমাত্রা ও outdoor নিরাপত্তা", ignoreCase = true) == true

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 8.dp),
        ) {
            Text(
                text = stringResource(R.string.assistant_title),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
            )
            Text(
                text = stringResource(R.string.app_subtitle),
                fontSize = 13.sp,
                color = TextSecondary,
            )

            Spacer(modifier = Modifier.height(10.dp))

            // U-3: Stale / Offline Banner on Assistant Screen
            if (isOffline || decisionData?.isStale == true) {
                Card(
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = CautionYellowBackground),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.Schedule, contentDescription = null, tint = CautionText)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.assistant_offline_banner),
                            color = CautionText,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }

            // Error Card if Assistant Query Failed (U-3)
            if (assistantError != null) {
                Card(
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = DangerRedBackground),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = DangerRed)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = assistantError ?: "",
                            color = DangerRed,
                            fontSize = 12.sp,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = { assistantError = null }) {
                            Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = DangerRed)
                        }
                    }
                }
            }

            // AI Response Card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.SmartToy,
                            contentDescription = null,
                            tint = SafeGreen,
                            modifier = Modifier.size(28.dp),
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text =
                                when {
                                    isRuleBasedFallback || isOffline -> stringResource(R.string.assistant_source_rule_based)
                                    !answerSourceLabel.isNullOrBlank() -> answerSourceLabel!!
                                    else -> "AirSafe AI"
                                },
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = SafeGreen,
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Clean loading-only indicator (internal MCP tool steps hidden from user chat)
                    if (isThinking) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = SafeGreen,
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.assistant_thinking_status),
                                fontSize = 13.sp,
                                color = TextSecondary,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    // Main Response Body
                    Text(
                        text = responseAnswer ?: defaultGreeting,
                        fontSize = 15.sp,
                        color = TextPrimary,
                        lineHeight = 22.sp,
                        textAlign = TextAlign.Start,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    )

                    // Numerical Grounding Card (U-5 & P6: Strictly verified telemetry only; never fabricated!)
                    val hiToShow = verifiedHi ?: decisionData?.heatIndexC
                    val aqiToShow = verifiedAqi ?: decisionData?.aqi
                    val decToShow = verifiedDecision ?: decisionData?.decision
                    val isModeled = decisionData?.isModeled ?: false
                    val isLargeFont = LocalDensity.current.fontScale > 1.3f
                    val metricAccentColor = if (isModeled) Color(0xFFE65100) else SafeGreen

                    val hasValidAnswer =
                        !isRefusal &&
                            responseAnswer != null &&
                            hiToShow != null &&
                            aqiToShow != null &&
                            decToShow != null &&
                            decToShow != "UNKNOWN"
                    if (hasValidAnswer) {
                        Spacer(modifier = Modifier.height(12.dp))
                        val hiText =
                            if (isModeled) {
                                stringResource(
                                    R.string.grounding_modeled_hi_format,
                                    hiToShow.toString().toBengaliDigits(isBengali),
                                )
                            } else {
                                stringResource(
                                    R.string.grounding_verified_hi_format,
                                    hiToShow.toString().toBengaliDigits(isBengali),
                                )
                            }
                        val localizedDecisionLabel = getLocalizedDecisionAction(decToShow, isBengali, context)
                        val aqiBadgeText = "AQI: ${aqiToShow.toString().toBengaliDigits(isBengali)} ($localizedDecisionLabel)"
                        Card(
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(containerColor = if (isModeled) Color(0xFFFFF8E1) else SafeGreenBackground),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            if (isLargeFont) {
                                Column(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Text(
                                        text = hiText,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = metricAccentColor,
                                        maxLines = 1,
                                        softWrap = false,
                                    )
                                    Text(
                                        text = aqiBadgeText,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = metricAccentColor,
                                        maxLines = 1,
                                        softWrap = false,
                                    )
                                }
                            } else {
                                FlowRow(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Text(
                                        text = hiText,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = metricAccentColor,
                                        maxLines = 1,
                                        softWrap = false,
                                    )
                                    Text(
                                        text = aqiBadgeText,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = metricAccentColor,
                                        maxLines = 1,
                                        softWrap = false,
                                    )
                                }
                            }
                        }
                    } else if (!isRefusal && responseAnswer != null && (decToShow == "UNKNOWN" || hiToShow == null || aqiToShow == null)) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Card(
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(containerColor = CautionYellowBackground),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            if (isLargeFont) {
                                Column(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Text(
                                        text = stringResource(R.string.data_not_found),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = CautionText,
                                        maxLines = 1,
                                        softWrap = false,
                                    )
                                    Text(
                                        text = stringResource(R.string.decision_unknown_title),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = DangerRed,
                                        maxLines = 1,
                                        softWrap = false,
                                    )
                                }
                            } else {
                                FlowRow(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Text(
                                        text = stringResource(R.string.data_not_found),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = CautionText,
                                        maxLines = 1,
                                        softWrap = false,
                                    )
                                    Text(
                                        text = stringResource(R.string.decision_unknown_title),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = DangerRed,
                                        maxLines = 1,
                                        softWrap = false,
                                    )
                                }
                            }
                        }
                    }

                    // Interactive Policy Citation Chip (F-05 & A-7)
                    if (!isRefusal && activeCitation != null && !isThinking) {
                        Spacer(modifier = Modifier.height(12.dp))
                        AssistChip(
                            onClick = { showCitationDialog = true },
                            modifier = Modifier.defaultMinSize(minHeight = 48.dp),
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = null,
                                    tint = SafeGreen,
                                    modifier = Modifier.size(16.dp),
                                )
                            },
                            label = {
                                val citationTitle =
                                    if (isBengali) activeCitation!!.policyTitleBn else activeCitation!!.policyTitleEn
                                Text(
                                    text = stringResource(R.string.assistant_citation_prefix, citationTitle),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                )
                            },
                            colors =
                                AssistChipDefaults.assistChipColors(
                                    containerColor = Color.White,
                                    labelColor = SafeGreen,
                                ),
                        )
                    }
                }
            }
        }

        // Question Input Field with Quick Suggestions
        Column {
            if (!isRefusal && followUpSuggestions.isNotEmpty() && !isThinking) {
                Text(
                    text = stringResource(R.string.assistant_suggested_followups),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextSecondary,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    followUpSuggestions.take(2).forEach { suggestion ->
                        AssistChip(
                            onClick = { queryText = suggestion },
                            modifier = Modifier.defaultMinSize(minHeight = 44.dp),
                            label = {
                                Text(
                                    text = suggestion,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                )
                            },
                        )
                    }
                }
            } else {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AssistChip(
                        onClick = {
                            queryText = context.getString(R.string.assistant_quick_pe_question)
                        },
                        modifier = Modifier.defaultMinSize(minHeight = 48.dp),
                        label = {
                            Text(
                                text = stringResource(R.string.chip_outdoor_pe),
                                fontSize = 11.sp,
                            )
                        },
                    )
                    AssistChip(
                        onClick = {
                            queryText = context.getString(R.string.assistant_quick_heat_question)
                        },
                        modifier = Modifier.defaultMinSize(minHeight = 48.dp),
                        label = {
                            Text(
                                text = stringResource(R.string.chip_heat_rules),
                                fontSize = 11.sp,
                            )
                        },
                    )
                }
            }

            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = queryText,
                    onValueChange = { queryText = it },
                    placeholder = {
                        Text(stringResource(R.string.assistant_placeholder))
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(24.dp),
                )

                Spacer(modifier = Modifier.width(8.dp))

                IconButton(
                    onClick = {
                        if (queryText.isNotBlank() && !isThinking) {
                            val userQ = queryText.trim()
                            queryText = ""
                            assistantError = null
                            coroutineScope.launch {
                                isThinking = true
                                val schoolId = userSession?.schoolId ?: "school-dhaka-001"

                                try {
                                    // Step 1: Stream Grounded Assistant Response via SSE (no fake typewriter replay)
                                    responseAnswer = ""
                                    isRuleBasedFallback = false
                                    answerSourceLabel = null

                                    val apiResp =
                                        suspendCancellableCoroutine<AssistantChatResponse> { cont ->
                                            val eventSource =
                                                AirSafeApiClient.streamAssistantChat(
                                                    schoolId = schoolId,
                                                    request = AssistantChatRequest(query = userQ, isBengali = isBengali, stream = true),
                                                    onToolCall = { _ -> },
                                                    onTokenDelta = { delta ->
                                                        coroutineScope.launch(Dispatchers.Main) {
                                                            isThinking = false
                                                            responseAnswer = (responseAnswer ?: "") + delta
                                                        }
                                                    },
                                                    onComplete = { resp ->
                                                        if (cont.isActive) cont.resume(resp)
                                                    },
                                                    onError = { err ->
                                                        if (cont.isActive) cont.resumeWithException(err)
                                                    },
                                                )
                                            cont.invokeOnCancellation { eventSource.cancel() }
                                        }

                                    // Step 2: Update verified telemetry, source badge, and friendly policy citation
                                    verifiedHi = apiResp.heatIndexC
                                    verifiedAqi = apiResp.aqi
                                    verifiedDecision = apiResp.decision
                                    usedBedrockModel = apiResp.usedBedrockModel
                                    isRuleBasedFallback = apiResp.isRuleBasedFallback
                                    answerSourceLabel = apiResp.answerSourceLabel

                                    val friendlyTitle = getLocalizedPolicyTitle(apiResp.citationId, context)
                                    val defaultExcerpt = context.getString(R.string.policy_excerpt_default_rules)
                                    val rawExcerpt = apiResp.citationExcerpt?.trim().orEmpty()
                                    val excerptHasBengali = rawExcerpt.any { it in '\u0980'..'\u09FF' }
                                    val excerptLanguageMatches =
                                        rawExcerpt.isNotBlank() && ((isBengali && excerptHasBengali) || (!isBengali && !excerptHasBengali))
                                    val cleanExcerpt =
                                        if (excerptLanguageMatches) {
                                            stripRawEnumTokens(rawExcerpt).ifBlank { defaultExcerpt }
                                        } else {
                                            defaultExcerpt
                                        }
                                    activeCitation =
                                        PolicyCitation(
                                            policyTitleBn = friendlyTitle,
                                            policyTitleEn = friendlyTitle,
                                            sectionTextBn = cleanExcerpt,
                                            sectionTextEn = cleanExcerpt,
                                            version = friendlyTitle,
                                        )
                                    followUpSuggestions = apiResp.suggestedFollowUps
                                    isThinking = false
                                    responseAnswer = apiResp.answer
                                } catch (e: Exception) {
                                    usedBedrockModel = "offline-fallback"
                                    isRuleBasedFallback = true
                                    answerSourceLabel = null
                                    isThinking = false
                                    assistantError =
                                        context.getString(R.string.assistant_server_unavailable, e.javaClass.simpleName)

                                    // Deterministic Fail-Closed local fallback (P6: never fabricate numbers)
                                    val hi = decisionData?.heatIndexC
                                    val aqi = decisionData?.aqi
                                    val decision = decisionData?.decision ?: "UNKNOWN"
                                    val friendlyTitle = context.getString(R.string.policy_title_friendly)

                                    if (decision == "UNKNOWN" || hi == null || aqi == null) {
                                        responseAnswer = context.getString(R.string.assistant_fallback_no_data)
                                        val noDataExcerpt = context.getString(R.string.policy_excerpt_offline_no_data)
                                        activeCitation =
                                            PolicyCitation(
                                                policyTitleBn = friendlyTitle,
                                                policyTitleEn = friendlyTitle,
                                                sectionTextBn = noDataExcerpt,
                                                sectionTextEn = noDataExcerpt,
                                                version = friendlyTitle,
                                            )
                                    } else {
                                        verifiedHi = hi
                                        verifiedAqi = aqi
                                        verifiedDecision = decision
                                        val localizedDecision = getLocalizedDecisionAction(decision, isBengali, context)
                                        responseAnswer =
                                            context.getString(
                                                R.string.assistant_fallback_verified,
                                                hi.toString(),
                                                aqi.toString(),
                                                localizedDecision,
                                            )
                                        val offlineExcerpt = context.getString(R.string.policy_excerpt_offline_rules)
                                        activeCitation =
                                            PolicyCitation(
                                                policyTitleBn = friendlyTitle,
                                                policyTitleEn = friendlyTitle,
                                                sectionTextBn = offlineExcerpt,
                                                sectionTextEn = offlineExcerpt,
                                                version = friendlyTitle,
                                            )
                                    }
                                } finally {
                                    isThinking = false
                                }
                            }
                        }
                    },
                    modifier =
                        Modifier
                            .size(50.dp)
                            .background(SafeGreen, RoundedCornerShape(25.dp)),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send",
                        tint = Color.White,
                    )
                }
            }
        }
    }

    // Modal Dialog: Policy Citation Details
    if (showCitationDialog && activeCitation != null) {
        AlertDialog(
            onDismissRequest = { showCitationDialog = false },
            title = {
                Text(
                    text = stringResource(R.string.policy_title_friendly),
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = SafeGreen,
                )
            },
            text = {
                Column {
                    Text(
                        text = stringResource(R.string.assistant_citation_excerpt_label),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        color = TextSecondary,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = if (isBengali) activeCitation!!.sectionTextBn else activeCitation!!.sectionTextEn,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        color = TextPrimary,
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { showCitationDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = SafeGreen),
                ) {
                    Text(stringResource(R.string.btn_close_ok))
                }
            },
        )
    }
}

data class PolicyCitation(
    val policyTitleBn: String,
    val policyTitleEn: String,
    val sectionTextBn: String,
    val sectionTextEn: String,
    val version: String,
)
