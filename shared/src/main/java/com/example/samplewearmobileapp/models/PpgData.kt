package com.example.samplewearmobileapp.models

/**
 * PPG Data class for transportation between modules.
 *
 * Each object represents a batch of data.
 * @param ppgValues IntArray of individual PPG values
 * @param timestamps LongArray of timestamp of the
 * corresponding PPG value with the same index
 * @param ppgType The type of the PPG data stored
 * @param windowSize The maximum number of values can be stored
 * on each array.
 * @param size The actual number of values that is stored.
 *
 * @see PpgType
 */
data class PpgData(
//    var number: Int,
    var ppgValues : IntArray,
    var timestamps : LongArray,
    var ppgType: PpgType,
    var windowSize: Int,
    var size: Int = 0
) {
    constructor(windowSize: Int, ppgType: PpgType): this(
        IntArray(windowSize),
        LongArray(windowSize),
        ppgType,
        windowSize
    )

    /**
     * Clear PPG Value array and timestamp array.
     */
    fun clear() {
        for (i in 0 until windowSize) {
            ppgValues[i] = 0
            timestamps[i] = 0
            size = 0
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as PpgData

        if (!ppgValues.contentEquals(other.ppgValues)) return false
        if (!timestamps.contentEquals(other.timestamps)) return false
        if (ppgType != other.ppgType) return false
        if (windowSize != other.windowSize) return false

        return true
    }

    override fun hashCode(): Int {
        var result = ppgValues.contentHashCode()
        result = 31 * result + timestamps.contentHashCode()
        result = 31 * result + ppgType.hashCode()
        result = 31 * result + windowSize
        return result
    }

    override fun toString(): String {
        val string = StringBuilder()
        string.append("PpgData$").append(hashCode()).append(" :\n")
        string.append("Ppg Type: ").append(ppgType.name).append("\n")
        string.append("Window Size: ").append(windowSize).append("\n")
        string.append("Size: ").append(size).append("\n")
        return string.toString()
    }
}
