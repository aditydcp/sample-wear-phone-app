package com.example.samplewearmobileapp

import android.Manifest
import android.bluetooth.BluetoothAdapter.*
import android.bluetooth.BluetoothManager
import android.content.*
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.*
import android.preference.PreferenceManager
import android.provider.DocumentsContract
import android.text.InputType
import android.util.Log
import android.view.*
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import com.androidplot.xy.XYPlot
import com.example.samplewearmobileapp.BluetoothService.REQUEST_CODE_ENABLE_BLUETOOTH
import com.example.samplewearmobileapp.Constants.ECG_SAMPLE_RATE
import com.example.samplewearmobileapp.Constants.PREF_ANALYSIS_VISIBILITY
import com.example.samplewearmobileapp.Constants.PREF_DEVICE_ID
import com.example.samplewearmobileapp.Constants.PREF_PATIENT_NAME
import com.example.samplewearmobileapp.Constants.PREF_TREE_URI
import com.example.samplewearmobileapp.EcgImager.createImage
import com.example.samplewearmobileapp.constants.codes.ActivityCode
import com.example.samplewearmobileapp.constants.Entity.PHONE_APP
import com.example.samplewearmobileapp.constants.MessagePath
import com.example.samplewearmobileapp.databinding.ActivityMainBinding
import com.example.samplewearmobileapp.models.Message
import com.example.samplewearmobileapp.models.PlotArrays
import com.example.samplewearmobileapp.utils.AppUtils
import com.example.samplewearmobileapp.utils.UriUtils
import com.google.android.gms.common.api.GoogleApiClient
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.Wearable
import com.google.gson.Gson
import com.polar.sdk.api.PolarBleApi
import com.polar.sdk.api.PolarBleApi.DeviceStreamingFeature
import com.polar.sdk.api.PolarBleApiCallback
import com.polar.sdk.api.PolarBleApiDefaultImpl.defaultImplementation
import com.polar.sdk.api.PolarBleApiDefaultImpl.versionInfo
import com.polar.sdk.api.errors.PolarInvalidArgument
import com.polar.sdk.api.model.PolarDeviceInfo
import com.polar.sdk.api.model.PolarEcgData
import com.polar.sdk.api.model.PolarHrData
import com.polar.sdk.api.model.PolarSensorSetting
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.Disposable
import java.io.FileOutputStream
import java.io.FileWriter
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToLong
import kotlin.system.exitProcess

