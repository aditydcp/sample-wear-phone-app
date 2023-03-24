package com.example.samplewearmobileapp.constants.codes

/**
 * Extra constants for detailed instruction that
 * `ActivityCode` fails to convey.
 *
 * @see ActivityCode
 */
object ExtraCode {
    // Commandeering PPG Green activity
    const val STOP_PPG_GREEN = 0x01
    const val START_PPG_GREEN = 0x02
    const val RESTART_PPG_GREEN = 0x03
    const val DATA_PPG_GREEN = 0x04

    // Commandeering PPG IR activity
    const val STOP_PPG_IR = 0x11
    const val START_PPG_IR = 0x12
    const val RESTART_PPG_IR = 0x13
    const val DATA_PPG_IR = 0x14

    // Commandeering PPG Red activity
    const val STOP_PPG_RED = 0x21
    const val START_PPG_RED = 0x22
    const val RESTART_PPG_RED = 0x23
    const val DATA_PPG_RED = 0x24
}