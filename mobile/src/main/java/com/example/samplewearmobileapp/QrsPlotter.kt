package com.example.samplewearmobileapp

import android.graphics.Color
import android.util.Log
import android.view.View
import com.androidplot.util.PixelUtils
import com.androidplot.xy.*
import com.example.samplewearmobileapp.Constants.N_DOMAIN_LARGE_BOXES
import com.example.samplewearmobileapp.Constants.N_ECG_PLOT_POINTS
import com.example.samplewearmobileapp.Constants.N_LARGE
import com.example.samplewearmobileapp.Constants.N_TOTAL_POINTS
import com.example.samplewearmobileapp.utils.AppUtils

class QrsPlotter {
    private lateinit var parentActivity: MainActivity
    private var plot: XYPlot

    // ECG
    private lateinit var formatterEcg: XYSeriesFormatter<XYRegionFormatter>
    lateinit var seriesEcg: SimpleXYSeries

    // Square
    private lateinit var formatterSquares: XYSeriesFormatter<XYRegionFormatter>
    lateinit var seriesSquares: SimpleXYSeries

    // Score
    private lateinit var formatterScores: XYSeriesFormatter<XYRegionFormatter>
    lateinit var seriesScores: SimpleXYSeries

    // Peaks
    private lateinit var formatterPeaks: XYSeriesFormatter<XYRegionFormatter>
    lateinit var seriesPeaks: SimpleXYSeries

    /**
     * The next index in the data (or the length of the series.)
     */
    var dataIndex: Long = 0

    /**
     * Simplified constructor.
     * @param plot The XYPlot.
     */
    constructor(plot: XYPlot) {
        this.plot = plot
        // Don't do anything else
    }

    /**
     * Full constructor.
     * @param activity Parent activity (MainActivity).
     * @param plot The XYPlot.
     */
    constructor(activity: MainActivity, plot: XYPlot) {
        Log.d(TAG, this.javaClass.simpleName + " QrsPlotter constructor")
        // This is the parent activity, needed for resources
        this.parentActivity = activity
        this.plot = plot
        dataIndex = 0
        formatterEcg = LineAndPointFormatter(
            Color.rgb(0, 153, 255),
            null, null, null
        )
        formatterEcg.isLegendIconEnabled = false
        seriesEcg = SimpleXYSeries("ECG")
        formatterSquares = LineAndPointFormatter(
            Color.rgb(255, 216, 0),
            null, null, null
        )
        formatterSquares.isLegendIconEnabled = false
        seriesSquares = SimpleXYSeries("Derivative")
        formatterScores = LineAndPointFormatter(
            Color.rgb(50, 205, 50),
            null, null, null
        ) // Crimson
        formatterScores.isLegendIconEnabled = false
        seriesScores = SimpleXYSeries("Square")
        formatterPeaks = LineAndPointFormatter(null, Color.RED, null, null)
        formatterPeaks.isLegendIconEnabled = false
//        ((LineAndPointFormatter)mFormatter4).getVertexPaint()
//        .setStrokeWidth(20);
        seriesPeaks = SimpleXYSeries("Peaks")
        this.plot.addSeries(seriesSquares, formatterSquares)
        this.plot.addSeries(seriesScores, formatterScores)
        this.plot.addSeries(seriesPeaks, formatterPeaks)
        this.plot.addSeries(seriesEcg, formatterEcg)
        setupPlot()
    }

    /**
     * Sets the plot parameters, calculating the range boundaries to have the
     * same grid as the domain.  Calls update when done.
     */
    fun setupPlot() {
        Log.d(TAG, this.javaClass.simpleName + " setupPlot")
        if (plot.visibility == View.GONE) return
        try {
            // Calculate the range limits to make the blocks be square
            // Using .5 mV and nLarge / samplingRate for total grid size
            // rMax is half the total, rMax at top and -rMax at bottom
            val rMax: Double
            val gridRect = plot.graph.gridRect
            rMax = if (gridRect == null) {
                Log.d(
                    TAG, """QrsPlotter.setupPlot: gridRect is null
                        |thread: ${Thread.currentThread().name}""".trimMargin()
                )
                return
            } else {
                (.25 * N_DOMAIN_LARGE_BOXES * gridRect.height()
                        / gridRect.width())
            }

            // Range
            // Set the range block to be .1 mV so a large block will be .5 mV
            plot.setRangeBoundaries(-rMax, rMax, BoundaryMode.FIXED)
            // Make the x axis visible
            val color = plot.graph.rangeGridLinePaint.color
            plot.graph.rangeOriginLinePaint.color = color
            plot.graph.rangeOriginLinePaint.strokeWidth = PixelUtils.dpToPix(1.5f)
            plot.setRangeStep(StepMode.INCREMENT_BY_VAL, .5)
            plot.linesPerRangeLabel = 5
            // Make it be centered
            plot.setUserRangeOrigin(0.0)

            // Domain
            updateDomainBoundaries()
            // Set the domain block to be .2 * N_LARGE so large block will be
            // nLarge samples
            plot.setDomainStep(StepMode.INCREMENT_BY_VAL, N_LARGE.toDouble())

//            // Allow panning
//            PanZoom.attach(mPlot, PanZoom.Pan.HORIZONTAL, PanZoom.Zoom.NONE);

            // Update the plot
            update()
        } catch (ex: Exception) {
            val msg = """Error in QrsPlotter.setupPLot:
                |isLaidOut=${plot.isLaidOut}
                |width=${plot.width}
                |height=${plot.height}""".trimMargin()
            AppUtils.excMsg(parentActivity, msg, ex)
            Log.e(TAG, msg, ex)
        }
    }

