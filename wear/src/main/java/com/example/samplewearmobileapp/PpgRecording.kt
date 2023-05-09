package com.example.samplewearmobileapp

import android.util.Log
import com.example.samplewearmobileapp.models.PpgType

class PpgRecording() {
    val values = mutableListOf<Int>()
    val timestamps = mutableListOf<Long>()
    lateinit var ppgType: PpgType

    constructor(type: PpgType): this() {
        this.ppgType = type
    }

    constructor(values: List<Int>, timestamps: List<Long>, type: PpgType): this() {
        for (value in values) {
            this.values.add(value)
        }
        for (timestamp in timestamps) {
            this.timestamps.add(timestamp)
        }
        this.ppgType = type
    }

    fun add(value: Int, timestamp: Long) {
        values.add(value)
        timestamps.add(timestamp)
    }

    fun clearFromStartUntil(index: Int) {
//        for (i in 0 until index + 1) {
//            values.removeAt(i)
//            timestamps.removeAt(i)
//        }
        for (i in 1 until index) {
            values.removeFirst()
            timestamps.removeFirst()
        }
        Log.i("Wear: PpgRecording.clearFromStartUntil()","Done.\n" +
                "Values size now: ${values.size}\n" +
                "Timestamps size now: ${timestamps.size}")
    }

    fun getSize(): Int? {
        if (values.size != timestamps.size) {
            Log.e("Wear: PpgRecording","Values and timestamps don't match.\n" +
                    "Values size: ${values.size}\n" +
                    "Timestamps size: ${timestamps.size}")
            return null
        }
        return values.size
    }
}