package com.example.samplewearmobileapp

import android.Manifest
import android.bluetooth.BluetoothAdapter.*
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.nfc.NfcAdapter.EXTRA_DATA
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.samplewearmobileapp.BluetoothLeService.Companion.EXTRA_HR
import com.example.samplewearmobileapp.BluetoothLeService.Companion.EXTRA_RR
import com.example.samplewearmobileapp.BluetoothLeService.Companion.UUID_HEART_RATE_MEASUREMENT
import com.example.samplewearmobileapp.BluetoothLeService.Companion.UUID_HEART_RATE_SERVICE
import com.example.samplewearmobileapp.BluetoothService.REQUEST_CODE_ENABLE_BLUETOOTH
import com.example.samplewearmobileapp.BluetoothService.bluetoothLeService
import com.example.samplewearmobileapp.BluetoothService.makeGattUpdateIntentFilter
import com.example.samplewearmobileapp.ConnectHrmActivity.Companion.RESULT_CODE_CONNECTION_FAILED
import com.example.samplewearmobileapp.ConnectHrmActivity.Companion.RESULT_CODE_CONNECTION_SUCCESS
import com.example.samplewearmobileapp.databinding.ActivityMainBinding
import com.google.android.gms.common.api.GoogleApiClient
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.Wearable
import com.google.gson.Gson
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class MainActivity : AppCompatActivity(), GoogleApiClient.ConnectionCallbacks {
    private lateinit var binding: ActivityMainBinding
    private lateinit var client: GoogleApiClient
    private var connectedNode: List<Node>? = null
    private var message: Message = Message()
    private var wearMessage: Message? = null
    private var appState = 0
    private var bluetoothState = STATE_OFF
    private var isBleConnected = false

    private lateinit var textWearStatus: TextView
    private lateinit var textWearHr: TextView
    private lateinit var textWearIbi: TextView
    private lateinit var textWearTimestamp: TextView
    private lateinit var textHrmStatus: TextView
    private lateinit var textHrmHr: TextView
    private lateinit var textHrmIbi: TextView
    private lateinit var textHrmTimestamp: TextView
    private lateinit var textTooltip: TextView
    private lateinit var buttonMain: Button
    private lateinit var buttonView: TextView
    private lateinit var buttonConnectHrm: TextView
    private lateinit var containerWear: LinearLayout
    private lateinit var containerHrm: LinearLayout

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

    private val gattUpdateReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                BluetoothLeService.ACTION_GATT_CONNECTED -> {
                    isBleConnected = true
                    Toast.makeText(applicationContext,
                        "BLE Device connected!",
                        Toast.LENGTH_LONG).show()
                    Log.d(TAG, "BLE Device connected!")
                    runOnUiThread {
                        textHrmStatus.text = getString(R.string.status_connected)
                    }
                }
                BluetoothLeService.ACTION_GATT_DISCONNECTED -> {
                    isBleConnected = false
                    Toast.makeText(applicationContext,
                        "BLE Device disconnected!",
                        Toast.LENGTH_LONG).show()
                    Log.d(TAG, "BLE Device disconnected!")
                    runOnUiThread {
                        textHrmStatus.text = getString(R.string.status_disconnected)

                        buttonConnectHrm.text = getString(R.string.button_connect_hrm)
                    }
                    buttonConnectHrm.setOnClickListener {
                        val intent = Intent(this@MainActivity, ConnectHrmActivity::class.java)
                        startActivityForResult(intent, REQUEST_CODE_CONNECT_HRM)
                    }
                }
                BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED -> {
                    Log.d(TAG,"Discovered Service Broadcast is received")

//                    // Show all the supported services and characteristics on the user interface.
//                    displayGattServices(bluetoothLeService?.getSupportedGattServices())
                }
                BluetoothService.ACTION_DATA_AVAILABLE -> {
                    Log.d(TAG, "Available data received!")
                    runOnUiThread {
                        textHrmTimestamp.text =
                            LocalDateTime.now()
                                .format(DateTimeFormatter.ISO_DATE_TIME)
                                .toString()
                    }
                    val hr = intent.getStringExtra(EXTRA_HR)
                    if (hr != null) {
                        runOnUiThread {
                            textHrmHr.text = hr
                        }
                    } else {
                        runOnUiThread {
                            textHrmHr.text = getString(R.string.default_value)
                        }
                    }
                    val rr = intent.getStringExtra(EXTRA_RR)
                    if (rr != null) {
                        runOnUiThread {
                            textHrmIbi.text = rr
                        }
                    } else {
                        runOnUiThread {
                            textHrmIbi.text = getString(R.string.default_value)
                        }
                    }
                    val data = intent.getStringExtra(EXTRA_DATA)
                    if (data != null)
                        Log.i(TAG,"Data arrived: $data")
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // request for permissions
        if (allPermissionsGranted()) {
            setupBluetooth()
        } else {
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS
            )
        }

        message.sender = Entity.PHONE_APP

        // set UI vars
        textWearStatus = binding.wearStatus
        textWearHr = binding.wearHr
        textWearIbi = binding.wearIbi
        textWearTimestamp = binding.wearTime
        textHrmStatus = binding.hrmStatus
        textHrmHr = binding.hrmHr
        textHrmIbi = binding.hrmIbi
        textHrmTimestamp = binding.hrmTime
        textTooltip = binding.buttonTooltip
        buttonMain = binding.buttonMain
        buttonView = binding.buttonView
        buttonConnectHrm = binding.hrmConnect
        containerWear = binding.wearContainer
        containerHrm = binding.hrmContainer

        runOnUiThread {
            textTooltip.text = getString(R.string.click_me_text)
            buttonMain.text = getString(R.string.button_start)
            buttonView.visibility = View.GONE
        }

        // bind this activity to BluetoothLeService
        val gattServiceIntent = Intent(this, BluetoothLeService::class.java)
        bindService(gattServiceIntent,
            BluetoothService.serviceConnection, Context.BIND_AUTO_CREATE)
            .also { Log.d(TAG, "bindService returns $it") }

        // register bluetooth state broadcast receiver
        registerReceiver(bluetoothStateReceiver, BluetoothService.BLUETOOTH_STATE_FILTER)
        registerReceiver(gattUpdateReceiver, makeGattUpdateIntentFilter())

        // build Google API Client with access to Wearable API
        client = GoogleApiClient.Builder(this)
            .addApi(Wearable.API)
            .addConnectionCallbacks(this)
            .build()
        client.connect()

        // set click listener
        buttonMain.setOnClickListener {
            message.content = getString(R.string.message_on_button_click)
            setMessageCode()
            toggleState()
            runOnUiThread {
                textTooltip.text = getString(R.string.clicked_text)
            }
            sendMessage(message, MessagePath.COMMAND)
        }

        containerWear.setOnClickListener {
            runOnUiThread {
                containerHrm.visibility = View.GONE
                buttonView.visibility = View.VISIBLE
            }
        }

        containerHrm.setOnClickListener {
            runOnUiThread {
                containerWear.visibility = View.GONE
                buttonView.visibility = View.VISIBLE
            }
        }

        buttonView.setOnClickListener {
            runOnUiThread {
                containerWear.visibility = View.VISIBLE
                containerHrm.visibility = View.VISIBLE
                buttonView.visibility = View.GONE
            }
        }

        buttonConnectHrm.setOnClickListener {
            val intent = Intent(this@MainActivity, ConnectHrmActivity::class.java)
            startActivityForResult(intent, REQUEST_CODE_CONNECT_HRM)
        }
    }

    /**
     * Callback function invoked when Node API successfully connects with Wear device.
     */
    override fun onConnected(p0: Bundle?) {
        Wearable.NodeApi.getConnectedNodes(client).setResultCallback {
            connectedNode = it.nodes
            Log.d(TAG,"Node connected")

            runOnUiThread {
                textWearStatus.text = getString(R.string.status_connected)
            }

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
            val receivedData = Gson().fromJson(String(data[0].dataItem.data), HeartData::class.java)
            runOnUiThread {
                textWearHr.text = receivedData.hr.toString()
                textWearIbi.text = receivedData.ibi.toString()
                textWearTimestamp.text = receivedData.timestamp
            }
        }
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) ==
                PackageManager.PERMISSION_GRANTED
    }

    /**
     * Setup Bluetooth for the first time.
     */
    private fun setupBluetooth() {
        // setup bluetooth adapter
        BluetoothService.manager = getSystemService(BluetoothManager::class.java)
        BluetoothService.adapter = BluetoothService.manager.adapter

        // check bluetooth availability
        if (BluetoothService.adapter == null) {
            runOnUiThread {
                textHrmStatus.text = getString(R.string.status_no_support)
            }
        }

        BluetoothService.enableBluetooth(this, this)
    }

