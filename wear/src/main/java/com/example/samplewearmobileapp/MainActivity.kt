package com.example.samplewearmobileapp

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import com.example.samplewearmobileapp.databinding.ActivityMainBinding
import com.google.android.gms.common.api.GoogleApiClient
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.PutDataRequest
import com.google.android.gms.wearable.Wearable
import com.google.gson.Gson
import com.samsung.android.service.health.tracking.HealthTrackerException
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class MainActivity : Activity(), GoogleApiClient.ConnectionCallbacks {
    private val tag = "Wear: MainActivity"
    private lateinit var binding: ActivityMainBinding
    private lateinit var client: GoogleApiClient
    private var connectedNode: List<Node>? = null
    private var message: Message = Message()
    private var currentMessage: Message? = null
    private var currentState = 0
    private var currentDataBatchNumber = 0

    private lateinit var textStatus : TextView
    private lateinit var textTip : TextView
//    private lateinit var hrContainer : LinearLayout
//    private lateinit var textHeartRate : TextView
//    private lateinit var textHeartRateStatus : TextView
//    private lateinit var textIbi : TextView
//    private lateinit var textIbiStatus : TextView
    private lateinit var ppgGreenContainer : LinearLayout
    private lateinit var textPpgGreen : TextView
    private lateinit var textPpgGreenNumber : TextView
    private lateinit var textPpgGreenTimestamp : TextView

    lateinit var uiUpdateThread: Thread
    private lateinit var connectionManager: ConnectionManager
//    private lateinit var heartRateListener: HeartRateListener
    private lateinit var ppgGreenListener: PpgGreenListener
    private var connected = false
    private var permissionGranted = false
//    private var heartRateDataLast = HeartRateData()
    private var ppgGreenDataLast = PpgGreenData()

    val trackerDataObserver: TrackerDataObserver = object : TrackerDataObserver {
//        override fun onHeartRateTrackerDataChanged(hrData: HeartRateData) {
//            this@MainActivity.runOnUiThread(Runnable {
//                Log.i(tag,"HR Status: " + hrData.status)
//                when(hrData.status) {
//                    HeartRateStatus.HR_STATUS_FIND_HR.code -> {
//                        textHeartRateStatus.text = HeartRateStatus.HR_STATUS_FIND_HR.statusText
//                        textHeartRate.text = hrData.hr.toString()
//                        Log.i(tag, "HR: ${hrData.hr}")
//                        textIbi.text = hrData.ibi.toString()
//                        Log.i(tag, "IBI: ${hrData.ibi}")
//                        Log.i(tag, "IBI quality: ${hrData.qIbi}")
//                        if(hrData.qIbi == 0) {
//                            textIbiStatus.text = getString(R.string.ibi_status_good)
//                        }
//                        else {
//                            textIbiStatus.text = getString(R.string.ibi_status_bad)
//                        }
//                    }
//                    HeartRateStatus.HR_STATUS_ATTACHED.code -> {
//                        textHeartRateStatus.text =
//                            HeartRateStatus.HR_STATUS_ATTACHED.statusText
//                    }
//                    HeartRateStatus.HR_STATUS_DETACHED.code -> {
//                        Log.i(tag, "Detached")
//                        textHeartRateStatus.text =
//                            HeartRateStatus.HR_STATUS_DETACHED.statusText
//                    }
//                    HeartRateStatus.HR_STATUS_DETECT_MOVE.code -> {
//                        Log.i(tag, "Movement detected")
//                        textHeartRateStatus.text =
//                            HeartRateStatus.HR_STATUS_DETECT_MOVE.statusText
//                    }
//                    HeartRateStatus.HR_STATUS_NO_DATA_FLUSH.code -> {
//                        Log.i(tag, "No data flush")
//                        textHeartRateStatus.text =
//                            HeartRateStatus.HR_STATUS_NO_DATA_FLUSH.statusText
//                    }
//                    HeartRateStatus.HR_STATUS_LOW_RELIABILITY.code -> {
//                        Log.i(tag, "Low reliability")
//                        textHeartRateStatus.text =
//                            HeartRateStatus.HR_STATUS_LOW_RELIABILITY.statusText
//                    }
//                    HeartRateStatus.HR_STATUS_VERY_LOW_RELIABILITY.code -> {
//                        Log.i(tag, "Very low reliability")
//                        textHeartRateStatus.text =
//                            HeartRateStatus.HR_STATUS_VERY_LOW_RELIABILITY.statusText
//                    }
//                    else -> {
//                        Log.i(tag, "None")
//                        textHeartRateStatus.text = HeartRateStatus.HR_STATUS_NONE.statusText
//                        textHeartRate.text = getString(R.string.default_value)
//                    }
//                }
//                sendHrData(hrData)
//                heartRateDataLast = hrData
//            })
//        }

        override fun onPpgGreenTrackerDataChanged(ppgGreenData: PpgGreenData) {
            Log.i(tag,"PPG Green Status: " + ppgGreenData.status)
            when(ppgGreenData.status) {
                PpgGreenStatus.PPG_GREEN_STATUS_GOOD.code -> {
                    Log.i(tag, "Green PPG Data received")
                    currentDataBatchNumber++
                    this@MainActivity.runOnUiThread {
                        textPpgGreen.text = ppgGreenData.ppgValue.toString()
                        Log.i(tag, "PPG Green : ${ppgGreenData.ppgValue}")
                        textPpgGreenTimestamp.text = ppgGreenData.timestamp.toString()
                        Log.i(tag, "PPG Green Timestamp : ${ppgGreenData.timestamp}")
                        textPpgGreenNumber.text = currentDataBatchNumber.toString()
                    }
                }
                PpgGreenStatus.PPG_GREEN_STATUS_NONE.code -> {
                    Log.i(tag, "No Green PPG Data")
                }
            }
            sendPpgGreenData(ppgGreenData)
            ppgGreenDataLast = ppgGreenData
        }

        override fun onError(errorResourceId: Int) {
            runOnUiThread {
                Toast.makeText(
                    applicationContext,
                    getString(errorResourceId),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private val connectionObserver: ConnectionObserver = object : ConnectionObserver {
        override fun onConnectionResult(stringResourceId: Int) {
            runOnUiThread {
                Toast.makeText(
                    applicationContext, getString(stringResourceId), Toast.LENGTH_LONG
                ).show()
            }
            if (stringResourceId != R.string.ConnectedToHs) {
                finish()
            }

            connected = true
            TrackerDataNotifier.instance?.addObserver(trackerDataObserver)
//            heartRateListener = HeartRateListener()
            ppgGreenListener = PpgGreenListener()
//            connectionManager.initHeartRate(heartRateListener)
            connectionManager.initPpgGreen(ppgGreenListener)

            // commented out because tracker started at other point of the app
            //heartRateListener.startTracker()
        }

        override fun onError(e: HealthTrackerException?) {
            if (e != null) {
                if (e.errorCode == HealthTrackerException.OLD_PLATFORM_VERSION
                    || e.errorCode == HealthTrackerException.PACKAGE_NOT_INSTALLED)
                    runOnUiThread {
                        Toast.makeText(
                            applicationContext,
                            getString(R.string.HealthPlatformVersionIsOutdated),
                            Toast.LENGTH_LONG
                        ).show()
                    }
            }
            if (e != null) {
                if (e.hasResolution()) {
                    e.resolve(this@MainActivity)
                } else {
                    runOnUiThread {
                        Toast.makeText(
                            applicationContext, getString(R.string.ConnectionError), Toast.LENGTH_LONG
                        ).show()
                    }
                    Log.e(tag, "Could not connect to Health Tracking Service: " + e.message)
                }
            }
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("Wear","onCreate")

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        message.sender = Entity.WEAR_APP

        currentDataBatchNumber = 0

        uiUpdateThread = Thread {}
        uiUpdateThread.start()

        // requests permission
        if (ActivityCompat.checkSelfPermission(
                applicationContext,
                getString(R.string.BodySensors)
            ) == PackageManager.PERMISSION_DENIED
        ) requestPermissions(
            arrayOf(
                Manifest.permission.BODY_SENSORS
            ), 0
        )
        else {
            permissionGranted = true
            createConnectionManager()
        }

        // set UI vars
        textStatus = binding.statusMsg
        textTip = binding.message
//        hrContainer = binding.heartRateContainer
//        textHeartRate = binding.heartRate
//        textHeartRateStatus = binding.heartRateStatus
//        textIbi = binding.ibi
//        textIbiStatus = binding.ibiStatus
        ppgGreenContainer = binding.ppgGreenContainer
        textPpgGreenNumber = binding.ppgGreenBatch
        textPpgGreen = binding.ppgGreen
        textPpgGreenTimestamp = binding.ppgGreenTimestamp

        // set initial UI
        textStatus.text = getString(R.string.default_status)
        textTip.text = getString(R.string.message_placeholder)
//        textHeartRate.text = getString(R.string.default_value)
//        textHeartRateStatus.text = getString(R.string.default_status)
//        textIbi.text = getString(R.string.default_value)
//        textIbiStatus.text = getString(R.string.default_status)
        textPpgGreenNumber.text = getString(R.string.default_value)
        textPpgGreen.text = getString(R.string.default_value)
        textPpgGreenTimestamp.text = getString(R.string.default_value)

        textTip.visibility = View.VISIBLE
//        hrContainer.visibility = View.GONE
        ppgGreenContainer.visibility = View.GONE

        // set clickables
//        hrContainer.setOnClickListener { // hide the HR container
//            runOnUiThread {
//                hrContainer.visibility = View.GONE
//            }
//        }
//        ppgGreenContainer.setOnClickListener { // show the HR container
//            runOnUiThread {
//                hrContainer.visibility = View.VISIBLE
//            }
//        }

        // build Google API Client with access to Wearable API
        client = GoogleApiClient.Builder(this)
            .addConnectionCallbacks(this)
            .addApi(Wearable.API)
            .build()
        client.connect()
        Log.d("Wear","build Google API passed")
    }

    override fun onDestroy() {
        Log.d(tag, "onDestroy")
        super.onDestroy()
//        heartRateListener.stopTracker()
        ppgGreenListener.stopTracker()
        TrackerDataNotifier.instance?.removeObserver(trackerDataObserver)
        connectionManager.disconnect()
    }

    private fun createConnectionManager() {
        try {
            connectionManager = ConnectionManager(connectionObserver)
            connectionManager.connect(applicationContext)
        } catch (t: Throwable) {
            Log.e(tag, t.message!!)
        }
    }

    override fun onConnected(bundle: Bundle?) {
        Wearable.NodeApi.getConnectedNodes(client).setResultCallback {
            connectedNode = it.nodes
            Log.d("Wear","Node connected")

            // prompt to reset phone app state
            // onConnected is not immediately run when onCreate
            // so the prompt should be executed on connection
            message.code = ActivityCode.STOP_ACTIVITY
            message.content = "Reset Phone App State"
            sendMessage(message, MessagePath.COMMAND)
            Log.d("Wear","prompt reset passed")
        }
        Wearable.MessageApi.addListener(client) { messageEvent ->
            currentMessage = Gson().fromJson(String(messageEvent.data), Message::class.java)
            onMessageArrived(messageEvent.path)
        }
    }

    override fun onConnectionSuspended(code: Int) {
        Log.w("Wear", "Google Api Client connection suspended!")
    }

    private fun onMessageArrived(messagePath: String) {
        currentMessage?.let {
            when (messagePath) {
                MessagePath.COMMAND -> {
                    when (it.code) {
                        ActivityCode.START_ACTIVITY -> {
                            runOnUiThread {
//                                hrContainer.visibility = View.VISIBLE
                                ppgGreenContainer.visibility = View.VISIBLE
                                textTip.visibility = View.GONE
                                textStatus.text = getString(R.string.status_running)
                            }

                            switchState(1)
//                            heartRateListener.startTracker()
                            ppgGreenListener.startTracker()
                        }
                        ActivityCode.STOP_ACTIVITY -> {
                            runOnUiThread {
                                textStatus.text = getString(R.string.status_stopped)
                            }

                            switchState(0)
//                            heartRateListener.stopTracker()
                            ppgGreenListener.stopTracker()
                        }
                        ActivityCode.DO_NOTHING -> {
                            runOnUiThread {
                                Toast.makeText(
                                    applicationContext,
                                    getString(R.string.no_content),
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    }
                }
                MessagePath.REQUEST -> {
                    if (it.code == ActivityCode.START_ACTIVITY) { // receive state request
                        Log.i("Wear","A request received!")
                        switchState(currentState)
                    }
                }
                MessagePath.INFO -> {
                    TODO("Not yet implemented")
                }
            }
        }
    }

    private fun sendMessage(message: Message, path: String) {
        val gson = Gson()
        connectedNode?.forEach { node ->
            val bytes = gson.toJson(message).toByteArray()
            Wearable.MessageApi.sendMessage(client, node.id, path, bytes)
            Log.i("Wear","Message sent!")
            Log.i("Wear","$message")
        }
    }

    private fun sendHrData(hrData: HeartRateData) {
        val heartData = HeartData(
            hrData.hr,
            hrData.ibi,
            LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME).toString()
        )
        val bytes = Gson().toJson(heartData).toByteArray()
        Wearable.DataApi.putDataItem(client,
            PutDataRequest.create(MessagePath.DATA_HR).setData(bytes).setUrgent()
        )
        Log.i("Wear","Heart Data sent via DataApi!")
    }

    private fun sendPpgGreenData(ppgGreenData: PpgGreenData) {
        val ppgData = PpgData(
            currentDataBatchNumber,
            ppgGreenData.ppgValue,
            ppgGreenData.timestamp
        )
        val bytes = Gson().toJson(ppgData).toByteArray()
        Wearable.DataApi.putDataItem(client,
            PutDataRequest.create(MessagePath.DATA_PPG_GREEN).setData(bytes).setUrgent()
        )
        Log.i("Wear","PPG Green Data sent via DataApi!")
    }

    private fun switchState(forceCode: Int = 99) {
        if (forceCode != 99) {
            currentState = forceCode
        }
        else if (currentState == 0) {
            currentState = 1
        }
        else if (currentState == 1) {
            currentState = 0
        }
        message.code = currentState
        message.content = "Wear state: $currentState"
        sendMessage(message, MessagePath.INFO)
        Log.d("Wear","stateNum changed to: $currentState")
    }
}