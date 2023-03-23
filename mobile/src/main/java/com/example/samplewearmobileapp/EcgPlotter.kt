package com.example.samplewearmobileapp

import android.graphics.RectF
import android.util.Log
import com.androidplot.util.PixelUtils
import com.androidplot.xy.*
import com.example.samplewearmobileapp.Constants.MICRO_TO_MILLI_VOLT
import com.example.samplewearmobileapp.Constants.N_DOMAIN_LARGE_BOXES
import com.example.samplewearmobileapp.Constants.N_ECG_PLOT_POINTS
import com.example.samplewearmobileapp.Constants.N_LARGE
import com.example.samplewearmobileapp.Constants.N_TOTAL_VISIBLE_POINTS
import com.example.samplewearmobileapp.utils.AppUtils
import com.polar.sdk.api.model.PolarEcgData
import java.util.*

class EcgPlotter {
    private lateinit var parentActivity: MainActivity
    private var plot: XYPlot
    private lateinit var formatter: XYSeriesFormatter<XYRegionFormatter>

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
     * ECG value of the same index.
     */
    private lateinit var seriesTimestamp: SimpleXYSeries

    /**
     * The next index in the data (or the length of the series.)
     */
    private var dataIndex: Long = 0

    /**
     * Simplified constructor.
     * @param plot The XYPlot.
     */
    constructor(plot: XYPlot) {
        this.plot = plot
    }
    /**
     * Full constructor.
     * @param activity Parent activity (MainActivity).
     * @param plot The XYPlot.
     * @param title The plot title.
     * @param lineColor Color of the line in integer.
     * @param showVertices Boolean value of whether to show vertices or not.
     */
    constructor(
        activity: MainActivity, plot: XYPlot,
        title: String?, lineColor: Int?, showVertices: Boolean
    ) {
        Log.d(TAG, this.javaClass.simpleName + " EcgPlotter Constructor")
        // This is the Activity, needed for resources
        this.parentActivity = activity
        this.plot = plot
        this.dataIndex = 0
        formatter = LineAndPointFormatter(
            lineColor,
            if (showVertices) lineColor else null, null, null
        )
        formatter.isLegendIconEnabled = false
        seriesVisible = SimpleXYSeries(title)
        seriesAll = SimpleXYSeries(title)
        // only add to plot the visible series
        plot.addSeries(seriesVisible, formatter)
        setupPlot()
    }

    /**
     * Get a new EcgPlotter instance, using the given XYPlot but other values
     * from the current one. Use for replacing the current plotter.
     *
     * @param plot The new XYPlot.
     * @return The new instance of EcgPlotter with the new XYPlot.
     */
    fun getNewInstance(plot: XYPlot): EcgPlotter {
        val newPlotter =
            EcgPlotter(plot)
        newPlotter.plot = plot
        newPlotter.parentActivity = this.parentActivity
        newPlotter.dataIndex = this.dataIndex
        newPlotter.formatter = this.formatter
        newPlotter.seriesVisible = this.seriesVisible
        newPlotter.seriesAll = this.seriesAll
        // only add to plot the visible series
        newPlotter.plot.addSeries(seriesVisible, formatter)
        newPlotter.setupPlot()
        return newPlotter
    }

