package com.example.samplewearmobileapp

import kotlin.math.roundToInt

object Constants {
    var PREF_DEVICE_ID = "deviceId"
//    var PREF_ACQ_DEVICE_IDS = "mruDeviceIds"
    var PREF_TREE_URI = "treeUri"
    var PREF_PATIENT_NAME = "patientName"
    var PREF_ANALYSIS_VISIBILITY = "analysisVisibility"

    /**
     * Sampling rate for ECG.
     * @see PPG_GREEN_SAMPLE_RATE
     * @see PPG_IR_RED_SAMPLE_RATE
     */
    const val ECG_SAMPLE_RATE: Double = 130.0

    /**
     * Sampling rate for PPG Green.
     * @see PPG_IR_RED_SAMPLE_RATE
     * @see ECG_SAMPLE_RATE
     */
    const val PPG_GREEN_SAMPLE_RATE: Double = 25.0

    /**
     * Sampling rate for PPG IR and Red.
     * @see PPG_GREEN_SAMPLE_RATE
     * @see ECG_SAMPLE_RATE
     */
    const val PPG_IR_RED_SAMPLE_RATE: Double = 100.0

    /**
     * Number of small boxes in a large box.
     */
    var N_SMALL_BOXES_PER_LARGE_BOX = 5

    /**
     * The number of samples in a large box on an ECG plot.
     */
    var N_LARGE: Int =
        (ECG_SAMPLE_RATE / N_SMALL_BOXES_PER_LARGE_BOX).toInt() // =26

    /**
     * The total number of points to record for imager.
     */
    var N_TOTAL_VISIBLE_ECG_POINTS: Int =
        (30 * ECG_SAMPLE_RATE).toInt() // =3900 -> 30 sec

    /**
     * The number of points to show in an ECG plot.
     */
    var N_ECG_PLOT_POINTS = 4 * N_SMALL_BOXES_PER_LARGE_BOX * N_LARGE
    // =520 points -> 4 sec

    /**
     * Number of large boxes visible on the x axis.
     */
    var N_DOMAIN_LARGE_BOXES = N_ECG_PLOT_POINTS / N_LARGE // = 20

    // needed to show 4 secs in PPG plot to match the ECG plot
    /**
     * The number of points to show in PPG Green plot
     */
    var N_PPG_GREEN_PLOT_POINTS = (4 * PPG_GREEN_SAMPLE_RATE).toInt()
    // 4 secs * 25 Hz = 100 points

    /**
     * The number of points to show in PPG IR & Red plot
     */
    var N_PPG_IR_RED_PLOT_POINTS = (4 * PPG_IR_RED_SAMPLE_RATE).toInt()
    // 4 secs * 100 Hz = 400 points

    /**
     * Ratio of mV to mm for a box.
     */
    var RATIO_MM_MV = 100

    /**
     * Data window size. Must be large enough for maximum number of
     * coefficients.
     */
    var DATA_WINDOW = 20

    /**
     * Moving average data window size.
     */
    var MOV_AVG_WINDOW = 20

    /**
     * Moving average HR window size.
     */
    var MOV_AVG_HR_WINDOW = 25

    /**
     * Moving average height window size.
     */
    var MOV_AVG_HEIGHT_WINDOW = 5

    /**
     * Moving average height default.
     */
    var MOV_AVG_HEIGHT_DEFAULT = .025

    /**
     * Moving average height threshold factor.
     * Note: threshold = MOV_AVG_HEIGHT_THRESHOLD_FACTOR * Moving_average.avg()
     */
    var MOV_AVG_HEIGHT_THRESHOLD_FACTOR = .4

    /**
     * The maximum number of samples between R and S. The normal
     * duration (interval) of the QRS complex is between 0.08 and 0.10
     * seconds When the duration is between 0.10 and 0.12 seconds, it is
     * intermediate or slightly prolonged. A QRS duration of greater than
     * 0.12 seconds is considered abnormal.
     */
    var MAX_QRS_LENGTH =
        (.12 * ECG_SAMPLE_RATE).roundToInt() // 13

    /***
     * The heart rate interval. The algorithm is based on there being only one
     * heart beat in this interval. Assumes maximum heart rate is 200.
     */
    var HR_200_INTERVAL: Int =
        (60.0 / 200.0 * ECG_SAMPLE_RATE).toInt() // 39

    /**
     * How many seconds the domain interval is for the HRPlotter.
     */
    var HR_PLOT_DOMAIN_INTERVAL = (1 * 60000 // 1 min
            ).toLong()

    /**
     * Number of standard deviations above mean to use to threshold.
     */
    var NUMBER_OF_STDDEV = 2

    /**
     * Convert μV to mV.
     */
    var MICRO_TO_MILLI_VOLT = .001

    /**
     * Convert millisecond to seconds.
     */
    var MS_TO_SEC = .001

    /**
     * Filter coefficients for Butterworth fs=130 low_cutoff=5 high_cutoff=20
     */
    var A_BUTTERWORTH3 = doubleArrayOf(
        1.0, -4.026234474291334,
        7.118704187414651,
        -7.142612123715484, 4.314550872956459,
        -1.4837877480823038, 0.2259301306922936
    )
    var B_BUTTERWORTH3 = doubleArrayOf(
        0.025966345753506013, 0.0,
        -0.07789903726051804,
        0.0, 0.07789903726051804, 0.0, -0.025966345753506013
    )

    /**
     * Filter coefficients for Pan Tompkins derivative
     */
    var A_DERIVATIVE = doubleArrayOf(12.0)
    var B_DERIVATIVE = doubleArrayOf(25.0, -48.0, 36.0, -16.0, 3.0)

}