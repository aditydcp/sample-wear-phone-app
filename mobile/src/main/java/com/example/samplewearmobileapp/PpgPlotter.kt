package com.example.samplewearmobileapp

import android.util.Log
import com.androidplot.xy.*
import com.example.samplewearmobileapp.Constants.N_PPG_GREEN_PLOT_POINTS
import com.example.samplewearmobileapp.Constants.N_PPG_IR_RED_PLOT_POINTS
import com.example.samplewearmobileapp.models.PpgType
import com.example.samplewearmobileapp.models.PpgType.*
import com.example.samplewearmobileapp.models.RunningMax
import com.example.samplewearmobileapp.utils.AppUtils
import java.text.DecimalFormat
import kotlin.math.ceil
import kotlin.math.floor

class PpgPlotter: PlotterListener {
    private lateinit var parentActivity: MainActivity
    private var plot: XYPlot
    private lateinit var formatter: XYSeriesFormatter<XYRegionFormatter>

    /**
     * The type of PPG. (Green, IR, Red)
     * @see PpgType
     */
    private lateinit var ppgType: PpgType

    /**
     * The number of points to show in the plot.
     * This number is set conditionally corresponding
     * to PpgType.
     * @see N_PPG_GREEN_PLOT_POINTS
     * @see N_PPG_IR_RED_PLOT_POINTS
     */
    private var visiblePointLimit: Int = 0

    /**
     * The series that contain *only* the data
     * used for displaying the plot in the app.
     * This series is limited by `N_TOTAL_VISIBLE_POINTS`.
     */
    private lateinit var seriesVisible: SimpleXYSeries

    /**
     * The series that contain **all** data.
     */
    private lateinit var seriesAll: SimpleXYSeries

    /**
     * The series that contain **all** timestamp data.
     * Timestamp is taken from Polar device and is in `Long` type.
     * The timestamp corresponds to the timestamp of
     * PPG value of the same index.
     */
    private lateinit var seriesTimestamp: SimpleXYSeries

    /**
     * The next index in the data (or the length of the series.)
     */
    private var dataIndex: Long = 0

//    /**
//     * This is the time of the last occurrence of a value added to the plot.
//     * Used to set the domain and range boundaries.
//     */
//    private var lastTime = Double.NaN
//    private var startTime = Double.NaN
    private var runningMax: RunningMax = RunningMax(N_PPG_IR_RED_PLOT_POINTS)

    /**
     * Simplified constructor.
     * @param plot The XYPlot.
     */
    constructor(plot: XYPlot) {
        this.plot = plot
        visiblePointLimit = when (ppgType) {
            PPG_GREEN -> N_PPG_GREEN_PLOT_POINTS
            PPG_IR, PPG_RED -> N_PPG_IR_RED_PLOT_POINTS
        }
    }
    /**
     * Full constructor.
     * @param activity Parent activity (MainActivity).
     * @param plot The XYPlot.
     * @param ppgType The type of PPG (Green, IR or Red).
     * @param title The plot title.
     * @param lineColor Color of the line in integer.
     * @param showVertices Boolean value of whether to show vertices or not.
     */
    constructor(
        activity: MainActivity, plot: XYPlot, ppgType: PpgType,
        title: String?, lineColor: Int?, showVertices: Boolean
    ) {
        Log.d(TAG, this.javaClass.simpleName + " PpgPlotter Constructor")
        // This is the Activity, needed for resources
        this.parentActivity = activity
        this.plot = plot
        this.dataIndex = 0
        this.ppgType = ppgType
        visiblePointLimit = when (ppgType) {
            PPG_GREEN -> N_PPG_GREEN_PLOT_POINTS
            PPG_IR, PPG_RED -> N_PPG_IR_RED_PLOT_POINTS
        }
        formatter = LineAndPointFormatter(
            lineColor,
            if (showVertices) lineColor else null, null, null
        )
        formatter.isLegendIconEnabled = false
        seriesVisible = SimpleXYSeries(title)
        seriesAll = SimpleXYSeries(title)
        seriesTimestamp = SimpleXYSeries("Ecg-Timestamp")
        // only add to plot the visible series
        plot.addSeries(seriesVisible, formatter)
        setupPlot()
    }

    /**
     * Get a new PpgPlotter instance, using the given XYPlot but other values
     * from the current one. Use for replacing the current plotter.
     *
     * @param plot The new XYPlot.
     * @return The new instance of PpgPlotter with the new XYPlot.
     */
    fun getNewInstance(plot: XYPlot): PpgPlotter {
        val newPlotter =
            PpgPlotter(plot)
        newPlotter.plot = plot
        newPlotter.parentActivity = this.parentActivity
        newPlotter.dataIndex = this.dataIndex
        newPlotter.ppgType = this.ppgType
        newPlotter.visiblePointLimit = this.visiblePointLimit
        newPlotter.formatter = this.formatter
        newPlotter.seriesVisible = this.seriesVisible
        newPlotter.seriesAll = this.seriesAll
        newPlotter.seriesTimestamp = this.seriesTimestamp
        // only add to plot the visible series
        newPlotter.plot.addSeries(seriesVisible, formatter)
        newPlotter.setupPlot()
        return newPlotter
    }