class MainActivity : AppCompatActivity(), GoogleApiClient.ConnectionCallbacks,
    SharedPreferences.OnSharedPreferenceChangeListener {
    private lateinit var binding: ActivityMainBinding
    private lateinit var client: GoogleApiClient
    private lateinit var menu: Menu
//    private lateinit var acquaintedDevices: MutableList<DeviceInfo>
    private var polarApi: PolarBleApi? = null
    private var ecgDisposable: Disposable? = null
    var ecgPlotter: EcgPlotter? = null
    private var qrsDetector: QrsDetector? = null
    var qrsPlotter: QrsPlotter? = null
    var hrPlotter: HrPlotter? = null

    private var connectedNode: List<Node>? = null
    private var message: Message = Message(PHONE_APP)
    private var wearMessage: Message? = null
    private var appState = 0
    private var bluetoothState = STATE_OFF
    private var isUsingAnalysis = false

    /**
     * Whether to save as CSV, Plot, or both.
     */
    private enum class SaveType {
        DATA, PLOT, BOTH
//        , DEVICE_HR, QRS_HR, ALL
    }

    private var isConnected = false
    private var isPlaying = false

    private var sharedPreferences: SharedPreferences? = null

    private var deviceId = ""
    private var deviceFirmware = "NA"
    private var deviceName = "NA"
    private var deviceAddress = "NA"
    private var deviceBatteryLevel = "NA"

    //* Date when stopped playing. Updated whenever playing started or
    // stopped. */
    private var stopTime: Date? = null

    private var deviceStopHr = "NA"
    private var calculatedStopHr = "NA"

    private lateinit var ecgContainer: ViewGroup
    private lateinit var textEcgHr: TextView
    private lateinit var textEcgInfo: TextView
    private lateinit var textEcgTime: TextView
    private lateinit var ecgPlot: XYPlot
    private lateinit var analysisContainer: ViewGroup
    private lateinit var qrsPlot: XYPlot
    private lateinit var hrPlot: XYPlot

//    private lateinit var textWearStatus: TextView
//    private lateinit var textWearHr: TextView
//    private lateinit var textWearIbi: TextView
//    private lateinit var textWearTimestamp: TextView
//    private lateinit var textHrmStatus: TextView
//    private lateinit var textHrmHr: TextView
//    private lateinit var textHrmIbi: TextView
//    private lateinit var textHrmTimestamp: TextView
//    private lateinit var textTooltip: TextView
//    private lateinit var buttonMain: Button
//    private lateinit var buttonView: TextView
//    private lateinit var buttonConnectHrm: TextView
//    private lateinit var containerWear: LinearLayout
//    private lateinit var containerHrm: LinearLayout

    private val bluetoothStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            bluetoothState = intent.getIntExtra(EXTRA_STATE, STATE_OFF)
            when (bluetoothState) {
                STATE_TURNING_OFF -> {
                    Toast.makeText(context,
                        "Bluetooth turning off",
                        Toast.LENGTH_SHORT).show()
                }
                STATE_TURNING_ON -> {
                    Toast.makeText(context,
                        "Bluetooth turning on",
                        Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    /**
     * Launcher for opening document tree.
     *
     * Successful launch provides PREF_TREE_URI.
     */
    private val openDocumentTreeLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result: ActivityResult ->
        Log.d(
            TAG, "openDocumentTreeLauncher: result" +
                    ".getResultCode()=" + result.resultCode
        )
        // Find the UID for this application
        Log.d(TAG, "URI=" + UriUtils.getApplicationUid(this))
        Log.d(
            TAG, "Current permissions (initial): "
                    + UriUtils.getNPersistedPermissions(this)
        )
        try {
            if (result.resultCode == RESULT_OK) {
                // Get Uri from Storage Access Framework.
                val treeUri = result.data!!.data
                val editor =
                    getPreferences(MODE_PRIVATE)
                        .edit()
                if (treeUri == null) {
                    editor.putString(PREF_TREE_URI, null)
                    editor.apply()
                    AppUtils.errMsg(
                        this, ("Failed to get " +
                                "persistent " +
                                "access permissions")
                    )
                    return@registerForActivityResult
                }
                // Persist access permissions.
                try {
                    this.contentResolver.takePersistableUriPermission(
                        treeUri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or
                                Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    )
                    // Save the current treeUri as PREF_TREE_URI
                    editor.putString(
                        PREF_TREE_URI,
                        treeUri.toString()
                    )
                    editor.apply()
                    // Trim the persisted permissions
                    UriUtils.trimPermissions(this, 1)
                } catch (ex: java.lang.Exception) {
                    val msg = ("Failed to " +
                            "takePersistableUriPermission for "
                            + treeUri.path)
                    AppUtils.excMsg(this, msg, ex)
                }
                Log.d(TAG, ("Current permissions (final): "
                        + UriUtils.getNPersistedPermissions(this))
                )
            }
        } catch (ex: java.lang.Exception) {
            Log.e(
                TAG, "Error in openDocumentTreeLauncher: " +
                        "startActivity for result", ex
            )
        }
    }

    // Launcher for Settings
    private val settingsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result: ActivityResult ->
        var code = "Unknown"
        if (result.resultCode == RESULT_OK) {
            code = "RESULT_OK"
        } else if (result.resultCode == RESULT_CANCELED) {
            code = "RESULT_CANCELED"
        }
        // PREF_DEVICE_ID
        val oldDeviceId: String = deviceId
        deviceId = sharedPreferences!!.getString(
            PREF_DEVICE_ID,
            ""
        ).toString()
        Log.d(
            TAG, "settingsLauncher: resultCode=" + code
                    + " oldDeviceId=" + oldDeviceId
                    + " DeviceId=" + deviceId
        )
        if (oldDeviceId != deviceId) {
            resetDeviceId(oldDeviceId)
        }
        // PREF_ANALYSIS_VISIBILITY
        isUsingAnalysis = sharedPreferences!!.getBoolean(
            PREF_ANALYSIS_VISIBILITY, true
        )
        setAnalysisVisibility()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d(TAG, this.javaClass.simpleName + " onCreate")
        super.onCreate(savedInstanceState)

        // Capture global exceptions
        Thread.setDefaultUncaughtExceptionHandler { _, paramThrowable ->
            Log.e(TAG, "Unexpected exception :", paramThrowable)
            // Any non-zero exit code
            exitProcess(2)
        }

        // Keep the screen on
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // request for permissions
        if (allPermissionsGranted()) {
            // start Bluetooth
            setupBluetooth()
        } else {
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS
            )
        }

//        message.sender = Entity.PHONE_APP

        // set UI vars
        ecgContainer = binding.ecgContainer
        textEcgHr = binding.ecgHr
        textEcgInfo = binding.ecgInfo
        textEcgTime = binding.ecgTime
        ecgPlot = binding.ecgPlot
        analysisContainer = binding.analysisContainer
        qrsPlot = binding.qrsPlot
        hrPlot = binding.hrPlot

//        textWearStatus = binding.wearStatus
//        textWearHr = binding.wearHr
//        textWearIbi = binding.wearIbi
//        textWearTimestamp = binding.wearTime
//        textHrmStatus = binding.hrmStatus
//        textHrmHr = binding.hrmHr
//        textHrmIbi = binding.hrmIbi
//        textHrmTimestamp = binding.hrmTime
//        textTooltip = binding.buttonTooltip
//        buttonMain = binding.buttonMain
//        buttonView = binding.buttonView
//        buttonConnectHrm = binding.hrmConnect
//        containerWear = binding.wearContainer
//        containerHrm = binding.hrmContainer

//        runOnUiThread {
//            textTooltip.text = getString(R.string.click_me_text)
//            buttonMain.text = getString(R.string.button_start)
//            buttonView.visibility = View.GONE
//        }

        if (!isUsingAnalysis) {
            analysisContainer.visibility = View.GONE
        }

        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        // register preference listener
        sharedPreferences!!.registerOnSharedPreferenceChangeListener(this)

        setLastHr()
        stopTime = Date()

//        // Start Bluetooth
        deviceId = sharedPreferences!!.getString(PREF_DEVICE_ID, "").toString()
        Log.d(TAG, "DeviceId=$deviceId")
//        val gson = Gson()
//        val type = object : TypeToken<LinkedList<DeviceInfo?>?>() {}.type
//        val json: String? = sharedPreferences!!.getString(PREF_ACQ_DEVICE_IDS, null)
//        acquaintedDevices = gson.fromJson(json, type)
//        if (acquaintedDevices == null) {
//            acquaintedDevices = ArrayList<DeviceInfo>()
//        }

//        if (deviceId == null || deviceId == "") {
//            selectDeviceId()
//        }

        // register bluetooth state broadcast receiver
        registerReceiver(bluetoothStateReceiver, BluetoothService.BLUETOOTH_STATE_FILTER)

        // build Google API Client with access to Wearable API
        client = GoogleApiClient.Builder(this)
            .addApi(Wearable.API)
            .addConnectionCallbacks(this)
            .build()
        client.connect()

//        // set click listener
//        buttonMain.setOnClickListener {
//            message.content = getString(R.string.message_on_button_click)
//            setMessageCode()
//            toggleState()
//            runOnUiThread {
//                textTooltip.text = getString(R.string.clicked_text)
//            }
//            sendMessage(message, MessagePath.COMMAND)
//        }
//
//        containerWear.setOnClickListener {
//            runOnUiThread {
//                containerHrm.visibility = View.GONE
//                buttonView.visibility = View.VISIBLE
//            }
//        }
//
//        containerHrm.setOnClickListener {
//            runOnUiThread {
//                containerWear.visibility = View.GONE
//                buttonView.visibility = View.VISIBLE
//            }
//        }
//
//        buttonView.setOnClickListener {
//            runOnUiThread {
//                containerWear.visibility = View.VISIBLE
//                containerHrm.visibility = View.VISIBLE
//                buttonView.visibility = View.GONE
//            }
//        }
//
//        buttonConnectHrm.setOnClickListener {
//            val intent = Intent(this@MainActivity, ConnectHrmActivity::class.java)
//            startActivity(intent)
//        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
//        Log.d(TAG, this.getClass().getSimpleName() + " onCreateOptionsMenu");
//        Log.d(TAG, "    mPlaying=" + mPlaying);
        this@MainActivity.menu = menu
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.main_menu, menu)
        if (polarApi == null) {
            menu.findItem(R.id.pause).title = "Start"
            menu.findItem(R.id.save).isVisible = false
        } else if (isPlaying) {
            menu.findItem(R.id.pause).icon = ResourcesCompat.getDrawable(
                resources,
                R.drawable.ic_stop_white_36dp, null
            )
            menu.findItem(R.id.pause).title = "Pause"
            menu.findItem(R.id.save).isVisible = false
        } else {
            menu.findItem(R.id.pause).icon = ResourcesCompat.getDrawable(
                resources,
                R.drawable.ic_play_arrow_white_36dp, null
            )
            menu.findItem(R.id.pause).title = "Start"
            menu.findItem(R.id.save).isVisible = true
        }
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val id = item.itemId
        if (id == R.id.pause) {
            if (polarApi == null) {
                return true
            }
            if (isPlaying) {
                // Turn it off
                setLastHr()
                stopTime = Date()
                isPlaying = false
                setPanBehavior()
                if (ecgDisposable != null) {
                    // Turns it off
                    toggleEcgStream()
                }
                menu.findItem(R.id.pause).icon = ResourcesCompat.getDrawable(
                    resources,
                    R.drawable.ic_play_arrow_white_36dp, null
                )
                menu.findItem(R.id.pause).title = "Start"
                menu.findItem(R.id.save).isVisible = true
            } else {
                // Turn it on
                setLastHr()
                stopTime = Date()
                isPlaying = true
                setPanBehavior()
                textEcgTime.text = getString(
                    R.string.elapsed_time,
                    0.0
                )
                // Clear the plot
                ecgPlotter?.clear()
                qrsPlotter?.clear()
                hrPlotter?.clear()
                if (ecgDisposable == null) {
                    // Turns it on
                    toggleEcgStream()
                }
                menu.findItem(R.id.pause).icon = ResourcesCompat.getDrawable(
                    resources,
                    R.drawable.ic_stop_white_36dp, null
                )
                menu.findItem(R.id.pause).title = "Pause"
                menu.findItem(R.id.save).isVisible = false
            }
            return true
        } else if (id == R.id.save_plot) {
            saveDataWithNote(SaveType.PLOT)
            return true
        } else if (id == R.id.save_data) {
            saveDataWithNote(SaveType.DATA)
            return true
        } else if (id == R.id.save_both) {
            saveDataWithNote(SaveType.BOTH)
            return true
        } else if (id == R.id.info) {
            displayInfo()
            return true
        } else if (id == R.id.restart_api) {
            restartPolarApi()
            return true
        } else if (id == R.id.redo_plot_setup) {
            redoPlotSetup()
            return true
        } else if (id == R.id.device_id) {
            selectDeviceId()
            return true
        } else if (id == R.id.choose_data_directory) {
            chooseDataDirectory()
            return true
        } else if (id == R.id.help) {
            showHelp()
            return true
        } else if (item.itemId == R.id.menu_settings) {
            showSettings()
            return true
        }
        return false
    }

    override fun onConnected(p0: Bundle?) {
        Wearable.NodeApi.getConnectedNodes(client).setResultCallback {
            connectedNode = it.nodes
            Log.d(TAG,"Node connected")

//            runOnUiThread {
//                textWearStatus.text = getString(R.string.status_connected)
//            }

            // request Wear App current state
            // request on connection to ensure message is sent
            message.content = "Requesting Wear App current state"
            message.code = ActivityCode.START_ACTIVITY
            sendMessage(message, MessagePath.REQUEST)
        }
        Wearable.MessageApi.addListener(client) { messageEvent ->
            wearMessage = Gson().fromJson(String(messageEvent.data), Message::class.java)
            onMessageArrived(messageEvent.path)
        }
        Wearable.DataApi.addListener(client) { data ->
            Log.d(TAG, "Data count arrived : ${data.count}")
            for (dataEvent in data) {
                Log.d(TAG, "Data Event\n" +
                        "URI Last Path Segment: ${dataEvent.dataItem.uri.lastPathSegment}\n" +
                        "URI Path: ${dataEvent.dataItem.uri.path}\n" +
                        "URI encoded path: ${dataEvent.dataItem.uri.encodedPath}\n" +
                        "URI Host: ${dataEvent.dataItem.uri.host}")
            }
//            when (data[0].dataItem.uri.path) {
//                MessagePath.DATA_HR -> {
//                    val receivedData = Gson().fromJson(String(data[0].dataItem.data), HeartData::class.java)
//                    runOnUiThread {
//                        textWearHr.text = receivedData.hr.toString()
//                        textWearIbi.text = receivedData.ibi.toString()
//                        textWearTimestamp.text = receivedData.timestamp
//                    }
//                }
//                MessagePath.DATA_PPG_GREEN -> {
//
//                }
//            }
//            val receivedData = Gson().fromJson(String(data[0].dataItem.data), HeartData::class.java)
//            runOnUiThread {
//                textWearHr.text = receivedData.hr.toString()
//                textWearIbi.text = receivedData.ibi.toString()
//                textWearTimestamp.text = receivedData.timestamp
//            }
        }
    }

    override fun onConnectionSuspended(p0: Int) {
        connectedNode = null
//        runOnUiThread {
//            textWearStatus.text = getString(R.string.status_disconnected)
//        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == REQUEST_CODE_ENABLE_BLUETOOTH) {
            when (resultCode) {
                RESULT_OK -> {
                    Toast.makeText(this,
                        "Bluetooth enabled.",
                        Toast.LENGTH_SHORT).show()
                }
                RESULT_CANCELED -> {
                    Toast.makeText(this,
                        "Bluetooth has not been enabled.",
                        Toast.LENGTH_SHORT).show()
                }
            }
        }
        else super.onActivityResult(requestCode, resultCode, data)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults:
        IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                setupBluetooth()
            } else {
                Toast.makeText(this,
                    "Permissions not granted by the user.",
                    Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    override fun onSharedPreferenceChanged(
        sharedPreferences: SharedPreferences?,
        key: String
    ) {
        Log.d(TAG, "onSharedPreferenceChanged: key=$key")
    }

    /**
     * Tries to disconnect deviceId from the current polarAPI and restarts the polarAPI.
     */
    private fun restartPolarApi() {
        Log.d(TAG, this.javaClass.simpleName + " restartApi:")
        resetDeviceId(deviceId)
    }

    private fun restart() {
        Log.d(
            TAG, this.javaClass.simpleName + " restart:"
                    + " mApi=" + polarApi
                    + " mDeviceId=" + deviceId
        )
        if (polarApi != null || deviceId == null || deviceId.isEmpty()) {
            return
        }
        if (ecgDisposable != null) {
            // Turns it off
            toggleEcgStream()
        }
        isPlaying = false
        ecgPlotter?.clear()
        textEcgHr.text = ""
        textEcgInfo.text = ""
        textEcgTime.text = ""
        invalidateOptionsMenu()
        Toast.makeText(
            this,
            getString(R.string.connecting) + " " + deviceId,
            Toast.LENGTH_SHORT
        ).show()

//        // Don't use SDK if BT is not enabled or permissions are not granted.
//        if (!mBleSupported) return
        if (!allPermissionsGranted()) {
//            if (!mAllPermissionsAsked) {
//                mAllPermissionsAsked = true
//                Utils.warnMsg(this, getString(R.string.permission_not_granted))
//            }
//            return
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS
            )
        }

        polarApi = defaultImplementation(
            this,
            PolarBleApi.FEATURE_POLAR_SENSOR_STREAMING or
                    PolarBleApi.FEATURE_BATTERY_INFO or
                    PolarBleApi.FEATURE_DEVICE_INFO or
                    PolarBleApi.FEATURE_POLAR_FILE_TRANSFER or
                    PolarBleApi.FEATURE_HR
        )
        // DEBUG
        // Post a Runnable to have plots to be setup again in 1 sec
        val handler = Handler(Looper.getMainLooper())
        handler.postDelayed({
//            Log.d(TAG,
//                    "No connection handler: time=" + sdfShort.format(new
//                    Date()));
            if (!isConnected) {
                AppUtils.warnMsg(
                    this@MainActivity, "No connection to " + deviceId
                            + " after 1 minute"
                )
            }
        }, 60000)

        polarApi!!.setApiCallback(object: PolarBleApiCallback() {
            override fun blePowerStateChanged(b: Boolean) {
                Log.d(TAG, "BluetoothStateChanged $b")
            }

            override fun deviceConnected(s: PolarDeviceInfo) {
                Log.d(TAG, "*Device connected " + s.deviceId)
                deviceAddress = s.address
                deviceName = s.name
                isConnected = true
//                // Set the MRU preference here after we know the name
//                setDevicePreferences(DeviceInfo(deviceName, deviceId))
                Toast.makeText(
                    this@MainActivity,
                    getString(R.string.connected_string, s.name),
                    Toast.LENGTH_SHORT
                ).show()
            }

            override fun deviceDisconnected(s: PolarDeviceInfo) {
                Log.d(TAG, "*Device disconnected $s")
                isConnected = false
            }

            override fun streamingFeaturesReady(
                identifier: String,
                features: Set<DeviceStreamingFeature>
            ) {
                for (feature in features) {
                    Log.d(TAG, "Streaming feature is ready for 1: $feature")
                    when (feature) {
                        DeviceStreamingFeature.ECG -> toggleEcgStream()
//                        DeviceStreamingFeature.PPI,
//                        DeviceStreamingFeature.ACC,
//                        DeviceStreamingFeature.MAGNETOMETER,
//                        DeviceStreamingFeature.GYRO,
//                        DeviceStreamingFeature.PPG -> {}
                        else -> {}
                    }
                }
            }

            override fun hrFeatureReady(s: String) {
                Log.d(TAG, "*HR Feature ready $s")
            }

            override fun disInformationReceived(
                s: String,
                u: UUID,
                s1: String
            ) {
                if (u == UUID.fromString(
                        "00002a28-0000-1000-8000" +
                                "-00805f9b34fb"
                    )
                ) {
                    deviceFirmware = s1.trim { it <= ' ' }
                    Log.d(TAG, "*Firmware: $s $deviceFirmware")
                    textEcgInfo.text = getString(
                        R.string.info_string,
                        deviceName, deviceBatteryLevel, deviceFirmware, deviceId
                    )
                }
            }

            override fun batteryLevelReceived(s: String, i: Int) {
                deviceBatteryLevel = i.toString()
                Log.d(TAG, "*Battery level $s $i")
                textEcgInfo.text = getString(
                    R.string.info_string,
                    deviceName, deviceBatteryLevel, deviceFirmware, deviceId
                )
            }

            override fun hrNotificationReceived(
                s: String,
                polarHrData: PolarHrData
            ) {
                if (isPlaying) {
//                    Log.d(TAG,
//                            "*HR " + polarHrData.hr + " mPlaying=" +
//                            mPlaying);
                    textEcgHr.text = polarHrData.hr.toString()
//                    // Add to HR plot
                    val time = Date().time
                    hrPlotter?.addValues1(
                        time.toDouble(),
                        polarHrData.hr.toDouble(),
                        polarHrData.rrsMs
                    )
                    hrPlotter?.fullUpdate()
                }
            }
        })
        try {
            polarApi!!.connectToDevice(deviceId)
            isPlaying = true
            setLastHr()
            stopTime = Date()
        } catch (ex: PolarInvalidArgument) {
            val msg = """
                mDeviceId=$deviceId
                ConnectToDevice: Bad argument:
                """.trimIndent()
            AppUtils.excMsg(this, msg, ex)
            Log.d(TAG, "restart: $msg")
            isPlaying = false
            setLastHr()
            stopTime = Date()
        }
        invalidateOptionsMenu()
    }

    override fun onResume() {
        Log.d(TAG, this.javaClass.simpleName + " onResume:")
        super.onResume()

        // Check if PREF_TREE_URI is valid and remove it if not

        // Check if PREF_TREE_URI is valid and remove it if not
        if (UriUtils.getNPersistedPermissions(this) <= 0) {
            val editor = getPreferences(MODE_PRIVATE)
                .edit()
            editor.putString(PREF_TREE_URI, null)
            editor.apply()
        }

        polarApi?.foregroundEntered()
        invalidateOptionsMenu()

        // Setup the plots if not done
        if (ecgPlotter == null) {
            ecgPlot.post {
                ecgPlotter = EcgPlotter(
                    this, ecgPlot,
                    "ECG", Color.RED, false
                )
            }
        }
        if (hrPlotter == null) {
            hrPlot.post { hrPlotter = HrPlotter(this, hrPlot) }
        }
        if (qrsPlotter == null) {
            qrsPlot.post { qrsPlotter = QrsPlotter(this, qrsPlot) }
        }

        // Set the visibility of the Analysis plot
        isUsingAnalysis = sharedPreferences!!.getBoolean(PREF_ANALYSIS_VISIBILITY, true)
        setAnalysisVisibility()

        // Start the connection to the device
        Log.d(TAG, "DeviceId=$deviceId")
        Log.d(TAG, "polarApi=$polarApi")
        deviceId = sharedPreferences?.getString(PREF_DEVICE_ID, "").toString()
        if (deviceId == null || deviceId.isEmpty()) {
            Toast.makeText(
                this,
                getString(R.string.no_device),
                Toast.LENGTH_SHORT
            ).show()
        } else {
            restart()
        }
    }

    override fun onPause() {
        Log.v(TAG, this.javaClass.simpleName + " onPause")
        super.onPause()

        polarApi?.backgroundEntered()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // This seems to be necessary with Android 12
        // Otherwise onDestroy is not called
        Log.d(TAG, this.javaClass.simpleName + ": onBackPressed")
        finish()
        super.onBackPressed()
    }

    override fun onRestart() {
        super.onRestart()
        Log.i(TAG,"Lifecycle: onRestart()")
        // re-register bluetooth state receiver
        registerReceiver(bluetoothStateReceiver, BluetoothService.BLUETOOTH_STATE_FILTER)
    }

    override fun onStop() {
        super.onStop()
        Log.i(TAG, "Lifecycle: onStop()")
        // unregister receiver
        unregisterReceiver(bluetoothStateReceiver)
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG,"Lifecycle: onDestroy()")
        polarApi?.shutDown()
        // unregister receiver
        unregisterReceiver(bluetoothStateReceiver)
        sharedPreferences?.unregisterOnSharedPreferenceChangeListener(this)
    }

    /**
     * Sets the analysis plots visibility using the current value of isUsingAnalysis.
     */
    private fun setAnalysisVisibility() {
        Log.d(TAG, this.javaClass.simpleName + " setAnalysisVisibility: " +
                "$isUsingAnalysis")
        if (isUsingAnalysis) {
            analysisContainer.visibility = View.VISIBLE
        } else {
            analysisContainer.visibility = View.GONE
        }
    }

    /**
     * Show the help.
     */
    private fun showHelp() {
        Log.d(TAG, "showHelp")
//        try {
//            // Start theInfoActivity
//            val intent = Intent()
//            intent.setClass(this, InfoActivity::class.java)
//            intent.addFlags(
//                Intent.FLAG_ACTIVITY_NEW_TASK
//                        or Intent.FLAG_ACTIVITY_SINGLE_TOP
//            )
//            intent.putExtra(INFO_URL, "file:///android_asset/kedotnetecg.html")
//            startActivity(intent)
//        } catch (ex: java.lang.Exception) {
//            Utils.excMsg(this, getString(R.string.help_show_error), ex)
//        }
    }

    /**
     * Calls the settings activity.
     */
    private fun showSettings() {
        Log.d(TAG, "showSettings")
        val intent = Intent(
            this@MainActivity,
            SettingsActivity::class.java
        )
        settingsLauncher.launch(intent)
    }

    /**
     * Checks whether all permissions required has been granted.
     * @return Boolean value of whether all permissions required has been granted
     */
    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) ==
                PackageManager.PERMISSION_GRANTED
    }

    /**
     * Setting up Bluetooth Manager and Adapter for use.
     */
    private fun setupBluetooth() {
        // setup bluetooth adapter
        BluetoothService.manager = getSystemService(BluetoothManager::class.java)
        BluetoothService.adapter = BluetoothService.manager.adapter

        // check bluetooth availability
//        if (BluetoothService.adapter == null) {
//            runOnUiThread {
//                textHrmStatus.text = getString(R.string.status_no_support)
//            }
//        }

        BluetoothService.enableBluetooth(this, this)
    }

    /***
     * Sets the last HR from the series in the HR plotter.
     */
    private fun setLastHr() {
        calculatedStopHr = "NA"
        deviceStopHr = "NA"

        if (hrPlotter == null) return
        var lastVal: Long
        if (hrPlotter!!.hrSeries1 != null && hrPlotter!!.hrSeries1!!.size() > 0) {
            lastVal = hrPlotter!!.hrSeries1!!.getyVals().last.toDouble().roundToLong()
            deviceStopHr = String.format(Locale.US, "%d", lastVal)
        }
        if (hrPlotter!!.hrSeries2 != null && hrPlotter!!.hrSeries2!!.size() > 0) {
            lastVal = hrPlotter!!.hrSeries2!!.getyVals().last.toDouble().roundToLong()
            calculatedStopHr = String.format(Locale.US, "%d", lastVal)
        }
    }

    /**
     * Panning while collecting data causes problems. Turn it off when
     * playing and turn it on with stopped. Zooming is not enabled.
     */
    private fun setPanBehavior() {
        ecgPlotter?.setPanning(!isPlaying)
        qrsPlotter?.setPanning(!isPlaying)
        hrPlotter?.setPanning(!isPlaying)
    }

    private fun redoPlotSetup() {
        ecgPlotter?.setupPlot()
        qrsPlotter?.setupPlot()
        hrPlotter?.setupPlot()
    }

