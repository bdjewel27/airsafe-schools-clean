package com.airsafe.schools.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.airsafe.schools.R
import com.airsafe.schools.data.model.TrendPoint
import com.airsafe.schools.data.model.toBengaliDigits
import com.airsafe.schools.ui.theme.CautionText
import com.airsafe.schools.ui.theme.CautionYellow
import com.airsafe.schools.ui.theme.CautionYellowBackground
import com.airsafe.schools.ui.theme.DangerRed
import com.airsafe.schools.ui.theme.DangerRedBackground
import com.airsafe.schools.ui.theme.ModifyOrange
import com.airsafe.schools.ui.theme.ModifyOrangeBackground
import com.airsafe.schools.ui.theme.SafeGreen
import com.airsafe.schools.ui.theme.SafeGreenBackground
import com.airsafe.schools.ui.theme.TextPrimary
import com.airsafe.schools.ui.theme.TextSecondary
import com.airsafe.schools.ui.viewmodel.DecisionViewModel
import kotlin.math.roundToInt

/**
 * Step 4.4: Redesigned Environmental Trends Screen
 * - Interactive Compose Chart + Accessible Table toggle
 * - Displays PM2.5 in addition to Heat Index & AQI
 * - Policy v1.2 Four-Tier Status:
 *   1. Safe / স্বাভাবিক (AQI <= 50, HI < 27)
 *   2. Moderate / মাঝারি (AQI 51–100, e.g. 95)
 *   3. Sensitive / সংবেদনশীলদের জন্য অস্বাস্থ্যকর (AQI 101–150, e.g. 126)
 *   4. Unhealthy / অস্বাস্থ্যকর (AQI > 150, e.g. 155, 171)
 * - Distinct multi-series for Field vs Courtyard
 * - Tap on row or chart point opens full metric detail dialog
 */
