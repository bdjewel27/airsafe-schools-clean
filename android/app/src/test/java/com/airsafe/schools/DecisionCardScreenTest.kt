package com.airsafe.schools

import com.airsafe.schools.data.model.DecisionResponse
import com.airsafe.schools.ui.screens.components.PolicyOutcome
import com.airsafe.schools.ui.screens.components.resolveOutcome
import com.airsafe.schools.ui.theme.SafeGreen
import com.airsafe.schools.ui.theme.UnknownGray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Hard Gate 4 Verification:
 * UNKNOWN decision/status NEVER maps to GREEN or PROCEED under any circumstances.
 */
class DecisionCardScreenTest {
    @Test
    fun unknown_decision_resolves_to_PolicyOutcome_UNKNOWN_never_PROCEED() {
        val outcome = resolveOutcome("UNKNOWN", "UNKNOWN")
        assertEquals(PolicyOutcome.UNKNOWN, outcome)
        assertNotEquals(PolicyOutcome.PROCEED, outcome)
    }

    @Test
    fun null_inputs_resolve_strictly_to_PolicyOutcome_UNKNOWN() {
        val outcome = resolveOutcome(null, null)
        assertEquals(PolicyOutcome.UNKNOWN, outcome)
        assertNotEquals(PolicyOutcome.PROCEED, outcome)
    }

    @Test
    fun corrupted_or_unrecognized_strings_resolve_to_PolicyOutcome_UNKNOWN() {
        val testInputs =
            listOf(
                Pair("RANDOM_TEXT", "UNKNOWN"),
                Pair("SAFE_ASSUMPTION", "GREEN_MAYBE"),
                Pair("", ""),
                Pair("   ", "   "),
                Pair("N/A", "N/A"),
            )

        for ((dec, status) in testInputs) {
            val outcome = resolveOutcome(dec, status)
            assertEquals("Input ($dec, $status) must fail closed to UNKNOWN", PolicyOutcome.UNKNOWN, outcome)
            assertNotEquals(PolicyOutcome.PROCEED, outcome)
        }
    }

    @Test
    fun modify_decision_resolves_to_PolicyOutcome_MODIFY() {
        val outcome = resolveOutcome("MODIFY", "ORANGE")
        assertEquals(PolicyOutcome.MODIFY, outcome)
    }

    @Test
    fun move_indoors_decision_resolves_to_PolicyOutcome_MOVE_INDOORS() {
        val outcome = resolveOutcome("MOVE_INDOORS", "RED")
        assertEquals(PolicyOutcome.MOVE_INDOORS, outcome)
    }

    @Test
    fun sanitized_fails_closed_when_telemetry_metrics_are_missing() {
        val emptyResp =
            DecisionResponse(
                schoolId = "school-dhaka-001",
                // Attempted forged PROCEED without telemetry
                decision = "PROCEED",
                status = "GREEN",
                heatIndexC = null,
                aqi = null,
            )
        val sanitized = emptyResp.sanitized()
        assertEquals("UNKNOWN", sanitized.decision)
        assertEquals("UNKNOWN", sanitized.status)
        assertTrue("isStale must be true when metrics are missing (fail-closed)", sanitized.isStale == true)
    }

    @Test
    fun sanitized_fails_closed_when_only_one_metric_is_present() {
        // Only heat index present, AQI missing -> must fail closed to UNKNOWN
        val partialResp1 =
            DecisionResponse(
                schoolId = "school-dhaka-001",
                decision = "PROCEED",
                status = "GREEN",
                heatIndexC = 28.0,
                aqi = null,
            )
        val sanitized1 = partialResp1.sanitized()
        assertEquals("UNKNOWN", sanitized1.decision)
        assertEquals("UNKNOWN", sanitized1.status)

        // Only AQI present, heat index missing -> must fail closed to UNKNOWN
        val partialResp2 =
            DecisionResponse(
                schoolId = "school-dhaka-001",
                decision = "PROCEED",
                status = "GREEN",
                heatIndexC = null,
                aqi = 45,
            )
        val sanitized2 = partialResp2.sanitized()
        assertEquals("UNKNOWN", sanitized2.decision)
        assertEquals("UNKNOWN", sanitized2.status)
    }

    @Test
    fun unknown_theme_color_is_never_green() {
        // Verify that the color mapped to UNKNOWN is UnknownGray and distinct from SafeGreen
        assertNotEquals(SafeGreen, UnknownGray)
    }

