package com.example.samplewearmobileapp

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.CountDownTimer
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import com.example.samplewearmobileapp.constants.codes.ActivityCode
import com.example.samplewearmobileapp.constants.Entity
import com.example.samplewearmobileapp.constants.MessagePath
import com.example.samplewearmobileapp.constants.codes.ExtraCode.TOGGLE_ACTIVITY
import com.example.samplewearmobileapp.databinding.ActivityMainBinding
import com.example.samplewearmobileapp.models.HeartData
import com.example.samplewearmobileapp.models.Message
import com.example.samplewearmobileapp.models.PpgData
import com.example.samplewearmobileapp.models.PpgType
import com.example.samplewearmobileapp.trackers.Listener
import com.example.samplewearmobileapp.trackers.heartrate.HeartRateData
import com.example.samplewearmobileapp.trackers.ppggreen.PpgGreenData
import com.example.samplewearmobileapp.trackers.ppggreen.PpgGreenListener
import com.example.samplewearmobileapp.trackers.ppggreen.PpgGreenStatus
import com.example.samplewearmobileapp.trackers.ppgir.PpgIrData
import com.example.samplewearmobileapp.trackers.ppgir.PpgIrListener
import com.example.samplewearmobileapp.trackers.ppgred.PpgRedData
import com.example.samplewearmobileapp.trackers.ppgred.PpgRedListener
import com.google.android.gms.common.api.GoogleApiClient
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.PutDataRequest
import com.google.android.gms.wearable.Wearable
import com.google.gson.Gson
import com.samsung.android.service.health.tracking.HealthTrackerException
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicBoolean

class MainActivity : Activity(), GoogleApiClient.ConnectionCallbacks {
    private val tag = "Wear: MainActivity"
    private lateinit var binding: ActivityMainBinding
    private lateinit var client: GoogleApiClient
    private var connectedNode: List<Node>? = null
    private var message: Message = Message()
    private var currentMessage: Message? = null
    private var currentState = 0
    private var currentPpgGreenDataNumber = 0
    private var currentPpgIrDataNumber = 0
    private var currentPpgRedDataNumber = 0
    private var isOnDemandMeasurementRunning = AtomicBoolean(false)

    private lateinit var textStatus: TextView
    private lateinit var textTip: TextView
//    private lateinit var hrContainer: LinearLayout
//    private lateinit var textHeartRate: TextView
//    private lateinit var textHeartRateStatus: TextView
//    private lateinit var textIbi: TextView
//    private lateinit var textIbiStatus: TextView
    private lateinit var ppgGreenContainer: LinearLayout
    private lateinit var textPpgGreen: TextView
    private lateinit var textPpgGreenNumber: TextView
    private lateinit var textPpgGreenTimestamp: TextView
    private lateinit var ppgIrContainer: LinearLayout
    private lateinit var textPpgIr: TextView
    private lateinit var textPpgIrStatus: TextView
    private lateinit var textPpgIrNumber: TextView
    private lateinit var textPpgIrTimestamp: TextView
    private lateinit var ppgRedContainer: LinearLayout
    private lateinit var textPpgRed: TextView
    private lateinit var textPpgRedStatus: TextView
    private lateinit var textPpgRedNumber: TextView
    private lateinit var textPpgRedTimestamp: TextView


    private lateinit var uiUpdateThread: Thread
    private lateinit var connectionManager: ConnectionManager
//    private lateinit var heartRateListener: HeartRateListener
    private lateinit var ppgGreenListener: PpgGreenListener
    private lateinit var ppgIrListener: PpgIrListener
    private lateinit var ppgRedListener: PpgRedListener
    private var connected = false
    private var permissionGranted = false
//    private var heartRateDataLast = HeartRateData()
    private var ppgGreenDataLast = PpgGreenData()
    private var ppgIrDataLast = PpgIrData()
    private var ppgRedDataLast = PpgRedData()