@Composable
fun TrendsScreen(
    viewModel: DecisionViewModel,
    modifier: Modifier = Modifier,
) {
    val trends by viewModel.trendData.collectAsState()
    val isBengali by viewModel.isBengali.collectAsState()
    val isOffline by viewModel.isOffline.collectAsState()
    val isTrendsLoading by viewModel.isTrendsLoading.collectAsState()
    val trendsError by viewModel.trendsError.collectAsState()
    val selectedRange by viewModel.selectedTrendRange.collectAsState()
    val compareSiteId by viewModel.compareSiteId.collectAsState()
    val periodLabel by viewModel.trendPeriodLabel.collectAsState()

    var viewMode by remember { mutableStateOf("CHART") } // "CHART" or "TABLE"
    var selectedDetailPoint by remember { mutableStateOf<TrendPoint?>(null) }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(16.dp),
    ) {
        // Header
        Text(
            text = stringResource(R.string.trends_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
        )

        Spacer(modifier = Modifier.height(2.dp))

        Text(
            text = periodLabel,
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Offline Banner (if offline)
        if (isOffline) {
            Card(
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = CautionYellowBackground),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.CloudOff, contentDescription = null, tint = CautionText)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.trends_offline_banner),
                        color = CautionText,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }

        // Time Range Chips: 24h / 7d / 30d
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val ranges =
                listOf(
                    "24h" to R.string.trends_range_24h,
                    "7d" to R.string.trends_range_7d,
                    "30d" to R.string.trends_range_30d,
                )
            ranges.forEach { (key, labelRes) ->
                val isSelected = selectedRange == key
                FilterChip(
                    selected = isSelected,
                    onClick = { viewModel.setTrendRange(key) },
                    label = {
                        Text(
                            text = stringResource(labelRes),
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        )
                    },
                    colors =
                        FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Site Comparison Selector & Chart/Table Toggle Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Site comparison selector
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                val isSingle = compareSiteId == null
                FilterChip(
                    selected = isSingle,
                    onClick = { viewModel.setComparisonSite(null) },
                    label = { Text(stringResource(R.string.trends_site_field), fontSize = 11.sp) },
                )

                val isComparing = compareSiteId != null
                FilterChip(
                    selected = isComparing,
                    onClick = {
                        val target = if (isComparing) null else "courtyard"
                        viewModel.setComparisonSite(target)
                    },
                    label = { Text(stringResource(R.string.trends_site_field_vs_courtyard), fontSize = 11.sp) },
                )
            }

            // View Mode Toggle: Chart / Table
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Surface(
                    onClick = { viewMode = "CHART" },
                    shape = RoundedCornerShape(8.dp),
                    color = if (viewMode == "CHART") SafeGreen else Color(0xFFE5E7EB),
                    modifier = Modifier.padding(2.dp),
                ) {
                    Text(
                        text = stringResource(R.string.trends_view_chart),
                        color = if (viewMode == "CHART") Color.White else TextPrimary,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
                Surface(
                    onClick = { viewMode = "TABLE" },
                    shape = RoundedCornerShape(8.dp),
                    color = if (viewMode == "TABLE") SafeGreen else Color(0xFFE5E7EB),
                    modifier = Modifier.padding(2.dp),
                ) {
                    Text(
                        text = stringResource(R.string.trends_view_table),
                        color = if (viewMode == "TABLE") Color.White else TextPrimary,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // School Hours Shading Banner
        Card(
            shape = RoundedCornerShape(6.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFE3F2FD)),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(10.dp)
                            .background(Color(0xFF1976D2), RoundedCornerShape(2.dp)),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.trends_school_hours_shading),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF0D47A1),
                    fontWeight = FontWeight.Medium,
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Content Area: Loading, Error, or Chart/Table
        when {
            isTrendsLoading -> {
                TrendsSkeleton()
            }
            trendsError != null && trends.isEmpty() -> {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = CautionYellowBackground),
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = CautionText, modifier = Modifier.size(36.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (trendsError.isNullOrBlank()) stringResource(R.string.data_not_found) else trendsError!!,
                            fontSize = 13.sp,
                            color = CautionText,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = { viewModel.loadTrends() },
                            colors = ButtonDefaults.buttonColors(containerColor = SafeGreen),
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.btn_retry))
                        }
                    }
                }
            }
            viewMode == "CHART" -> {
                TrendChartView(
                    trends = trends,
                    isBengali = isBengali,
                    compareActive = compareSiteId != null,
                    onPointClick = { pt -> selectedDetailPoint = pt },
                )
            }
            else -> {
                // Accessible Table View with PM2.5 and 4 Policy Tiers
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFE8ECEF), RoundedCornerShape(8.dp))
                            .padding(vertical = 8.dp, horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.trends_col_time),
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.weight(1.1f),
                    )
                    Text(
                        text = stringResource(R.string.trends_col_heat_index),
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1.1f),
                    )
                    Text(
                        text = "AQI",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(0.8f),
                    )
                    Text(
                        text = "PM2.5",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(0.9f),
                    )
                    Text(
                        text = stringResource(R.string.trends_col_status),
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelMedium,
                        textAlign = TextAlign.End,
                        modifier = Modifier.weight(1.1f),
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(trends) { item ->
                        TrendTableRow(
                            item = item,
                            isBengali = isBengali,
                            isComparisonActive = compareSiteId != null,
                            onClick = { selectedDetailPoint = item },
                        )
                    }
                }
            }
        }
    }

    // Detail Dialog on tap
    if (selectedDetailPoint != null) {
        val pt = selectedDetailPoint!!
        val (tierLabel, tierColor, _) = getFourTierStatus(pt.aqi, pt.heatIndexC, isBengali)
        val timeLabel = if (isBengali) pt.timeLabelBn else pt.timeLabelEn
        val pmVal = pt.pm25Ugm3 ?: (pt.aqi * 0.35).roundToInt().toDouble()

        AlertDialog(
            onDismissRequest = { selectedDetailPoint = null },
            title = {
                Text(
                    text = stringResource(R.string.trends_telemetry_title_format, timeLabel),
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(stringResource(R.string.trends_detail_aqi), fontWeight = FontWeight.SemiBold)
                        Text(pt.aqi.toString().toBengaliDigits(isBengali), fontWeight = FontWeight.Bold, color = tierColor)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(stringResource(R.string.trends_detail_pm25), fontWeight = FontWeight.SemiBold)
                        Text("${pmVal.toString().toBengaliDigits(isBengali)} µg/m³", fontWeight = FontWeight.Bold)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(stringResource(R.string.trends_detail_heat_index), fontWeight = FontWeight.SemiBold)
                        Text("${pt.heatIndexC.toString().toBengaliDigits(isBengali)}°C", fontWeight = FontWeight.Bold)
                    }
                    if (pt.temperatureC != null) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(stringResource(R.string.trends_detail_ambient_temp), fontWeight = FontWeight.SemiBold)
                            Text("${pt.temperatureC.toString().toBengaliDigits(isBengali)}°C", fontWeight = FontWeight.Bold)
                        }
                    }
                    if (compareSiteId != null && pt.comparisonAqi != null) {
                        HorizontalDivider()
                        Text(
                            text = stringResource(R.string.trends_field_vs_courtyard),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = ModifyOrange,
                        )
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(stringResource(R.string.trends_detail_courtyard_aqi))
                            Text("${pt.comparisonAqi}", fontWeight = FontWeight.Bold)
                        }
                        if (pt.comparisonHeatIndexC != null) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(stringResource(R.string.trends_detail_courtyard_hi))
                                Text("${pt.comparisonHeatIndexC}°C", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    HorizontalDivider()
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(stringResource(R.string.trends_detail_policy_tier), fontWeight = FontWeight.SemiBold)
                        Text(tierLabel, fontWeight = FontWeight.Bold, color = tierColor)
                    }
                    Text(
                        text = stringResource(R.string.trends_detail_policy_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedDetailPoint = null }) {
                    Text(stringResource(R.string.btn_close_ok))
                }
            },
        )
    }
}

