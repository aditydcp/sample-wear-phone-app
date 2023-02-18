package com.example.samplewearmobileapp

import android.util.Log
import org.junit.Test

import org.junit.Assert.*
import java.util.*

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {
    private val uuid = "2A37"

    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }

    @Test
    fun stringUUID_isCorrect() {
        val length = uuid.toString().length
//        val leadingZeroesCount = 8 - length

        var uuidString = ""
        for (i in 8 downTo length+1) {
            uuidString = uuidString.plus("0")
        }
        uuidString = uuidString.plus(uuid.toString())
            .plus("-0000-1000-8000-00805F9B34FB")

        assertEquals("00002A37-0000-1000-8000-00805F9B34FB",uuidString)
    }

    @Test
    fun stringUUID_isEqualToUuid() {
        val length = uuid.toString().length
//        val leadingZeroesCount = 8 - length

        var uuidString = ""
        for (i in 8 downTo length+1) {
            uuidString = uuidString.plus("0")
        }
        uuidString = uuidString.plus(uuid.toString())
            .plus("-0000-1000-8000-00805F9B34FB")

        val realUUID = UUID.fromString(uuidString)
        assertEquals(realUUID.toString(), uuidString)
    }
}