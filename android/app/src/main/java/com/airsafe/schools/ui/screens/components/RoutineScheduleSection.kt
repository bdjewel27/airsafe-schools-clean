package com.airsafe.schools.ui.screens.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.airsafe.schools.R
import com.airsafe.schools.data.model.ActivityScheduleItem
import com.airsafe.schools.data.model.getLocalizedActivityName
import com.airsafe.schools.data.model.toBengaliDigits
import com.airsafe.schools.ui.theme.CautionYellow
import com.airsafe.schools.ui.theme.DangerRed
import com.airsafe.schools.ui.theme.ModifyOrange
import com.airsafe.schools.ui.theme.SafeGreen
import com.airsafe.schools.ui.theme.TextPrimary

/**
 * Step 4.1 & 4.2: Clean, single-line schedule items matching the proposed design.
 * Eliminates duplicate information, multiple badges, and raw enums.
 */
@Composable
fun RoutineScheduleSection(
    activities: List<ActivityScheduleItem>,
    isBengali: Boolean,
    modifier: Modifier = Modifier,
) {
    if (activities.isEmpty()) return
    val context = LocalContext.current

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        activities.forEach { act ->
            val actStatusColor =
                when (act.status?.uppercase()) {
                    "RED" -> DangerRed
                    "ORANGE" -> ModifyOrange
                    "YELLOW" -> CautionYellow
                    else -> SafeGreen
                }
            val actName = getLocalizedActivityName(act.nameBn, act.nameEn, isBengali, context)
            val timeSlot = (act.timeSlot ?: "").toBengaliDigits(isBengali)
            val timeAndName = if (timeSlot.isNotBlank()) "$timeSlot • $actName" else actName

            val badgeLabel =
                when (act.decision?.uppercase()) {
                    "MOVE_INDOORS" -> stringResource(R.string.decision_action_move_indoors)
                    "MODIFY" -> stringResource(R.string.decision_action_modify)
                    "PRECAUTIONS", "PROCEED_WITH_PRECAUTIONS" -> stringResource(R.string.decision_action_precautions)
                    "PROCEED" -> stringResource(R.string.decision_action_proceed)
                    else -> stringResource(R.string.decision_action_proceed)
                }

            Surface(
                shape = RoundedCornerShape(10.dp),
                color = Color(0xFFF9FAFB),
                border = BorderStroke(1.dp, Color(0xFFE5E7EB)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = timeAndName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        softWrap = false,
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = badgeLabel,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = actStatusColor,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }
        }
    }
}
