package com.example.samplewearmobileapp.constants.codes

/**
 * Constants to determine what a message is instructing
 * @see DO_NOTHING
 * @see START_ACTIVITY
 * @see STOP_ACTIVITY
 * @see ExtraCode
 */
object ActivityCode {
    //TODO("Need better Object. Combined special cases of Path and Codes")
    /**
     * Use this code to tell the receiver
     * that no further action is needed
     * after receiving the message
     */
    const val DO_NOTHING = 0

    /**
     * Use this code to tell the receiver
     * that they need to act
     * after receiving the message
     */
    const val START_ACTIVITY = 1

    /**
     * Use this code to tell the receiver
     * that they need to stop action(s)
     * after receiving the message
     */
    const val STOP_ACTIVITY = 2
}