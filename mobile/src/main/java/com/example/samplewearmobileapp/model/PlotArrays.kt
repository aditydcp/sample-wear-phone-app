package com.example.samplewearmobileapp.model

/**
 * Class to hold arrays for plotting.  The ecg array is the ECG values.
 * The peaks arrays is the same length and is true or false depending on if
 * the ECG value corresponds to a peak. These are arrays as opposed to the
 * LinkedList's in the respective series.
 */
class PlotArrays(var ecg: DoubleArray, var peaks: BooleanArray)