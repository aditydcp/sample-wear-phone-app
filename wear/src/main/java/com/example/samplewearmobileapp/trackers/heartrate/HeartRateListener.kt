package com.example.samplewearmobileapp.trackers.heartrate

import android.util.Log
import com.example.samplewearmobileapp.trackers.Listener
import com.example.samplewearmobileapp.R
import com.example.samplewearmobileapp.TrackerDataNotifier
import com.samsung.android.service.health.tracking.HealthTracker
import com.samsung.android.service.health.tracking.data.DataPoint
import com.samsung.android.service.health.tracking.data.ValueKey

class HeartRateListener internal constructor() : Listener() {
    private val tag = "HeartRateListener"

    init {
        val trackerEventListener: HealthTracker.TrackerEventListener = object :
            HealthTracker.TrackerEventListener {
            override fun onDataReceived(list: List<DataPoint>) {
                for (dataPoint in list) {
                    readValuesFromDataPoint(dataPoint)
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
        val hrData = HeartRateData()
        hrData.status = dataPoint.getValue(ValueKey.HeartRateSet.STATUS)
        hrData.hr = dataPoint.getValue(ValueKey.HeartRateSet.HEART_RATE)

        val hrIbi = dataPoint.getValue(ValueKey.HeartRateSet.HEART_RATE_IBI)
        hrData.qIbi = hrIbi shr HeartRateData.IBI_QUALITY_SHIFT and HeartRateData.IBI_QUALITY_MASK
        hrData.ibi = hrIbi and HeartRateData.IBI_MASK

//        TrackerDataNotifier.instance?.notifyHeartRateTrackerObservers(hrData)
        Log.d(tag, dataPoint.toString())
    }
}