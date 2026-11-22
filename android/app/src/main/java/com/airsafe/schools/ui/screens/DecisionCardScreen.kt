package com.airsafe.schools.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.NoteAdd
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.airsafe.schools.BuildConfig
import com.airsafe.schools.R
import com.airsafe.schools.data.model.DecisionUiState
import com.airsafe.schools.data.model.UserRole
import com.airsafe.schools.data.model.calculateDataAgeMinutes
import com.airsafe.schools.data.model.formatElapsedMinutes
import com.airsafe.schools.data.model.formatLocalizedOverrideText
import com.airsafe.schools.data.model.formatLocalizedReason
import com.airsafe.schools.data.model.getLocalizedActivityName
import com.airsafe.schools.data.model.getLocalizedDecisionTitle
import com.airsafe.schools.data.model.getLocalizedGuidanceText
import com.airsafe.schools.data.model.getLocalizedHealthNote
import com.airsafe.schools.data.model.getLocalizedIntensity
import com.airsafe.schools.ui.screens.components.ActivityNoteDialog
import com.airsafe.schools.ui.screens.components.DecisionCardSkeleton
import com.airsafe.schools.ui.screens.components.ExplanationDialog
import com.airsafe.schools.ui.screens.components.OverrideDialog
import com.airsafe.schools.ui.screens.components.PolicyOutcome
import com.airsafe.schools.ui.screens.components.RoutineScheduleSection
import com.airsafe.schools.ui.screens.components.RoutineUpdateDialog
import com.airsafe.schools.ui.screens.components.resolveOutcome
import com.airsafe.schools.ui.theme.CautionText
import com.airsafe.schools.ui.theme.CautionYellow
import com.airsafe.schools.ui.theme.DangerRed
import com.airsafe.schools.ui.theme.DangerRedBackground
import com.airsafe.schools.ui.theme.ModifyOrange
import com.airsafe.schools.ui.theme.SafeGreen
import com.airsafe.schools.ui.theme.TextPrimary
import com.airsafe.schools.ui.theme.TextSecondary
import com.airsafe.schools.ui.theme.UnknownGray
import com.airsafe.schools.ui.viewmodel.DecisionViewModel

