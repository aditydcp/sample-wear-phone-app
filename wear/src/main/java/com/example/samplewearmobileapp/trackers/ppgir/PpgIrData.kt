package com.example.samplewearmobileapp.trackers.ppgir

class PpgIrData {
    var ppgValue = 0
    var timestamp: Long = 0

    internal constructor()
    internal constructor(ppgValue: Int, timestamp: Long) {
        this.ppgValue = ppgValue
        this.timestamp = timestamp
    }
}