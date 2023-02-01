package com.example.samplewearmobileapp

import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

data class HeartData(
    var hr: Int,
    var ibi: Int,
    var timestamp: LocalDateTime
) {
    constructor() : this(0,0, LocalDateTime.now())
}