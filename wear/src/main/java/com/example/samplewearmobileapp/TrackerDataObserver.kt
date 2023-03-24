package com.example.samplewearmobileapp

interface TrackerDataObserver {
//    fun onHeartRateTrackerDataChanged(hrData: HeartRateData)
    fun onPpgGreenTrackerDataChanged(ppgGreenData: PpgGreenData)

    fun onError(errorResourceId: Int)
}