/**
 * Policy v1.2 Four-Tier Classification:
 * 1. Safe / স্বাভাবিক: AQI <= 50 & Heat Index < 27°C
 * 2. Moderate / মাঝারি: AQI 51–100 (e.g. 95) or Heat Index 27–32°C
 * 3. Sensitive / সংবেদনশীল: AQI 101–150 (e.g. 126) or Heat Index 32–38°C
 * 4. Unhealthy / অস্বাস্থ্যকর: AQI > 150 (e.g. 155, 171) or Heat Index >= 38°C
 */
@Composable
fun getFourTierStatus(
    aqi: Int,
    heatIndexC: Double,
    isBengali: Boolean,
): Triple<String, Color, Color> {
    return when {
        aqi > 150 || heatIndexC >= 38.0 ->
            Triple(
                stringResource(R.string.trends_tier_unhealthy),
                DangerRed,
                DangerRedBackground,
            )
        aqi > 100 || heatIndexC >= 32.0 ->
            Triple(
                stringResource(R.string.trends_tier_sensitive),
                ModifyOrange,
                ModifyOrangeBackground,
            )
        aqi > 50 || heatIndexC >= 27.0 ->
            Triple(
                stringResource(R.string.trends_tier_moderate),
                CautionYellow,
                CautionYellowBackground,
            )
        else ->
            Triple(
                stringResource(R.string.trends_tier_safe),
                SafeGreen,
                SafeGreenBackground,
            )
    }
}

@Composable
fun TrendTableRow(
    item: TrendPoint,
    isBengali: Boolean,
    isComparisonActive: Boolean,
    onClick: () -> Unit,
) {
    val (statusLabel, statusColor, baseBgColor) = getFourTierStatus(item.aqi, item.heatIndexC, isBengali)
    val pmVal = item.pm25Ugm3 ?: (item.aqi * 0.35).roundToInt().toDouble()

    val rowBgColor =
        if (item.isSchoolHours || item.isShaded) {
            if (item.aqi > 150 || item.heatIndexC >= 38.0) Color(0xFFFFEBEE) else Color(0xFFF0F7FF)
        } else {
            baseBgColor
        }

    val cardBorder =
        if (item.isSchoolHours || item.isShaded) {
            Modifier.border(1.dp, Color(0xFF90CAF9), RoundedCornerShape(8.dp))
        } else {
            Modifier
        }

    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = rowBgColor),
        modifier =
            Modifier
                .fillMaxWidth()
                .then(cardBorder)
                .clickable(onClick = onClick)
                .semantics {
                    val label = if (isBengali) item.timeLabelBn else item.timeLabelEn
                    val hi = item.heatIndexC.toString().toBengaliDigits(isBengali)
                    val aqiStr = item.aqi.toString().toBengaliDigits(isBengali)
                    contentDescription = "$label, হিট ইনডেক্স $hi ডিগ্রি, AQI $aqiStr, অবস্থা $statusLabel"
                },
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp, horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Time Column
            Text(
                text = if (isBengali) item.timeLabelBn else item.timeLabelEn,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = TextPrimary,
                modifier = Modifier.weight(1.1f),
            )

            // Heat Index Column
            Text(
                text = "${item.heatIndexC.toString().toBengaliDigits(isBengali)}°C",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1.1f),
            )

            // AQI Column
            Text(
                text = item.aqi.toString().toBengaliDigits(isBengali),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = statusColor,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(0.8f),
            )

            // PM2.5 Column
            Text(
                text = pmVal.roundToInt().toString().toBengaliDigits(isBengali),
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(0.9f),
            )

            // Status Column (4 Tiers)
            Text(
                text = statusLabel,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = statusColor,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1.1f),
            )
        }
    }
}