    /**
     * Sets the plot parameters, calculating the range boundaries to have the
     * same grid as the domain. Calls update when done.
     */
    fun setupPlot() {
        Log.d(TAG, this.javaClass.simpleName + " setupPlot")
        try {
            // Calculate the range limits to make the blocks be square.
            // A large box is .5 mV. rMax corresponds to half the total
            // number of large boxes, rMax at top and -rMax at bottom
            val rMax: Double
            val gridRect: RectF = plot.graph.gridRect
            rMax = if (gridRect == null) {
                Log.d(
                    TAG, """ECGPlotter.setupPLot: gridRect is null 
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
            val color: Int = plot.graph.rangeGridLinePaint.color
            plot.graph.rangeOriginLinePaint.color = color
            plot.graph.rangeOriginLinePaint.strokeWidth = PixelUtils.dpToPix(1.5f)
            plot.setRangeStep(StepMode.INCREMENT_BY_VAL, .1)
            plot.linesPerRangeLabel = 5
            // Make it be centered
            plot.setUserRangeOrigin(0.0)

            // Domain
            updateDomainBoundaries()
            // Set the domain block to be .2 * nlarge so large block will be
            // nLarge samples
            plot.setDomainStep(StepMode.INCREMENT_BY_VAL, .2 * N_LARGE)
            plot.linesPerDomainLabel = 5

//        // Allow panning
//        PanZoom.attach(mPlot, PanZoom.Pan.HORIZONTAL, PanZoom.Zoom.NONE);

            // Update the plot
            update()
        } catch (ex: Exception) {
            val msg = """Error in EcgPlotter.setupPLot:
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
     * @param polarEcgData The data that came in.
     */
    fun addValues(polarEcgData: PolarEcgData) {
        val sampleCount = polarEcgData.samples.size
        if (sampleCount == 0) return

        // Add the new values, removing old values if needed
        for (ecgDataSample in polarEcgData.samples) {
            // remove old values only on visible series
            if (seriesVisible.size() >= N_TOTAL_VISIBLE_POINTS) {
                seriesVisible.removeFirst()
            }
            // Convert from  μV to mV and add to series
            seriesVisible.addLast(dataIndex, MICRO_TO_MILLI_VOLT * ecgDataSample.voltage)
            // Add the value to the all series as well
            seriesAll.addLast(dataIndex, MICRO_TO_MILLI_VOLT * ecgDataSample.voltage)
            seriesTimestamp.addLast(dataIndex, ecgDataSample.timeStamp)
            dataIndex++
        }
        // Reset the domain boundaries
        updateDomainBoundaries()
        update()
    }

    private fun updateDomainBoundaries() {
        val plotMin: Long = dataIndex - N_ECG_PLOT_POINTS
        val plotMax: Long = dataIndex
        plot.setDomainBoundaries(plotMin, plotMax, BoundaryMode.FIXED)
    }

    /**
     * Updates the plot. Runs on the UI thread.
     */
    private fun update() {
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
     * Gets info about the view.
     */
    private fun getPlotInfo(): String {
        val lf = "\n"
        val sb = StringBuilder()
        if (plot == null) {
            sb.append("Plot is null")
            return sb.toString()
        }
        sb.append("Title=").append(plot.title.text)
            .append(lf)
        sb.append("Range Title=").append(plot.rangeTitle.text)
            .append(lf)
        sb.append("Domain Title=").append(plot.domainTitle.text)
            .append(lf)
        sb.append("Range Origin=").append(plot.rangeOrigin)
            .append(lf)
        val timeVal: Long = plot.domainOrigin.toLong()
        val date = Date(timeVal)
        sb.append("Domain Origin=").append(date).append(lf)
        sb.append("Range Step Value=").append(plot.rangeStepValue)
            .append(lf)
        sb.append("Domain Step Value=").append(plot.domainStepValue)
            .append(lf)
        sb.append("Graph Width=").append(
            plot.graph.size.width.value)
            .append(lf)
        sb.append("Graph Height=").append(
            plot.graph.size.height.value)
            .append(lf)
        sb.append("DataIndex=").append(dataIndex)
            .append(lf)
        if (seriesVisible != null) {
            if (seriesVisible.getxVals() != null) {
                sb.append("Series Size=")
                    .append(seriesVisible.getxVals().size)
                    .append(lf)
            }
        } else {
            sb.append("Series=Null").append(lf)
        }
        return sb.toString()
    }

    /**
     * Get a series containing *only* the data used for display
     * @return a `SimpleXYSeries` containing display plot data
     */
    fun getVisibleSeries(): SimpleXYSeries {
        return seriesVisible
    }

    /**
     * Get a series containing *all* ECG data during recording
     * @return a `SimpleXYSeries` containing complete ECG data
     */
    fun getDataSeries(): SimpleXYSeries {
        return seriesAll
    }

    /**
     * Get a series of the timestamp taken from Polar device
     * @return a `SimpleXYSeries` containing timestamp values
     */
    fun getTimestampSeries(): SimpleXYSeries {
        return seriesTimestamp
    }

    /**
     * Get a series with the full data with
     * the timestamp taken from Polar device
     * @return a `SimpleXYSeries` with
     * timestamp as the Y values and
     * ECG as the X values
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
        private const val TAG = "EcgPlotter"
    }
}