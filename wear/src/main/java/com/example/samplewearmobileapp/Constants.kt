package com.example.samplewearmobileapp

object Constants {
    /**
     * Convert millisecond to seconds.
     */
    const val MS_TO_SEC = .001

    /**
     * Time covered in 1 data batch in ms.
     */
    const val BATCHING_DURATION: Long = 12000
    // 12000 ms = 12 sec

    /**
     * Sampling rate for PPG Green.
     * @see PPG_IR_RED_SAMPLE_RATE
     */
    const val PPG_GREEN_SAMPLE_RATE: Double = 25.0

    /**
     * Sampling rate for PPG IR and Red.
     * @see PPG_GREEN_SAMPLE_RATE
     */
    const val PPG_IR_RED_SAMPLE_RATE: Double = 100.0

    /**
     * The rate of PPG Green sampling event in ms.
     */
    const val PPG_GREEN_TICK_RATE: Long = 12000
    // 12000 ms = 12 sec. each 12 sec, sampling happens.

    /**
     * The rate of PPG IR and Red sampling event in ms.
     */
    const val PPG_IR_RED_TICK_RATE: Long =
        (1000 / PPG_IR_RED_SAMPLE_RATE).toLong()
    // each second 100 samplings happen

    /**
     * The rate of PPG IR and Red batching event batch in ms.
     */
    const val PPG_IR_RED_BATCH_TICK_RATE: Long = 3000
    // the same as PPG_GREEN_TICK_RATE = 12000 ms = 12 sec.
    // this is implemented due to flooding when data are sent each second
    // UPDATE: now using 3s

    /**
     * The number of PPG Green data points
     * to be included in 1 batch.
     */
    const val PPG_GREEN_BATCH_SIZE: Int =
        (PPG_GREEN_SAMPLE_RATE * PPG_GREEN_TICK_RATE * MS_TO_SEC).toInt()
    // 25 Hz * 12 sec = 300 data points

    /**
     * The number of PPG IR and Red data points
     * to be included in 1 batch.
     * This version used 300 data points.
     */
    const val PPG_IR_RED_BATCH_SIZE: Int =
        (PPG_IR_RED_SAMPLE_RATE * PPG_IR_RED_BATCH_TICK_RATE * MS_TO_SEC).toInt()
//        1200
//        (PPG_IR_RED_SAMPLE_RATE * PPG_IR_RED_CUSTOM_TICK_RATE * MS_TO_SEC).toInt()
//        (1 * PPG_IR_RED_SAMPLE_RATE).toInt() // = 100 data points
    // 100 Hz * 12 sec = 1200 data points
    // using actual tick rate OR 1 second would mean sending too frequently
    // causing the mobile module to hang and flooding the data queue
    // so instead, safely use 12 sec (also to match the PPG Green batch)
    // UPDATE: now using 3s
    // 100 Hz * 3000 ms / 1000 = 300 data points
}