package com.airsafe.schools.ui.screens.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.airsafe.schools.ui.theme.CautionText
import com.airsafe.schools.ui.theme.CautionYellowBackground

/**
 * Step 5.6: Push Notification & Local Notification Fallback Notice Banner
 *
 * When FCM configuration (google-services.json) is missing or unavailable,
 * displays a clear, localized warning banner indicating that alerts are restricted
 * to in-app local notifications only.
 */
@Composable
fun NotificationNoticeBanner(
    isFcmAvailable: Boolean,
    modifier: Modifier = Modifier,
) {
    if (!isFcmAvailable) {
        val warningText = stringResource(R.string.banner_fcm_fallback_warning)
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = CautionYellowBackground,
            border = BorderStroke(1.dp, Color(0xFFFFEEBA)),
            modifier =
                modifier
                    .fillMaxWidth()
                    .semantics {
                        contentDescription = warningText
                    },
        ) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.NotificationsOff,
                    contentDescription = null,
                    tint = CautionText,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = warningText,
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 11.sp,
                    color = CautionText,
                    fontWeight = FontWeight.Medium,
                    lineHeight = 15.sp,
                )
            }
        }
    }
}
