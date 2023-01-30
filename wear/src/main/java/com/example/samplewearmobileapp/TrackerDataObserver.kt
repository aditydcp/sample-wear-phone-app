package com.example.samplewearmobileapp

interface TrackerDataObserver {
    fun onHeartRateTrackerDataChanged(hrData: HeartRateData)

    fun onError(errorResourceId: Int)
}