//    /**
//     * Display all available services on a GATT server.
//     */
//    private fun displayGattServices(gattServices: List<BluetoothGattService?>?) {
//        Log.d(TAG,"Displaying Gatt Services...")
//
//        if (gattServices == null) return
//        var uuid: String?
//        val unknownServiceString: String = "Unknown service"
//        val unknownCharaString: String = "Unknown characteristic"
//        val gattServiceData: MutableList<HashMap<String, String>> = mutableListOf()
//        val gattCharacteristicData: MutableList<ArrayList<HashMap<String, String>>> =
//            mutableListOf()
//        val mGattCharacteristics: MutableList<BluetoothGattCharacteristic> = mutableListOf()
//
//        // Loops through available GATT Services.
//        gattServices.forEach { gattService ->
//            val currentServiceData = HashMap<String, String>()
//            var uuid: String? = null
//            if (gattService != null) {
//                uuid = gattService.uuid.toString()
//            }
//            currentServiceData[LIST_NAME] = SampleGattAttributes.lookup(uuid, unknownServiceString)
//            currentServiceData[LIST_UUID] = uuid.toString()
//            gattServiceData += currentServiceData
//
//            val gattCharacteristicGroupData: ArrayList<HashMap<String, String>> = arrayListOf()
//            val characteristics: MutableList<BluetoothGattCharacteristic> = mutableListOf()
//
//            // Loops through available Characteristics.
//            gattService?.characteristics?.forEach { gattCharacteristic ->
//                characteristics += gattCharacteristic
//                val currentCharaData: HashMap<String, String> = hashMapOf()
//                uuid = gattCharacteristic.uuid.toString()
//                currentCharaData[LIST_NAME] = SampleGattAttributes.lookup(uuid, unknownCharaString)
//                currentCharaData[LIST_UUID] = uuid!!
//                gattCharacteristicGroupData += currentCharaData
//            }
//            mGattCharacteristics += characteristics
//            gattCharacteristicData += gattCharacteristicGroupData
//        }
//        Log.i(TAG, "Services: $gattServiceData")
//        Log.i(TAG, "Characteristics: $gattCharacteristicData")
//        Log.i(TAG, "mCharacteristics: $mGattCharacteristics")
//    }

    /**
     * Responding incoming message from Message API according to the path.
     */
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

    /**
     * Send a Message to Wear device on a path.
     */
    private fun sendMessage(message: Message, path: String) {
        val gson = Gson()
        connectedNode?.forEach { node ->
            val bytes = gson.toJson(message).toByteArray()
            Wearable.MessageApi.sendMessage(client, node.id, path, bytes)
//            Toast.makeText(applicationContext, "Message sent!", Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Set the code for MainActivity.Message object based on appState value.
     *
     * Optional parameter forceCode can be used to set custom code
     * other than 0 and 1.
     */
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

    /**
     * Toggle MainActivity.appState value and updates UI accordingly.
     *
     * Optional parameter forceCode can be used to set custom code
     * other than 0 and 1.
     */
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
        runOnUiThread {
            if (appState == 0) {
                buttonMain.text = getString(R.string.button_start)
                textWearStatus.text = getString(R.string.status_stopped)
            }
            if (appState == 1) {
                buttonMain.text = getString(R.string.button_stop)
                textWearStatus.text = getString(R.string.status_running)
            }
            textTooltip.text = appState.toString()
        }
        Log.d(TAG,"stateNum changed to: $appState")
    }

    /**
     * Callback function invoked when Node API connection with Wear device is suspended.
     */
    override fun onConnectionSuspended(p0: Int) {
        connectedNode = null
        runOnUiThread {
            textWearStatus.text = getString(R.string.status_disconnected)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when (requestCode) {
            REQUEST_CODE_ENABLE_BLUETOOTH -> {
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
            REQUEST_CODE_CONNECT_HRM -> {
                Log.i(TAG, "Mobile.ConnectHrmActivity results received.\n" +
                        "Result code: $resultCode")
                when (resultCode) {
                    RESULT_CODE_CONNECTION_SUCCESS -> {
                        runOnUiThread {
                            textHrmStatus.text = getString(R.string.status_connected)

                            buttonConnectHrm.text = getString(R.string.button_start_receiving)
                        }

                        // re-register receivers
                        try {
                            registerReceiver(bluetoothStateReceiver,
                                BluetoothService.BLUETOOTH_STATE_FILTER)
                        } catch (e: Exception) {
                            Log.d(TAG, "Bluetooth State Receiver is already registered")
                        }
                        try {
                            registerReceiver(gattUpdateReceiver,
                                makeGattUpdateIntentFilter())
                        } catch (e: Exception) {
                            Log.d(TAG, "Gatt Update Receiver is already registered")
                        }

                        buttonConnectHrm.setOnClickListener {
                            Log.d(TAG, "Start receiving Button clicked!")
                            val characteristic = bluetoothLeService?.getCharacteristicFromService(
                                UUID_HEART_RATE_SERVICE, UUID_HEART_RATE_MEASUREMENT)
                            if (characteristic != null) {
                                bluetoothLeService?.setCharacteristicNotification(
                                    characteristic, true, this
                                )
                                bluetoothLeService?.readCharacteristic(
                                    characteristic, this)
                            }

                            runOnUiThread {
                                textHrmStatus.text = getString(R.string.status_running)
                            }
//                            bluetoothLeService?.discoverGattServices(this)
                        }

                        if (bluetoothLeService?.discoverGattServices(this)
                            == true) {
                            Log.d(TAG, "Discovering Gatt services...")
                        } else {
                            Log.d(TAG, "Gatt service discover encountered a problem.")
                        }
                    }
                    RESULT_CODE_CONNECTION_FAILED -> {
                        runOnUiThread {
                            textHrmStatus.text = getString(R.string.status_disconnected)
                        }
                    }
                }
            }
            else -> {
                super.onActivityResult(requestCode, resultCode, data)
            }
        }
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

    override fun onRestart() {
        super.onRestart()
        Log.i(TAG,"Lifecycle: onRestart()")
        // re-register receivers
        try {
            registerReceiver(bluetoothStateReceiver, BluetoothService.BLUETOOTH_STATE_FILTER)
        } catch (e: Exception) {
            Log.d(TAG, "Bluetooth State Receiver is already registered")
        }
        try {
            registerReceiver(gattUpdateReceiver, makeGattUpdateIntentFilter())
        } catch (e: Exception) {
            Log.d(TAG, "Gatt Update Receiver is already registered")
        }
    }

    override fun onStop() {
        super.onStop()
        Log.i(TAG, "Lifecycle: onStop()")
        // unregister receivers
        try {
            unregisterReceiver(bluetoothStateReceiver)
        } catch (e: Exception) {
            Log.d(TAG,"Bluetooth State Receiver is already unregistered")
        }
        try {
            unregisterReceiver(gattUpdateReceiver)
        } catch (e: Exception) {
            Log.d(TAG,"Gatt Update Receiver is already unregistered")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG,"Lifecycle: onDestroy()")
        // unregister receivers
        try {
            unregisterReceiver(bluetoothStateReceiver)
        } catch (e: Exception) {
            Log.d(TAG,"Bluetooth State Receiver is already unregistered")
        }
        try {
            unregisterReceiver(gattUpdateReceiver)
        } catch (e: Exception) {
            Log.d(TAG,"Gatt Update Receiver is already unregistered")
        }
    }

    companion object {
        private const val TAG = "Mobile.MainActivity"
        private const val LIST_NAME = "LIST_NAME"
        private const val LIST_UUID = "LIST_UUID"
        const val REQUEST_CODE_CONNECT_HRM = 20
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
                    add(Manifest.permission.ACCESS_BACKGROUND_LOCATION,)
                }
            }.toTypedArray()
    }
}