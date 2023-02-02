package com.example.samplewearmobileapp

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

data class HeartData(
    var hr: Int,
    var ibi: Int,
    var timestamp: String
) {
    constructor() : this(0,0,
        LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME).toString())
}