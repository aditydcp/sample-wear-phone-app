package com.example.samplewearmobileapp

object Constants {
    /**
     * Convert millisecond to seconds.
     */
    const val MS_TO_SEC = .001

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
     * The number of PPG Green data points
     * to be included in 1 batch.
     */
    const val PPG_GREEN_BATCH_SIZE: Int =
        (PPG_GREEN_SAMPLE_RATE * PPG_GREEN_TICK_RATE * MS_TO_SEC).toInt()
    // 25 Hz * 12 sec = 300 data points

    /**
     * The number of PPG IR and Red data points
     * to be included in 1 batch.
     */
    const val PPG_IR_RED_BATCH_SIZE: Int =
        (1 * PPG_IR_RED_SAMPLE_RATE).toInt() // = 100 data points
    // using tick rate would be too small
    // and causing data loss in transportation
    // so instead, batch data every 1 second(s)
}