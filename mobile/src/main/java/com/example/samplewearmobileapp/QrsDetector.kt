package com.example.samplewearmobileapp

import com.example.samplewearmobileapp.Constants.A_DERIVATIVE
import com.example.samplewearmobileapp.Constants.B_DERIVATIVE
import com.example.samplewearmobileapp.Constants.DATA_WINDOW
import com.example.samplewearmobileapp.Constants.ECG_SAMPLE_RATE
import com.example.samplewearmobileapp.Constants.HR_200_INTERVAL
import com.example.samplewearmobileapp.Constants.MICRO_TO_MILLI_VOLT
import com.example.samplewearmobileapp.Constants.MOV_AVG_HR_WINDOW
import com.example.samplewearmobileapp.model.FixedSizeList
import com.example.samplewearmobileapp.model.RunningAverage
import com.polar.sdk.api.model.PolarEcgData
import java.util.*
import kotlin.math.sqrt

class QrsDetector(activity: MainActivity) {
    private var parentActivity: MainActivity = activity

    private var peakIndices: FixedSizeList<Int> = FixedSizeList(DATA_WINDOW)
    private var peakIndex = -1
    private var minPeakIndex = -1
    private var maxPeakIndex = -1

    private var sumValue: Double = STAT_INITIAL_MEAN * HR_200_INTERVAL
    private var sumSquare: Double = (STAT_INITIAL_STDDEV * STAT_INITIAL_STDDEV -
            STAT_INITIAL_MEAN * STAT_INITIAL_MEAN) * HR_200_INTERVAL
    private var statCount: Int = HR_200_INTERVAL
    private var mean = STAT_INITIAL_MEAN
    private var stdDev = STAT_INITIAL_STDDEV
    private var threshold = mean + N_SIGMA * stdDev

    private var sampleCount = 0
    private var startTime = Double.NaN

    // These keep track of the lowest and highest ECG values in the scoring
    // window. The max should correspond to R and the min to S. The normal
    // duration (interval) of the QRS complex is between 0.08 and 0.10
    // seconds When the duration is between 0.10 and 0.12 seconds, it is
    // intermediate or slightly prolonged. A QRS duration of greater than
    // 0.12 seconds is considered abnormal. 0.12 ms = 16 samples. 0.10
    // samples = 13 samples.

    //    private final FixedSizeList<Double> curButterworth =
    //            new FixedSizeList<>(DATA_WINDOW);
    private val cursorDerivative: FixedSizeList<Double> = FixedSizeList(DATA_WINDOW)
    private val cursorScore: FixedSizeList<Double> = FixedSizeList(DATA_WINDOW)
    private val cursorEcg: FixedSizeList<Double> = FixedSizeList(DATA_WINDOW)

    private val ecgValues: MutableList<Double> = ArrayList()

    /**
     * Moving average of the RR. Used to get the HR as 60 / avgRR.
     */
    private val movingAverageRr: RunningAverage = RunningAverage(MOV_AVG_HR_WINDOW)

    fun process(polarEcgData: PolarEcgData) {
        // Update the ECG plot
        ecgPlotter()?.addValues(polarEcgData)

        // samples contains the ecgValues values in μV, mv = .001 * μV;
        for (ecgValue in polarEcgData.samples) {
            detectQrs(MICRO_TO_MILLI_VOLT * ecgValue)
        }
    }

