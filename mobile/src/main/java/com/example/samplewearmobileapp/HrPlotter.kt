package com.example.samplewearmobileapp

import android.graphics.Color
import android.util.Log
import com.androidplot.xy.*
import com.example.samplewearmobileapp.Constants.HR_PLOT_DOMAIN_INTERVAL
import com.example.samplewearmobileapp.models.RunningMax
import com.example.samplewearmobileapp.utils.AppUtils
import java.text.*
import java.util.*
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlin.math.roundToLong

class HrPlotter {
    private lateinit var parentActivity: MainActivity
    private var plot: XYPlot

    private val plotHr1 = true
    private val plotRr1 = true
    private val plotHr2 = true
    private val plotRr2 = true

    /**
     * This is the time of the last occurrence of a value added to the plot.
     * Used to set the domain and range boundaries.
     */
    private var lastTime = Double.NaN

    private var startTime = Double.NaN
    private var startRrTime = Double.NEGATIVE_INFINITY

    private var runningMax1: RunningMax = RunningMax(50)
    private var runningMax2: RunningMax = RunningMax(50)

    private var lastRrTime = 0.0
    private var lastUpdateTime = 0.0
    private var totalRrTime = 0.0

    private var hrFormatter1: XYSeriesFormatter<XYRegionFormatter>? = null
    private var rrFormatter1: XYSeriesFormatter<XYRegionFormatter>? = null
    var hrSeries1: SimpleXYSeries? = null
    var rrSeries1: SimpleXYSeries? = null

    private var hrFormatter2: XYSeriesFormatter<XYRegionFormatter>? = null
    private var rrFormatter2: XYSeriesFormatter<XYRegionFormatter>? = null
    var hrSeries2: SimpleXYSeries? = null
    var rrSeries2: SimpleXYSeries? = null

