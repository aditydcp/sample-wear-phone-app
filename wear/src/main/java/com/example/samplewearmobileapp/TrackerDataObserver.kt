package com.example.samplewearmobileapp

interface TrackerDataObserver {
//    fun onHeartRateTrackerDataChanged(hrData: HeartRateData)
    fun onPpgGreenTrackerDataChanged(ppgGreenData: PpgGreenData)
    fun onPpgIrTrackerDataChanged(ppgIrData: PpgIrData)
    fun onPpgRedTrackerDataChanged(ppgRedData: PpgRedData)

    fun onError(errorResourceId: Int)
}