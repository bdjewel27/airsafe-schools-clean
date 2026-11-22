package com.airsafe.schools.ui.screens.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.airsafe.schools.R
import com.airsafe.schools.data.model.DecisionResponse
import com.airsafe.schools.ui.theme.DangerRed
import com.airsafe.schools.ui.theme.SafeGreen
import com.airsafe.schools.ui.theme.SafeGreenBackground
import com.airsafe.schools.ui.theme.TextPrimary
import com.airsafe.schools.ui.theme.TextSecondary

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

@Composable
fun ExplanationDialog(
    safeData: DecisionResponse?,
    isBengali: Boolean,
    onDismiss: () -> Unit,
) {
    val baseContext = LocalContext.current
    val localizedContext =
        remember(isBengali, baseContext) {
            val locale = java.util.Locale(if (isBengali) "bn" else "en")
            val cfg =
                android.content.res.Configuration(baseContext.resources.configuration).apply {
                    setLocale(locale)
                }
            baseContext.createConfigurationContext(cfg)
        }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = localizedContext.getString(R.string.dialog_explanation_alert_title),
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp,
                color = DangerRed,
            )
        },
        text = {
            Column {
                Text(
                    text = localizedContext.getString(R.string.dialog_current_status_decision),
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = TextPrimary,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = com.airsafe.schools.data.model.getLocalizedDecisionAction(safeData?.decision, isBengali, localizedContext),
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 16.sp,
                    color = DangerRed,
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = localizedContext.getString(R.string.dialog_primary_reason),
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = TextPrimary,
                )
                Spacer(modifier = Modifier.height(4.dp))
                val rawPrimary =
                    if (!isBengali && !safeData?.primaryReasonEn.isNullOrBlank()) {
                        safeData?.primaryReasonEn
                    } else {
                        safeData?.primaryReason
                    }
                Text(
                    text =
                        com.airsafe.schools.data.model.formatLocalizedReason(
                            rawReason = rawPrimary,
                            decision = safeData?.decision,
                            aqi = safeData?.aqi,
                            heatIndexC = safeData?.heatIndexC,
                            isBengali = isBengali,
                            context = localizedContext,
                        ),
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    color = TextSecondary,
                )
                if (!safeData?.reasons.isNullOrEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = localizedContext.getString(R.string.dialog_policy_rules_reasons),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                        color = TextPrimary,
                    )
                    safeData?.reasons?.forEach { r ->
                        val localizedReason =
                            com.airsafe.schools.data.model.formatLocalizedReason(
                                rawReason = r,
                                decision = safeData.decision,
                                aqi = safeData.aqi,
                                heatIndexC = safeData.heatIndexC,
                                isBengali = isBengali,
                                context = localizedContext,
                            )
                        Text(
                            text = "• $localizedReason",
                            fontSize = 12.sp,
                            color = TextSecondary,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    val notFoundStr = localizedContext.getString(R.string.data_not_found)
                    val hiStr = safeData?.heatIndexC?.toString()?.toBengaliDigits(isBengali) ?: notFoundStr
                    val aqiStr = safeData?.aqi?.toString()?.toBengaliDigits(isBengali) ?: notFoundStr
                    Text(
                        text =
                            if (safeData?.heatIndexC != null) {
                                localizedContext.getString(
                                    R.string.dialog_heat_index_format,
                                    hiStr,
                                )
                            } else {
                                "${localizedContext.getString(R.string.metric_heat_index)}: $notFoundStr"
                            },
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary,
                    )
                    Text(
                        text =
                            if (safeData?.aqi != null) {
                                localizedContext.getString(R.string.dialog_aqi_format, aqiStr)
                            } else {
                                localizedContext.getString(R.string.dialog_aqi_format, notFoundStr)
                            },
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = SafeGreen),
            ) {
                Text(localizedContext.getString(R.string.dialog_close), color = Color.White)
            }
        },
    )
}