    /**
     * Runs the QRS detection algorithm on the given ECG value.
     *
     * @param ecg The value to process.
     */
    private fun detectQrs(ecg: Double) {
        // Record the start time as now.
        if (java.lang.Double.isNaN(startTime)) startTime = Date().time.toDouble()
        ecgValues.add(ecg)
        sampleCount++
        cursorEcg.add(ecg)
        val hr: Double
        val rr: Double

//        // Butterworth
//        input = curEcg;
//        if (curButterworth.size() == DATA_WINDOW) curButterworth.remove(0);
//        curButterworth.add(0.);    // Doesn't matter
//        doubleVal = filter(A_BUTTERWORTH3, B_BUTTERWORTH3, input,
//                curButterworth);
//        curButterworth.set(curButterworth.size() - 1, doubleVal);

        // Derivative (Only using positive part)
        var input: FixedSizeList<Double> = cursorEcg
        cursorDerivative.add(0.0) // Doesn't matter
        var doubleValue: Double = filter(
            A_DERIVATIVE, B_DERIVATIVE, input,
            cursorDerivative
        )
        doubleValue = doubleValue.coerceAtLeast(0.0)
        cursorDerivative[cursorDerivative.size - 1] = doubleValue

        // Score
        statCount++
        sumValue += doubleValue
        sumSquare += doubleValue * doubleValue
        mean = sumValue / statCount
        val variance: Double = sumSquare / statCount + mean * mean
        stdDev = sqrt(variance)
        threshold = mean + N_SIGMA * stdDev
        cursorScore.add(threshold)
        var maxEcg: Double
        val lastMaxEcgVal: Double
        val lastIndex: Int
        val lastPeakIndex: Int
        var startSearch: Int
        val endSearch: Int
        input = cursorDerivative
        val i = sampleCount - 1

        // Process finding the peaks
        if (i % HR_200_INTERVAL == 0) {
            // End of interval, process this interval
            if (i > 0 && peakIndex != -1) {
//                Log.d(TAG, "doAlgorithm: " +
//                        ".......... start processing interval i=" + i
//                        + " mPeakIndex=" + mPeakIndex
//                        + " mMinPeakIndex=" + mMinPeakIndex
//                        + " mMaxPeakIndex=" + mMaxPeakIndex
//                );
                // There is an mPeakIndex != -1
                // Look between mMinPeakIndex and mMaxPeakIndex for a the
                // largest ecg value
                startSearch = (i - HR_200_INTERVAL).coerceAtLeast(minPeakIndex)
                if (startSearch < 0) startSearch = 0
                endSearch = i.coerceAtMost(maxPeakIndex + SEARCH_EXTEND)
                maxEcg = -Double.MAX_VALUE
                //                Log.d(TAG, "doAlgorithm: " +
//                        ".......... start searching: startSearch="
//                        + startSearch
//                        + " endSearch=" + endSearch);
                for (i1 in startSearch until endSearch + 1) {
                    if (ecgValues[i1] > maxEcg) {
                        maxEcg = ecgValues[i1]
                        peakIndex = i1
                    }
                } // End of search
                //                Log.d(TAG, "doAlgorithm: " +
//                        ".......... end searching: startSearch="
//                        + startSearch
//                        + " endSearch=" + endSearch
//                        + " mPeakIndex=" + mPeakIndex
//                        + " maxEcg=" + maxEcg
//                );
                // Check if there is a close one in the previous interval
                if (peakIndices.size > 0) {
                    lastIndex = peakIndices.size - 1 // last
                    // index in mPeakIndices
                    lastPeakIndex = peakIndices[lastIndex]
                    if (peakIndex - lastPeakIndex < HR_200_INTERVAL) {
                        lastMaxEcgVal = ecgValues[lastPeakIndex]
                        if (maxEcg >= lastMaxEcgVal) {
                            // Replace the old one
                            peakIndices.last = peakIndex
                            qrsPlotter()!!.replaceLastPeakValue(
                                peakIndex,
                                maxEcg
                            )
                            //                            Log.d(TAG, "doAlgorithm: " +
//                                    "replaceLastPeakValue:"
//                                    + " mPeakIndex=" + mPeakIndex
//                                    + ", maxEcg=" + maxEcg);
                        }
                    } else {
                        // Is not near a previous one, add it
                        peakIndices.add(peakIndex)
                        qrsPlotter()!!.addPeakValue(
                            peakIndex,
                            maxEcg
                        )
                        //                        Log.d(TAG, "doAlgorithm: " +
//                                "addPeakValue:"
//                                + " mPeakIndex=" + mPeakIndex
//                                + ", maxEcg=" + maxEcg);
                    }
                } else {
                    // First peak
                    peakIndices.add(peakIndex)
                    qrsPlotter()!!.addPeakValue(peakIndex, maxEcg)
                    //                    Log.d(TAG, "doAlgorithm: " +
//                            "addPeakValue:"
//                            + " mPeakIndex=" + mPeakIndex
//                            + ", maxEcg=" + maxEcg);
                }

                // Do HR/RR plot
                if (peakIndices.size > 1) {
                    rr = 1000 / ECG_SAMPLE_RATE *
                            (peakIndex - peakIndices[peakIndices.size - 2])
                    hr = 60000.0 / rr
                    if (!java.lang.Double.isInfinite(hr)) {
//                    movingAverageHr.add(hr);
                        movingAverageRr.add(rr)
                        // Wait to start plotting until HR average is well
                        // defined
                        if (movingAverageRr.size() >= MOV_AVG_HR_WINDOW) {
//                        hrPlotter().addValues2(mStartTime + 1000 *
//                        mMaxIndex / FS,
//                                movingAverageHr.average(), rr);
                            hrPlotter()!!.addValues2(
                                startTime + 1000 * peakIndex / ECG_SAMPLE_RATE,
                                60000.0 / movingAverageRr.average(), rr
                            )
                            hrPlotter()!!.fullUpdate()
                        }
                    }
                }
            }
            // Start a new interval
            peakIndex = -1
            minPeakIndex = -1
            maxPeakIndex = -1
            //            Log.d(TAG, "doAlgorithm: " +
//                    ".......... end processing interval i=" + i);
        } // End of end of process interval

        // Check for max ecg
        val lastValue: Double = input.last
        val scoreValue: Double = cursorScore.last
        if (lastValue > scoreValue) {
            peakIndex = i
            if (peakIndex > maxPeakIndex) maxPeakIndex = peakIndex
            if (minPeakIndex == -1 || peakIndex < minPeakIndex) minPeakIndex = peakIndex
        }

        // Plot
        // Multipliers on curSquare and curScore should be the same
        val scaleFactor = 5.0
        qrsPlotter()!!.addValues(
            ecg, scaleFactor * cursorDerivative.last,
            scaleFactor * cursorScore.last
        )
    }