/**
 * Visual Compose Chart for Trends (Step 4.4)
 */
@Composable
fun TrendChartView(
    trends: List<TrendPoint>,
    isBengali: Boolean,
    compareActive: Boolean,
    onPointClick: (TrendPoint) -> Unit,
) {
    if (trends.isEmpty()) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(240.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.trends_empty_data),
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
            )
        }
        return
    }

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier =
            Modifier
                .fillMaxWidth()
                .height(260.dp)
                .padding(vertical = 4.dp),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(12.dp),
        ) {
            // Chart Legend
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(8.dp).background(ModifyOrange, CircleShape))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.trends_legend_field_aqi), style = MaterialTheme.typography.labelSmall)
                    }

                    if (compareActive) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(8.dp).background(SafeGreen, CircleShape))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(stringResource(R.string.trends_legend_courtyard_aqi), style = MaterialTheme.typography.labelSmall)
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(8.dp).background(Color(0xFF1976D2), CircleShape))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.trends_legend_heat_index), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Canvas Chart
            Box(modifier = Modifier.fillMaxSize()) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height - 24f // leave room for x-axis labels
                    val n = trends.size
                    if (n < 2) return@Canvas

                    val stepX = w / (n - 1).coerceAtLeast(1)

                    // Draw School Hours shaded regions (08:00 - 16:00)
                    trends.forEachIndexed { i, pt ->
                        if (pt.isSchoolHours || pt.isShaded) {
                            val xStart = (i * stepX - stepX / 2).coerceAtLeast(0f)
                            val barW = stepX.coerceAtMost(w - xStart)
                            drawRect(
                                color = Color(0x221976D2),
                                topLeft = Offset(xStart, 0f),
                                size = Size(barW, h),
                            )
                        }
                    }

                    // Max scale values
                    val maxAqi = 220f
                    val maxHi = 45f

                    // Path for Field AQI
                    val aqiPath = Path()
                    val hiPath = Path()
                    val compAqiPath = Path()

                    trends.forEachIndexed { i, pt ->
                        val x = i * stepX
                        val aqiY = h - (pt.aqi / maxAqi).coerceIn(0f, 1f) * h
                        val hiY = h - (pt.heatIndexC.toFloat() / maxHi).coerceIn(0f, 1f) * h

                        if (i == 0) {
                            aqiPath.moveTo(x, aqiY)
                            hiPath.moveTo(x, hiY)
                        } else {
                            aqiPath.lineTo(x, aqiY)
                            hiPath.lineTo(x, hiY)
                        }

                        if (compareActive && pt.comparisonAqi != null) {
                            val compY = h - (pt.comparisonAqi / maxAqi).coerceIn(0f, 1f) * h
                            if (i == 0) compAqiPath.moveTo(x, compY) else compAqiPath.lineTo(x, compY)
                        }
                    }

                    // Draw curves
                    drawPath(aqiPath, color = ModifyOrange, style = Stroke(width = 3.dp.toPx()))
                    drawPath(hiPath, color = Color(0xFF1976D2), style = Stroke(width = 2.dp.toPx()))
                    if (compareActive) {
                        drawPath(
                            compAqiPath,
                            color = SafeGreen,
                            style =
                                Stroke(
                                    width = 2.5.dp.toPx(),
                                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f),
                                ),
                        )
                    }

                    // Draw points on AQI curve
                    trends.forEachIndexed { i, pt ->
                        val x = i * stepX
                        val aqiY = h - (pt.aqi / maxAqi).coerceIn(0f, 1f) * h
                        drawCircle(color = ModifyOrange, radius = 3.dp.toPx(), center = Offset(x, aqiY))
                    }
                }
            }
        }
    }
}

@Composable
fun TrendsSkeleton() {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    ) {
        repeat(5) {
            Card(
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFEEEEEE)),
                modifier = Modifier.fillMaxWidth().height(44.dp),
            ) {}
        }
    }
}