@Composable
fun PolicyDialog(
    activeDec: String,
    isBengali: Boolean,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Gavel, contentDescription = null, tint = SafeGreen, modifier = Modifier.size(22.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.dialog_policy_rules_title),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    text = stringResource(R.string.dialog_policy_effective_date),
                    fontSize = 11.sp,
                    color = TextSecondary,
                )
                Spacer(modifier = Modifier.height(10.dp))

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = SafeGreenBackground,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = stringResource(R.string.dialog_policy_active_decision),
                            fontSize = 11.sp,
                            color = TextSecondary,
                        )
                        Text(
                            text = "$activeDec (${stringResource(R.string.dialog_policy_governed_tag)})",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = SafeGreen,
                        )
                    }
                }

                Text(
                    text = stringResource(R.string.dialog_governing_safety_rules),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                )
                Spacer(modifier = Modifier.height(6.dp))

                val rules =
                    listOf(
                        R.string.policy_rule_1,
                        R.string.policy_rule_2,
                        R.string.policy_rule_3,
                        R.string.policy_rule_4,
                        R.string.policy_rule_5,
                        R.string.policy_rule_6,
                    )

                rules.forEach { r ->
                    Text(
                        text = "• " + stringResource(r),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextPrimary,
                        modifier = Modifier.padding(vertical = 2.dp),
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "API Endpoint: /policies/current",
                    fontSize = 10.sp,
                    color = TextSecondary,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = SafeGreen),
            ) {
                Text(stringResource(R.string.dialog_dismiss), color = Color.White)
            }
        },
    )
}

@Composable
fun ActivityNoteDialog(
    currentActName: String,
    isBengali: Boolean,
    onSaveNote: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var noteInput by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_add_note_title)) },
        text = {
            Column {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                ) {
                    Text(
                        text = stringResource(R.string.dialog_target_activity, currentActName),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = SafeGreen,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
                OutlinedTextField(
                    value = noteInput,
                    onValueChange = { noteInput = it },
                    label = { Text(stringResource(R.string.dialog_note_description)) },
                    placeholder = { Text(stringResource(R.string.dialog_note_placeholder)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                if (noteInput.isNotBlank()) {
                    onSaveNote(noteInput)
                    onDismiss()
                }
            }) {
                Text(stringResource(R.string.dialog_note_submit))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.dialog_note_cancel))
            }
        },
    )
}

@Composable
fun OverrideDialog(
    currentDec: String,
    isBengali: Boolean,
    onApplyOverride: (decision: String, reason: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var selectedOverrideDecision by remember { mutableStateOf("MOVE_INDOORS") }
    var overrideReason by remember { mutableStateOf("") }
    var overrideValidationError by remember { mutableStateOf<String?>(null) }
    val reasonErrorMsg = stringResource(R.string.dialog_override_reason_error)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_override_title_justification)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.dialog_override_description),
                    fontSize = 12.sp,
                    color = TextSecondary,
                )
                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = stringResource(R.string.dialog_override_target_decision),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(4.dp))
                val allowedOptions =
                    when (currentDec) {
                        "MOVE_INDOORS" -> listOf("MOVE_INDOORS")
                        "MODIFY" -> listOf("MOVE_INDOORS", "MODIFY")
                        else -> listOf("MOVE_INDOORS", "MODIFY", "PRECAUTIONS")
                    }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    allowedOptions.forEach { opt ->
                        val chipLabel =
                            when (opt) {
                                "MOVE_INDOORS" -> stringResource(R.string.decision_action_move_indoors)
                                "MODIFY" -> stringResource(R.string.decision_action_modify)
                                "PRECAUTIONS", "PROCEED_WITH_PRECAUTIONS" -> stringResource(R.string.decision_action_precautions)
                                "PROCEED" -> stringResource(R.string.decision_action_proceed)
                                else -> stringResource(R.string.decision_action_unknown)
                            }
                        FilterChip(
                            selected = selectedOverrideDecision.contains(opt),
                            onClick = { selectedOverrideDecision = opt },
                            label = { Text(chipLabel, fontSize = 12.sp, maxLines = 1, softWrap = false) },
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = overrideReason,
                    onValueChange = {
                        overrideReason = it
                        if (it.trim().length >= 5) overrideValidationError = null
                    },
                    label = { Text(stringResource(R.string.dialog_override_reason)) },
                    placeholder = { Text(stringResource(R.string.dialog_override_reason_placeholder)) },
                    isError = overrideValidationError != null,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (overrideValidationError != null) {
                    Text(
                        text = overrideValidationError!!,
                        color = DangerRed,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (overrideReason.trim().length < 5) {
                        overrideValidationError = reasonErrorMsg
                    } else {
                        val decisionText =
                            when (selectedOverrideDecision) {
                                "MODIFY" -> "MODIFY"
                                "PRECAUTIONS" -> "PROCEED_WITH_PRECAUTIONS"
                                else -> "MOVE_INDOORS"
                            }
                        onApplyOverride(decisionText, overrideReason)
                        onDismiss()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = DangerRed),
            ) {
                Text(stringResource(R.string.btn_override_apply), color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.btn_cancel))
            }
        },
    )
}

