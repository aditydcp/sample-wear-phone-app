package com.example.samplewearmobileapp

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.*
import com.dsi.ant.plugins.antplus.pcc.AntPlusHeartRatePcc
import com.dsi.ant.plugins.antplus.pcc.defines.DeviceState
import com.dsi.ant.plugins.antplus.pcc.defines.RequestAccessResult
import com.dsi.ant.plugins.antplus.pccbase.AsyncScanController
import com.dsi.ant.plugins.antplus.pccbase.AsyncScanController.AsyncScanResultDeviceInfo
import com.dsi.ant.plugins.antplus.pccbase.AsyncScanController.IAsyncScanResultReceiver

class ConnectHrmActivity : HeartRateActivity() {
    var mTextView_Status: TextView? = null
    var mAlreadyConnectedDeviceInfos: ArrayList<AsyncScanResultDeviceInfo>? = null
    var mScannedDeviceInfos: ArrayList<AsyncScanResultDeviceInfo>? = null
    var adapter_devNameList: ArrayAdapter<String>? = null
    var adapter_connDevNameList: ArrayAdapter<String>? = null

    var hrScanCtrl: AsyncScanController<AntPlusHeartRatePcc>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        initScanDisplay()
        super.onCreate(savedInstanceState)
    }

    private fun initScanDisplay() {
        setContentView(R.layout.activity_connect_hrm)
        mTextView_Status = findViewById(R.id.connect_status_text)
        mTextView_Status!!.text = "Scanning for heart rate devices asynchronously..."
        mAlreadyConnectedDeviceInfos = java.util.ArrayList()
        mScannedDeviceInfos = java.util.ArrayList()

        //Setup already connected devices list
        adapter_connDevNameList =
            ArrayAdapter(this, android.R.layout.simple_list_item_1, android.R.id.text1)
        val listView_alreadyConnectedDevs =
            findViewById(R.id.devices_acquainted_list) as ListView
        listView_alreadyConnectedDevs.adapter = adapter_connDevNameList
        listView_alreadyConnectedDevs.onItemClickListener =
            AdapterView.OnItemClickListener { parent, view, pos, id ->
                //Return the id of the selected already connected device
                requestConnectToResult(mAlreadyConnectedDeviceInfos!![pos])
            }


        //Setup found devices display list
        adapter_devNameList =
            ArrayAdapter(this, android.R.layout.simple_list_item_1, android.R.id.text1)
        val listView_Devices = findViewById(R.id.devices_found_new_list) as ListView
        listView_Devices.adapter = adapter_devNameList
        listView_Devices.onItemClickListener =
            AdapterView.OnItemClickListener { parent, view, pos, id ->
                //Return the id of the selected already connected device
                requestConnectToResult(mScannedDeviceInfos!![pos])
            }
    }

    /**
     * Requests access to the given search result.
     * @param asyncScanResultDeviceInfo The search result to attempt to connect to.
     */
    protected fun requestConnectToResult(asyncScanResultDeviceInfo: AsyncScanResultDeviceInfo) {
        //Inform the user we are connecting
        runOnUiThread {
            mTextView_Status!!.text = "Connecting to " + asyncScanResultDeviceInfo.deviceDisplayName
            releaseHandle = hrScanCtrl!!.requestDeviceAccess(
                asyncScanResultDeviceInfo,
                { result, resultCode, initialDeviceState ->
                    if (resultCode == RequestAccessResult.SEARCH_TIMEOUT) {
                        //On a connection timeout the scan automatically resumes, so we inform the user, and go back to scanning
                        runOnUiThread {
                            Toast.makeText(
                                this@ConnectHrmActivity,
                                "Timed out attempting to connect, try again",
                                Toast.LENGTH_LONG
                            ).show()
                            mTextView_Status!!.text =
                                "Scanning for heart rate devices asynchronously..."
                        }
                    } else {
                        //Otherwise the results, including SUCCESS, behave the same as
                        base_IPluginAccessResultReceiver.onResultReceived(
                            result,
                            resultCode,
                            initialDeviceState
                        )
                        hrScanCtrl = null
                    }
                }, base_IDeviceStateChangeReceiver
            )
        }
    }

    /**
     * Requests the asynchronous scan controller
     */
    override fun requestAccessToPcc() {
        initScanDisplay()
        hrScanCtrl = AntPlusHeartRatePcc.requestAsyncScanController(this, 0,
            object : IAsyncScanResultReceiver {
                override fun onSearchStopped(reasonStopped: RequestAccessResult) {
                    //The triggers calling this function use the same codes and require the same actions as those received by the standard access result receiver
                    base_IPluginAccessResultReceiver.onResultReceived(
                        null,
                        reasonStopped,
                        DeviceState.DEAD
                    )
                }

                override fun onSearchResult(deviceFound: AsyncScanResultDeviceInfo) {
                    for (i in mScannedDeviceInfos!!) {
                        //The current implementation of the async scan will reset it's ignore list every 30s,
                        //So we have to handle checking for duplicates in our list if we run longer than that
                        if (i.antDeviceNumber == deviceFound.antDeviceNumber) {
                            //Found already connected device, ignore
                            return
                        }
                    }

                    //We split up devices already connected to the plugin from un-connected devices to make this information more visible to the user,
                    //since the user most likely wants to be aware of which device they are already using in another app
                    if (deviceFound.isAlreadyConnected) {
                        mAlreadyConnectedDeviceInfos!!.add(deviceFound)
                        runOnUiThread {
                            if (adapter_connDevNameList!!.isEmpty) //connected device category is invisible unless there are some present
                            {
                                findViewById<ListView>(R.id.devices_acquainted_list).visibility =
                                    View.VISIBLE
                                findViewById<TextView>(R.id.devices_acquainted_title).visibility =
                                    View.VISIBLE
                            }
                            adapter_connDevNameList!!.add(deviceFound.deviceDisplayName)
                            adapter_connDevNameList!!.notifyDataSetChanged()
                        }
                    } else {
                        mScannedDeviceInfos!!.add(deviceFound)
                        runOnUiThread {
                            adapter_devNameList!!.add(deviceFound.deviceDisplayName)
                            adapter_devNameList!!.notifyDataSetChanged()
                        }
                    }
                }
            })
    }


    /**
     * Ensures our controller is closed whenever we reset
     */
    override fun handleReset() {
        if (hrScanCtrl != null) {
            hrScanCtrl!!.closeScanController()
            hrScanCtrl = null
        }
        super.handleReset()
    }

    override fun onRestart() {
        super.onRestart()
        Log.i(TAG, "Lifecycle: onRestart()")
    }

    override fun onStop() {
        super.onStop()
        Log.i(TAG, "Lifecycle: onStop()")
    }

    /**
     * Ensures our controller is closed whenever we exit
     */
    override fun onDestroy() {
        if (hrScanCtrl != null) {
            hrScanCtrl!!.closeScanController()
            hrScanCtrl = null
        }
        super.onDestroy()
        Log.i(TAG, "Lifecycle: onDestroy()")
    }

    companion object {
        private const val TAG = "Mobile.ConnectHrmActivity"
    }
}