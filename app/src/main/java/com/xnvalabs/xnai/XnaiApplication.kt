package com.xnvalabs.xnai

import android.app.Application
import android.util.Log
import org.opencv.android.OpenCVLoader

class XnaiApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Thread { XnaiBootstrap.initialize(this) }.apply { name = "xnai-bootstrap"; start() }
        XnaiAutonomyScheduler.setEnabled(this, XnaiSettings(this).autonomyEnabled)
        if (!OpenCVLoader.initLocal()) {
            Log.e("XNAI", "OpenCV initialization failed. Shape detection will be unavailable.")
        }
    }
}