    /**
     * Calculates a result for a generalized filter with coefficients a and b.
     * Returns 0 if x and y are not long enough to provide sufficient values
     * for the sums over the coefficients.
     *
     *```
     * y[n] = 1 / a[0] * (suma - sumb)
     * suma = sum from 1 to q of a[j] * y[n-j], q = len(a)
     * sumb = sum from 0 to p of b[j] * x[n-j], p = len(b)
     * ```
     * Uses the values at the ends of x and y.
     *
     * @param a The A filter coefficients.
     * @param b The B filter coefficients.
     * @param x x values for the filter.
     * @param y y values for the filter.
     * @return The new value.
     */
    private fun filter(
        a: DoubleArray, b: DoubleArray, x: List<Double>,
        y: List<Double>?
    ): Double {
        // TODO Consider handling lenx < lenb and leny < lena differently
        //  rather than exit
        val lena = a.size
        val lenb = b.size
        val lenx = x.size
        val leny: Int
        if (lenx < lenb) return 0.0
        var suma = 0.0
        if (y != null) {
            leny = y.size
            if (leny < lena) return 0.0
            for (i in 0 until lena) {
                suma += a[i] * y[leny - i - 1]
            }
        }
        var sumb = 0.0
        for (i in 0 until lenb) {
            sumb += b[i] * x[lenx - i - 1]
        }
        return (sumb - suma) / a[0]
    }

    private fun ecgPlotter(): EcgPlotter? {
        return parentActivity.ecgPlotter
    }

    private fun qrsPlotter(): QrsPlotter? {
        return parentActivity.qrsPlotter
    }

    private fun hrPlotter(): HrPlotter? {
        return parentActivity.hrPlotter
    }

    companion object {
        // Initialize these with observed values
        private const val STAT_INITIAL_MEAN = .00
        private const val STAT_INITIAL_STDDEV = .1
        private const val N_SIGMA = 1.0
        private const val SEARCH_EXTEND = 2
    }
}