package com.example.samplewearmobileapp.model

import java.util.*

class RunningAverage(private var maxItems: Int) {
    private var list: LinkedList<Double>? = null
    private var sum = 0.0

    init {
        list = LinkedList()
        sum = 0.0
    }

    fun add(value: Double) {
        if (list!!.size == maxItems) {
            sum -= list!!.first
            list!!.removeFirst()
        }
        list!!.add(value)
        sum += value
    }

    fun average(): Double {
        return if (list!!.size == 0) 0.0 else sum / list!!.size
    }

    fun sum(): Double {
        return sum
    }

    fun size(): Int {
        return list!!.size
    }
}