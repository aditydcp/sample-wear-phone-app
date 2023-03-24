package com.example.samplewearmobileapp

import android.util.Log
import com.samsung.android.service.health.tracking.HealthTracker
import com.samsung.android.service.health.tracking.data.DataPoint
import com.samsung.android.service.health.tracking.data.ValueKey

class PpgRedListener internal constructor() : Listener() {
    private val tag = "PpgRedListener"

    init {
        val trackerEventListener: HealthTracker.TrackerEventListener = object :
            HealthTracker.TrackerEventListener {
            override fun onDataReceived(list: List<DataPoint>) {
                for (data in list) {
                    readValuesFromDataPoint(data)
                }
            }

            override fun onFlushCompleted() {
                Log.i(tag, " onFlushCompleted called")
            }

            override fun onError(trackerError: HealthTracker.TrackerError) {
                Log.e(tag, " onError called: $trackerError")
                setHandlerRunning(false)
                if (trackerError == HealthTracker.TrackerError.PERMISSION_ERROR) {
                    TrackerDataNotifier.instance?.notifyError(R.string.NoPermission)
                }
                if (trackerError == HealthTracker.TrackerError.SDK_POLICY_ERROR) {
                    TrackerDataNotifier.instance?.notifyError(R.string.SdkPolicyError)
                }
            }
        }
        setTrackerEventListener(trackerEventListener)
    }

    fun readValuesFromDataPoint(dataPoint: DataPoint) {
        Log.i(tag, "Timestamp : " + dataPoint.timestamp)
        Log.i(
            tag,
            "Ppg InfraRed Value : " + dataPoint.getValue(ValueKey.PpgRedSet.PPG_RED)
        )

        val ppgRedData = PpgRedData()
        ppgRedData.ppgValue = dataPoint.getValue(ValueKey.PpgRedSet.PPG_RED)
        ppgRedData.timestamp = dataPoint.timestamp

        TrackerDataNotifier.instance?.notifyPpgRedTrackerObservers(ppgRedData)
        Log.d(tag, dataPoint.toString())
    }
}