    /**
     * Implements a strip chart adding new data at the end.
     *
     * @param ecg Value for the first series. Ecg
     * @param square Value for the second series. Square
     * @param score Value for the third series. Score
     */
    fun addValues(ecg: Number?, square: Number?, score: Number?) {
//        Log.d(TAG, this.getClass().getSimpleName()
//                + "addValues: dataIndex=" + mDataIndex + " mSeriesSize="
//                + mSeries1.size() + " mSeries2Size=" + mSeries2.size()
//                + " val1=" + val1 + " val2=" + val2);
        // Add the new values, removing old values if needed
        // Convert from  μV to mV
        if (ecg != null) {
            if (seriesEcg.size() >= N_TOTAL_POINTS) {
                seriesEcg.removeFirst()
            }
            seriesEcg.addLast(dataIndex, ecg)
        }
        if (square != null) {
            if (seriesSquares.size() >= N_TOTAL_POINTS) {
                seriesSquares.removeFirst()
            }
            seriesSquares.addLast(dataIndex, square)
        }
        if (score != null) {
            if (seriesScores.size() >= N_TOTAL_POINTS) {
                seriesScores.removeFirst()
            }
            seriesScores.addLast(dataIndex, score)
        }
        dataIndex++
        // Reset the domain boundaries
        updateDomainBoundaries()
        update()
    }

    fun addPeakValue(sample: Int, ecg: Double) {
//        Log.d(TAG, this.getClass().getSimpleName()
//                + "addPeakValue: dataIndex=" + mDataIndex + " mSeriesSize="
//                + mSeries4.size()
//                + " sample=" + sample + " ecg=" + ecg);
//
        // Remove old values if needed
        removeOutOfRangeValues()
        seriesPeaks.addLast(sample, ecg)
//        Log.d(TAG, "Added peak value: sample=" + sample + " size=" +
//        mSeries4.size()
//                + " ecg=" + ecg + " mDataIndex=" + mDataIndex);
    }

    fun replaceLastPeakValue(sample: Int, ecg: Double) {
//        Log.d(TAG, this.getClass().getSimpleName()
//                + "addPeakValue: dataIndex=" + mDataIndex + " mSeriesSize="
//                + mSeries4.size()
//                + " sample=" + sample + " ecg=" + ecg);
//
        // Remove old values if needed
        removeOutOfRangeValues()
        seriesPeaks.removeLast()
        seriesPeaks.addLast(sample, ecg)
//            Log.d(TAG, "Replaced peak value: sample=" + sample + " size=" +
//            mSeries4.size()
//                    + " ecg=" + ecg + " mDataIndex=" + mDataIndex);
    }

    /**
     * Removes peaks with indices that are no longer in range.
     */
    fun removeOutOfRangeValues() {
        // Remove old values if needed
        val xMin = dataIndex - N_TOTAL_POINTS
        while (seriesPeaks.size() > 0 && seriesPeaks.getxVals().first.toInt() < xMin) {
            seriesPeaks.removeFirst()
        }
    }

    private fun updateDomainBoundaries() {
        if (plot.visibility == View.GONE) return
        val plotMin: Long = dataIndex - N_ECG_PLOT_POINTS
        val plotMax: Long = dataIndex
        plot.setDomainBoundaries(plotMin, plotMax, BoundaryMode.FIXED)
//        Log.d(TAG, this.getClass().getSimpleName() + "
//        updateDomainBoundaries: "
//                + "plotMin=" + plotMin + " plotMax=" + plotMax
//                + " size=" + mSeries1.size());
//        int colorInt = mPlot.getGraph().getGridBackgroundPaint().getColor();
//        String hexColor = String.format("#%06X", (0xFFFFFF & colorInt));
//        Log.d(TAG, "gridBgColor=" + hexColor);
    }

    /**
     * Updates the plot. Runs on the UI thread.
     */
    private fun update() {
        if (plot.visibility == View.GONE) return
        //            Log.d(TAG, this.getClass().getSimpleName()
//                    + " update: thread: " + Thread.currentThread()
//                    .getName());
        if (dataIndex % 73 == 0L) {
            parentActivity.runOnUiThread { plot.redraw() }
        }
    }

    /**
     * Set panning on or off.
     *
     * @param on Whether to be on or off (true for on).
     */
    fun setPanning(on: Boolean) {
        if (on) {
            PanZoom.attach(
                plot, PanZoom.Pan.HORIZONTAL,
                PanZoom.Zoom.NONE
            )
        } else {
            PanZoom.attach(plot, PanZoom.Pan.NONE, PanZoom.Zoom.NONE)
        }
    }

    /**
     * Clears the plot and resets dataIndex.
     */
    fun clear() {
        dataIndex = 0
        seriesEcg.clear()
        seriesSquares.clear()
        seriesScores.clear()
        seriesPeaks.clear()
        update()
    }

    companion object {
        private const val TAG = "QrsPlotter"
    }
}