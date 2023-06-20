package com.example.samplewearmobileapp

import android.graphics.Color
import android.util.Log
import android.view.View
import com.androidplot.util.PixelUtils
import com.androidplot.xy.*
import com.example.samplewearmobileapp.Constants.NANO_TO_MICRO_SEC
import com.example.samplewearmobileapp.Constants.N_DOMAIN_LARGE_BOXES
import com.example.samplewearmobileapp.Constants.N_ECG_PLOT_POINTS
import com.example.samplewearmobileapp.Constants.N_LARGE
import com.example.samplewearmobileapp.Constants.N_TOTAL_VISIBLE_ECG_POINTS
import com.example.samplewearmobileapp.utils.AppUtils
import java.util.Date

class QrsPlotter: PlotterListener {
    private lateinit var parentActivity: MainActivity
    private var plot: XYPlot

    // ECG
    private lateinit var formatterEcg: XYSeriesFormatter<XYRegionFormatter>
    /**
     * The series that contain *only* the ecg data
     * used for displaying the plot in the app.
     * This series is limited by `N_TOTAL_VISIBLE_POINTS`.
     */
    lateinit var seriesPlotEcg: SimpleXYSeries
    /**
     * The series that contain **all** ecg data.
     */
    lateinit var seriesDataEcg: SimpleXYSeries

    // Square
    private lateinit var formatterSquares: XYSeriesFormatter<XYRegionFormatter>
    /**
     * The series that contain *only* the squares data
     * used for displaying the plot in the app.
     * This series is limited by `N_TOTAL_VISIBLE_POINTS`.
     */
    lateinit var seriesPlotSquares: SimpleXYSeries
    /**
     * The series that contain **all** squares data.
     */
    lateinit var seriesDataSquares: SimpleXYSeries

    // Score
    private lateinit var formatterScores: XYSeriesFormatter<XYRegionFormatter>
    /**
     * The series that contain *only* the scores data
     * used for displaying the plot in the app.
     * This series is limited by `N_TOTAL_VISIBLE_POINTS`.
     */
    lateinit var seriesPlotScores: SimpleXYSeries
    /**
     * The series that contain **all** scores data.
     */
    lateinit var seriesDataScores: SimpleXYSeries

    // Peaks
    private lateinit var formatterPeaks: XYSeriesFormatter<XYRegionFormatter>
    /**
     * The series that contain *only* the peaks data
     * used for displaying the plot in the app.
     * This series is limited by `N_TOTAL_VISIBLE_POINTS`.
     */
    lateinit var seriesPlotPeaks: SimpleXYSeries
    /**
     * The series that contain **all** peaks data.
     */
    lateinit var seriesDataPeaks: SimpleXYSeries

    /**
     * The series that contain **all** timestamp data.
     * Timestamp is taken from Polar device and is in `Long` type.
     * The timestamp corresponds to the timestamp of
     * ECG value of the same index.
     */
    lateinit var seriesTimestamp: SimpleXYSeries

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
        seriesPlotEcg = SimpleXYSeries("ECG")
        seriesDataEcg = SimpleXYSeries("ECG")

        formatterSquares = LineAndPointFormatter(
            Color.rgb(255, 216, 0),
            null, null, null
        )
        formatterSquares.isLegendIconEnabled = false
        seriesPlotSquares = SimpleXYSeries("Derivative")
        seriesDataSquares = SimpleXYSeries("Derivative")

        formatterScores = LineAndPointFormatter(
            Color.rgb(50, 205, 50),
            null, null, null
        ) // Crimson
        formatterScores.isLegendIconEnabled = false
        seriesPlotScores = SimpleXYSeries("Square")
        seriesDataScores = SimpleXYSeries("Square")

        formatterPeaks = LineAndPointFormatter(null, Color.RED, null, null)
        formatterPeaks.isLegendIconEnabled = false
//        ((LineAndPointFormatter)mFormatter4).getVertexPaint()
//        .setStrokeWidth(20);
        seriesPlotPeaks = SimpleXYSeries("Peaks")
        seriesDataPeaks = SimpleXYSeries("Peaks")

