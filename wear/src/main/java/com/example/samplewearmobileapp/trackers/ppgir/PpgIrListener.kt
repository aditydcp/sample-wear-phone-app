package com.example.samplewearmobileapp.trackers.ppgir

import android.util.Log
import com.example.samplewearmobileapp.R
import com.example.samplewearmobileapp.TrackerDataNotifier
import com.example.samplewearmobileapp.trackers.Listener
import com.samsung.android.service.health.tracking.HealthTracker
import com.samsung.android.service.health.tracking.data.DataPoint
import com.samsung.android.service.health.tracking.data.ValueKey

class PpgIrListener internal constructor() : Listener() {
    private val tag = "PpgIrListener"

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
            "Ppg InfraRed Value : " + dataPoint.getValue(ValueKey.PpgIrSet.PPG_IR)
        )

        val ppgIrData = PpgIrData()
        ppgIrData.ppgValue = dataPoint.getValue(ValueKey.PpgIrSet.PPG_IR)
        ppgIrData.timestamp = dataPoint.timestamp

        TrackerDataNotifier.instance?.notifyPpgIrTrackerObservers(ppgIrData)
        Log.d(tag, dataPoint.toString())
    }
}