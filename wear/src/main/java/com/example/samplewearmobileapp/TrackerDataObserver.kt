package com.example.samplewearmobileapp

import com.example.samplewearmobileapp.trackers.ppggreen.PpgGreenData
import com.example.samplewearmobileapp.trackers.ppgir.PpgIrData
import com.example.samplewearmobileapp.trackers.ppgred.PpgRedData

interface TrackerDataObserver {
//    fun onHeartRateTrackerDataChanged(hrData: HeartRateData)
    fun onPpgGreenTrackerDataChanged(ppgGreenData: PpgGreenData)
    fun onPpgIrTrackerDataChanged(ppgIrData: PpgIrData)
    fun onPpgRedTrackerDataChanged(ppgRedData: PpgRedData)

    fun onError(errorResourceId: Int)
}