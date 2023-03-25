package com.example.samplewearmobileapp.trackers.ppgred

class PpgRedData {
    var ppgValue = 0
    var timestamp: Long = 0

    internal constructor()
    internal constructor(ppgValue: Int, timestamp: Long) {
        this.ppgValue = ppgValue
        this.timestamp = timestamp
    }
}