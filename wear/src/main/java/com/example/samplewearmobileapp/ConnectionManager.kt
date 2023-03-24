package com.example.samplewearmobileapp

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.samsung.android.service.health.tracking.ConnectionListener
import com.samsung.android.service.health.tracking.HealthTrackerException
import com.samsung.android.service.health.tracking.HealthTrackingService
import com.samsung.android.service.health.tracking.data.HealthTrackerType

class ConnectionManager(observer: ConnectionObserver) {
    private val tag = "Connection Manager"
    private var connectionObserver : ConnectionObserver = observer
    private lateinit var healthTrackingService: HealthTrackingService
    private val connectionListener: ConnectionListener = object : ConnectionListener {
        override fun onConnectionSuccess() {
            Log.i(tag,"Connected")
            connectionObserver.onConnectionResult(R.string.ConnectedToHs)
            if (!isHeartRateAvailable(healthTrackingService)) {
                Log.i(tag, "Device does not support Heart Rate tracking")
                connectionObserver.onConnectionResult(R.string.NoHrSupport)
            }
        }

        override fun onConnectionEnded() {
            Log.i(tag, "Disconnected")
        }

        override fun onConnectionFailed(e: HealthTrackerException?) {
            connectionObserver.onError(e)
        }
    }

    fun connect(context: Context?) {
        healthTrackingService = HealthTrackingService(connectionListener, context)
        healthTrackingService.connectService()
    }

    fun disconnect() {
        healthTrackingService.disconnectService()
    }

//    fun initHeartRate(heartRateListener: HeartRateListener) {
//        val healthTracker = healthTrackingService.getHealthTracker(HealthTrackerType.HEART_RATE)
//        heartRateListener.setHealthTracker(healthTracker)
//        setHandlerForListener(heartRateListener)
//    }

    fun initPpgGreen(ppgGreenListener: PpgGreenListener) {
        val healthTracker = healthTrackingService.getHealthTracker(HealthTrackerType.PPG_GREEN)
        ppgGreenListener.setHealthTracker(healthTracker)
        setHandlerForListener(ppgGreenListener)
    }

    private fun setHandlerForListener(listener: Listener) {
        listener.setHandler(Handler(Looper.getMainLooper()))
    }

    private fun isHeartRateAvailable(healthTrackingService: HealthTrackingService): Boolean {
        val availableTrackers = healthTrackingService.trackingCapability.supportHealthTrackerTypes
        return availableTrackers.contains(HealthTrackerType.HEART_RATE)
    }
}