    // TODO: use countdown timer
    private val onDemandCountDownTimer: CountDownTimer = object : CountDownTimer(
        ON_DEMAND_MEASUREMENT_DURATION.toLong(),
        ON_DEMAND_MEASUREMENT_TICK.toLong()
    ) {
        override fun onTick(timeLeft: Long) {
            if (!isOnDemandMeasurementRunning.get())
                cancel()
        }

        override fun onFinish() {
            if (!isOnDemandMeasurementRunning.get()) return
            Log.i(tag, "On-Demand measurement finished")
            runOnUiThread {
                textPpgIrStatus.setText(R.string.status_finished)
                textPpgRedStatus.setText(R.string.status_finished)
            }
            ppgIrListener.stopTracker()
            ppgRedListener.stopTracker()
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            isOnDemandMeasurementRunning.set(false)
        }
    }

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
                    currentPpgGreenDataNumber++
                    this@MainActivity.runOnUiThread {
                        textPpgGreen.text = ppgGreenData.ppgValue.toString()
                        Log.i(tag, "PPG Green : ${ppgGreenData.ppgValue}")
                        textPpgGreenTimestamp.text = ppgGreenData.timestamp.toString()
                        Log.i(tag, "PPG Green Timestamp : ${ppgGreenData.timestamp}")
                        textPpgGreenNumber.text = currentPpgGreenDataNumber.toString()
                    }
                }
                PpgGreenStatus.PPG_GREEN_STATUS_NONE.code -> {
                    Log.i(tag, "No Green PPG Data")
                }
            }
            // commented out sending data for trial
            sendPpgGreenData(ppgGreenData)
            ppgGreenDataLast = ppgGreenData
        }

        override fun onPpgIrTrackerDataChanged(ppgIrData: PpgIrData) {
            Log.i(tag, "InfraRed PPG Data received")
            currentPpgIrDataNumber++
            this@MainActivity.runOnUiThread {
                textPpgIrStatus.text = getString(R.string.status_measuring)
                textPpgIr.text = ppgIrData.ppgValue.toString()
                Log.i(tag, "PPG IR : ${ppgIrData.ppgValue}")
                textPpgIrTimestamp.text = ppgIrData.timestamp.toString()
                Log.i(tag, "PPG IR Timestamp : ${ppgIrData.timestamp}")
                textPpgIrNumber.text = currentPpgIrDataNumber.toString()
            }
            // commented out sending data for trial
            sendPpgIrData(ppgIrData)
            ppgIrDataLast = ppgIrData
        }

        override fun onPpgRedTrackerDataChanged(ppgRedData: PpgRedData) {
            Log.i(tag, "Red PPG Data received")
            currentPpgRedDataNumber++
            this@MainActivity.runOnUiThread {
                textPpgRedStatus.text = getString(R.string.status_measuring)
                textPpgRed.text = ppgRedData.ppgValue.toString()
                Log.i(tag, "PPG Red : ${ppgRedData.ppgValue}")
                textPpgRedTimestamp.text = ppgRedData.timestamp.toString()
                Log.i(tag, "PPG Red Timestamp : ${ppgRedData.timestamp}")
                textPpgRedNumber.text = currentPpgRedDataNumber.toString()
            }
            // commented out sending data for trial
            sendPpgRedData(ppgRedData)
            ppgRedDataLast = ppgRedData
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
            ppgIrListener = PpgIrListener()
            ppgRedListener = PpgRedListener()

//            connectionManager.initHeartRate(heartRateListener)
            connectionManager.initPpgGreen(ppgGreenListener)
            connectionManager.initPpgIr(ppgIrListener)
            connectionManager.initPpgRed(ppgRedListener)

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

        currentPpgGreenDataNumber = 0

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
        textPpgGreenNumber = binding.ppgGreenNumber
        textPpgGreen = binding.ppgGreen
        textPpgGreenTimestamp = binding.ppgGreenTimestamp
        ppgIrContainer = binding.ppgIrContainer
        textPpgIrStatus = binding.ppgIrStatus
        textPpgIrNumber = binding.ppgIrNumber
        textPpgIr = binding.ppgIr
        textPpgIrTimestamp = binding.ppgIrTimestamp
        ppgRedContainer = binding.ppgRedContainer
        textPpgRedStatus = binding.ppgRedStatus
        textPpgRedNumber = binding.ppgRedNumber
        textPpgRed = binding.ppgRed
        textPpgRedTimestamp = binding.ppgRedTimestamp

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
        textPpgIrStatus.text = getString(R.string.default_status)
        textPpgIrNumber.text = getString(R.string.default_value)
        textPpgIr.text = getString(R.string.default_value)
        textPpgIrTimestamp.text = getString(R.string.default_value)
        textPpgRedStatus.text = getString(R.string.default_status)
        textPpgRedNumber.text = getString(R.string.default_value)
        textPpgRed.text = getString(R.string.default_value)
        textPpgRedTimestamp.text = getString(R.string.default_value)

        textTip.visibility = View.VISIBLE
//        hrContainer.visibility = View.GONE
        ppgGreenContainer.visibility = View.GONE
        ppgIrContainer.visibility = View.GONE
        ppgRedContainer.visibility = View.GONE

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

    private fun invalidateAppStatus() {
        if (!ppgGreenListener.isTracking() &&
            !ppgIrListener.isTracking() &&
            !ppgRedListener.isTracking()) {
            runOnUiThread {
                textStatus.text = getString(R.string.status_stopped)
                textPpgIrStatus.text = getString(R.string.status_stopped)
                textPpgRedStatus.text = getString(R.string.status_stopped)
            }
        }
    }

    override fun onDestroy() {
        Log.d(tag, "onDestroy")
        super.onDestroy()
//        heartRateListener.stopTracker()
        ppgGreenListener.stopTracker()
        ppgIrListener.stopTracker()
        ppgRedListener.stopTracker()
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
                        ActivityCode.START_ACTIVITY -> { // start all tracker
                            runOnUiThread {
//                                hrContainer.visibility = View.VISIBLE
                                ppgGreenContainer.visibility = View.VISIBLE
                                ppgIrContainer.visibility = View.VISIBLE
                                ppgRedContainer.visibility = View.VISIBLE
                                textTip.visibility = View.GONE
                                textStatus.text = getString(R.string.status_running)
                            }

                            switchState(1)
//                            heartRateListener.startTracker()
                            ppgGreenListener.startTracker()
                            ppgIrListener.startTracker()
                            ppgRedListener.startTracker()
                        }
                        ActivityCode.STOP_ACTIVITY -> { // stop all tracker
                            runOnUiThread {
                                textStatus.text = getString(R.string.status_stopped)
                                textPpgIrStatus.text = getString(R.string.status_stopped)
                                textPpgRedStatus.text = getString(R.string.status_stopped)
                            }

                            switchState(0)
//                            heartRateListener.stopTracker()
                            ppgGreenListener.stopTracker()
                            ppgIrListener.stopTracker()
                            ppgRedListener.stopTracker()
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
                MessagePath.DATA_PPG_GREEN -> {
                    if (it.code == ActivityCode.START_ACTIVITY) { // start tracker
                        if (it.extraCode == TOGGLE_ACTIVITY) { // if toggle is instructed
                            toggleTracker(ppgGreenListener)
                            invalidateAppStatus()
                            return
                        }

                        runOnUiThread {
                            ppgGreenContainer.visibility = View.VISIBLE
                            textTip.visibility = View.GONE
                            textStatus.text = getString(R.string.status_running)
                        }

                        ppgGreenListener.startTracker()
                    }
                    else if (it.code == ActivityCode.STOP_ACTIVITY) { // stop tracker
                        ppgGreenListener.stopTracker()
                        invalidateAppStatus()
                    }
                }
                MessagePath.DATA_PPG_IR -> {
                    if (it.code == ActivityCode.START_ACTIVITY) { // start tracker
                        if (it.extraCode == TOGGLE_ACTIVITY) { // if toggle instructed
                            toggleTracker(ppgIrListener)
                            if (!ppgIrListener.isTracking()) runOnUiThread {
                                textPpgIrStatus.text = getString(R.string.status_stopped)
                            }
                            invalidateAppStatus()
                            return
                        }

                        runOnUiThread {
                            ppgIrContainer.visibility = View.VISIBLE
                            textTip.visibility = View.GONE
                            textStatus.text = getString(R.string.status_running)
                        }

                        ppgIrListener.startTracker()
                    }
                    else if (it.code == ActivityCode.STOP_ACTIVITY) { // stop tracker
                        ppgIrListener.stopTracker()
                        if (!ppgIrListener.isTracking()) runOnUiThread {
                            textPpgIrStatus.text = getString(R.string.status_stopped)
                        }
                        invalidateAppStatus()
                    }
                }
                MessagePath.DATA_PPG_RED -> {
                    if (it.code == ActivityCode.START_ACTIVITY) { // start tracker
                        if (it.extraCode == TOGGLE_ACTIVITY) { // if toggle instructed
                            toggleTracker(ppgRedListener)
                            if (!ppgRedListener.isTracking()) runOnUiThread {
                                textPpgRedStatus.text = getString(R.string.status_stopped)
                            }
                            invalidateAppStatus()
                            return
                        }

                        runOnUiThread {
                            ppgRedContainer.visibility = View.VISIBLE
                            textTip.visibility = View.GONE
                            textStatus.text = getString(R.string.status_running)
                        }

                        ppgRedListener.startTracker()
                    }
                    else if (it.code == ActivityCode.STOP_ACTIVITY) { // stop tracker
                        ppgRedListener.stopTracker()
                        if (!ppgRedListener.isTracking()) runOnUiThread {
                            textPpgRedStatus.text = getString(R.string.status_stopped)
                        }
                        invalidateAppStatus()
                    }
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
            currentPpgGreenDataNumber,
            ppgGreenData.ppgValue,
            ppgGreenData.timestamp,
            PpgType.PPG_GREEN
        )
        val bytes = Gson().toJson(ppgData).toByteArray()
        Wearable.DataApi.putDataItem(client,
            PutDataRequest.create(MessagePath.DATA_PPG_GREEN).setData(bytes).setUrgent()
        )
        Log.i("Wear","PPG Green Data sent via DataApi!")
    }

    private fun sendPpgIrData(ppgIrData: PpgIrData) {
        val ppgData = PpgData(
            currentPpgIrDataNumber,
            ppgIrData.ppgValue,
            ppgIrData.timestamp,
            PpgType.PPG_IR
        )
        val bytes = Gson().toJson(ppgData).toByteArray()
        Wearable.DataApi.putDataItem(client,
            PutDataRequest.create(MessagePath.DATA_PPG_IR).setData(bytes).setUrgent()
        )
        Log.i("Wear","PPG IR Data sent via DataApi!")
    }

    private fun sendPpgRedData(ppgRedData: PpgRedData) {
        val ppgData = PpgData(
            currentPpgRedDataNumber,
            ppgRedData.ppgValue,
            ppgRedData.timestamp,
            PpgType.PPG_RED
        )
        val bytes = Gson().toJson(ppgData).toByteArray()
        Wearable.DataApi.putDataItem(client,
            PutDataRequest.create(MessagePath.DATA_PPG_RED).setData(bytes).setUrgent()
        )
        Log.i("Wear","PPG Red Data sent via DataApi!")
    }

    private fun toggleTracker(listener: Listener) {
        when (listener) {
            ppgGreenListener -> {
                if (ppgGreenListener.isTracking()) ppgGreenListener.stopTracker()
                else ppgGreenListener.startTracker()
            }
            ppgIrListener -> {
                if (ppgIrListener.isTracking()) ppgIrListener.stopTracker()
                else ppgIrListener.startTracker()
            }
            ppgRedListener -> {
                if (ppgRedListener.isTracking()) ppgRedListener.stopTracker()
                else ppgRedListener.startTracker()
            }
        }
    }

    /**
     * Toggles app state between 0 and 1.
     *
     * If a `forceCode` other than 0 or 1 has been passed before,
     * `switchState` will not work until another call with
     *  a `forceCode` of 0 or 1 is executed.
     *
     * @param forceCode Optional.
     * Any integer other than 99.
     *
     * If present, this function will set the force code
     * as the app state. Note that using value other than 0 or 1
     * will break the function until another call using 0 or 1
     * as the force code.
     */
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

    companion object {
        /**
         * Measurement duration for On-Demand data type in ms.
         */
        private const val ON_DEMAND_MEASUREMENT_DURATION = 30000 // 30k ms = 30 secs
        private const val ON_DEMAND_MEASUREMENT_TICK = 250 // for ticking countdown timer
    }
}