        seriesTimestamp = SimpleXYSeries("Timestamp")

        // only add the "plot series" to the plot for displaying
        this.plot.addSeries(seriesPlotSquares, formatterSquares)
        this.plot.addSeries(seriesPlotScores, formatterScores)
        this.plot.addSeries(seriesPlotPeaks, formatterPeaks)
        this.plot.addSeries(seriesPlotEcg, formatterEcg)
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
     * @param timestamp The timestamp of the other values.
     */
    fun addValues(
        ecg: Number?,
        square: Number?,
        score: Number?,
        timestamp:  Long?
    ) {
//        Log.d(TAG, this.getClass().getSimpleName()
//                + "addValues: dataIndex=" + mDataIndex + " mSeriesSize="
//                + mSeries1.size() + " mSeries2Size=" + mSeries2.size()
//                + " val1=" + val1 + " val2=" + val2);
        // Add the new values, removing old values if needed
        // Convert from  μV to mV
        if (ecg != null) {
            // only remove old values in the "plot series"
            if (seriesPlotEcg.size() >= N_TOTAL_VISIBLE_ECG_POINTS) {
                seriesPlotEcg.removeFirst()
            }
            // add the new values to both series
            seriesPlotEcg.addLast(dataIndex, ecg)
            seriesDataEcg.addLast(dataIndex, ecg)
        }
        if (square != null) {
            // only remove old values in the "plot series"
            if (seriesPlotSquares.size() >= N_TOTAL_VISIBLE_ECG_POINTS) {
                seriesPlotSquares.removeFirst()
            }
            seriesPlotSquares.addLast(dataIndex, square)
            seriesDataSquares.addLast(dataIndex, square)
        }
        if (score != null) {
            // only remove old values in the "plot series"
            if (seriesPlotScores.size() >= N_TOTAL_VISIBLE_ECG_POINTS) {
                seriesPlotScores.removeFirst()
            }
            seriesPlotScores.addLast(dataIndex, score)
            seriesDataScores.addLast(dataIndex, score)
        }
        if (timestamp != null) {
            seriesTimestamp.addLast(dataIndex,
                (NANO_TO_MICRO_SEC * timestamp).toLong().adjustEpoch())
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
        removeOutOfRangePlotPeakValues()
        seriesPlotPeaks.addLast(sample, ecg)
        seriesDataPeaks.addLast(sample, ecg)
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
        removeOutOfRangePlotPeakValues()
        seriesPlotPeaks.removeLast()
        seriesDataPeaks.removeLast()
        seriesPlotPeaks.addLast(sample, ecg)
        seriesDataPeaks.addLast(sample, ecg)
//            Log.d(TAG, "Replaced peak value: sample=" + sample + " size=" +
//            mSeries4.size()
//                    + " ecg=" + ecg + " mDataIndex=" + mDataIndex);
    }

    /**
     * Removes peaks with indices that are no longer in range.
     */
    fun removeOutOfRangePlotPeakValues() {
        // Remove old values if needed
        val xMin = dataIndex - N_TOTAL_VISIBLE_ECG_POINTS
        while (seriesPlotPeaks.size() > 0 && seriesPlotPeaks.getxVals().first.toInt() < xMin) {
            seriesPlotPeaks.removeFirst()
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
    override fun update() {
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
        seriesPlotEcg.clear()
        seriesDataEcg.clear()
        seriesPlotSquares.clear()
        seriesDataSquares.clear()
        seriesPlotScores.clear()
        seriesDataScores.clear()
        seriesPlotPeaks.clear()
        seriesDataPeaks.clear()
        update()
    }

    companion object {
        private const val TAG = "QrsPlotter"

        private fun Long.adjustEpoch(): Long {
            return this + Date(2000 - 1900, 0, 1, 7, 0).time
        }
    }
}