package com.example.samplewearmobileapp.models

import java.util.ArrayList

class RunningMax(private val windowSize: Int) {
    private var values: MutableList<Double> = ArrayList()
    fun add(value: Double) {
        values.add(value)
        while (values.size > windowSize) {
            values.removeAt(0)
        }
    }

    fun max(): Double {
        var max = -Double.MAX_VALUE
        for (value in values) {
            if (value > max) max = value
        }
        return max
    }

    fun size(): Int {
        return values.size
    }
}