package com.example.samplewearmobileapp

//object HeartRateStatus {
//    const val HR_STATUS_NONE = 0
//    const val HR_STATUS_FIND_HR = 1
//    const val HR_STATUS_ATTACHED = -1
//    const val HR_STATUS_DETECT_MOVE = -2
//    const val HR_STATUS_DETACHED = -3
//    const val HR_STATUS_LOW_RELIABILITY = -8
//    const val HR_STATUS_VERY_LOW_RELIABILITY = -10
//    const val HR_STATUS_NO_DATA_FLUSH = -99
//}

enum class HeartRateStatus(val code: Int, val statusText: String) {
    HR_STATUS_NONE(0,"None"),
    HR_STATUS_FIND_HR(1,"Good"),
    HR_STATUS_ATTACHED(-1,"Attached"),
    HR_STATUS_DETECT_MOVE(-2,"Movement Detected"),
    HR_STATUS_DETACHED(-3, "Detached"),
    HR_STATUS_LOW_RELIABILITY(-8, "Low Reliability"),
    HR_STATUS_VERY_LOW_RELIABILITY(-10,"Very Low Reliability"),
    HR_STATUS_NO_DATA_FLUSH(-99,"No Data Flush")
}