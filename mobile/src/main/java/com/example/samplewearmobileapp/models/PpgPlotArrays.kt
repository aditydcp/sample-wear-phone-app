package com.example.samplewearmobileapp.models

/**
 * Class to hold arrays for plotting PPG.
 * These are arrays as opposed to the
 * LinkedList's in the respective series.
 * @property ppg The ppg values array.
 * @property timestamp The timestamp values array.
 */
class PpgPlotArrays (var ppg: IntArray, var timestamp: LongArray)