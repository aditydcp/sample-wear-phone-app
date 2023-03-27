package com.example.samplewearmobileapp.trackers

import android.os.Handler
import android.util.Log
import com.samsung.android.service.health.tracking.HealthTracker
import com.samsung.android.service.health.tracking.HealthTracker.TrackerEventListener

open class Listener {
    private val tag = "Listener Base Class"

    private lateinit var handler: Handler
    private lateinit var healthTracker: HealthTracker
    private var isHandlerRunning = false

    private lateinit var trackerEventListener: TrackerEventListener

    fun setHealthTracker(tracker: HealthTracker) {
        healthTracker = tracker
    }

    fun setHandler(handler: Handler) {
        this.handler = handler
    }

    fun setHandlerRunning(handlerRunning: Boolean) {
        isHandlerRunning = handlerRunning
    }

    fun setTrackerEventListener(tracker: TrackerEventListener) {
        trackerEventListener = tracker
    }

    fun startTracker() {
        Log.i(tag, "startTracker called ")
        Log.d(tag, "healthTracker: $healthTracker")
        Log.d(tag, "trackerEventListener: $trackerEventListener")
        if (!isHandlerRunning) {
            handler.post {
                healthTracker.setEventListener(trackerEventListener)
                setHandlerRunning(true)
            }
        }
    }

    fun stopTracker() {
        Log.i(tag, "stopTracker called ")
        Log.d(tag, "healthTracker: $healthTracker")
        Log.d(tag, "trackerEventListener: $trackerEventListener")
        if (isHandlerRunning) {
            healthTracker.unsetEventListener()
            setHandlerRunning(false)

            handler.removeCallbacksAndMessages(null)
        }
    }

    fun isTracking(): Boolean {
        return isHandlerRunning
    }
}