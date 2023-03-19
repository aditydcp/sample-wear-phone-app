package com.example.samplewearmobileapp

//object PpgGreenStatus {
//    const val PPG_GREEN_STATUS_NONE = 0
//    const val PPG_GREEN_STATUS_GOOD = 1
//}

enum class PpgGreenStatus(val code: Int, val statusText: String) {
    PPG_GREEN_STATUS_NONE(0,"None"),
    PPG_GREEN_STATUS_GOOD(1,"Good")
}