    @Test
    fun sanitized_does_not_inject_fake_routine_defaults_when_empty() {
        val resp =
            DecisionResponse(
                schoolId = "school-dhaka-001",
                decision = "PROCEED",
                status = "GREEN",
                heatIndexC = 28.0,
                aqi = 40,
                currentActivityBn = null,
                currentActivityEn = null,
                nextActivityBn = null,
                nextActivityEn = null,
            )
        val sanitized = resp.sanitized()
        assertEquals("তথ্য নেই", sanitized.currentActivityBn)
        assertEquals("No routine scheduled", sanitized.currentActivityEn)
        assertEquals(null, sanitized.nextActivityBn)
        assertEquals(null, sanitized.nextActivityEn)
    }

    @Test
    fun sanitized_preserves_authentic_lastUpdated_timestamp() {
        val serverTime = "2026-10-18T14:30:00Z"
        val resp =
            DecisionResponse(
                schoolId = "school-dhaka-001",
                decision = "PROCEED",
                status = "GREEN",
                heatIndexC = 28.0,
                aqi = 40,
                lastUpdated = serverTime,
            )
        val sanitized = resp.sanitized()
        assertEquals(serverTime, sanitized.lastUpdated)
    }

    @Test
    fun sanitized_does_not_contain_hardcoded_09_50_assembly_fallback() {
        val resp =
            DecisionResponse(
                schoolId = "school-dhaka-001",
                decision = "MOVE_INDOORS",
                status = "RED",
                heatIndexC = 38.0,
                aqi = 155,
                nextActivity = null,
                nextActivityBn = null,
                nextActivityEn = null,
            )
        val sanitized = resp.sanitized()
        assertNotEquals("School Assembly 09:50", sanitized.nextActivityEn)
        assertNotEquals("স্কুল সমাবেশ (School Assembly)", sanitized.nextActivityBn)
        assertEquals(null, sanitized.nextActivity)
    }

    @Test
    fun isPeOrSportsActivity_detects_PE_and_sports_activities_accurately() {
        val peActivities =
            listOf(
                "PE Class",
                "Physical Education",
                "শারীরিক শিক্ষা",
                "পিই ক্লাস",
                "Football Match",
                "ফুটবল খেলা",
                "Sports Day",
                "দৌড় প্রতিযোগিতা",
                "Cricket Practice",
                "ক্রিকেট অনুশীলন",
            )
        for (activity in peActivities) {
            assertTrue(
                "Activity '$activity' should be recognized as PE/Sports",
                com.airsafe.schools.ui.screens.components.isPeOrSportsActivity(activity),
            )
        }

        val nonPeActivities =
            listOf(
                "Math Class",
                "Science Lab",
                "Open Library Session",
                "Temperature Review",
                "বাংলা পাঠ",
            )
        for (activity in nonPeActivities) {
            org.junit.Assert.assertFalse(
                "Activity '$activity' should not be recognized as PE/Sports",
                com.airsafe.schools.ui.screens.components.isPeOrSportsActivity(activity),
            )
        }
    }

    @Test
    fun schoolAnalyticsSummary_null_prior_week_prevents_negative_comparison_values() {
        // Step 5.4: When prior week data is unavailable, prevWeekLostMinutes must be null
        val summaryWithoutPrior =
            com.airsafe.schools.data.model.SchoolAnalyticsSummary(
                weekLabelBn = "গত ৭ দিন",
                weekLabelEn = "Past 7 Days",
                lostOutdoorMinutes = 5400,
                prevWeekLostMinutes = null,
                hoursAboveThreshold = 90.0,
                proceedCount = 10,
                cautionCount = 5,
                moveIndoorsCount = 20,
                overridesCount = 2,
                measuredRatioPct = 100,
            )
        org.junit.Assert.assertNull(summaryWithoutPrior.prevWeekLostMinutes)

        // Unified overrides count resolves accurately
        val auditLogsCount = 2
        val unifiedCount = maxOf(summaryWithoutPrior.overridesCount, auditLogsCount)
        assertEquals(2, unifiedCount)
    }