    /**
     * Sets up the plot
     */
    fun setupPlot() {
        Log.d(TAG, this.javaClass.simpleName + " setupPlot")
        try {
            // Set the domain and range boundaries
            updateDomainRangeBoundaries()

            // Range labels will increment by 1
            plot.setRangeStep(StepMode.SUBDIVIDE, 8.0)
//            plot.graph.setLineLabelEdges(
//                XYGraphWidget.Edge.LEFT
//            )
//            // Make left labels be an integer (no decimal places)
//            plot.graph.getLineLabelStyle(XYGraphWidget.Edge.LEFT).format = DecimalFormat("#")

            // Set the domain block to be .25 of visible limit
            // to match the ECG Plot
            plot.setDomainStep(StepMode.INCREMENT_BY_VAL, visiblePointLimit * .25)
//            plot.linesPerDomainLabel = 5

//            // Allow panning
//            PanZoom.attach(mPlot, PanZoom.Pan.HORIZONTAL, PanZoom.Zoom.NONE);
            update()
        } catch (ex: Exception) {
            val msg = """Error in PpgPlotter.setupPlot:
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
     * @param ppgValue The PPG value that came in.
     * @param timestamp The timestamp of the incoming PPG value.
     */
    fun addValues(ppgValue: Int, timestamp: Long) {
        // remove old values only on visible series if needed
        if (seriesVisible.size() >= visiblePointLimit) {
            seriesVisible.removeFirst()
        }
        // Add the new values
        runningMax.add(ppgValue.toDouble())
        seriesVisible.addLast(dataIndex, ppgValue)
        seriesAll.addLast(dataIndex, ppgValue)
        seriesTimestamp.addLast(dataIndex, timestamp)
        dataIndex++

        // Reset the domain boundaries
//        updateDomainBoundaries()
        updateDomainRangeBoundaries()
        update()
    }

//    private fun updateDomainBoundaries() {
//        val plotMin: Long = dataIndex - Constants.N_ECG_PLOT_POINTS
//        val plotMax: Long = dataIndex
//        plot.setDomainBoundaries(plotMin, plotMax, BoundaryMode.FIXED)
//    }

    private fun updateDomainRangeBoundaries() {
        // get the Max value. 60 is the minimum amount.
        val max: Double = runningMax.max().coerceAtLeast(60.0)
        val min: Double = runningMax.min().coerceAtMost(0.0)

        // Set range (vertical) boundaries
        val upperBoundary: Number = ceil(max + (10/100.0 * max))
        val lowerBoundary: Number = floor(min - (10/100.0 * min)).coerceAtLeast(0.0)
        plot.setRangeBoundaries(lowerBoundary, upperBoundary, BoundaryMode.FIXED)

        // Set domain (horizontal) boundaries
        val plotMin: Long = dataIndex - visiblePointLimit
        val plotMax: Long = dataIndex
        plot.setDomainBoundaries(plotMin, plotMax, BoundaryMode.FIXED)
    }

    /**
     * Updates the plot. Runs on the UI thread.
     */
    override fun update() {
        parentActivity.runOnUiThread { plot.redraw() }
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
     * Get a series containing *only* the data used for display
     * @return a `SimpleXYSeries` containing display plot data
     */
    fun getVisibleSeries(): SimpleXYSeries {
        return seriesVisible
    }

    /**
     * Get a series containing *all* PPG data during recording
     * @return a `SimpleXYSeries` containing complete PPG data
     */
    fun getDataSeries(): SimpleXYSeries {
        return seriesAll
    }

    /**
     * Get a series of the timestamp
     * @return a `SimpleXYSeries` containing timestamp values
     */
    fun getTimestampSeries(): SimpleXYSeries {
        return seriesTimestamp
    }

    /**
     * Get a series with the full data with
     * the timestamp
     * @return a `SimpleXYSeries` with
     * timestamp as the Y values and
     * PPG as the X values
     */
    fun getCompiledDataSeries(): SimpleXYSeries {
        return SimpleXYSeries(
            seriesAll.getyVals().toMutableList(),
            seriesTimestamp.getyVals().toMutableList(),
            "Complete-ECG"
        )
    }

    fun getDataIndex(): Long {
        return dataIndex
    }

    fun getPpgType(): PpgType {
        return ppgType
    }

    /**
     * Clears the plot and resets dataIndex.
     */
    fun clear() {
        dataIndex = 0
        seriesVisible.clear()
        seriesAll.clear()
        update()
    }

    companion object {
        private const val TAG = "PpgPlotter"
    }
}