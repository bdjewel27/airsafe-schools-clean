package com.airsafe.schools

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class UiEnumLeakTest {
    private val bannedEnums =
        listOf(
            "PROCEED_WITH_PRECAUTIONS",
            "MOVE_INDOORS",
            "CANCEL_OUTDOOR",
            "NORMAL_OUTDOOR",
            "NORMAL_OPERATION",
            "CAMPUS_IOT_SENSOR",
            "OPENAQ_OFFICIAL",
            "OPEN_METEO_MODELED",
            "AQI_BAND_",
            "RULE-",
            "(PROCEED)",
            "(MODIFY)",
            "SQLite",
            "VIGOROUS",
            "MODERATE",
            "LIGHT",
            "HEAVY",
        )

    @Test
    fun uiStrings_inEnglishAndBangla_mustNotContainInternalEnums() {
        val candidates =
            listOf(
                File("src/main/res/values/strings.xml"),
                File("src/main/res/values-bn/strings.xml"),
                File("app/src/main/res/values/strings.xml"),
                File("app/src/main/res/values-bn/strings.xml"),
            ).filter { it.exists() }

        assertTrue("strings.xml files must exist for testing", candidates.isNotEmpty())

        val regex = Regex("<string[^>]*>([\\s\\S]*?)</string>")
        val violations = mutableListOf<String>()

        for (file in candidates) {
            val content = file.readText(Charsets.UTF_8)
            regex.findAll(content).forEach { match ->
                val text = match.groupValues[1]
                for (banned in bannedEnums) {
                    if (text.contains(banned)) {
                        violations.add("${file.path}: leaked '$banned' in '$text'")
                    }
                }
            }
        }

        assertTrue(
            "UI text must not contain internal enums in Bangla or English:\n" + violations.joinToString("\n"),
            violations.isEmpty(),
        )
    }

    @Test
    fun englishStrings_mustNotContainBengaliScript_andKeysMustMatch() {
        val enFile =
            listOf(
                File("src/main/res/values/strings.xml"),
                File("app/src/main/res/values/strings.xml"),
            ).firstOrNull { it.exists() }
        val bnFile =
            listOf(
                File("src/main/res/values-bn/strings.xml"),
                File("app/src/main/res/values-bn/strings.xml"),
            ).firstOrNull { it.exists() }

        assertTrue("Both English and Bengali strings.xml must exist", enFile != null && bnFile != null)

        val keyRegex = Regex("<string\\s+name=\"([^\"]+)\"[^>]*>([\\s\\S]*?)</string>")
        val enMap =
            keyRegex.findAll(enFile!!.readText(Charsets.UTF_8))
                .associate { it.groupValues[1] to it.groupValues[2] }
        val bnMap =
            keyRegex.findAll(bnFile!!.readText(Charsets.UTF_8))
                .associate { it.groupValues[1] to it.groupValues[2] }

        val bengaliCharLeaks =
            enMap.filter { (k, v) ->
                k != "lang_toggle_label" && v.any { it in '\u0980'..'\u09FF' }
            }.keys

        assertTrue(
            "English strings.xml must not contain Bengali script (found in: $bengaliCharLeaks)",
            bengaliCharLeaks.isEmpty(),
        )

        val missingInBn = enMap.keys - bnMap.keys
        assertTrue(
            "All English string keys must exist in values-bn/strings.xml (missing: $missingInBn)",
            missingInBn.isEmpty(),
        )
    }
}
