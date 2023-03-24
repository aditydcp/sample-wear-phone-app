package com.example.samplewearmobileapp.models

import com.example.samplewearmobileapp.constants.codes.ActivityCode

data class Message(
    var sender: String,
    var content: String?,
    var code: Int,
    var extraCode: Int?
) {
    /**
     * Empty/dummy constructor
     */
    constructor() : this(
        "Dum dum",
        "Dummy message",
        ActivityCode.DO_NOTHING,
        null
    )

    /**
     * Initial constructor with only sender name.
     * @param sender Name of the sender
     */
    constructor(sender: String) : this(
        sender,
        null,
        ActivityCode.DO_NOTHING,
        null
    )

    /**
     * Default constructor for sending activity code.
     * @param sender Name of the sender
     * @param activityCode The activity code
     * @see ActivityCode
     */
    constructor(sender: String, activityCode: Int) : this(
        sender,
        null,
        activityCode,
        null
    )

    /**
     * Constructor for sending activity code with an extra.
     * @param sender Name of the sender
     * @param activityCode The activity code
     * @param extraCode Extra code
     * @see ActivityCode
     */
    constructor(sender: String, activityCode: Int, extraCode: Int) : this(
        sender,
        null,
        activityCode,
        extraCode
    )
}
