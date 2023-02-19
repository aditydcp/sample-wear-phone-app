package com.example.samplewearmobileapp

import android.bluetooth.BluetoothDevice
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.*
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.forEach
import com.example.samplewearmobileapp.BluetoothService.bluetoothLeService
import com.example.samplewearmobileapp.BluetoothService.makeGattUpdateIntentFilter
import com.example.samplewearmobileapp.BluetoothService.serviceConnection
import com.example.samplewearmobileapp.databinding.ActivityConnectHrmBinding

class ConnectHrmActivity : AppCompatActivity() {
    private lateinit var binding: ActivityConnectHrmBinding
    private lateinit var devicesAcquaintedInfo: ArrayList<BluetoothDevice>
    private lateinit var devicesNewInfo: ArrayList<BluetoothDevice>
    private lateinit var listAcquaintedDevicesAdapter: ArrayAdapter<String>
    private lateinit var listNewDevicesAdapter: ArrayAdapter<String>
//    private var bluetoothLeService : BluetoothLeService? = null
    private var targetDeviceAddress : String? = null
    private var isConnected = false

    private lateinit var textStatus: TextView
    private lateinit var spinnerStatus: ProgressBar
    private lateinit var buttonSearch: TextView
    private lateinit var textAcquaintedDevices: TextView
    private lateinit var listAcquaintedDevices: ListView
    private lateinit var textNewDevices: TextView
    private lateinit var listNewDevices: ListView

//    val serviceConnection: ServiceConnection = object : ServiceConnection {
//        override fun onServiceConnected(
//            name: ComponentName,
//            service: IBinder
//        ) {
//            bluetoothLeService = (service as BluetoothLeService.LocalBinder).getService()
//            Log.d(TAG, "BluetoothLeService online")
//            bluetoothLeService?.let { bluetooth ->
//                if (!bluetooth.initialize()) {
//                    Log.e(TAG, "Unable to initialize Bluetooth")
//                }
//            }
//        }
//
//        override fun onServiceDisconnected(name: ComponentName) {
//            bluetoothLeService = null
//            Log.d(TAG, "BluetoothLeService disconnected")
//        }
//    }