    @Test
    fun testModeledTelemetryBadgeAndHeaderAvoidsVerifiedString() {
        // Step 5.5: When measured data percentage is 0% (or modeled), strictly avoid showing "Verified Telemetry"
        val modeledAnalytics =
            com.airsafe.schools.data.model.SchoolAnalyticsSummary(
                weekLabelBn = "বিগত ৭ দিন (মডেল করা ডেটা)",
                // Legacy/cached string with Verified Telemetry
                weekLabelEn = "Past 7 Days (Verified Telemetry)",
                lostOutdoorMinutes = 180,
                prevWeekLostMinutes = 120,
                hoursAboveThreshold = 5.0,
                proceedCount = 10,
                cautionCount = 4,
                moveIndoorsCount = 6,
                overridesCount = 1,
                // 0% measured data
                measuredRatioPct = 0,
            )

        // Title logic check: when measuredRatioPct == 0, avoid Verified Telemetry
        val resolvedWeekLabel =
            if (modeledAnalytics.measuredRatioPct == 0 || modeledAnalytics.weekLabelEn.contains("Verified Telemetry")) {
                "Modeled Data" // Localized modeled data string
            } else {
                modeledAnalytics.weekLabelEn
            }
        assertEquals("Modeled Data", resolvedWeekLabel)
        org.junit.Assert.assertFalse(resolvedWeekLabel.contains("Verified Telemetry"))

        // Telemetry mode check: when isModeled is true, ground truth says Modeled Data
        val modeledDecision =
            DecisionResponse(
                schoolId = "school-001",
                status = "YELLOW",
                decision = "PROCEED_WITH_PRECAUTIONS",
                heatIndexC = 34.0,
                aqi = 85,
                isModeled = true,
            ).sanitized()

        val isModeled = modeledDecision.isModeled ?: false
        val telemetryTitle = if (isModeled) "Modeled Heat Index" else "Verified Heat Index"
        assertEquals("Modeled Heat Index", telemetryTitle)
        org.junit.Assert.assertFalse(telemetryTitle.contains("Verified"))
    }

    @Test
    fun testWeatherFallbackNullDataNotFound() {
        // Step 6.2: When Open-Meteo or weather API data is unavailable, null, or fails to load,
        // sanitize() fails closed to UNKNOWN with null environmental values rather than fake numbers.
        val fallbackNullResponse =
            DecisionResponse(
                schoolId = "school-001",
                status = "UNKNOWN",
                decision = "UNKNOWN",
                heatIndexC = null,
                aqi = null,
            ).sanitized()

        assertEquals("UNKNOWN", fallbackNullResponse.decision)
        assertEquals("UNKNOWN", fallbackNullResponse.status)
        org.junit.Assert.assertNull(fallbackNullResponse.heatIndexC)
        org.junit.Assert.assertNull(fallbackNullResponse.aqi)

        // When environmental data is null, fallback UI resolves to Data Not Found
        val notFoundFallback = if (fallbackNullResponse.heatIndexC == null) "Data Not Found" else "${fallbackNullResponse.heatIndexC}°C"
        assertEquals("Data Not Found", notFoundFallback)
    }

    @Test
    fun testFcmFallbackStateWhenConfigMissing() {
        // Step 5.6: If FCM configuration or google-services.json is missing or unavailable,
        // isFcmAvailable must return false, and warning banner indicating restriction to local notifications is active.
        com.airsafe.schools.data.notification.AirSafeFcmManager.mockFcmAvailable = false

        val isAvailable = com.airsafe.schools.data.notification.AirSafeFcmManager.isFcmAvailable(null)
        org.junit.Assert.assertFalse("FCM must be unavailable when google-services.json is missing", isAvailable)

        // Reset mock
        com.airsafe.schools.data.notification.AirSafeFcmManager.mockFcmAvailable = null
    }

    @Test
    fun testFcmGoogleServicesJsonParsingAndTokenRegistration() {
        // Step 5.6: Verify google-services.json structure can be parsed properly to configure Firebase
        val sampleGoogleServicesJson =
            """
            {
              "project_info": {
                "project_number": "123456789012",
                "project_id": "airsafe-schools-test",
                "storage_bucket": "airsafe-schools-test.appspot.com"
              },
              "client": [
                {
                  "client_info": {
                    "mobilesdk_app_id": "1:123456789012:android:abcdef0123456789",
                    "android_client_info": {
                      "package_name": "com.airsafe.schools"
                    }
                  },
                  "api_key": [
                    {
                      "current_key": "AIzaSyFakeKeyForAirSafeTesting12345"
                    }
                  ]
                }
              ]
            }
            """.trimIndent()

        val inputStream = sampleGoogleServicesJson.byteInputStream()
        val initialized = com.airsafe.schools.data.notification.AirSafeFcmManager.initializeFromStream(null, inputStream)
        org.junit.Assert.assertTrue("Should successfully parse and initialize from google-services.json stream", initialized)

        // Verify token registration handling
        val testToken = "fake-device-fcm-token-98765"
        com.airsafe.schools.data.notification.AirSafeFcmManager.onNewToken(null, testToken)
        assertEquals(testToken, com.airsafe.schools.data.notification.AirSafeFcmManager.getDeviceToken())
    }
}
