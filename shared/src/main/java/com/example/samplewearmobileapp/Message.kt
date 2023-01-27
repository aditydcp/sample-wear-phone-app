package com.example.samplewearmobileapp

data class Message(
    var content: String,
    var code: Int
) {
    constructor() : this("Dummy message", ActivityCode.DO_NOTHING)
}