/**
 * Step 4.1: Streamlined Action-First Home Screen
 * Matches the proposed reference design:
 * - Single task: Can activities be held outside, and why?
 * - Single compact status line (data age, modeled/measured, online/offline) replacing 3 banners.
 * - Hero decision card in plain language, single-line reason with numbers, and "Why? See Details" button.
 * - Upcoming activities in a clean list, 1 line each.
 * - Coordinator override as a discreet link inside/below the card.
 * - No raw enums or code-names.
 * - Release builds exclude "Test Alert".
 * - Fully accessible on 200% font scale.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DecisionCardScreen(
    viewModel: DecisionViewModel,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsState()
    val isBengali by viewModel.isBengali.collectAsState()
    val isOffline by viewModel.isOffline.collectAsState()
    val userSession by viewModel.userSession.collectAsState()
    val activeOverride by viewModel.activeOverride.collectAsState()
    val activityNotes by viewModel.activityNotes.collectAsState()
    val actionError by viewModel.actionError.collectAsState()
    val scrollState = rememberScrollState()
    val context = LocalContext.current

    var showNoteDialog by remember { mutableStateOf(false) }
    var showOverrideDialog by remember { mutableStateOf(false) }
    var showRoutineDialog by remember { mutableStateOf(false) }
    var showExplanationDialog by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // 1. Error Banner (Scoped strictly to Home action errors)
            if (actionError != null) {
                Card(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = actionError ?: "",
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = { viewModel.clearActionError() }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = stringResource(R.string.dialog_dismiss),
                                tint = MaterialTheme.colorScheme.onErrorContainer,
                            )
                        }
                    }
                }
            }

            // 2. Clean Minimal Header: School Name (Left) & User Info + Actions (Right)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier =
                        Modifier
                            .weight(1f)
                            .padding(end = 8.dp),
                ) {
                    val defaultSchool = stringResource(R.string.default_school_name)
                    val schoolName =
                        if (userSession != null) {
                            if (isBengali) (userSession?.schoolNameBn ?: defaultSchool) else (userSession?.schoolNameEn ?: defaultSchool)
                        } else {
                            defaultSchool
                        }
                    Text(
                        text = schoolName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = SafeGreen,
                        maxLines = 1,
                    )
                    val roleCoordinatorStr = stringResource(R.string.role_coordinator)
                    val roleTeacherStr = stringResource(R.string.role_teacher)
                    val portalTitleStr = stringResource(R.string.portal_title)
                    Text(
                        text =
                            if (userSession != null) {
                                val roleStr =
                                    if (userSession?.role == UserRole.COORDINATOR) {
                                        roleCoordinatorStr
                                    } else {
                                        roleTeacherStr
                                    }
                                val cleanName =
                                    if (isBengali) {
                                        userSession!!.nameBn
                                    } else {
                                        userSession!!.nameEn
                                            .replace("Assistant Teacher ", "")
                                            .replace("Principal ", "")
                                            .replace("Teacher ", "")
                                    }
                                "$cleanName • $roleStr"
                            } else {
                                portalTitleStr
                            },
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        maxLines = 1,
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    // Language Toggle
                    Surface(
                        onClick = { viewModel.toggleLanguage() },
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        modifier =
                            Modifier
                                .defaultMinSize(minWidth = 48.dp, minHeight = 36.dp),
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.lang_toggle_label),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = SafeGreen,
                            )
                        }
                    }

                    // Logout
                    IconButton(
                        onClick = { viewModel.logout() },
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                            contentDescription = stringResource(R.string.btn_logout),
                            tint = DangerRed,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 3. Compact Status Line: Replaces all 3 banners
            val successData = (uiState as? DecisionUiState.Success)?.data
            val rawMins = calculateDataAgeMinutes(successData?.lastUpdated, successData?.dataAgeMinutes)
            val freshnessStr = formatElapsedMinutes(rawMins, isBengali, context)
            val isModeled = successData?.isModeled != false
            val modeledStr =
                if (isModeled) {
                    stringResource(R.string.status_modeled_data)
                } else {
                    stringResource(R.string.status_measured)
                }
            val onlineStr =
                if (isOffline) {
                    stringResource(R.string.status_offline)
                } else {
                    stringResource(R.string.status_online)
                }
            val onlineColor = if (isOffline) CautionText else SafeGreen
            val isLargeFont = LocalDensity.current.fontScale > 1.3f

            Surface(
                shape = RoundedCornerShape(10.dp),
                color = Color(0xFFF3F4F6),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
            ) {
                if (isLargeFont) {
                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.label_data_freshness_format, freshnessStr, modeledStr),
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                            fontWeight = FontWeight.Medium,
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier =
                                    Modifier
                                        .size(8.dp)
                                        .background(onlineColor, CircleShape),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = onlineStr,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = onlineColor,
                                maxLines = 1,
                                softWrap = false,
                            )
                        }
                    }
                } else {
                    FlowRow(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.label_data_freshness_format, freshnessStr, modeledStr),
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                            fontWeight = FontWeight.Medium,
                        )

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier =
                                    Modifier
                                        .size(8.dp)
                                        .background(onlineColor, CircleShape),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = onlineStr,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = onlineColor,
                                maxLines = 1,
                                softWrap = false,
                            )
                        }
                    }
                }
            }

            // Active Coordinator Override Notice (if applicable)
            if (activeOverride != null) {
                val formattedOverride = formatLocalizedOverrideText(activeOverride ?: "", isBengali, context)
                Card(
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = DangerRedBackground),
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.Security, contentDescription = null, tint = DangerRed, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.banner_override_active_format, formattedOverride),
                            color = DangerRed,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }

            // 4. Main Body: Loading Skeleton or Hero Decision Card
            when (val state = uiState) {
                is DecisionUiState.Loading -> {
                    DecisionCardSkeleton(isBengali = isBengali)
                }
                is DecisionUiState.Success -> {
                    val safeData = state.data.sanitized()
                    val outcome = resolveOutcome(safeData.decision, safeData.status)

                    val statusColor =
                        when (outcome) {
                            PolicyOutcome.MOVE_INDOORS -> DangerRed
                            PolicyOutcome.MODIFY -> ModifyOrange
                            PolicyOutcome.PROCEED_WITH_PRECAUTIONS -> CautionYellow
                            PolicyOutcome.PROCEED -> SafeGreen
                            PolicyOutcome.UNKNOWN -> UnknownGray
                        }

                    val icon =
                        when (outcome) {
                            PolicyOutcome.MOVE_INDOORS -> Icons.Filled.Cancel
                            PolicyOutcome.MODIFY -> Icons.Filled.Warning
                            PolicyOutcome.PROCEED_WITH_PRECAUTIONS -> Icons.Filled.Warning
                            PolicyOutcome.PROCEED -> Icons.Filled.CheckCircle
                            PolicyOutcome.UNKNOWN -> Icons.AutoMirrored.Filled.HelpOutline
                        }

                    val titleText = getLocalizedDecisionTitle(safeData.decision, isBengali, context)
                    val guidanceText = getLocalizedGuidanceText(safeData.decision, safeData.maxDurationMinutes, isBengali, context)

                    val hasScheduledAct =
                        (!safeData.currentActivityBn.isNullOrBlank() && safeData.currentActivityBn != "তথ্য নেই") ||
                            (
                                !safeData.currentActivityEn.isNullOrBlank() &&
                                    safeData.currentActivityEn != "No routine scheduled" &&
                                    safeData.currentActivityEn != "No data available"
                            )
                    val currActIntensity = getLocalizedIntensity(safeData.routineIntensity, context)
                    val currentActivityHeader =
                        if (hasScheduledAct) {
                            val localizedCurrAct =
                                getLocalizedActivityName(
                                    nameBn = safeData.currentActivityBn,
                                    nameEn = safeData.currentActivityEn,
                                    isBengali = isBengali,
                                    context = context,
                                )
                            stringResource(R.string.label_now_prefix, "$localizedCurrAct ($currActIntensity)")
                        } else {
                            stringResource(R.string.label_now_outdoor_default)
                        }

                    val rawReason =
                        if (!isBengali && !safeData.primaryReasonEn.isNullOrBlank()) {
                            safeData.primaryReasonEn
                        } else {
                            safeData.primaryReason
                        }
                    val primaryReasonText =
                        formatLocalizedReason(
                            rawReason = rawReason,
                            decision = safeData.decision,
                            aqi = safeData.aqi,
                            heatIndexC = safeData.heatIndexC,
                            isBengali = isBengali,
                            context = context,
                        )

                    // Hero Decision Card
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = BorderStroke(1.5.dp, statusColor.copy(alpha = 0.5f)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .semantics(mergeDescendants = false) {
                                    contentDescription = "$titleText. $guidanceText. $primaryReasonText"
                                },
                    ) {
                        Column(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(18.dp),
                        ) {
                            // Current Activity Caption
                            Text(
                                text = currentActivityHeader,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = TextSecondary,
                                maxLines = 1,
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            // Large Decision Title
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    tint = statusColor,
                                    modifier = Modifier.size(28.dp),
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = titleText,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = statusColor,
                                    modifier = Modifier.testTag("primary_decision_text"),
                                )
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            // Action Guidance Subtitle
                            Text(
                                text = guidanceText,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Normal,
                                color = TextPrimary,
                                softWrap = true,
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            // Single Line Reason with Numbers
                            Text(
                                text = primaryReasonText,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                color = TextSecondary,
                                softWrap = true,
                            )

                            Spacer(modifier = Modifier.height(14.dp))

                            // "Why? See Details" Button + Discreet Coordinator Override Link
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Button(
                                    onClick = { showExplanationDialog = true },
                                    colors = ButtonDefaults.buttonColors(containerColor = SafeGreen),
                                    shape = RoundedCornerShape(20.dp),
                                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
                                ) {
                                    Text(
                                        text = stringResource(R.string.btn_why_details),
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                    )
                                }

                                if (userSession?.role == UserRole.COORDINATOR) {
                                    Text(
                                        text = stringResource(R.string.link_override),
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = ModifyOrange,
                                        modifier =
                                            Modifier
                                                .clickable { showOverrideDialog = true }
                                                .padding(8.dp),
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // 5. Upcoming Activities (Clean single-line schedule items)
                    val activities = safeData.activities ?: emptyList()
                    val upcoming = activities.filter { it.isCurrent != true }
                    if (upcoming.isNotEmpty()) {
                        Text(
                            text = stringResource(R.string.header_upcoming_activities),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        RoutineScheduleSection(
                            activities = upcoming,
                            isBengali = isBengali,
                        )
                    }

                    // 6. Teacher Context Notes (clean list if present)
                    if (activityNotes.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = stringResource(R.string.header_active_health_notes),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextSecondary,
                            modifier = Modifier.align(Alignment.Start),
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        activityNotes.forEach { note ->
                            val localizedNote = getLocalizedHealthNote(note, isBengali, context)
                            Text(
                                text = "📌 $localizedNote",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextPrimary,
                                modifier = Modifier.align(Alignment.Start),
                            )
                        }
                    }

                    // Teacher Context Note Action
                    if (userSession?.role == UserRole.TEACHER) {
                        Spacer(modifier = Modifier.height(8.dp))
                        TextButton(
                            onClick = { showNoteDialog = true },
                            enabled = !isOffline,
                            modifier = Modifier.align(Alignment.Start),
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.NoteAdd,
                                contentDescription = null,
                                tint = SafeGreen,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.btn_add_health_note),
                                style = MaterialTheme.typography.labelMedium,
                                color = SafeGreen,
                            )
                        }
                    }

                    // Coordinator Routine Setup Action
                    if (userSession?.role == UserRole.COORDINATOR) {
                        Spacer(modifier = Modifier.height(8.dp))
                        TextButton(
                            onClick = { showRoutineDialog = true },
                            enabled = !isOffline,
                            modifier = Modifier.align(Alignment.Start),
                        ) {
                            Icon(
                                imageVector = Icons.Default.DateRange,
                                contentDescription = null,
                                tint = SafeGreen,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.btn_manage_routine),
                                style = MaterialTheme.typography.labelMedium,
                                color = SafeGreen,
                            )
                        }
                    }

                    // 7. Test Alert (Removed in Release builds; kept only in Debug with clear TEST ONLY label)
                    if (BuildConfig.DEBUG) {
                        Spacer(modifier = Modifier.height(20.dp))
                        OutlinedButton(
                            onClick = { viewModel.triggerSimulationDeterioration() },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = DangerRed),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                        ) {
                            Text(
                                text = "[DEBUG TEST ONLY] Simulate Deterioration Alert",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
                is DecisionUiState.Error -> {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                text = if (state.message.isBlank()) stringResource(R.string.data_not_found) else state.message,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(onClick = { viewModel.loadCurrentDecision() }) {
                                Text(stringResource(R.string.btn_retry))
                            }
                        }
                    }
                }
            }
        }
    }

    // Dialog: Explanation & Sensor Details ("Why? See Details")
    if (showExplanationDialog) {
        val curData = (uiState as? DecisionUiState.Success)?.data
        ExplanationDialog(
            safeData = curData,
            isBengali = isBengali,
            onDismiss = { showExplanationDialog = false },
        )
    }

    // Dialog: Teacher Note
    if (showNoteDialog) {
        val curActName =
            (uiState as? DecisionUiState.Success)?.data?.let {
                getLocalizedActivityName(it.currentActivityBn, it.currentActivityEn, isBengali, context)
            } ?: stringResource(R.string.activity_outdoor_default)
        ActivityNoteDialog(
            currentActName = curActName,
            isBengali = isBengali,
            onSaveNote = { note -> viewModel.addActivityNote(note) },
            onDismiss = { showNoteDialog = false },
        )
    }

    // Dialog: Coordinator Override
    if (showOverrideDialog) {
        val currentDec = (uiState as? DecisionUiState.Success)?.data?.decision ?: "UNKNOWN"
        OverrideDialog(
            currentDec = currentDec,
            isBengali = isBengali,
            onApplyOverride = { dec, reason -> viewModel.applyCoordinatorOverride(dec, reason) },
            onDismiss = { showOverrideDialog = false },
        )
    }

    // Dialog: Coordinator Routine
    if (showRoutineDialog) {
        val curData = (uiState as? DecisionUiState.Success)?.data
        val initialName = curData?.let { getLocalizedActivityName(it.currentActivityBn, it.currentActivityEn, isBengali, context) } ?: ""
        val initialNextName = curData?.let { getLocalizedActivityName(it.nextActivityBn, it.nextActivityEn, isBengali, context) } ?: ""
        RoutineUpdateDialog(
            initialActivityName = initialName,
            initialNextActivityName = initialNextName,
            isBengali = isBengali,
            onSaveRoutine = { name, nextName, intensity, classGrade, duration, justification ->
                viewModel.updateRoutineActivity(
                    activityNameBn = name,
                    activityNameEn = name,
                    intensity = intensity,
                    durationMinutes = duration,
                    targetClass = classGrade,
                    nextActivityNameBn = nextName,
                    nextActivityNameEn = nextName,
                    justification = justification,
                )
            },
            onDismiss = { showRoutineDialog = false },
        )
    }
}
