package com.example.samplewearmobileapp.trackers.ppggreen

class PpgGreenData {
    var ppgValue = 0
    var status = PpgGreenStatus.PPG_GREEN_STATUS_NONE.code
    var timestamp: Long = 0

    internal constructor()
    internal constructor(status: Int) {
        this.status = status
    }
    internal constructor(status: Int, ppgValue: Int) {
        this.status = status
        this.ppgValue = ppgValue
    }
    internal constructor(status: Int, ppgValue: Int, timestamp: Long) {
        this.status = status
        this.ppgValue = ppgValue
        this.timestamp = timestamp
    }
}