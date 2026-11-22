package com.airsafe.schools

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.airsafe.schools.data.model.DecisionResponse
import com.airsafe.schools.ui.screens.components.ExplanationDialog
import com.airsafe.schools.ui.theme.AirSafeTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FontScaleAndLocalizationComposeTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun bottomNavLabels_at200PercentFontScale_renderSingleLineWithoutWrapping() {
        composeTestRule.setContent {
            AirSafeTheme {
                CompositionLocalProvider(LocalDensity provides Density(density = 1f, fontScale = 2.0f)) {
                    Box(modifier = Modifier.width(72.dp)) {
                        BottomNavLabel(text = "Assistant")
                    }
                    Box(modifier = Modifier.width(72.dp)) {
                        BottomNavLabel(text = "Analytics")
                    }
                    Box(modifier = Modifier.width(72.dp)) {
                        BottomNavLabel(text = "পরিসংখ্যান")
                    }
                }
            }
        }

        listOf("Assistant", "Analytics", "পরিসংখ্যান").forEach { label ->
            val node = composeTestRule.onNodeWithText(label)
            node.assertIsDisplayed()
            val bounds = node.fetchSemanticsNode().boundsInRoot
            assertTrue("Label '$label' should fit on a single compact line at 200% font scale", bounds.height < 40f)
        }
    }

    @Test
    fun explanationDialog_whenBengaliActive_rendersAllLabelsInBengaliWithoutEnglishLeaks() {
        val sampleData =
            DecisionResponse(
                schoolId = "school-dhaka-001",
                status = "ORANGE",
                decision = "MODIFY",
                heatIndexC = 38.4,
                aqi = 128,
                primaryReason = "some students are weak",
                reasons = listOf("Asthma-sensitive students present"),
            )

        composeTestRule.setContent {
            AirSafeTheme {
                ExplanationDialog(
                    safeData = sampleData,
                    isBengali = true,
                    onDismiss = {},
                )
            }
        }

        composeTestRule.onNodeWithText("জরুরি এয়ারসেফ সতর্কতা ব্যাখ্যা").assertIsDisplayed()
        composeTestRule.onNodeWithText("বর্তমান অবস্থা ও সিদ্ধান্ত:").assertIsDisplayed()
        composeTestRule.onNodeWithText("প্রধান কারণ:").assertIsDisplayed()
        composeTestRule.onNodeWithText("নীতিমালার নিয়ম ও কারণসমূহ:").assertIsDisplayed()
        composeTestRule.onNodeWithText("তাপ সূচক: ৩৮.৪°C").assertIsDisplayed()
        composeTestRule.onNodeWithText("ইউএস একিউআই: ১২৮").assertIsDisplayed()
        composeTestRule.onNodeWithText("বন্ধ করুন").assertIsDisplayed()
        composeTestRule.onNodeWithText("কয়েকজন শিক্ষার্থী দুর্বল থাকার কারণে সতর্কতা").assertIsDisplayed()
        composeTestRule.onNodeWithText("• শ্বাসকষ্ট বা অ্যাজমাপ্রবণ শিক্ষার্থী উপস্থিত রয়েছে").assertIsDisplayed()
    }
}
