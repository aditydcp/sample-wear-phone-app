package com.example.samplewearmobileapp

data class Message(
    var sender: String,
    var content: String,
    var code: Int
) {
    constructor() : this("Dum dum","Dummy message", ActivityCode.DO_NOTHING)
}
