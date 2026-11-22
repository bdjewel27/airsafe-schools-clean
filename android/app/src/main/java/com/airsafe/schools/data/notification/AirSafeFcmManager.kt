package com.airsafe.schools.data.notification

import android.content.Context
import androidx.annotation.VisibleForTesting
import com.airsafe.schools.util.AppLog
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import java.io.File
import java.io.InputStream

/**
 * Step 5.6: Push Notification (FCM) & Fallback Manager
 *
 * Handles:
 * 1. google-services.json detection and Firebase initialization.
 * 2. Device FCM token registration when google-services.json / FCM config is present.
 * 3. Graceful fallback state when google-services.json is missing or unavailable:
 *    alerts fall back safely to in-app local notifications (AirSafeNotificationHelper).
 */
object AirSafeFcmManager {
    private const val TAG = "AirSafeFcmManager"

    @Volatile
    private var isConfigured: Boolean = false

    @Volatile
    private var deviceToken: String? = null

    @VisibleForTesting
    internal var mockFcmAvailable: Boolean? = null

    /**
     * Checks if FCM push notifications are actively available and configured.
     * Returns false if google-services.json or FirebaseApp configuration is missing.
     */
    fun isFcmAvailable(context: Context? = null): Boolean {
        mockFcmAvailable?.let { return it }
        return isConfigured || (context != null && FirebaseApp.getApps(context).isNotEmpty())
    }

    /**
     * Initializes FCM handling and registers device token if google-services.json is present.
     */
    fun initialize(context: Context) {
        try {
            // 1. Check if already initialized (e.g. by Google Services plugin)
            if (FirebaseApp.getApps(context).isNotEmpty()) {
                isConfigured = true
                registerDeviceToken()
                return
            }

            // 2. Check if google-services.json is available in assets or files
            val jsonStream = findGoogleServicesJsonStream(context)
            if (jsonStream != null) {
                val initialized = initializeFromStream(context, jsonStream)
                if (initialized) {
                    isConfigured = true
                    registerDeviceToken()
                    return
                }
            }

            // 3. Fallback: Check if google_app_id string resource is available
            val appIdResId = context.resources.getIdentifier("google_app_id", "string", context.packageName)
            if (appIdResId != 0) {
                FirebaseApp.initializeApp(context)
                isConfigured = true
                registerDeviceToken()
                return
            }

            // If neither is present, gracefully mark as unavailable (local notifications fallback)
            isConfigured = false
            AppLog.i(
                TAG,
                "google-services.json is missing or unavailable. " +
                    "Push notifications disabled; relying strictly on in-app local notifications.",
            )
        } catch (e: Throwable) {
            isConfigured = false
            AppLog.w(TAG, "FCM initialization fallback: ${e.message}")
        }
    }

    private fun findGoogleServicesJsonStream(context: Context): InputStream? {
        // Check assets
        try {
            return context.assets.open("google-services.json")
        } catch (_: Throwable) {
            // Not in assets
        }

        // Check internal filesDir
        val localFile = File(context.filesDir, "google-services.json")
        if (localFile.exists()) {
            try {
                return localFile.inputStream()
            } catch (_: Throwable) {
            }
        }

        return null
    }

    fun initializeFromStream(
        context: Context?,
        inputStream: InputStream,
    ): Boolean {
        return try {
            val content = inputStream.bufferedReader().use { it.readText() }
            val root = com.google.gson.JsonParser.parseString(content).asJsonObject
            val projectInfo = root.getAsJsonObject("project_info")
            val projectId = projectInfo?.get("project_id")?.asString ?: ""
            val projectNumber = projectInfo?.get("project_number")?.asString ?: ""

            val clientArr = root.getAsJsonArray("client")
            if (clientArr == null || clientArr.size() == 0) return false
            val clientObj = clientArr[0].asJsonObject
            val clientInfo = clientObj.getAsJsonObject("client_info")
            val appId = clientInfo?.get("mobilesdk_app_id")?.asString ?: ""

            val apiKeyArr = clientObj.getAsJsonArray("api_key")
            val apiKey = apiKeyArr?.get(0)?.asJsonObject?.get("current_key")?.asString ?: ""

            if (appId.isBlank() || apiKey.isBlank() || projectId.isBlank()) {
                return false
            }

            if (context != null) {
                val options =
                    FirebaseOptions.Builder()
                        .setApplicationId(appId)
                        .setApiKey(apiKey)
                        .setProjectId(projectId)
                        .apply {
                            if (projectNumber.isNotBlank()) {
                                setGcmSenderId(projectNumber)
                            }
                        }
                        .build()

                if (FirebaseApp.getApps(context).isEmpty()) {
                    FirebaseApp.initializeApp(context, options)
                }
            }
            isConfigured = true
            AppLog.i(TAG, "Firebase initialized successfully from google-services.json (project: $projectId)")
            true
        } catch (e: Throwable) {
            AppLog.e(TAG, "Failed to parse or initialize google-services.json: ${e.message}", e)
            false
        }
    }

    private fun registerDeviceToken() {
        try {
            FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val token = task.result
                    deviceToken = token
                    AppLog.i(TAG, "FCM Device Token registered: $token")
                } else {
                    AppLog.w(TAG, "Fetching FCM registration token failed: ${task.exception?.message}")
                }
            }
        } catch (e: Throwable) {
            AppLog.w(TAG, "Register device token error: ${e.message}")
        }
    }

    fun onNewToken(
        context: Context?,
        token: String,
    ) {
        deviceToken = token
        AppLog.d(TAG, "Updated device token: $token")
    }

    fun getDeviceToken(): String? = deviceToken
}