fun isPeOrSportsActivity(name: String): Boolean {
    val n = name.trim().lowercase()
    val words = n.split(Regex("[^a-zA-Z0-9\u0980-\u09FF]+")).filter { it.isNotBlank() }
    val peTokens = setOf("pe", "পিই", "pt", "পিটি")
    val hasPeToken = words.any { it in peTokens }
    val keywords =
        listOf(
            "শারীরিক", "physical", "football", "ফুটবল", "sports",
            "খেলা", "দৌড়", "দৌড়", "cricket", "ক্রিকেট", "athletics",
        )
    return hasPeToken || keywords.any { n.contains(it) }
}

@Composable
fun RoutineUpdateDialog(
    initialActivityName: String,
    initialNextActivityName: String,
    isBengali: Boolean,
    onSaveRoutine: (name: String, nextName: String, intensity: String, classGrade: String, duration: Int, justification: String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var routineActivityName by remember { mutableStateOf(initialActivityName) }
    var routineNextActivityName by remember { mutableStateOf(initialNextActivityName) }
    val isInitialPe = isPeOrSportsActivity(initialActivityName)
    var selectedIntensity by remember { mutableStateOf(if (isInitialPe) "VIGOROUS" else "MODERATE") }
    var routineTargetClass by remember { mutableStateOf("Class 5") }
    var routineDurationMinutes by remember { mutableStateOf("45") }
    var peJustification by remember { mutableStateOf("") }
    var peValidationError by remember { mutableStateOf<String?>(null) }
    var showPeDowngradeDialog by remember { mutableStateOf(false) }
    var downgradeDialogReason by remember { mutableStateOf("") }
    var downgradeDialogError by remember { mutableStateOf<String?>(null) }

    val isCurrentPe = isPeOrSportsActivity(routineActivityName)
    val peReasonErrorMsg = stringResource(R.string.dialog_routine_pe_error)

    if (showPeDowngradeDialog) {
        AlertDialog(
            onDismissRequest = {
                showPeDowngradeDialog = false
            },
            title = {
                Text(
                    text = stringResource(R.string.pe_downgrade_dialog_title),
                    fontWeight = FontWeight.Bold,
                    color = DangerRed,
                )
            },
            text = {
                Column {
                    Text(
                        text = stringResource(R.string.pe_downgrade_dialog_warning),
                        fontSize = 12.sp,
                        color = TextPrimary,
                        lineHeight = 17.sp,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = downgradeDialogReason,
                        onValueChange = {
                            downgradeDialogReason = it
                            if (it.trim().length >= 5) downgradeDialogError = null
                        },
                        label = { Text(stringResource(R.string.dialog_routine_light_reason_label), fontSize = 11.sp) },
                        placeholder = { Text(stringResource(R.string.dialog_routine_light_reason_placeholder), fontSize = 11.sp) },
                        isError = downgradeDialogError != null,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (downgradeDialogError != null) {
                        Text(
                            text = downgradeDialogError!!,
                            color = DangerRed,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (downgradeDialogReason.trim().length < 5) {
                            downgradeDialogError = peReasonErrorMsg
                        } else {
                            peJustification = downgradeDialogReason.trim()
                            peValidationError = null
                            selectedIntensity = "LIGHT"
                            showPeDowngradeDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DangerRed),
                ) {
                    Text(stringResource(R.string.pe_downgrade_dialog_confirm), color = Color.White)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showPeDowngradeDialog = false
                    },
                ) {
                    Text(stringResource(R.string.btn_cancel))
                }
            },
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.btn_manage_routine)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    text = stringResource(R.string.dialog_routine_desc_config),
                    fontSize = 12.sp,
                    color = TextSecondary,
                )
                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = stringResource(R.string.current_activity) + ":",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                )
                OutlinedTextField(
                    value = routineActivityName,
                    onValueChange = { routineActivityName = it },
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = stringResource(R.string.next_activity) + ":",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                )
                OutlinedTextField(
                    value = routineNextActivityName,
                    onValueChange = { routineNextActivityName = it },
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = stringResource(R.string.dialog_routine_intensity_label),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    listOf("LIGHT", "MODERATE", "VIGOROUS").forEach { intensity ->
                        val chipText =
                            when (intensity) {
                                "LIGHT" -> stringResource(R.string.intensity_light)
                                "MODERATE" -> stringResource(R.string.intensity_moderate)
                                else -> stringResource(R.string.intensity_vigorous)
                            }
                        FilterChip(
                            selected = selectedIntensity == intensity,
                            onClick = {
                                if (intensity == "LIGHT" && isCurrentPe && selectedIntensity != "LIGHT") {
                                    downgradeDialogReason = peJustification
                                    downgradeDialogError = null
                                    showPeDowngradeDialog = true
                                } else {
                                    selectedIntensity = intensity
                                }
                            },
                            label = { Text(chipText, fontSize = 11.sp) },
                        )
                    }
                }

                if (isCurrentPe && selectedIntensity == "LIGHT") {
                    Spacer(modifier = Modifier.height(8.dp))
                    Card(
                        colors = CardDefaults.cardColors(containerColor = DangerRed.copy(alpha = 0.08f)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = stringResource(R.string.dialog_routine_pe_warning),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = DangerRed,
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            OutlinedTextField(
                                value = peJustification,
                                onValueChange = {
                                    peJustification = it
                                    if (it.trim().length >= 5) peValidationError = null
                                },
                                label = { Text(stringResource(R.string.dialog_routine_light_reason_label), fontSize = 11.sp) },
                                placeholder = { Text(stringResource(R.string.dialog_routine_light_reason_placeholder), fontSize = 11.sp) },
                                isError = peValidationError != null,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            if (peValidationError != null) {
                                Text(
                                    text = peValidationError!!,
                                    color = DangerRed,
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(top = 4.dp),
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = stringResource(R.string.dialog_routine_target_class_duration),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = routineTargetClass,
                        onValueChange = { routineTargetClass = it },
                        modifier = Modifier.weight(1f),
                        label = { Text(stringResource(R.string.dialog_routine_class_label)) },
                    )
                    OutlinedTextField(
                        value = routineDurationMinutes,
                        onValueChange = { routineDurationMinutes = it },
                        modifier = Modifier.weight(1f),
                        label = { Text(stringResource(R.string.dialog_routine_duration_label)) },
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val duration = routineDurationMinutes.toIntOrNull() ?: 45
                    if (isCurrentPe && selectedIntensity == "LIGHT") {
                        if (peJustification.trim().length < 5) {
                            downgradeDialogReason = peJustification
                            downgradeDialogError = peReasonErrorMsg
                            showPeDowngradeDialog = true
                            return@Button
                        }
                    }
                    val justificationToSend = if (isCurrentPe && selectedIntensity == "LIGHT") peJustification.trim() else null
                    onSaveRoutine(
                        routineActivityName,
                        routineNextActivityName,
                        selectedIntensity,
                        routineTargetClass,
                        duration,
                        justificationToSend,
                    )
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = SafeGreen),
            ) {
                Text(stringResource(R.string.dialog_routine_save), color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.btn_cancel))
            }
        },
    )
}

@Composable
fun RoutineUpdateDialog(
    initialActivityName: String,
    initialNextActivityName: String,
    isBengali: Boolean,
    onSaveRoutine: (name: String, nextName: String, intensity: String, classGrade: String, duration: Int) -> Unit,
    onDismiss: () -> Unit,
) = RoutineUpdateDialog(
    initialActivityName = initialActivityName,
    initialNextActivityName = initialNextActivityName,
    isBengali = isBengali,
    onSaveRoutine = { name, nextName, intensity, classGrade, duration, _ ->
        onSaveRoutine(name, nextName, intensity, classGrade, duration)
    },
    onDismiss = onDismiss,
)

@Composable
fun MetricDetailDialog(
    metricType: String,
    safeData: DecisionResponse?,
    isBengali: Boolean,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            val titleRes =
                when (metricType) {
                    "HEAT_INDEX" -> R.string.dialog_metric_heat_index_rules
                    "AQI" -> R.string.dialog_metric_aqi_rules
                    else -> R.string.dialog_metric_source_reliability
                }
            Text(
                text = stringResource(titleRes),
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp,
                color = SafeGreen,
            )
        },
        text = {
            Column {
                when (metricType) {
                    "HEAT_INDEX" -> {
                        val notFoundStr = stringResource(R.string.data_not_found)
                        val hiVal = safeData?.heatIndexC?.toString()?.toBengaliDigits(isBengali) ?: notFoundStr
                        Text(
                            text =
                                if (safeData?.heatIndexC != null) {
                                    stringResource(
                                        R.string.dialog_metric_current_hi_format,
                                        hiVal,
                                    )
                                } else {
                                    "${stringResource(R.string.metric_heat_index)}: $notFoundStr"
                                },
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = TextPrimary,
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = stringResource(R.string.dialog_metric_hi_full_guidance),
                            fontSize = 12.sp,
                            lineHeight = 17.sp,
                            color = TextSecondary,
                        )
                    }
                    "AQI" -> {
                        val notFoundStr = stringResource(R.string.data_not_found)
                        val aqiVal = safeData?.aqi?.toString()?.toBengaliDigits(isBengali) ?: notFoundStr
                        val pmVal = safeData?.pm25Ugm3?.toString()?.toBengaliDigits(isBengali) ?: notFoundStr
                        Text(
                            text =
                                if (safeData?.aqi != null) {
                                    stringResource(
                                        R.string.dialog_metric_current_aqi_format,
                                        aqiVal,
                                        pmVal,
                                    )
                                } else {
                                    "AQI: $notFoundStr"
                                },
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = TextPrimary,
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = stringResource(R.string.dialog_metric_aqi_full_guidance),
                            fontSize = 12.sp,
                            lineHeight = 17.sp,
                            color = TextSecondary,
                        )
                    }
                    else -> {
                        val isModeled = safeData?.isModeled ?: true
                        val confidence =
                            safeData?.confidenceLevelPct?.toString()?.toBengaliDigits(isBengali)
                                ?: "94".toBengaliDigits(isBengali)
                        val sourceLabel = stringResource(if (isModeled) R.string.source_modeled_satellite else R.string.source_measured_iot)
                        Text(
                            text = stringResource(R.string.dialog_metric_source_format, sourceLabel, confidence),
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = TextPrimary,
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = stringResource(R.string.dialog_metric_source_policy_desc),
                            fontSize = 12.sp,
                            lineHeight = 17.sp,
                            color = TextSecondary,
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = SafeGreen),
            ) {
                Text(stringResource(R.string.dialog_close), color = Color.White)
            }
        },
    )
}
