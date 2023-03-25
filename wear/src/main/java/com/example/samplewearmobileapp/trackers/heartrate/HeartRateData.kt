package com.example.samplewearmobileapp.trackers.heartrate

class HeartRateData {
    var status: Int = HeartRateStatus.HR_STATUS_NONE.code
    var hr = 0
    var ibi = 0
    var qIbi = 1

    internal constructor()
    internal constructor(status: Int, hr: Int, ibi: Int, qIbi: Int) {
        this.status = status
        this.hr = hr
        this.ibi = ibi
        this.qIbi = qIbi
    }

//    val hrIbi: Int
//        get() = qIbi shl IBI_QUALITY_SHIFT or ibi

    companion object {
        const val IBI_QUALITY_SHIFT = 15
        const val IBI_QUALITY_MASK = 0x1
        const val IBI_MASK = 0x7FFF
    }
}