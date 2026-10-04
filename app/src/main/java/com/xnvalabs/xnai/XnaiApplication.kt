package com.xnvalabs.xnai

import android.app.Application
import android.util.Log
import org.opencv.android.OpenCVLoader

class XnaiApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        XnaiBootstrap.initialize(this)
        XnaiAutonomyScheduler.setEnabled(this, XnaiSettings(this).autonomyEnabled)
        if (!OpenCVLoader.initLocal()) {
            Log.e("XNAI", "OpenCV initialization failed. Shape detection will be unavailable.")
        }
    }
}
