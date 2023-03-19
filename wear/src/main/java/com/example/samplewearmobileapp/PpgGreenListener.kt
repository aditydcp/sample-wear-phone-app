package com.example.samplewearmobileapp

import android.util.Log
import com.samsung.android.service.health.tracking.HealthTracker
import com.samsung.android.service.health.tracking.data.DataPoint
import com.samsung.android.service.health.tracking.data.ValueKey

class PpgGreenListener internal constructor() : Listener() {
    private val tag = "PpgGreenListener"

    init {
        val trackerEventListener: HealthTracker.TrackerEventListener = object :
            HealthTracker.TrackerEventListener {
            override fun onDataReceived(list: List<DataPoint>) {
                if (list.isNotEmpty()) {
                    Log.i(tag, "Ppg Data List Size : " + list.size)
                    for (dataPoint in list) {
                        readValuesFromDataPoint(dataPoint)
                    }
                } else {
                    Log.i(tag, "onDataReceived List is empty")
                    readZeroValue()
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
            "Ppg Green Value : " + dataPoint.getValue(ValueKey.PpgGreenSet.PPG_GREEN)
        )

        val ppgGreenData = PpgGreenData()
        ppgGreenData.status = PpgGreenStatus.PPG_GREEN_STATUS_GOOD.code
        ppgGreenData.ppgValue = dataPoint.getValue(ValueKey.PpgGreenSet.PPG_GREEN)
        ppgGreenData.timestamp = dataPoint.timestamp

        TrackerDataNotifier.instance?.notifyPpgGreenTrackerObservers(ppgGreenData)
        Log.d(tag, dataPoint.toString())
    }

    fun readZeroValue() {
        val zeroPpgGreenData = PpgGreenData(
            PpgGreenStatus.PPG_GREEN_STATUS_NONE.code,
            0,
            0
        )
        TrackerDataNotifier.instance?.notifyPpgGreenTrackerObservers(zeroPpgGreenData)
        Log.d(tag, "Zero PPG Value notified")
    }
}