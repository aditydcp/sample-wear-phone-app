package com.example.samplewearmobileapp.constants

object MessagePath {
    /**
     * Message path for instructing the receiver to do something.
     * For requesting information, use `REQUEST` path.
     * @see INFO
     * @see REQUEST
     */
    const val COMMAND = "/command" // instructing to do something

    /**
     * Message path for requesting information.
     * The response of a request must use `INFO` path.
     * @see INFO
     * @see COMMAND
     */
    const val REQUEST = "/request" // requesting information

    /**
     * Message path for providing information
     * @see REQUEST
     * @see COMMAND
     */
    const val INFO = "/info" // providing information

    const val DATA_HR = "/datahr" // for HR Data traffic
    const val DATA_PPG_GREEN = "/datappggreen" // for PPG Green Data traffic
    const val DATA_PPG_IR = "/datappgir" // for PPG IR Data traffic
    const val DATA_PPG_RED = "/datappgred" // for PPG Red Data traffic
}