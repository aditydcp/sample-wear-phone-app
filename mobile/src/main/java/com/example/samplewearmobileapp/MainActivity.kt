package com.example.samplewearmobileapp

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothAdapter.*
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
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
import com.example.samplewearmobileapp.databinding.ActivityMainBinding
import com.google.android.gms.common.api.GoogleApiClient
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.Wearable
import com.google.gson.Gson

class MainActivity : AppCompatActivity(), GoogleApiClient.ConnectionCallbacks {
    private lateinit var binding: ActivityMainBinding
    private lateinit var client: GoogleApiClient
    // TODO: Consider creating a shared object for Bluetooth manager
    private lateinit var bluetoothManager: BluetoothManager
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var connectedNode: List<Node>? = null
    private var message: Message = Message()
    private var wearMessage: Message? = null
    private var appState = 0
    private var bluetoothState = STATE_OFF

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

        // register bluetooth state broadcast receiver
        val filter = IntentFilter(ACTION_STATE_CHANGED)
        registerReceiver(bluetoothStateReceiver, filter)

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
            startActivity(intent)
        }
    }

    override fun onConnected(p0: Bundle?) {
        Wearable.NodeApi.getConnectedNodes(client).setResultCallback {
            connectedNode = it.nodes
            Log.d("Mobile","Node connected")

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

    override fun onConnectionSuspended(p0: Int) {
        connectedNode = null
        runOnUiThread {
            textWearStatus.text = getString(R.string.status_disconnected)
        }
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

    override fun onDestroy() {
        super.onDestroy()

        // unregister receiver
        unregisterReceiver(bluetoothStateReceiver)
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) ==
                PackageManager.PERMISSION_GRANTED
    }

    private fun setupBluetooth() {
        // setup bluetooth adapter
        bluetoothManager = getSystemService(BluetoothManager::class.java)
        bluetoothAdapter = bluetoothManager.adapter

        // check bluetooth availability
        if (bluetoothAdapter == null) {
            runOnUiThread {
                textHrmStatus.text = getString(R.string.status_no_support)
            }
        }

        enableBluetooth()
    }

    private fun enableBluetooth() {
        if (bluetoothAdapter?.isEnabled == false) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (ActivityCompat.checkSelfPermission(
                        this,
                        Manifest.permission.BLUETOOTH_CONNECT
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    ActivityCompat.requestPermissions(
                        this,
                        arrayOf(Manifest.permission.BLUETOOTH_CONNECT),
                        REQUEST_CODE_PERMISSIONS
                    )
                    return
                }
            }
            else {
                if (ActivityCompat.checkSelfPermission(
                        this,
                        Manifest.permission.BLUETOOTH
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    ActivityCompat.requestPermissions(
                        this,
                        arrayOf(Manifest.permission.BLUETOOTH),
                        REQUEST_CODE_PERMISSIONS
                    )
                    return
                }
            }
            startActivityForResult(enableBtIntent, REQUEST_CODE_ENABLE_BLUETOOTH)
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
        Log.d("Mobile","stateNum changed to: $appState")
    }

    companion object {
        private const val TAG = "Mobile.MainActivity"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private const val REQUEST_CODE_ENABLE_BLUETOOTH = 1
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