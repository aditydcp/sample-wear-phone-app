package com.example.samplewearmobileapp

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import android.widget.Toast
import com.dsi.ant.plugins.antplus.pcc.AntPlusHeartRatePcc
import com.dsi.ant.plugins.antplus.pcc.AntPlusHeartRatePcc.*
import com.dsi.ant.plugins.antplus.pcc.defines.RequestAccessResult
import com.dsi.ant.plugins.antplus.pccbase.AntPluginPcc.IDeviceStateChangeReceiver
import com.dsi.ant.plugins.antplus.pccbase.AntPluginPcc.IPluginAccessResultReceiver
import com.dsi.ant.plugins.antplus.pccbase.AntPlusLegacyCommonPcc.*
import com.dsi.ant.plugins.antplus.pccbase.PccReleaseHandle

abstract class HeartRateActivity : Activity() {
    protected abstract fun requestAccessToPcc()

    var hrPcc: AntPlusHeartRatePcc? = null
    protected var releaseHandle: PccReleaseHandle<AntPlusHeartRatePcc>? = null

    var tv_status: TextView? = null

    var tv_estTimestamp: TextView? = null

    var tv_rssi: TextView? = null

    var tv_computedHeartRate: TextView? = null
    var tv_heartBeatCounter: TextView? = null
    var tv_heartBeatEventTime: TextView? = null

    var tv_manufacturerSpecificByte: TextView? = null
    var tv_previousHeartBeatEventTime: TextView? = null

    var tv_calculatedRrInterval: TextView? = null

    var tv_cumulativeOperatingTime: TextView? = null

    var tv_manufacturerID: TextView? = null
    var tv_serialNumber: TextView? = null

    var tv_hardwareVersion: TextView? = null
    var tv_softwareVersion: TextView? = null
    var tv_modelNumber: TextView? = null