    private val gattUpdateReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                BluetoothLeService.ACTION_GATT_CONNECTED -> {
                    isConnected = true
                    Toast.makeText(applicationContext,
                        "BLE Device connected!",
                        Toast.LENGTH_LONG).show()
                    Log.d(TAG, "BLE Device connected!")
                    setResult(RESULT_CODE_CONNECTION_SUCCESS)
                    finish()
                }
                BluetoothLeService.ACTION_GATT_DISCONNECTED -> {
                    isConnected = false
                    Toast.makeText(applicationContext,
                        "BLE Device disconnected!",
                        Toast.LENGTH_LONG).show()
                    setResult(RESULT_CODE_CONNECTION_FAILED)
                    Log.d(TAG, "BLE Device disconnected!")
                    runOnUiThread {
                        textStatus.text = getString(R.string.connect_status_connection_failed)
                        spinnerStatus.visibility = View.INVISIBLE
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityConnectHrmBinding.inflate(layoutInflater)
        Log.i(TAG, "Lifecycle: onCreate()")
        setResult(RESULT_CODE_CONNECTION_FAILED)

        textStatus = binding.connectStatusText
        spinnerStatus = binding.connectSpinner
        buttonSearch = binding.connectButtonSearch
        textAcquaintedDevices = binding.devicesAcquaintedTitle
        listAcquaintedDevices = binding.devicesAcquaintedList
        textNewDevices = binding.devicesFoundNewTitle
        listNewDevices = binding.devicesFoundNewList

        initDisplay()

        // setup search again button
        buttonSearch.setOnClickListener {
            startSearch()
        }

        // bind this activity to BluetoothLeService
        val gattServiceIntent = Intent(this, BluetoothLeService::class.java)
        bindService(gattServiceIntent,
            serviceConnection, Context.BIND_AUTO_CREATE)
            .also { Log.d(TAG, "bindService returns $it") }

        // register broadcast receiver
        registerReceiver(gattUpdateReceiver, makeGattUpdateIntentFilter())

        // start bluetooth device finder
        startSearch()
    }

    private fun initDisplay() {
        Log.i(TAG, "initDisplay(): Initialize display")
        setContentView(binding.root)

        textStatus.text = getString(R.string.connect_status_searching)

        devicesAcquaintedInfo = ArrayList()
        devicesNewInfo = ArrayList()

        // Setup acquainted devices list
        textAcquaintedDevices.visibility = View.GONE
        listAcquaintedDevices.visibility = View.GONE
        listAcquaintedDevicesAdapter = ArrayAdapter(this,
            android.R.layout.simple_list_item_1,
            android.R.id.text1
        )
        listAcquaintedDevices.adapter = listAcquaintedDevicesAdapter
        listAcquaintedDevices.onItemClickListener =
            AdapterView.OnItemClickListener { _, _, position, _ ->
                // provide the position/id of the selected device
                attemptConnection(devicesAcquaintedInfo[position])
            }
        getAcquaintedDevices()

        // Setup newly found devices list
        listNewDevicesAdapter = ArrayAdapter(this,
            android.R.layout.simple_list_item_1,
            android.R.id.text1
        )
        listNewDevices.adapter = listNewDevicesAdapter
        listNewDevices.onItemClickListener =
            AdapterView.OnItemClickListener { _, _, position, _ ->
                // provide the position/id of the selected device
                attemptConnection(devicesNewInfo[position])
            }
    }

    private fun startSearch() {
        BluetoothService.leScanCallback = object : ScanCallback(){
            override fun onScanResult(callbackType: Int, result: ScanResult?) {
                super.onScanResult(callbackType, result)
                BluetoothService.checkBluetoothPermission(
                    applicationContext,
                    this@ConnectHrmActivity
                )
                if (result != null) {
                    // check if the scanned item is already present or not
                    var isPresent = false
                    devicesAcquaintedInfo.forEach {
                        if ((result.device.name == it.name) &&
                            (result.device.type == it.type) &&
                            (result.device.address == it.address))
                            isPresent = true
                    }
                    if (!isPresent) {
                        devicesNewInfo.forEach {
                            if ((result.device.name == it.name) &&
                                (result.device.type == it.type) &&
                                (result.device.address == it.address))
                                isPresent = true
                        }
                    }
                    if(!isPresent) {
                        devicesNewInfo.add(result.device)
                        listNewDevicesAdapter.add(result.device.name)
                        listNewDevicesAdapter.notifyDataSetChanged()
                    }
                }
            }
        }
        BluetoothService.toggleLeDeviceSearch(this, this) { isScanning ->
            runOnUiThread {
                if (isScanning) {
                    spinnerStatus.visibility = View.VISIBLE
                    buttonSearch.visibility = View.GONE
                    textStatus.text = getString(R.string.connect_status_searching)
                } else {
                    spinnerStatus.visibility = View.INVISIBLE
                    buttonSearch.visibility = View.VISIBLE
                    textStatus.text = getString(R.string.connect_status_stopped)
                }
            }
        }
    }

    private fun attemptConnection(deviceInfo: BluetoothDevice) {
        targetDeviceAddress = deviceInfo.address

        BluetoothService.checkBluetoothPermission(this, this)
        Log.d(TAG,"Device name: ${deviceInfo.name}\n" +
                "Device address: ${deviceInfo.address}\n" +
                "Device type: ${deviceInfo.type}\n" +
                "Device class: ${deviceInfo.bluetoothClass.deviceClass}\n" +
                "Device major class: ${deviceInfo.bluetoothClass.majorDeviceClass}")

        // perform connection
        Log.i(TAG, "Performing connection...")
        runOnUiThread {
            textStatus.text = getString(R.string.connect_status_connecting)
            spinnerStatus.visibility = View.VISIBLE
        }
        BluetoothService.stopLeDeviceSearch(this, this)
        bluetoothLeService?.connect(targetDeviceAddress!!, this@ConnectHrmActivity)
            .also {
                if (!it!!) {
                    setResult(RESULT_CODE_CONNECTION_FAILED)
                    runOnUiThread {
                        textStatus.text = getString(R.string.connect_status_connection_failed)
                        spinnerStatus.visibility = View.INVISIBLE
                    }
            } }
    }

    private fun getAcquaintedDevices() {
        BluetoothService.checkBluetoothPermission(this, this)
        val acquaintedDevices = BluetoothService.adapter?.bondedDevices

        // show the container if there is acquainted devices
        if(!acquaintedDevices.isNullOrEmpty()) {
            runOnUiThread {
                textAcquaintedDevices.visibility = View.VISIBLE
                listAcquaintedDevices.visibility = View.VISIBLE
            }
        }

        // store each devices info
        acquaintedDevices?.forEach { device ->
            // store only BLE devices
            if(device.type == BluetoothDevice.DEVICE_TYPE_LE) {
                devicesAcquaintedInfo.add(device)
                listAcquaintedDevicesAdapter.add(device.name)
            }
        }
        listAcquaintedDevicesAdapter.notifyDataSetChanged()
    }

    override fun finish() {
        Log.i(TAG,"Activity finished on code: " +
                if (isConnected) {
                    "$RESULT_CODE_CONNECTION_SUCCESS"
                } else {
                    "$RESULT_CODE_CONNECTION_FAILED"
                })
        super.finish()
    }

    override fun onRestart() {
        super.onRestart()
        Log.i(TAG, "Lifecycle: onRestart()")
        try {
            registerReceiver(gattUpdateReceiver, makeGattUpdateIntentFilter())
        } catch (e: Exception) {
            Log.d(TAG,"Gatt Update Receiver is already registered")
        }
        if (bluetoothLeService != null) {
            val result = bluetoothLeService!!
                .connect(targetDeviceAddress!!, this)
            Log.d(TAG, "Connect request result=$result")
        }
    }

//    override fun onResume() {
//        super.onResume()
//        try {
//            registerReceiver(gattUpdateReceiver, makeGattUpdateIntentFilter())
//        } catch (e: Exception) {
//            Log.d(TAG,"Gatt Update Receiver is already registered")
//        }
//        if (bluetoothLeService != null) {
//            val result = bluetoothLeService!!
//                .connect(targetDeviceAddress!!, this)
//            Log.d(TAG, "Connect request result=$result")
//        }
//    }

    override fun onStop() {
        super.onStop()
        Log.i(TAG, "Lifecycle: onStop()")
        // stop search
        BluetoothService.stopLeDeviceSearch(this, this)
        // unregister receiver
        try {
            unregisterReceiver(gattUpdateReceiver)
        } catch (e: Exception) {
            Log.d(TAG,"Gatt Update Receiver is already unregistered")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "Lifecycle: onDestroy()")
        // stop search
        BluetoothService.stopLeDeviceSearch(this, this)
        // unregister receiver
        try {
            unregisterReceiver(gattUpdateReceiver)
        } catch (e: Exception) {
            Log.d(TAG,"Gatt Update Receiver is already unregistered")
        }
    }

    companion object {
        private const val TAG = "Mobile.ConnectHrmActivity"
        const val RESULT_CODE_CONNECTION_SUCCESS = 1
        const val RESULT_CODE_CONNECTION_FAILED = 0
    }
}