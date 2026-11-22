package com.airsafe.schools

import com.airsafe.schools.data.model.stripRawEnumTokens
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Step 4.2 Unit Test:
 * Enforces that user-facing UI text, localized strings, decision titles, action chips,
 * and activity intensity labels NEVER contain raw uppercase enum patterns ([A-Z]+_[A-Z]+).
 */
class NoRawEnumInUITest {
    private val rawEnumRegex = Regex(".*\\b[A-Z0-9]{3,}_[A-Z0-9]{3,}\\b.*")

    private fun loadStringMap(relPath: String): Map<String, String> {
        val candidates = listOf(File(relPath), File("../app/$relPath"))
        val file = candidates.firstOrNull { it.exists() } ?: error("Missing strings file: $relPath")
        val regex = Regex("<string\\s+name=\"([^\"]+)\"[^>]*>([\\s\\S]*?)</string>")
        return regex.findAll(file.readText(Charsets.UTF_8)).associate {
            it.groupValues[1] to it.groupValues[2]
        }
    }

    @Test
    fun decisionTitles_neverContainRawEnums_inEnglishOrBengali() {
        val enMap = loadStringMap("src/main/res/values/strings.xml")
        val bnMap = loadStringMap("src/main/res/values-bn/strings.xml")
        val keys =
            listOf(
                "decision_proceed_title",
                "decision_precautions_title",
                "decision_modify_title",
                "decision_move_indoors_title",
                "decision_unknown_title",
            )

        keys.forEach { key ->
            val enTitle = enMap[key] ?: ""
            val bnTitle = bnMap[key] ?: ""
            assertFalse("English decision title for '$key' must not contain raw enum: $enTitle", enTitle.matches(rawEnumRegex))
            assertFalse("Bengali decision title for '$key' must not contain raw enum: $bnTitle", bnTitle.matches(rawEnumRegex))
        }
    }

    @Test
    fun intensityLabels_neverReturnRawEnums_inEnglishOrBengali() {
        val enMap = loadStringMap("src/main/res/values/strings.xml")
        val bnMap = loadStringMap("src/main/res/values-bn/strings.xml")
        val keyMap =
            mapOf(
                "LIGHT" to "intensity_light",
                "MODERATE" to "intensity_moderate",
                "VIGOROUS" to "intensity_vigorous",
                "HEAVY" to "intensity_vigorous",
            )

        keyMap.forEach { (intensity, key) ->
            val enLabel = enMap[key] ?: ""
            val bnLabel = bnMap[key] ?: ""

            assertFalse("English intensity label for '$intensity' must not be raw enum", enLabel == intensity)
            assertFalse("Bengali intensity label for '$intensity' must not be raw enum", bnLabel == intensity)
            assertTrue("English label should be human-readable", enLabel in listOf("Light", "Moderate", "Vigorous"))
            assertTrue("Bengali label should be human-readable", bnLabel in listOf("হালকা", "মাঝারি", "ভারী"))
        }
    }

    @Test
    fun targetClass_properlyLocalized_inBengali() {
        val enMap = loadStringMap("src/main/res/values/strings.xml")
        val bnMap = loadStringMap("src/main/res/values-bn/strings.xml")

        assertTrue("Bengali target class should be translated", bnMap["class_grade_5"] == "৫ম শ্রেণি")
        assertTrue("English target class should remain Class 5", enMap["class_grade_5"] == "Class 5")
    }

    @Test
    fun guidanceText_neverContainsRawEnums() {
        val enMap = loadStringMap("src/main/res/values/strings.xml")
        val bnMap = loadStringMap("src/main/res/values-bn/strings.xml")
        val keys =
            listOf(
                "decision_proceed_desc",
                "decision_precautions_desc",
                "guidance_modify_format",
                "decision_move_indoors_desc",
                "decision_unknown_desc",
            )

        keys.forEach { key ->
            val enGuidance = enMap[key] ?: ""
            val bnGuidance = bnMap[key] ?: ""

            assertFalse("English guidance for '$key' must not contain raw enum: $enGuidance", enGuidance.matches(rawEnumRegex))
            assertFalse("Bengali guidance for '$key' must not contain raw enum: $bnGuidance", bnGuidance.matches(rawEnumRegex))
        }
    }

    @Test
    fun stringResourceXmlFiles_doNotContainRawEnumsInUserFacingValues() {
        val resDirs =
            listOf(
                File("src/main/res/values/strings.xml"),
                File("src/main/res/values-bn/strings.xml"),
                File("../app/src/main/res/values/strings.xml"),
                File("../app/src/main/res/values-bn/strings.xml"),
            )

        val existingResFiles = resDirs.filter { it.exists() }
        assertTrue("At least one strings.xml file must exist for verification", existingResFiles.isNotEmpty())

        val rawEnumBlacklist = listOf("PROCEED_WITH_PRECAUTIONS", "MOVE_INDOORS")

        existingResFiles.forEach { file ->
            val content = file.readText()
            rawEnumBlacklist.forEach { forbiddenToken ->
                // Ensure no string tag has raw enum as its inner text: e.g. >PROCEED_WITH_PRECAUTIONS<
                val forbiddenPattern = Regex(">\\s*$forbiddenToken\\s*<")
                assertFalse(
                    "File ${file.path} must not contain raw enum '$forbiddenToken' as user-facing value",
                    forbiddenPattern.containsMatchIn(content),
                )
            }
        }
    }

    @Test
    fun explanationDialogAndBottomNavLabels_areStrictlyLocalizedWithoutLeaks() {
        val enMap = loadStringMap("src/main/res/values/strings.xml")
        val bnMap = loadStringMap("src/main/res/values-bn/strings.xml")

        assertEquals("Assistant", enMap["tab_assistant"])
        assertEquals("সহকারী", bnMap["tab_assistant"])
        assertEquals("Analytics", enMap["tab_analytics"])
        assertEquals("পরিসংখ্যান", bnMap["tab_analytics"])

        assertEquals("জরুরি এয়ারসেফ সতর্কতা ব্যাখ্যা", bnMap["dialog_explanation_alert_title"])
        assertEquals("বর্তমান অবস্থা ও সিদ্ধান্ত:", bnMap["dialog_current_status_decision"])
        assertEquals("প্রধান কারণ:", bnMap["dialog_primary_reason"])
        assertEquals("নীতিমালার নিয়ম ও কারণসমূহ:", bnMap["dialog_policy_rules_reasons"])
        assertEquals("তাপ সূচক: %1\$s°C", bnMap["dialog_heat_index_format"])
        assertEquals("ইউএস একিউআই: %1\$s", bnMap["dialog_aqi_format"])
        assertEquals("বন্ধ করুন", bnMap["dialog_close"])
        assertEquals("School Outdoor Safety Policy", enMap["policy_title_friendly"])
        assertEquals("স্কুল বহিরঙ্গন নিরাপত্তা নীতিমালা", bnMap["policy_title_friendly"])

        assertFalse((bnMap["dialog_aqi_format"] ?: "").contains("US AQI"))
        assertEquals("Some students feel hot", stripRawEnumTokens("MODIFY: Some students feel hot (MODIFY)"))
    }
}