    var tv_dataStatus: TextView? = null
    var tv_rrFlag: TextView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleReset()
    }

    /**
     * Resets the PCC connection to request access again and clears any existing display data.
     */
    protected open fun handleReset() {
        //Release the old access if it exists
        if (releaseHandle != null) {
            releaseHandle!!.close()
        }
        requestAccessToPcc()
    }

    protected open fun showDataDisplay(status: String?) {
        setContentView(R.layout.activity_heart_rate)
        tv_status = findViewById<View>(R.id.textView_Status) as TextView
        tv_estTimestamp = findViewById<View>(R.id.textView_EstTimestamp) as TextView
        tv_rssi = findViewById<View>(R.id.textView_Rssi) as TextView
        tv_computedHeartRate = findViewById<View>(R.id.textView_ComputedHeartRate) as TextView
        tv_heartBeatCounter = findViewById<View>(R.id.textView_HeartBeatCounter) as TextView
        tv_heartBeatEventTime = findViewById<View>(R.id.textView_HeartBeatEventTime) as TextView
        tv_manufacturerSpecificByte =
            findViewById<View>(R.id.textView_ManufacturerSpecificByte) as TextView
        tv_previousHeartBeatEventTime =
            findViewById<View>(R.id.textView_PreviousHeartBeatEventTime) as TextView
        tv_calculatedRrInterval = findViewById<View>(R.id.textView_CalculatedRrInterval) as TextView
        tv_cumulativeOperatingTime =
            findViewById<View>(R.id.textView_CumulativeOperatingTime) as TextView
        tv_manufacturerID = findViewById<View>(R.id.textView_ManufacturerID) as TextView
        tv_serialNumber = findViewById<View>(R.id.textView_SerialNumber) as TextView
        tv_hardwareVersion = findViewById<View>(R.id.textView_HardwareVersion) as TextView
        tv_softwareVersion = findViewById<View>(R.id.textView_SoftwareVersion) as TextView
        tv_modelNumber = findViewById<View>(R.id.textView_ModelNumber) as TextView
        tv_dataStatus = findViewById<View>(R.id.textView_DataStatus) as TextView
        tv_rrFlag = findViewById<View>(R.id.textView_rRFlag) as TextView

        //Reset the text display
        tv_status!!.text = status
        tv_estTimestamp!!.text = "---"
        tv_rssi!!.text = "---"
        tv_computedHeartRate!!.text = "---"
        tv_heartBeatCounter!!.text = "---"
        tv_heartBeatEventTime!!.text = "---"
        tv_manufacturerSpecificByte!!.text = "---"
        tv_previousHeartBeatEventTime!!.text = "---"
        tv_calculatedRrInterval!!.text = "---"
        tv_cumulativeOperatingTime!!.text = "---"
        tv_manufacturerID!!.text = "---"
        tv_serialNumber!!.text = "---"
        tv_hardwareVersion!!.text = "---"
        tv_softwareVersion!!.text = "---"
        tv_modelNumber!!.text = "---"
        tv_dataStatus!!.text = "---"
        tv_rrFlag!!.text = "---"
    }

    /**
     * Switches the active view to the data display and subscribes to all the data events
     */
    open fun subscribeToHrEvents() {
        hrPcc!!.subscribeHeartRateDataEvent { estTimestamp, eventFlags, computedHeartRate, heartBeatCount, heartBeatEventTime, dataState -> // Mark heart rate with asterisk if zero detected
            val textHeartRate =
                computedHeartRate.toString() + if (DataState.ZERO_DETECTED == dataState) "*" else ""

            // Mark heart beat count and heart beat event time with asterisk if initial value
            val textHeartBeatCount =
                heartBeatCount.toString() + if (DataState.INITIAL_VALUE == dataState) "*" else ""
            val textHeartBeatEventTime =
                heartBeatEventTime.toString() + if (DataState.INITIAL_VALUE == dataState) "*" else ""
            runOnUiThread {
                tv_estTimestamp!!.text = estTimestamp.toString()
                tv_computedHeartRate!!.text = textHeartRate
                tv_heartBeatCounter!!.text = textHeartBeatCount
                tv_heartBeatEventTime!!.text = textHeartBeatEventTime
                tv_dataStatus!!.text = dataState.toString()
            }
        }
        hrPcc!!.subscribePage4AddtDataEvent { estTimestamp, eventFlags, manufacturerSpecificByte, previousHeartBeatEventTime ->
            runOnUiThread {
                tv_estTimestamp!!.text = estTimestamp.toString()
                tv_manufacturerSpecificByte!!.text =
                    String.format("0x%02X", manufacturerSpecificByte)
                tv_previousHeartBeatEventTime!!.text = previousHeartBeatEventTime.toString()
            }
        }
        hrPcc!!.subscribeCumulativeOperatingTimeEvent { estTimestamp, eventFlags, cumulativeOperatingTime ->
            runOnUiThread {
                tv_estTimestamp!!.text = estTimestamp.toString()
                tv_cumulativeOperatingTime!!.text = cumulativeOperatingTime.toString()
            }
        }
        hrPcc!!.subscribeManufacturerAndSerialEvent { estTimestamp, eventFlags, manufacturerID, serialNumber ->
            runOnUiThread {
                tv_estTimestamp!!.text = estTimestamp.toString()
                tv_manufacturerID!!.text = manufacturerID.toString()
                tv_serialNumber!!.text = serialNumber.toString()
            }
        }
        hrPcc!!.subscribeVersionAndModelEvent { estTimestamp, eventFlags, hardwareVersion, softwareVersion, modelNumber ->
            runOnUiThread {
                tv_estTimestamp!!.text = estTimestamp.toString()
                tv_hardwareVersion!!.text = hardwareVersion.toString()
                tv_softwareVersion!!.text = softwareVersion.toString()
                tv_modelNumber!!.text = modelNumber.toString()
            }
        }
        hrPcc!!.subscribeCalculatedRrIntervalEvent { estTimestamp, eventFlags, rrInterval, flag ->
            runOnUiThread {
                tv_estTimestamp!!.text = estTimestamp.toString()
                tv_rrFlag!!.text = flag.toString()

                // Mark RR with asterisk if source is not cached or page 4
                if (flag == RrFlag.DATA_SOURCE_CACHED || flag == RrFlag.DATA_SOURCE_PAGE_4) tv_calculatedRrInterval!!.text =
                    rrInterval.toString() else tv_calculatedRrInterval!!.text = "$rrInterval*"
            }
        }
        hrPcc!!.subscribeRssiEvent { estTimestamp, evtFlags, rssi ->
            runOnUiThread {
                tv_estTimestamp!!.text = estTimestamp.toString()
                tv_rssi!!.text = "$rssi dBm"
            }
        }
    }

    protected var base_IPluginAccessResultReceiver =
        IPluginAccessResultReceiver<AntPlusHeartRatePcc> { result, resultCode, initialDeviceState ->

            //Handle the result, connecting to events on success or reporting failure to user.
            showDataDisplay("Connecting...")
            when (resultCode) {
                RequestAccessResult.SUCCESS -> {
                    hrPcc = result
                    tv_status!!.text = result.deviceName + ": " + initialDeviceState
                    subscribeToHrEvents()
                    if (!result.supportsRssi()) tv_rssi!!.text = "N/A"
                }
                RequestAccessResult.CHANNEL_NOT_AVAILABLE -> {
                    Toast.makeText(
                        this@HeartRateActivity,
                        "Channel Not Available",
                        Toast.LENGTH_SHORT
                    ).show()
                    tv_status!!.text = "Error. Do Menu->Reset."
                }
                RequestAccessResult.ADAPTER_NOT_DETECTED -> {
                    Toast.makeText(
                        this@HeartRateActivity,
                        "ANT Adapter Not Available. Built-in ANT hardware or external adapter required.",
                        Toast.LENGTH_SHORT
                    ).show()
                    tv_status!!.text = "Error. Do Menu->Reset."
                }
                RequestAccessResult.BAD_PARAMS -> {
                    //Note: Since we compose all the params ourself, we should never see this result
                    Toast.makeText(
                        this@HeartRateActivity,
                        "Bad request parameters.",
                        Toast.LENGTH_SHORT
                    ).show()
                    tv_status!!.text = "Error. Do Menu->Reset."
                }
                RequestAccessResult.OTHER_FAILURE -> {
                    Toast.makeText(
                        this@HeartRateActivity,
                        "RequestAccess failed. See logcat for details.",
                        Toast.LENGTH_SHORT
                    ).show()
                    tv_status!!.text = "Error. Do Menu->Reset."
                }
                RequestAccessResult.DEPENDENCY_NOT_INSTALLED -> {
                    tv_status!!.text = "Error. Do Menu->Reset."
                    val adlgBldr = AlertDialog.Builder(this@HeartRateActivity)
                    adlgBldr.setTitle("Missing Dependency")
                    adlgBldr.setMessage(
                        """The required service
"${getMissingDependencyName()}"
 was not found. You need to install the ANT+ Plugins service or you may need to update your existing version if you already have it. Do you want to launch the Play Store to get it?"""
                    )
                    adlgBldr.setCancelable(true)
                    adlgBldr.setPositiveButton(
                        "Go to Store"
                    ) { dialog, which ->
                        var startStore: Intent? = null
                        startStore = Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse("market://details?id=" + getMissingDependencyPackageName())
                        )
                        startStore.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        this@HeartRateActivity.startActivity(startStore)
                    }
                    adlgBldr.setNegativeButton(
                        "Cancel"
                    ) { dialog, which -> dialog.dismiss() }
                    val waitDialog = adlgBldr.create()
                    waitDialog.show()
                }
                RequestAccessResult.USER_CANCELLED -> tv_status!!.text =
                    "Cancelled. Do Menu->Reset."
                RequestAccessResult.UNRECOGNIZED -> {
                    Toast.makeText(
                        this@HeartRateActivity,
                        "Failed: UNRECOGNIZED. PluginLib Upgrade Required?",
                        Toast.LENGTH_SHORT
                    ).show()
                    tv_status!!.text = "Error. Do Menu->Reset."
                }
                else -> {
                    Toast.makeText(
                        this@HeartRateActivity,
                        "Unrecognized result: $resultCode", Toast.LENGTH_SHORT
                    ).show()
                    tv_status!!.text = "Error. Do Menu->Reset."
                }
            }
        }

    //Receives state changes and shows it on the status display line
    protected var base_IDeviceStateChangeReceiver =
        IDeviceStateChangeReceiver { newDeviceState ->
            runOnUiThread {
                tv_status!!.text = hrPcc!!.deviceName + ": " + newDeviceState
            }
        }

    override fun onDestroy() {
        if (releaseHandle != null) {
            releaseHandle!!.close()
        }
        super.onDestroy()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.activity_heart_rate, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_reset -> {
                handleReset()
                tv_status!!.text = "Resetting..."
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}