package com.airsafe.schools.ui.screens.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.airsafe.schools.R
import com.airsafe.schools.ui.theme.SafeGreen
import com.airsafe.schools.ui.theme.SurfaceCard
import com.airsafe.schools.ui.theme.TextPrimary
import com.airsafe.schools.ui.theme.TextSecondary

enum class PolicyOutcome {
    PROCEED,
    PROCEED_WITH_PRECAUTIONS,
    MODIFY,
    MOVE_INDOORS,
    UNKNOWN,
}

fun resolveOutcome(
    decision: String?,
    status: String?,
): PolicyOutcome {
    val cleanDecision = decision?.trim()?.uppercase()
    val cleanStatus = status?.trim()?.uppercase()

    if (cleanDecision == "PROCEED" && cleanStatus == "GREEN") {
        return PolicyOutcome.PROCEED
    }
    if (cleanDecision == "PROCEED_WITH_PRECAUTIONS") {
        return PolicyOutcome.PROCEED_WITH_PRECAUTIONS
    }
    if (cleanDecision == "MODIFY" && (cleanStatus == "ORANGE" || cleanStatus == "YELLOW")) {
        return PolicyOutcome.MODIFY
    }
    if (cleanDecision == "MOVE_INDOORS" && cleanStatus == "RED") {
        return PolicyOutcome.MOVE_INDOORS
    }
    return PolicyOutcome.UNKNOWN
}

data class DecisionCardStyle(
    val statusColor: Color,
    val statusBgColor: Color,
    val icon: ImageVector,
    val statusText: String,
)

@Composable
fun MetricItem(
    label: String,
    value: String,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.defaultMinSize(minWidth = 68.dp),
    ) {
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        Spacer(modifier = Modifier.height(2.dp))
        Text(text = value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = TextPrimary)
    }
}

/**
 * U-3: Skeleton Screen for Loading State
 */
@Composable
fun DecisionCardSkeleton(isBengali: Boolean) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier =
                Modifier
                    .padding(20.dp)
                    .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier =
                    Modifier
                        .width(180.dp)
                        .height(20.dp)
                        .background(Color.LightGray.copy(alpha = 0.45f), RoundedCornerShape(6.dp)),
            )
            Spacer(modifier = Modifier.height(14.dp))
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(36.dp)
                        .background(Color.LightGray.copy(alpha = 0.3f), RoundedCornerShape(10.dp)),
            )
            Spacer(modifier = Modifier.height(20.dp))
            Box(
                modifier =
                    Modifier
                        .size(60.dp)
                        .background(Color.LightGray.copy(alpha = 0.4f), RoundedCornerShape(30.dp)),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(modifier = Modifier.size(26.dp), color = SafeGreen, strokeWidth = 3.dp)
            }
            Spacer(modifier = Modifier.height(14.dp))
            Box(
                modifier =
                    Modifier
                        .width(200.dp)
                        .height(26.dp)
                        .background(Color.LightGray.copy(alpha = 0.45f), RoundedCornerShape(6.dp)),
            )
            Spacer(modifier = Modifier.height(14.dp))
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .background(Color.LightGray.copy(alpha = 0.25f), RoundedCornerShape(10.dp)),
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                repeat(3) {
                    Box(
                        modifier =
                            Modifier
                                .width(80.dp)
                                .height(44.dp)
                                .background(Color.LightGray.copy(alpha = 0.35f), RoundedCornerShape(8.dp)),
                    )
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = stringResource(R.string.loading_verified_telemetry),
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}
