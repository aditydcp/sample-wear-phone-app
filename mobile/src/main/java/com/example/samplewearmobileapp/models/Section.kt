package com.example.samplewearmobileapp.models

import java.util.Date

data class Section(
    val id: Int,
    var name: String = "Section",
    val startTime: Date,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Section

        if (id != other.id) return false
        if (name != other.name) return false
        if (startTime != other.startTime) return false

        return true
    }
}
