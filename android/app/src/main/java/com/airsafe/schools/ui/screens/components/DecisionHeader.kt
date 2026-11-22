package com.airsafe.schools.ui.screens.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.airsafe.schools.R
import com.airsafe.schools.data.model.UserRole
import com.airsafe.schools.data.model.UserSession
import com.airsafe.schools.ui.theme.DangerRed
import com.airsafe.schools.ui.theme.TextPrimary
import com.airsafe.schools.ui.theme.TextSecondary

@Composable
fun DecisionHeader(
    userSession: UserSession?,
    isBengali: Boolean,
    onToggleLanguage: () -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val defaultSchool = stringResource(R.string.default_school_name)
    val defaultStaff = stringResource(R.string.default_staff_name)

    val schoolName =
        if (isBengali) {
            (userSession?.schoolNameBn ?: defaultSchool)
        } else {
            (userSession?.schoolNameEn ?: defaultSchool)
        }

    val userName =
        if (isBengali) {
            (userSession?.nameBn ?: defaultStaff)
        } else {
            (userSession?.nameEn ?: defaultStaff)
        }

    val roleLabel =
        when (userSession?.role) {
            UserRole.COORDINATOR -> stringResource(R.string.role_coordinator)
            UserRole.PLATFORM_ADMIN -> stringResource(R.string.role_admin)
            else -> stringResource(R.string.role_teacher)
        }

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = schoolName,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
            )
            Text(
                text = "$userName • $roleLabel",
                fontSize = 12.sp,
                color = TextSecondary,
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(
                onClick = onToggleLanguage,
                contentPadding = PaddingValues(horizontal = 8.dp),
            ) {
                Icon(
                    Icons.Default.Translate,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = stringResource(R.string.lang_toggle_label),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                )
            }

            IconButton(onClick = onLogout) {
                Icon(
                    Icons.AutoMirrored.Filled.ExitToApp,
                    contentDescription = stringResource(R.string.btn_logout),
                    tint = DangerRed,
                )
            }
        }
    }
}