//    /**
//     * Setup a new device into the shared preferences.
//     * This will remove the earliest device added to the list
//     * if the list is on max capacity.
//     * @param deviceInfo info of the new device
//     */
//    private fun setDevicePreferences(deviceInfo: DeviceInfo) {
//        val editor: SharedPreferences.Editor = sharedPreferences!!.edit()
//        // Remove any found so the new one will be added at the beginning
//        val removeList: MutableList<DeviceInfo> = ArrayList()
//        for (acquaintedDeviceInfo in acquaintedDevices) {
//            if (deviceInfo.name == acquaintedDeviceInfo.name &&
//                deviceInfo.id == acquaintedDeviceInfo.id) {
//                removeList.add(acquaintedDeviceInfo)
//            }
//        }
//        for (device in removeList) {
//            acquaintedDevices.remove(device)
//        }
//        // Remove at end if size exceed max
//        if (acquaintedDevices.size != 0 && acquaintedDevices.size == MAX_DEVICES) {
//            acquaintedDevices.removeAt(acquaintedDevices.size - 1)
//        }
//        // Add at the beginning
//        acquaintedDevices.add(0, deviceInfo)
//        val gson = Gson()
//        val json = gson.toJson(acquaintedDevices)
//        editor.putString(PREF_ACQ_DEVICE_IDS, json)
//        deviceId = deviceInfo.id
//        editor.putString(PREF_DEVICE_ID, deviceInfo.id)
//        editor.apply()
//    }

    /**
     * Commence selecting device ID.
     */
    private fun selectDeviceId() {
//        if (acquaintedDevices.size == 0) {
//            showDeviceIdDialog(null)
//            return
//        }
//        val dialog = arrayOf(AlertDialog.Builder(
//                this@MainActivity,
//                R.style.InverseTheme)
//        )
//        dialog[0].setTitle(R.string.device_id_item)
//        val items = arrayOfNulls<String>(acquaintedDevices.size + 1)
//        var deviceInfo: DeviceInfo
//        for (i in acquaintedDevices.indices) {
//            deviceInfo = acquaintedDevices[i]
//            items[i] = deviceInfo.name
//        }
//        items[acquaintedDevices.size] = "New"
//        val checkedItem = 0
//        dialog[0].setSingleChoiceItems(
//            items, checkedItem
//        ) { dialogInterface: DialogInterface, which: Int ->
//            if (which < acquaintedDevices.size) {
//                val deviceInfo1: DeviceInfo = acquaintedDevices[which]
//                val oldDeviceId: String = deviceId
//                setDevicePreferences(deviceInfo1)
//                Log.d(
//                    TAG, "which=" + which
//                            + " name=" + deviceInfo1.name + " id="
//                            + deviceInfo1.id
//                )
//                Log.d(
//                    TAG,
//                    "selectDeviceId: oldDeviceId=" + oldDeviceId
//                            + " mDeviceId=" + deviceId
//                )
//                if (oldDeviceId != deviceId) {
//                    resetDeviceId(oldDeviceId)
//                }
//            } else {
//                showDeviceIdDialog(null)
//            }
//            dialogInterface.dismiss()
//        }
//        dialog[0].setNegativeButton(
//            R.string.cancel
//        ) { dialog1, _ -> dialog1.dismiss() }
//        val alert = dialog[0].create()
//        alert.setCanceledOnTouchOutside(false)
//        alert.show()

        showDeviceIdDialog(null)
    }

    /**
     * Show Device ID dialog input.
     */
    private fun showDeviceIdDialog(view: View?) {
        val dialog = AlertDialog.Builder(
            this,
            R.style.InverseTheme
        )
        dialog.setTitle(R.string.device_id_item)

        val viewInflated: View = LayoutInflater.from(applicationContext).inflate(
            R.layout.device_id_dialog,
            if (view == null) null else view.rootView as ViewGroup,
            false
        )

        val input = viewInflated.findViewById<EditText>(R.id.input)
        input.inputType = InputType.TYPE_CLASS_TEXT
        deviceId = sharedPreferences?.getString(PREF_DEVICE_ID, "").toString()
        input.setText(deviceId)
        dialog.setView(viewInflated)

        dialog.setPositiveButton(
            R.string.ok
        ) { _, _ ->
            val oldDeviceId: String = deviceId
            deviceId = input.text.toString()
            Log.d(
                TAG, "showDeviceIdDialog: OK:  oldDeviceId="
                        + oldDeviceId + " newDeviceId="
                        + deviceId
            )
            val editor: SharedPreferences.Editor = sharedPreferences!!.edit()
            editor.putString(PREF_DEVICE_ID, deviceId)
            editor.apply()
            if (deviceId == null || deviceId.isEmpty()) {
                Toast.makeText(
                    this@MainActivity,
                    getString(R.string.no_device),
                    Toast.LENGTH_SHORT
                ).show()
            } else if (oldDeviceId != deviceId) {
                resetDeviceId(oldDeviceId)
            }
        }
        dialog.setNegativeButton(
            R.string.cancel
        ) { dialog1, _ ->
            Log.d(
                TAG,
                "showDeviceIdDialog: Cancel:  mDeviceId=$deviceId"
            )
            dialog1.cancel()
            if (deviceId == null || deviceId.isEmpty()) {
                Toast.makeText(
                    this@MainActivity,
                    getString(R.string.no_device),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
        dialog.show()
    }

    /**
     * Tries to disconnect the device from the current API and restarts the API,
     * which will use the current mDeviceId.
     *
     * @param oldDeviceId The previous deviceId.
     */
    private fun resetDeviceId(oldDeviceId: String) {
        Log.d(TAG, this.javaClass.simpleName + " resetDeviceId:")
        if (polarApi != null) {
            if (ecgDisposable != null) {
                ecgDisposable!!.dispose()
                ecgDisposable = null
            }
            try {
                polarApi!!.disconnectFromDevice(oldDeviceId)
            } catch (ex: PolarInvalidArgument) {
                val msg = """
                oldDeviceId=$oldDeviceId
                DisconnectFromDevice: Bad argument:
                """.trimIndent()
                AppUtils.excMsg(this@MainActivity, msg, ex)
                Log.d(
                    TAG, this.javaClass.simpleName
                            + " resetDeviceId: " + msg
                )
            }
            polarApi!!.shutDown()
            polarApi = null
        }
        qrsDetector = null
        restart()
    }

    /**
     * Commence saving the current samples as a file.
     * Prompts for a note, then calls
     * the appropriate save method.
     *
     * @param saveType The SaveType.
     */
    private fun saveDataWithNote(saveType: SaveType) {
        val msg: String
        val state = Environment.getExternalStorageState()
        if (Environment.MEDIA_MOUNTED != state) {
            msg = "External Storage is not available"
            Log.e(TAG, msg)
            AppUtils.errMsg(this, msg)
            return
        }
        // Get a note
        val dialog = AlertDialog.Builder(
            this,
            R.style.InverseTheme
        )
        dialog.setTitle(R.string.note_dialog_title)
        val viewInflated: View = LayoutInflater.from(applicationContext)
            .inflate(R.layout.device_id_dialog, null, false)
        val input = viewInflated.findViewById<EditText>(R.id.input)
        input.inputType = (InputType.TYPE_CLASS_TEXT
                or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                or InputType.TYPE_TEXT_FLAG_AUTO_CORRECT)
        dialog.setView(viewInflated)
        dialog.setPositiveButton(
            R.string.ok
        ) { _, _ ->
            when (saveType) {
                SaveType.DATA -> saveData(input.text.toString())
                SaveType.PLOT -> savePlot(input.text.toString())
                SaveType.BOTH -> {
                    saveData(input.text.toString())
                    savePlot(input.text.toString())
                }
//                SaveType.ALL -> {
//                    saveData(input.text.toString())
//                    savePlot(input.text.toString())
//                    saveSessionData(SaveType.DEVICE_HR)
//                    saveSessionData(SaveType.QRS_HR)
//                }
//                SaveType.DEVICE_HR, SaveType.QRS_HR -> saveSessionData(saveType)
            }
        }
        dialog.setNegativeButton(
            R.string.cancel
        ) { _, _ -> }
        dialog.show()
    }

    /**
     * Finishes the savePlot after getting the note.
     * This only saves plot of the last 30 seconds of recording.
     *
     * @param note The note.
     */
    private fun savePlot(note: String) {
        val prefs = getPreferences(MODE_PRIVATE)
        val treeUriStr = prefs.getString(PREF_TREE_URI, null)
        if (treeUriStr == null) {
            AppUtils.errMsg(this, "There is no data directory set")
            return
        }
        val patientName = prefs.getString(PREF_PATIENT_NAME, "")
        var msg: String
        val format = "yyyy-MM-dd_HH-mm"
        val df = SimpleDateFormat(format, Locale.US)
        val fileName = "ECG-" + df.format(stopTime!!) + ".png"
        try {
            val treeUri = Uri.parse(treeUriStr)
            val treeDocumentId = DocumentsContract.getTreeDocumentId(treeUri)
            val docTreeUri = DocumentsContract.buildDocumentUriUsingTree(
                treeUri,
                treeDocumentId
            )
            val resolver = this.contentResolver
            val pfd: ParcelFileDescriptor?
            val docUri = DocumentsContract.createDocument(
                resolver, docTreeUri,
                "image/png", fileName
            )
            pfd = contentResolver.openFileDescriptor(docUri!!, "w")
            FileOutputStream(pfd!!.fileDescriptor).use { strm ->
//                val logo = BitmapFactory.decodeResource(
//                    this.resources,
//                    R.drawable.polar_ecg
//                )
                val arrays: PlotArrays = getPlotArrays()
                val ecgValues: DoubleArray = arrays.ecg
                val peakValues: BooleanArray = arrays.peaks
//                ecgPlotter!!.getVisibleSeries().getyVals()
                val sampleCount = ecgValues.size
                val bm: Bitmap = createImage(
                    ECG_SAMPLE_RATE,
//                    logo,
                    patientName,
                    stopTime.toString(),
                    deviceId,
                    deviceFirmware,
                    deviceBatteryLevel,
                    note,
                    deviceStopHr,
                    calculatedStopHr,
                    java.lang.String.format(
                        Locale.US, "%d",
                        qrsPlotter!!.seriesDataPeaks.size()
                    ),
                    java.lang.String.format(
                        Locale.US,
                        "%.1f sec",
                        sampleCount / ECG_SAMPLE_RATE),
                    ecgValues,
                    peakValues
                )
                bm.compress(Bitmap.CompressFormat.PNG, 80, strm)
                strm.close()
                msg = "Wrote " + docUri.lastPathSegment
                Log.d(TAG, msg)
                AppUtils.infoMsg(this, msg)
            }
            pfd.close()
        } catch (ex: Exception) {
            msg = "Error saving plot"
            Log.e(TAG, msg)
            Log.e(TAG, Log.getStackTraceString(ex))
            AppUtils.excMsg(this, msg, ex)
        }
    }

    /**
     * Finishes the saveData after getting the note.
     *
     * @param note The note.
     */
    private fun saveData(note: String) {
        val prefs = getPreferences(MODE_PRIVATE)
        val treeUriStr = prefs.getString(PREF_TREE_URI, null)
        if (treeUriStr == null) {
            AppUtils.errMsg(this, "There is no data directory set")
            return
        }
        var msg: String
        val format = "yyyy-MM-dd_HH-mm"
        val df = SimpleDateFormat(format, Locale.US)
        val fileName = "ECG-" + df.format(stopTime!!) + ".csv"
        try {
            val treeUri = Uri.parse(treeUriStr)
            val treeDocumentId = DocumentsContract.getTreeDocumentId(treeUri)
            val docTreeUri = DocumentsContract.buildDocumentUriUsingTree(
                treeUri,
                treeDocumentId
            )
            val resolver = this.contentResolver
            val pfd: ParcelFileDescriptor?
            val docUri = DocumentsContract.createDocument(
                resolver, docTreeUri,
                "text/csv", fileName
            )
            pfd = contentResolver.openFileDescriptor(docUri!!, "w")
            FileWriter(pfd!!.fileDescriptor).use { writer ->
                PrintWriter(writer).use { out ->
                    // Write header
                    val arrays: PlotArrays = getPlotArrays()
                    val ecgValues: DoubleArray = arrays.ecg
                    val peakValues: BooleanArray = arrays.peaks
                    val timestamps: LongArray = arrays.timestamp
                    val peakCount: Int = qrsPlotter!!.seriesDataPeaks.size()
                    val sampleCount = ecgValues.size
                    val duration = java.lang.String.format(
                        Locale.US, "%.1f sec",
                        sampleCount / ECG_SAMPLE_RATE
                    )
                    out.write(
                        ("application=" + "KE.Net ECG Version: "
                                + AppUtils.getVersion(this)) + "\n"
                    )
                    out.write(
                        """
                        stoptime=${stopTime.toString()}
                        
                        """.trimIndent()
                    )
                    out.write("duration=$duration\n")
                    out.write("samplescount=$sampleCount\n")
                    out.write(
                        """
                        ${"samplingrate=$ECG_SAMPLE_RATE"}
                        
                        """.trimIndent()
                    )
                    out.write("stopdevicehr=$deviceStopHr\n")
                    out.write("stopcalculatedhr=$calculatedStopHr\n")
                    out.write("peakscount=$peakCount\n")
                    out.write("devicename=$deviceName\n")
                    out.write("deviceid=$deviceId\n")
                    out.write("battery=$deviceBatteryLevel\n")
                    out.write("firmware=$deviceFirmware\n")
                    out.write("note=$note\n")

                    // Write samples
                    for (i in 0 until sampleCount) {
                        out.write(
                            String.format(
                                Locale.US, "%.3f,%d,%d\n",
                                ecgValues[i],
                                if (peakValues[i]) 1 else 0,
                                timestamps[i]
                            )
                        )
                    }
                    out.flush()
                    msg = "Wrote " + docUri.lastPathSegment
                    Log.d(TAG, msg)
                    AppUtils.infoMsg(this, msg)
                }
            }
            pfd.close()
        } catch (ex: java.lang.Exception) {
            msg = "Error writing CSV file"
            Log.e(TAG, msg)
            Log.e(TAG, Log.getStackTraceString(ex))
            AppUtils.excMsg(this, msg, ex)
        }
    }

//    /**
//     * Finishes the saveData.
//     *
//     * @param saveType The saveType (either DEVICE_HR or QRS_HR).
//     */
//    private fun saveSessionData(saveType: SaveType) {
//        val prefs = getPreferences(MODE_PRIVATE)
//        val treeUriStr = prefs.getString(PREF_TREE_URI, null)
//        if (treeUriStr == null) {
//            AppUtils.errMsg(this, "There is no data directory set")
//            return
//        }
//        var msg: String
//        val format = "yyyy-MM-dd_HH-mm"
//        val df = SimpleDateFormat(format, Locale.US)
//        val fileName: String
//        val dataList: List<HrPlotter.HrRrSessionData>
//        try {
////            if (saveType == SaveType.DEVICE_HR) {
////                fileName = "PolarECG-DeviceHR-" + df.format(stopTime) + ".csv"
////                dataList = hrPlotter!!.hrRrList1
////            } else if (saveType == SaveType.QRS_HR) {
////                fileName = "PolarECG-QRSHR-" + df.format(stopTime) + ".csv"
////                dataList = hrPlotter!!.hrRrList2
////            } else {
////                AppUtils.errMsg(
////                    this,
////                    "Invalid saveType (" + saveType + "0 in " +
////                            "doSaveSessionData"
////                )
////                return
////            }
//            when (saveType) {
//                SaveType.DEVICE_HR -> {
//                    fileName = "PolarECG-DeviceHR-" + df.format(stopTime!!) + ".csv"
//                    dataList = hrPlotter!!.hrRrList1
//                }
//                SaveType.QRS_HR -> {
//                    fileName = "PolarECG-QRSHR-" + df.format(stopTime!!) + ".csv"
//                    dataList = hrPlotter!!.hrRrList2
//                }
//                else -> {
//                    AppUtils.errMsg(
//                        this,
//                        "Invalid saveType (" + saveType + "0 in " +
//                                "doSaveSessionData"
//                    )
//                    return
//                }
//            }
//            val treeUri = Uri.parse(treeUriStr)
//            val treeDocumentId = DocumentsContract.getTreeDocumentId(treeUri)
//            val docTreeUri = DocumentsContract.buildDocumentUriUsingTree(
//                treeUri,
//                treeDocumentId
//            )
//            val resolver = this.contentResolver
//            val pfd: ParcelFileDescriptor?
//            val docUri = DocumentsContract.createDocument(
//                resolver, docTreeUri,
//                "text/csv", fileName
//            )
//            pfd = contentResolver.openFileDescriptor((docUri)!!, "w")
//            FileWriter(pfd!!.fileDescriptor).use { writer ->
//                PrintWriter((writer)).use { out ->
//                    // Write header (None)
//                    // Write samples
//                    for (data: HrPlotter.HrRrSessionData in dataList) {
//                        out.write(data.csvString + "\n")
//                    }
//                    out.flush()
//                    msg = "Wrote " + docUri.lastPathSegment
//                    AppUtils.infoMsg(this, msg)
//                    Log.d(TAG, "    Wrote " + dataList.size + " items")
//                }
//            }
//            pfd.close()
//        } catch (ex: java.lang.Exception) {
//            msg = "Error writing $saveType CSV file"
//            Log.e(TAG, msg)
//            Log.e(TAG, Log.getStackTraceString(ex))
//            AppUtils.excMsg(this, msg, ex)
//        }
//    }

    /**
     * Brings up a system file chooser to get the data directory
     */
    private fun chooseDataDirectory() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        intent.addFlags(
            Intent.FLAG_GRANT_READ_URI_PERMISSION and
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        )
        openDocumentTreeLauncher.launch(intent)
    }

    /**
     * Gets ECG and Peaks and converts them into doubles. This relies on the
     * ecg arrays in EcgPlotter and QRSPlotter being the same and also the
     * respective DataIndex's. The ones in QRSPlotter are used. It also has
     * the peak values in form of binary value of `0` or `1`,
     * and also has timestamp taken from Polar device in `Long` type.
     *
     * @return PlotArrays with the ECG values, binary values that shows
     * whether it is a peak or not, and the timestamp.
     */
    private fun getPlotArrays(): PlotArrays {
        val arrays: PlotArrays
        // Remove any out-of-range values peak values
        qrsPlotter!!.removeOutOfRangePlotPeakValues()
        val ecgValues: LinkedList<Number> = qrsPlotter!!.seriesDataEcg.getyVals()
        val peakValues: LinkedList<Number> = qrsPlotter!!.seriesDataPeaks.getyVals()
        val peakXValues: LinkedList<Number> = qrsPlotter!!.seriesDataPeaks.getxVals()
        val timestampValues: LinkedList<Number> = qrsPlotter!!.seriesTimestamp.getyVals()
        val ecgLength = ecgValues.size
        val peaksLength = peakValues.size
        val ecg = DoubleArray(ecgLength)
        val peaks = BooleanArray(ecgLength)
        val timestamp = LongArray(ecgLength)
        for ((i, value) in ecgValues.withIndex()) {
            ecg[i] = value.toDouble()
            peaks[i] = false
            timestamp[i] = timestampValues[i].toLong()
        }
//        // The peak values correspond to an index related to when they came in.
//        // This is different from the index in the arrays, which goes from 0
//        // to no more than N_TOTAL_VISIBLE_POINTS.
//        var offset = 0
//        if (qrsPlotter!!.dataIndex > N_TOTAL_VISIBLE_POINTS) {
//            offset = (-(qrsPlotter!!.dataIndex - N_TOTAL_VISIBLE_POINTS)).toInt()
//        }

        var index: Int
        for (j in 0 until peaksLength) {
            index = peakXValues[j].toInt()
//            + offset
//            Log.d(TAG, String.format("j=%d indx=%d xval=%d",
//            j, indx, peakxvals.get(j).intValue()));
            peaks[index] = true
        }
        arrays = PlotArrays(ecg
            , peaks
            , timestamp)
        return arrays
    }

    /**
     * Toggles streaming for ECG.
     *
     * Turns streaming on when not running.
     *
     * Turns streaming off when it is currently running.
     */
    private fun toggleEcgStream() {
        Log.d(
            TAG, this.javaClass.simpleName + " streamECG:"
                    + " mEcgDisposable=" + ecgDisposable
                    + " mConnected=" + isConnected
        )
        if (!isConnected) {
            AppUtils.errMsg(this, "streamECG: Device is not connected yet")
            return
        }
        logEpochInfo("UTC")
        if (ecgDisposable == null) {
            // Set the local time to get correct timestamps. H10 apparently
            // resets its time to 01:01:2019 00:00:00 when connected to strap
            val timeZone = TimeZone.getTimeZone("UTC")
            val calNow = Calendar.getInstance(timeZone)
            Log.d(TAG, "setLocalTime to " + calNow.time)
            polarApi!!.setLocalTime(deviceId, calNow)
            ecgDisposable = polarApi!!.setLocalTime(deviceId, calNow)
                .andThen(
                    polarApi!!.requestStreamSettings(
                        deviceId,
                        DeviceStreamingFeature.ECG
                    )
                )
                .toFlowable()
                .flatMap { sensorSetting: PolarSensorSetting ->
                    polarApi!!.startEcgStreaming(
                        deviceId,
                        sensorSetting.maxSettings()
                    )
                }.observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    { polarEcgData: PolarEcgData ->
//                                        logTimestampInfo(polarEcgData);
                        if (qrsDetector == null) {
                            qrsDetector = QrsDetector(this@MainActivity)
                        }
                        //                                        logEcgDataInfo(polarEcgData);
                        qrsDetector!!.process(polarEcgData)
                        // Update the elapsed time
                        val elapsed: Double = ecgPlotter!!.getDataIndex() / 130.0
                        textEcgTime.text = getString(R.string.elapsed_time, elapsed)
                    },
                    { throwable: Throwable ->
                        Log.e(
                            TAG, "ECG Error: "
                                    + throwable.localizedMessage,
                            throwable
                        )
                        AppUtils.excMsg(
                            this@MainActivity, "ECG " +
                                    "Error: ",
                            throwable
                        )
                        ecgDisposable = null
                    },
                    {
                        Log.d(
                            TAG,
                            "ECG streaming complete"
                        )
                    }
                )
        } else {
            // NOTE stops streaming if it is "running"
            ecgDisposable?.dispose()
            ecgDisposable = null
            if (qrsDetector != null) qrsDetector = null
        }
    }

    private fun onMessageArrived(messagePath: String) {
        wearMessage?.let {
            when (messagePath) {
                MessagePath.COMMAND -> {
                    if (it.code == ActivityCode.STOP_ACTIVITY) { // reset this module's state
                        toggleState(0)
                    }
                }
                MessagePath.REQUEST -> {
                    TODO("Not yet implemented")
                }
                MessagePath.INFO -> {
                    when (it.code) {
                        ActivityCode.START_ACTIVITY -> { // get Wear's current state
                            toggleState(1)
                        }
                        ActivityCode.DO_NOTHING -> {
                            toggleState(0)
                        }
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
//            Toast.makeText(applicationContext, "Message sent!", Toast.LENGTH_LONG).show()
        }
    }

    private fun setMessageCode(forceCode: Int = 99) {
        if (forceCode != 99) {
            message.code = forceCode
        }
        else if (appState == 0) {
            message.code = ActivityCode.START_ACTIVITY
        }
        else if (appState == 1) {
            message.code = ActivityCode.STOP_ACTIVITY
        }
    }

    private fun toggleState(forceCode: Int = 99) {
        if (forceCode != 99) {
            appState = forceCode
        }
        else if (appState == 0) {
            appState = 1
        }
        else if (appState == 1) {
            appState = 0
        }
//        runOnUiThread {
//            if (appState == 0) {
//                buttonMain.text = getString(R.string.button_start)
//                textWearStatus.text = getString(R.string.status_stopped)
//            }
//            if (appState == 1) {
//                buttonMain.text = getString(R.string.button_stop)
//                textWearStatus.text = getString(R.string.status_running)
//            }
//            textTooltip.text = appState.toString()
//        }
        Log.d(TAG,"stateNum changed to: $appState")
    }

    private fun displayInfo() {
        val msg = StringBuilder()
        msg.append("Name: ").append(deviceName).append("\n")
        msg.append("Device Id: ").append(deviceId).append("\n")
        msg.append("Address: ").append(deviceAddress).append("\n")
        msg.append("Firmware: ").append(deviceFirmware).append("\n")
        msg.append("Battery Level: ").append(deviceBatteryLevel).append("\n")
        msg.append("API Connected: ").append(polarApi != null).append("\n")
        msg.append("Device Connected: ").append(isConnected).append("\n")
        msg.append("Playing: ").append(isPlaying).append("\n")
        msg.append("Receiving ECG: ").append(ecgDisposable != null)
            .append("\n")
        if (ecgPlotter != null && ecgPlotter!!.getVisibleSeries() != null && ecgPlotter!!.getVisibleSeries()
                .getyVals() != null
        ) {
            val elapsed: Double = ecgPlotter!!.getDataIndex() / ECG_SAMPLE_RATE
            msg.append("Elapsed Time: ")
                .append(getString(R.string.elapsed_time, elapsed))
                .append("\n")
            msg.append("Points plotted: ")
                .append(ecgPlotter!!.getVisibleSeries().getyVals().size)
                .append("\n")
        }
        var versionName: String? = "NA"
        try {
            versionName = if (Build.VERSION.SDK_INT >= 33) {
                packageManager.getPackageInfo(
                    packageName,
                    PackageManager.PackageInfoFlags.of(0)
                ).versionName
            } else {
                packageManager.getPackageInfo(
                    packageName,
                    0
                ).versionName
            }
        } catch (ex: java.lang.Exception) {
            // Do nothing
        }
        msg.append("ECG-App Version: ").append(versionName).append("\n")
        msg.append("Polar BLE API Version: ").append(versionInfo()).append("\n")
        msg.append(UriUtils.getRequestedPermissionsInfo(this))
        val prefs = getPreferences(MODE_PRIVATE)
        val treeUriStr = prefs.getString(PREF_TREE_URI, null)
        if (treeUriStr == null) {
            msg.append("Data Directory: Not set")
        } else {
            val treeUri = Uri.parse(treeUriStr)
            msg.append("Data Directory: ").append(treeUri.path)
        }
        AppUtils.infoMsg(this, msg.toString())
    }

    companion object {
        private const val TAG = "Mobile.MainActivity"
        private val shortDateFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
        // Currently the sampling rate for ECG is fixed at 130
        private const val MAX_DEVICES = 3
        const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS =
            mutableListOf(
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION
            ).apply {
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.R) {
                    add(Manifest.permission.BLUETOOTH)
                    add(Manifest.permission.BLUETOOTH_ADMIN)
                }
            }.apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    add(Manifest.permission.BLUETOOTH_SCAN)
                    add(Manifest.permission.BLUETOOTH_CONNECT)
                }
            }.apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    add(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                }
            }.toTypedArray()
    }

    private fun logEpochInfo(timeZoneString: String) {
        val sdf = SimpleDateFormat("dd:MM:yyyy HH:mm:ss", Locale.US)
        val sdf2 = SimpleDateFormat("EEE MMM dd HH:mm:ss zzz yyyy", Locale.US)
        /// Set the timezone
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        sdf2.timeZone = TimeZone.getTimeZone("UTC")
        try {
            val epoch = sdf.parse("01:01:2000 00:00:00")
            val epoch1 = sdf.parse("01:01:2019 00:00:00")
            if (epoch != null && epoch1 != null) {
                Log.d(
                    TAG, "epoch=" + sdf2.format(epoch)
                            + " epoch1=" + sdf2.format(epoch1)
                            + " " + epoch.time
                            + " " + epoch1.time
                            + " " + (epoch1.time - epoch.time)
                            + timeZoneString
                )
            } else {
                Log.d(TAG, "epoch=null and/or epoch2=null")
            }
        } catch (ex: java.lang.Exception) {
            Log.e(TAG, "Error parsing date", ex)
        }
    }

    private fun timestampInfo(ts: Long, name: String?): String? {
        val sdf = SimpleDateFormat("MMM dd yyyy HH:mm:ss zzz", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        val sdf1 = SimpleDateFormat("MMM dd yyyy HH:mm:ss zzz", Locale.US)
        val date = Date(ts)
        return String.format(
            Locale.US, "%15d %-8s %.2f years  %s %s",
            ts, name, msToYears(ts),
            sdf.format(date), sdf1.format(date)
        )
    }

    private fun msToYears(longVal: Long): Double {
        return longVal / (1000.0 * 60.0 * 60.0 * 24.0 * 365.0)
    }
}