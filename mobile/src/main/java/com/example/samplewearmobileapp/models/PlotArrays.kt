package com.example.samplewearmobileapp.models

/**
 * Class to hold arrays for plotting.
 *  These are arrays as opposed to the
 * LinkedList's in the respective series.
 * @property ecg The ecg array is the ECG values.
 * @property peaks The peaks arrays is the same length as ecg array
 * and is true or false depending on if the ECG value corresponds to a peak.
 * @property timestamp The timestamp as taken from the Polar device.
 */
class PlotArrays(
    var ecg: DoubleArray,
    var peaks: BooleanArray,
    var timestamp: LongArray
    )