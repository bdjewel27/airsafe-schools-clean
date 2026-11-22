package com.airsafe.schools.ui.screens

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.airsafe.schools.R
import com.airsafe.schools.data.model.formatLocalizedNumber
import com.airsafe.schools.data.model.getLocalizedDecisionAction
import com.airsafe.schools.data.model.toBengaliDigits
import com.airsafe.schools.ui.theme.CautionYellow
import com.airsafe.schools.ui.theme.DangerRed
import com.airsafe.schools.ui.theme.ModifyOrange
import com.airsafe.schools.ui.theme.SafeGreen
import com.airsafe.schools.ui.theme.SafeGreenBackground
import com.airsafe.schools.ui.theme.SurfaceCard
import com.airsafe.schools.ui.theme.TextPrimary
import com.airsafe.schools.ui.theme.TextSecondary
import com.airsafe.schools.ui.viewmodel.DecisionViewModel
import com.airsafe.schools.util.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun SchoolAnalyticsScreen(
    viewModel: DecisionViewModel,
    modifier: Modifier = Modifier,
) {
    val analytics by viewModel.analyticsData.collectAsState()
    val isBengali by viewModel.isBengali.collectAsState()
    val auditLogs by viewModel.overrideAuditLogs.collectAsState()
    val unifiedOverridesCount = maxOf(analytics.overridesCount, auditLogs.size)
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()
    var isExporting by remember { mutableStateOf(false) }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(16.dp),
    ) {
        // Screen Header
        Text(
            text = stringResource(R.string.analytics_title),
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
        )

        Text(
            text = stringResource(R.string.analytics_subtitle),
            fontSize = 13.sp,
            color = TextSecondary,
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Week Banner Card
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = SafeGreenBackground),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.DateRange, contentDescription = null, tint = SafeGreen)
                Spacer(modifier = Modifier.width(10.dp))
                val rawWeekLabel = if (isBengali) analytics.weekLabelBn else analytics.weekLabelEn
                val isModeledWeek =
                    analytics.measuredRatioPct == 0 ||
                        rawWeekLabel.contains("Modeled", ignoreCase = true) ||
                        rawWeekLabel.contains("মডেল", ignoreCase = true)
                val weekLabelText =
                    if (isModeledWeek) {
                        stringResource(R.string.status_modeled_data)
                    } else {
                        rawWeekLabel
                    }
                Text(
                    text = weekLabelText,
                    fontWeight = FontWeight.Bold,
                    color = SafeGreen,
                    fontSize = 14.sp,
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Core Metric: Lost Outdoor Minutes (Dynamic schedule/routine based)
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceCard),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.analytics_lost_time_title),
                    fontSize = 14.sp,
                    color = TextSecondary,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = formatLocalizedNumber(analytics.lostOutdoorMinutes, isBengali, 0),
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = DangerRed,
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.analytics_unit_minutes),
                        fontSize = 15.sp,
                        color = TextSecondary,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                }

                // Step 5.4: Conditional rendering for weekly comparison
                val prevLost = analytics.prevWeekLostMinutes
                if (prevLost != null) {
                    val diffMinutes = prevLost - analytics.lostOutdoorMinutes
                    val diffStr = formatLocalizedNumber(kotlin.math.abs(diffMinutes), isBengali, 0)
                    val diffText =
                        if (diffMinutes >= 0) {
                            stringResource(R.string.analytics_comparison_less, diffStr)
                        } else {
                            stringResource(R.string.analytics_comparison_more, diffStr)
                        }
                    val diffColor = if (diffMinutes >= 0) SafeGreen else ModifyOrange
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = diffText,
                        fontSize = 12.sp,
                        color = diffColor,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Secondary Metrics Grid
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            // Hours above threshold
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.weight(1f),
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = stringResource(R.string.analytics_above_threshold),
                        fontSize = 12.sp,
                        color = TextSecondary,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    val hoursFormatted = formatLocalizedNumber(analytics.hoursAboveThreshold, isBengali, 1)
                    Text(
                        text = "$hoursFormatted " + stringResource(R.string.analytics_unit_hours),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = CautionYellow,
                    )
                }
            }

            // Measured vs Modeled Ratio
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.weight(1f),
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = stringResource(R.string.analytics_measured_ratio),
                        fontSize = 12.sp,
                        color = TextSecondary,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    val ratioText =
                        if (analytics.measuredRatioPct == 0) {
                            stringResource(R.string.status_modeled_data)
                        } else {
                            val pctFormatted = formatLocalizedNumber(analytics.measuredRatioPct, isBengali, 0)
                            "$pctFormatted% " + stringResource(R.string.status_measured)
                        }
                    Text(
                        text = ratioText,
                        fontSize = if (analytics.measuredRatioPct == 0) 16.sp else 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (analytics.measuredRatioPct == 0) ModifyOrange else SafeGreen,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Decision Distribution Breakdown
        Text(
            text = stringResource(R.string.analytics_decision_breakdown),
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            color = TextPrimary,
        )

        Spacer(modifier = Modifier.height(10.dp))

        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceCard),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                AnalyticsRow(
                    label = stringResource(R.string.analytics_row_safe),
                    count = analytics.proceedCount,
                    color = SafeGreen,
                    isBengali = isBengali,
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = Color.LightGray.copy(alpha = 0.3f))
                AnalyticsRow(
                    label = stringResource(R.string.analytics_row_caution),
                    count = analytics.cautionCount,
                    color = CautionYellow,
                    isBengali = isBengali,
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = Color.LightGray.copy(alpha = 0.3f))
                AnalyticsRow(
                    label = stringResource(R.string.analytics_row_move_indoors),
                    count = analytics.moveIndoorsCount,
                    color = DangerRed,
                    isBengali = isBengali,
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = Color.LightGray.copy(alpha = 0.3f))
                AnalyticsRow(
                    label = stringResource(R.string.analytics_row_overrides),
                    count = unifiedOverridesCount,
                    color = Color.DarkGray,
                    isBengali = isBengali,
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // F-11: Immutable Coordinator Override Audit Trail
        Text(
            text = stringResource(R.string.audit_trail_title),
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            color = TextPrimary,
        )

        Spacer(modifier = Modifier.height(10.dp))

        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceCard),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                if (auditLogs.isEmpty()) {
                    Text(
                        text = stringResource(R.string.analytics_no_audit_records),
                        fontSize = 13.sp,
                        color = TextSecondary,
                    )
                } else {
                    auditLogs.forEachIndexed { index, item ->
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                val requestedAction = getLocalizedDecisionAction(item.requestedDecision, isBengali, context)
                                Text(
                                    text = requestedAction,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = if (item.requestedDecision == "MOVE_INDOORS") DangerRed else CautionYellow,
                                )
                                Text(
                                    text = (item.timestamp?.take(16)?.replace("T", " ") ?: "").toBengaliDigits(isBengali),
                                    fontSize = 11.sp,
                                    color = TextSecondary,
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = stringResource(R.string.analytics_reason_prefix, item.reason ?: "-"),
                                fontSize = 13.sp,
                                color = TextPrimary,
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text =
                                        stringResource(
                                            R.string.analytics_by_coordinator_prefix,
                                            item.coordinatorEmail ?: stringResource(R.string.role_coordinator),
                                        ),
                                    fontSize = 12.sp,
                                    color = TextSecondary,
                                )
                                if (item.relaxesFloor) {
                                    Text(
                                        text = stringResource(R.string.analytics_relaxes_floor_warning),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = DangerRed,
                                    )
                                }
                            }
                            if (!item.expiresAt.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text =
                                        stringResource(
                                            R.string.analytics_expires_prefix,
                                            (item.expiresAt?.take(16)?.replace("T", " ") ?: "").toBengaliDigits(isBengali),
                                        ),
                                    fontSize = 11.sp,
                                    color = TextSecondary,
                                )
                            }
                        }
                        if (index < auditLogs.size - 1) {
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 10.dp),
                                color = Color.LightGray.copy(alpha = 0.3f),
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // F-14: Official Printable PDF Report Generation & Export
        Button(
            onClick = {
                if (isExporting) return@Button
                isExporting = true
                val defaultSchoolName = context.getString(R.string.default_school_name)
                coroutineScope.launch {
                    val ok =
                        withContext(Dispatchers.IO) {
                            com.airsafe.schools.util.AirSafePdfReportGenerator.generateAndShareWeeklyReport(
                                context = context,
                                analytics = analytics,
                                schoolName = defaultSchoolName,
                                isBengali = isBengali,
                            )
                        }
                    if (!ok) {
                        Toast.makeText(context, context.getString(R.string.export_failed), Toast.LENGTH_SHORT).show()
                    }
                    isExporting = false
                }
            },
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(50.dp),
            shape = RoundedCornerShape(12.dp),
        ) {
            Icon(Icons.Default.Description, contentDescription = null, tint = Color.White)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.audit_export_pdf),
                fontWeight = FontWeight.Bold,
                color = Color.White,
                fontSize = 15.sp,
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // F-14: Share / Export Weekly Report (Android Share Sheet)
        Button(
            onClick = {
                val reportContent =
                    buildString {
                        appendLine(context.getString(R.string.analytics_share_header))
                        appendLine("---------------------------------------")
                        appendLine(
                            context.getString(
                                R.string.analytics_share_week,
                                if (isBengali) analytics.weekLabelBn else analytics.weekLabelEn,
                            ),
                        )
                        appendLine(
                            context.getString(
                                R.string.analytics_share_lost_mins,
                                formatLocalizedNumber(analytics.lostOutdoorMinutes, isBengali, 0),
                            ),
                        )
                        appendLine(
                            context.getString(
                                R.string.analytics_share_hours_above,
                                formatLocalizedNumber(analytics.hoursAboveThreshold, isBengali, 1),
                            ),
                        )
                        appendLine(
                            context.getString(
                                R.string.analytics_share_safe_sessions,
                                formatLocalizedNumber(analytics.proceedCount, isBengali, 0),
                            ),
                        )
                        appendLine(
                            context.getString(
                                R.string.analytics_share_move_indoors,
                                formatLocalizedNumber(analytics.moveIndoorsCount, isBengali, 0),
                            ),
                        )
                        appendLine(
                            context.getString(
                                R.string.analytics_share_overrides,
                                formatLocalizedNumber(unifiedOverridesCount, isBengali, 0),
                            ),
                        )
                        appendLine(
                            context.getString(
                                R.string.analytics_share_measured_ratio,
                                formatLocalizedNumber(analytics.measuredRatioPct, isBengali, 0),
                            ),
                        )
                        appendLine("---------------------------------------")
                        appendLine("AirSafe Schools • Verified Decision Engine")
                    }

                val sendIntent =
                    Intent(Intent.ACTION_SEND).apply {
                        putExtra(Intent.EXTRA_TEXT, reportContent)
                        type = "text/plain"
                    }
                val shareChooserTitle = context.getString(R.string.analytics_share_chooser)
                val shareIntent =
                    Intent.createChooser(sendIntent, shareChooserTitle).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                val launched =
                    runCatching {
                        if (sendIntent.resolveActivity(context.packageManager) != null) {
                            context.startActivity(shareIntent)
                            true
                        } else {
                            false
                        }
                    }.getOrElse {
                        AppLog.e("AnalyticsShare", "Weekly report share failed: ${it.message}", it)
                        false
                    }
                if (!launched) {
                    Toast.makeText(context, context.getString(R.string.export_no_app_found), Toast.LENGTH_SHORT).show()
                }
            },
            colors = ButtonDefaults.buttonColors(containerColor = SafeGreen),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(50.dp),
            shape = RoundedCornerShape(12.dp),
        ) {
            Icon(Icons.Default.Share, contentDescription = null, tint = Color.White)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.analytics_share_report),
                fontWeight = FontWeight.Bold,
                color = Color.White,
                fontSize = 15.sp,
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // F-14: Row-Level CSV Export for Governing Board / Analytics
        OutlinedButton(
            onClick = {
                if (isExporting) return@OutlinedButton
                val csvContent = runCatching { viewModel.getWeeklyAnalyticsCsv() }.getOrNull().orEmpty()
                if (csvContent.isBlank()) {
                    Toast.makeText(context, context.getString(R.string.export_empty_data), Toast.LENGTH_SHORT).show()
                    return@OutlinedButton
                }
                isExporting = true
                coroutineScope.launch {
                    val exportResult =
                        withContext(Dispatchers.IO) {
                            runCatching {
                                val reportsDir = File(context.cacheDir, "reports").apply { if (!exists()) mkdirs() }
                                val csvFile = File(reportsDir, "AirSafe_Weekly_Analytics_${System.currentTimeMillis()}.csv")
                                csvFile.writeText(csvContent, Charsets.UTF_8)
                                FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.fileprovider",
                                    csvFile,
                                )
                            }
                        }
                    exportResult.onSuccess { contentUri ->
                        val sendIntent =
                            Intent(Intent.ACTION_SEND).apply {
                                type = "text/csv"
                                putExtra(Intent.EXTRA_STREAM, contentUri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                        val shareChooserTitle = context.getString(R.string.analytics_export_csv_chooser)
                        val shareIntent =
                            Intent.createChooser(sendIntent, shareChooserTitle).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                        val launched =
                            runCatching {
                                if (sendIntent.resolveActivity(context.packageManager) != null) {
                                    context.startActivity(shareIntent)
                                    true
                                } else {
                                    false
                                }
                            }.getOrElse {
                                AppLog.e("AnalyticsCsv", "CSV share failed: ${it.message}", it)
                                false
                            }
                        if (!launched) {
                            Toast.makeText(context, context.getString(R.string.export_no_app_found), Toast.LENGTH_SHORT).show()
                        }
                    }.onFailure { err ->
                        AppLog.e("AnalyticsCsv", "CSV write failed: ${err.message}", err)
                        Toast.makeText(context, context.getString(R.string.export_failed), Toast.LENGTH_SHORT).show()
                    }
                    isExporting = false
                }
            },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(50.dp),
            shape = RoundedCornerShape(12.dp),
        ) {
            Icon(Icons.Default.Download, contentDescription = null, tint = TextPrimary)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.analytics_export_csv),
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                fontSize = 15.sp,
            )
        }
    }
}

@Composable
fun AnalyticsRow(
    label: String,
    count: Int,
    color: Color,
    isBengali: Boolean = true,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, fontSize = 14.sp, color = TextPrimary)
        Text(
            text = formatLocalizedNumber(count, isBengali, 0),
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = color,
        )
    }
}