    var hrRrList1: MutableList<HrRrSessionData> = ArrayList<HrRrSessionData>()
    var hrRrList2: MutableList<HrRrSessionData> = ArrayList<HrRrSessionData>()

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
        Log.d(TAG, this.javaClass.simpleName + " HrPlotter Constructor")
        // This is the mActivity, needed for resources
        parentActivity = activity
        this.plot = plot
        if (plotHr1) {
            hrFormatter1 = LineAndPointFormatter(
                Color.RED,
                null, null, null
            )
            (hrFormatter1 as LineAndPointFormatter).isLegendIconEnabled = false
            hrSeries1 = SimpleXYSeries("HR1")
        }
        if (plotRr1) {
            rrFormatter1 = LineAndPointFormatter(
                Color.rgb(0, 0x99, 0xFF),
                null, null, null
            )
            (rrFormatter1 as LineAndPointFormatter).isLegendIconEnabled = false
            rrSeries1 = SimpleXYSeries("RR1")
        }
        if (plotHr2) {
            hrFormatter2 = LineAndPointFormatter(
                Color.rgb(
                    0xFF, 0x88,
                    0xAA
                ),
                null, null, null
            )
            (hrFormatter2 as LineAndPointFormatter).isLegendIconEnabled = false
            hrSeries2 = SimpleXYSeries("HR2")
        }
        if (plotRr2) {
            rrFormatter2 = LineAndPointFormatter(
                Color.rgb(0, 0xBF, 0xFF),
                null, null, null
            )
            (rrFormatter2 as LineAndPointFormatter).isLegendIconEnabled = false
            rrSeries2 = SimpleXYSeries("RR2")
        }
        this.plot.addSeries(hrSeries1, hrFormatter1)
        this.plot.addSeries(rrSeries1, rrFormatter1)
        this.plot.addSeries(hrSeries2, hrFormatter2)
        this.plot.addSeries(rrSeries2, rrFormatter2)
        setupPlot()
    }

    /**
     * Get a new HrPlotter instance, using the given XYPlot but other values
     * from the current one. Use for replacing the current plotter.
     *
     * @param plot The new XYPlot.
     * @return The new instance of HrPlotter with the new XYPlot.
     */
    fun getNewInstance(plot: XYPlot): HrPlotter {
        val newPlotter = HrPlotter(plot)
        newPlotter.plot = plot
        newPlotter.parentActivity = parentActivity
        newPlotter.lastTime = lastTime
        newPlotter.startTime = startTime
        newPlotter.startRrTime = startRrTime
        newPlotter.runningMax1 = runningMax1
        newPlotter.runningMax2 = runningMax2
        newPlotter.hrRrList1 = hrRrList1
        newPlotter.hrRrList2 = hrRrList2
        newPlotter.lastRrTime = lastRrTime
        newPlotter.lastUpdateTime = lastUpdateTime
        newPlotter.totalRrTime = totalRrTime
        newPlotter.hrFormatter1 = hrFormatter1
        newPlotter.hrSeries1 = hrSeries1
        newPlotter.rrFormatter1 = rrFormatter1
        newPlotter.rrSeries1 = rrSeries1
        newPlotter.hrFormatter2 = hrFormatter2
        newPlotter.hrSeries2 = hrSeries2
        newPlotter.rrFormatter2 = rrFormatter2
        newPlotter.rrSeries2 = rrSeries2
        newPlotter.plot.addSeries(hrSeries1, hrFormatter1)
        newPlotter.plot.addSeries(rrSeries1, rrFormatter1)
        newPlotter.plot.addSeries(hrSeries2, hrFormatter2)
        newPlotter.plot.addSeries(rrSeries2, rrFormatter2)
        newPlotter.setupPlot()
        return newPlotter
    }

    /**
     * Sets the plot parameters. Calls update when done.
     */
    fun setupPlot() {
        Log.d(TAG, this.javaClass.simpleName + " setupPlot")
        try {
            // Set the domain and range boundaries
            updateDomainRangeBoundaries()

            // Range labels will increment by 10
            plot.setRangeStep(StepMode.INCREMENT_BY_VAL, 40.0)
            //        mPlot.setDomainStep(StepMode.INCREMENT_BY_VAL, 60000); // 1 min
            plot.setDomainStep(StepMode.SUBDIVIDE, 5.0)
            plot.graph.setLineLabelEdges(
                XYGraphWidget.Edge.BOTTOM,
                XYGraphWidget.Edge.LEFT
            )
            // Make left labels be an integer (no decimal places)
            plot.graph.getLineLabelStyle(XYGraphWidget.Edge.LEFT).format = DecimalFormat("#")
            // Set x axis labeling to be time
            plot.graph.getLineLabelStyle(XYGraphWidget.Edge.BOTTOM).format = object : Format() {
                private val dateFormat: SimpleDateFormat = X_AXIS_DATE_FORMAT

                override fun format(
                    obj: Any,
                    toAppendTo: StringBuffer,
                    pos: FieldPosition
                ): StringBuffer {
                    val time = (obj as Number).toDouble().roundToInt()
                    return dateFormat.format(time, toAppendTo, pos)
                }

                override fun parseObject(
                    source: String,
                    pos: ParsePosition
                ): Any? {
                    return null
                }
            }

//            // Allow panning
//            PanZoom.attach(mPlot, PanZoom.Pan.HORIZONTAL, PanZoom.Zoom.NONE);
            update()
        } catch (ex: Exception) {
            val msg = """Error in HrPlotter.setupPlot:
                |isLaidOut=${plot.isLaidOut} 
                |width=${plot.width} 
                |height=${plot.height}""".trimMargin()
            AppUtils.excMsg(parentActivity, msg, ex)
            Log.e(TAG, msg, ex)
        }
    }

    fun addValues1(time: Double, hr: Double, rrsMs: List<Int>) {
//        Log.d(TAG, this.getClass().getSimpleName() + ": addHrValues: time="
//                + mDateFormatSec.format(time) + " hr=" + hr + " hrSize=" +
//                mHrSeries1.size());
        if (plotHr1 || plotRr1) {
            hrRrList1.add(HrRrSessionData(time, hr, rrsMs))
            //            StringBuilder sb = new StringBuilder();
//            sb.append("HRPlotter: addValues1");
//            sb.append(" time=");
//            sb.append(X_AXIS_DATE_FORMAT.format(new Date(Math.round(time))));
//            sb.append(" hr=").append(Math.round(hr)).append(" rr=");
//            for (int rr : rrsMs) {
//                sb.append(rr).append(" ");
//            }
//            Log.d(TAG, sb.toString());
            if (java.lang.Double.isNaN(startTime)) startTime = time
            if (java.lang.Double.isNaN(lastTime)) {
                lastTime = time
            } else if (time > lastTime) {
                lastTime = time
            }
        }

        // HR
        if (plotHr1) {
            runningMax1.add(hr)
            hrSeries1!!.addLast(time, hr)
        }

        // RR
        val rrValuesCount = rrsMs.size
        if (plotRr1 && rrValuesCount > 0) {
            val tValues = DoubleArray(rrValuesCount)
            val rrValues = arrayOfNulls<Int>(rrValuesCount)

//            rrValues = rrsMs.toArray(rrValues)
            // replicate toArray() function
            for (i in rrsMs.indices) {
                rrValues[i] = rrsMs[i]
            }

            // Find the sum of the RR intervals
            var totalRr = 0.0
            for (i in 0 until rrValuesCount) {
                totalRr += rrValues[i]!!.toDouble()
            }
            // First time
            if (java.lang.Double.isInfinite(startRrTime)) {
                lastUpdateTime = time - totalRr
                lastRrTime = lastUpdateTime
                startRrTime = lastRrTime
                totalRrTime = 0.0
            }
            totalRrTime += totalRr
            //        Log.d(TAG, "lastRrTime=" + mLastRrTime
//                + " totalRR=" + totalRR
//                + " elapsed=" + (mLastRrTime - mStartRrTime)
//                + " totalRrTime=" + mTotalRrTime);
            var rr: Double
            var t = lastRrTime
            for (i in 0 until rrValuesCount) {
                rr = rrValues[i]!!.toDouble()
                t += rr
                tValues[i] = t
            }
            // Keep them in this interval
            if (tValues[0] < lastUpdateTime) {
                lastUpdateTime = tValues[0]
                val deltaT = lastUpdateTime
                t += deltaT
                for (i in 0 until rrValuesCount) {
                    tValues[i] += deltaT
                }
            }
            // Keep them from being in the future
            if (t > time) {
                val deltaT = t - time
                for (i in 0 until rrValuesCount) {
                    tValues[i] -= deltaT
                }
            }
            // Add to the series
            for (i in 0 until rrValuesCount) {
                rr = RR_SCALE * rrValues[i]!!
                runningMax1.add(rr)
                rrSeries1!!.addLast(tValues[i], rr)
                lastRrTime = tValues[i]
            }
            lastUpdateTime = time
        }
    }

    fun addValues2(time: Double, hr: Double, rr: Double) {
//        Log.d(TAG, this.getClass().getSimpleName() + ": addValues2: time="
//                + mDateFormatSec.format(time) + " hr=" + hr + " rr="
//                + RR_SCALE * rr);
        if (plotHr2 || plotRr2) {
            hrRrList2.add(HrRrSessionData(time, hr, rr))
            //            Log.d(TAG, "HRPlotter: addValues2"
//                    + " time=" + X_AXIS_DATE_FORMAT.format(new Date(Math
//                    .round(time)))
//                    + " hr=" + Math.round(hr)
//                    + " rr=" + Math.round(rr));
            if (java.lang.Double.isNaN(startTime)) startTime = time
            if (java.lang.Double.isNaN(lastTime)) {
                lastTime = time
            } else if (time > lastTime) {
                lastTime = time
            }
        }
        if (plotHr2) {
            runningMax2.add(hr)
            hrSeries2!!.addLast(time, hr)
        }
        if (plotRr2) {
            runningMax2.add(RR_SCALE * rr)
            rrSeries2!!.addLast(time, RR_SCALE * rr)
        }
    }

