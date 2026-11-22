package com.airsafe.schools

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class AirSafeApplication : Application() {
    override fun onCreate() {
        super.onCreate()
    }
}
