package com.airsafe.schools.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.airsafe.schools.R
import com.airsafe.schools.data.model.SensorSite
import com.airsafe.schools.data.model.toBengaliDigits
import com.airsafe.schools.ui.theme.SafeGreen
import com.airsafe.schools.ui.theme.SafeGreenBackground
import com.airsafe.schools.ui.theme.SurfaceCard
import com.airsafe.schools.ui.theme.TextPrimary
import com.airsafe.schools.ui.theme.TextSecondary
import com.airsafe.schools.ui.viewmodel.DecisionViewModel

@Composable
fun SensorSitesView(
    viewModel: DecisionViewModel,
    modifier: Modifier = Modifier,
) {
    val sites by viewModel.sensorSites.collectAsState()
    val isBengali by viewModel.isBengali.collectAsState()

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.sites_title),
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
        )

        Spacer(modifier = Modifier.height(10.dp))

        sites.forEach { site ->
            SensorSiteCard(site = site, isBengali = isBengali)
            Spacer(modifier = Modifier.height(10.dp))
        }
    }
}

@Composable
fun SensorSiteCard(
    site: SensorSite,
    isBengali: Boolean,
) {
    val notFoundText = stringResource(R.string.data_not_found)
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier =
            Modifier
                .fillMaxWidth()
                .semantics {
                    val tempStr = if (site.tempC != null) "${site.tempC}°C" else notFoundText
                    val pmStr = if (site.pm25Ugm3 != null) "${site.pm25Ugm3}" else notFoundText
                    contentDescription = "${if (isBengali) site.nameBn else site.nameEn}, $tempStr, PM2.5 $pmStr"
                },
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier.weight(1f).padding(end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Default.Sensors,
                        contentDescription = null,
                        tint = SafeGreen,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isBengali) site.nameBn else site.nameEn,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                    )
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (site.isOnline) SafeGreenBackground else Color(0xFFFFF3CD),
                ) {
                    val lastUpdated = if (isBengali) site.lastUpdatedBn else site.lastUpdatedEn
                    val statusText =
                        if (site.isOnline) {
                            stringResource(R.string.status_live_format, lastUpdated)
                        } else {
                            stringResource(R.string.status_offline_format, lastUpdated)
                        }
                    Text(
                        text = statusText,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (site.isOnline) SafeGreen else Color(0xFF856404),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                MetricSmallItem(
                    modifier = Modifier.weight(1f),
                    label = stringResource(R.string.metric_heat_index),
                    value = if (site.heatIndexC != null) "${site.heatIndexC.toString().toBengaliDigits(isBengali)}°C" else notFoundText,
                )
                MetricSmallItem(
                    modifier = Modifier.weight(1f),
                    label = stringResource(R.string.metric_temp),
                    value = if (site.tempC != null) "${site.tempC.toString().toBengaliDigits(isBengali)}°C" else notFoundText,
                )
                MetricSmallItem(
                    modifier = Modifier.weight(1f),
                    label = stringResource(R.string.metric_humidity),
                    value = if (site.rhPct != null) "${site.rhPct.toString().toBengaliDigits(isBengali)}%" else notFoundText,
                )
                MetricSmallItem(
                    modifier = Modifier.weight(1f),
                    label = stringResource(R.string.metric_pm25),
                    value = if (site.pm25Ugm3 != null) site.pm25Ugm3.toString().toBengaliDigits(isBengali) else notFoundText,
                )
            }
        }
    }
}

@Composable
fun MetricSmallItem(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = label, fontSize = 11.sp, color = TextSecondary, maxLines = 1)
        Spacer(modifier = Modifier.height(2.dp))
        Text(text = value, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary, maxLines = 1)
    }
}