//    public String getRrInfo() {
//        double elapsed = MS_TO_SEC * (mLastRrTime - mStartRrTime);
//        double total = MS_TO_SEC * mTotalRrTime;
//        double ratio = total / elapsed;
//        return "Tot=" + String.format(Locale.US, "%.2f s", elapsed)
//                + " RR=" + String.format(Locale.US, "%.2f s", total)
//                + " (" + String.format(Locale.US, "%.2f", ratio) + ")";
//    }

    private fun updateDomainRangeBoundaries() {
        var max: Double = runningMax1.max().coerceAtLeast(runningMax2.max())
        if (java.lang.Double.isNaN(max) || max < 60) max = 60.0
//        Log.d(TAG, this.getClass().getSimpleName() +
//        "updateDomainRangeBoundaries: startTime="
//                + startTime + " lastTime=" + lastTime
//                + " max=" + max);
        if (!java.lang.Double.isNaN(lastTime) && !java.lang.Double.isNaN(startTime)) {
            if (lastTime - startTime > HR_PLOT_DOMAIN_INTERVAL) {
                plot.setDomainBoundaries(
                    lastTime - HR_PLOT_DOMAIN_INTERVAL,
                    lastTime, BoundaryMode.FIXED
                )
            } else {
                plot.setDomainBoundaries(
                    startTime,
                    startTime + HR_PLOT_DOMAIN_INTERVAL,
                    BoundaryMode.FIXED
                )
            }
        } else {
            val time0 = Date().time
            val time1: Long = time0 + HR_PLOT_DOMAIN_INTERVAL
            plot.setDomainBoundaries(time0, time1, BoundaryMode.FIXED)
        }
        val upperBoundary: Number = ceil(max + 10).coerceAtMost(200.0)
        plot.setRangeBoundaries(0, upperBoundary, BoundaryMode.FIXED)
//        RectRegion rgn= mPlot.getOuterLimits();
//        Log.d(TAG,"OuterLimits="  + rgn.getMinX() + "," + rgn.getMaxX());
//        mPlot.getOuterLimits().set(mStartTime, mLastTime,
//                0, Math.ceil(max + 10));
    }

    /**
     * Updates the plot. Runs on the UI thread.
     */
    private fun update() {
//        Log.d(TAG, "HRPlotter: update: dataList sizes=" + mHrRrList1.size()
//                + "," + mHrRrList2.size());
        parentActivity.runOnUiThread { plot.redraw() }
    }

    /**
     * Sets the domain and range boundaries and the does update.
     */
    fun fullUpdate() {
        updateDomainRangeBoundaries()
        update()
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
     * Clears the plot.
     */
    fun clear() {
        hrSeries1!!.clear()
        rrSeries1!!.clear()
        hrSeries2!!.clear()
        rrSeries2!!.clear()
        lastTime = Double.NaN
        startTime = Double.NaN
        runningMax1 = RunningMax(50)
        runningMax2 = RunningMax(50)
        val time0 = Date().time
        val time1 = time0 + HR_PLOT_DOMAIN_INTERVAL
        plot.setDomainBoundaries(time0, time1, BoundaryMode.FIXED)
        update()
    }

    /**
     * Class for handling data to be written to a Session file as used by
     * Bluetooth Cardiac Monitor (BCM) and HxM Monitor.  Note that RR for
     * session data is in units of 1/1024 sec, not ms. THis is the raw unit
     * for RR in the BLE packet.
     */
    class HrRrSessionData {
        private val time: String
        private val hr: String
        private val rr: String

        constructor(time: Double, hr: Double, rrsMs: List<Int>) {
            this.time = sdf.format(Date(time.roundToLong()))
            this.hr = String.format(Locale.US, "%.0f", hr)
            val sb = StringBuilder()
            for (rr in rrsMs) {
                // Convert ms to 1/1024 sec.
                sb.append((1.024 * rr).roundToInt()).append(" ")
            }
            rr = sb.toString().trim { it <= ' ' }
        }

        constructor(time: Double, hr: Double, rr: Double) {
            this.time = sdf.format(Date(time.roundToLong()))
            this.hr = String.format(Locale.US, "%.0f", hr)
            // Convert ms to 1/1024 sec.
            this.rr = String.format(Locale.US, "%d", (1.024 * rr).roundToInt())
        }

        /***
         * Gets a string for writing session files. These have RR in units
         * of 1/1024 sec, not ms.
         * @return The string
         */
        val csvString: String
            get() = String.format("%s,%s,%s", time, hr, rr)

        companion object {
            // This is the same formatter as sessionSaveFormatter in Bluetooth
            // Cardiac Monitor.
            private val sdf = SimpleDateFormat(
                "yyyy-MM-dd HH:mm:ss.SSS", Locale.US
            )
        }
    }

    companion object {
        private const val TAG = "HrPlotter"
        private const val RR_SCALE = .1 // to 100 ms to use same axis
        private val X_AXIS_DATE_FORMAT = SimpleDateFormat("HH:mm:ss", Locale.US)
    }
}