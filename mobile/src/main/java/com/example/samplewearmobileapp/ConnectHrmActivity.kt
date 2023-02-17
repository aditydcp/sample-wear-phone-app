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
import com.example.samplewearmobileapp.databinding.ActivityConnectHrmBinding

class ConnectHrmActivity : AppCompatActivity() {
    private lateinit var binding: ActivityConnectHrmBinding
    private lateinit var devicesAcquaintedInfo: ArrayList<BluetoothDevice>
    private lateinit var devicesNewInfo: ArrayList<BluetoothDevice>
    private lateinit var listAcquaintedDevicesAdapter: ArrayAdapter<String>
    private lateinit var listNewDevicesAdapter: ArrayAdapter<String>
    private var bluetoothLeService : BluetoothLeService? = null
    private var targetDeviceAddress : String? = null
    private var isConnected = false

    private lateinit var textStatus: TextView
    private lateinit var spinnerStatus: ProgressBar
    private lateinit var buttonSearch: TextView
    private lateinit var textAcquaintedDevices: TextView
    private lateinit var listAcquaintedDevices: ListView
    private lateinit var textNewDevices: TextView
    private lateinit var listNewDevices: ListView

    private val serviceConnection: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(
            name: ComponentName,
            service: IBinder
        ) {
            bluetoothLeService = (service as BluetoothLeService.LocalBinder).getService()
            bluetoothLeService?.let { bluetooth ->
                if (!bluetooth.initialize()) {
                    Log.e(TAG, "Unable to initialize Bluetooth")
//                    finish()
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName) {
            bluetoothLeService = null
        }
    }

    private val gattUpdateReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                BluetoothLeService.ACTION_GATT_CONNECTED -> {
                    isConnected = true
                    Toast.makeText(applicationContext,
                        "BLE Device connected!",
                        Toast.LENGTH_LONG).show()
                }
                BluetoothLeService.ACTION_GATT_DISCONNECTED -> {
                    isConnected = false
                    Toast.makeText(applicationContext,
                        "BLE Device disconnected!",
                        Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityConnectHrmBinding.inflate(layoutInflater)
        Log.i(TAG, "Lifecycle: onCreate()")

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
        bindService(gattServiceIntent, serviceConnection, Context.BIND_AUTO_CREATE)

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
                    devicesNewInfo.add(result.device)
                    listNewDevicesAdapter.add(result.device.name)
                    listNewDevicesAdapter.notifyDataSetChanged()
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
        // TODO: Connect to Bluetooth Device
        targetDeviceAddress = deviceInfo.address

        BluetoothService.checkBluetoothPermission(this, this)
        Log.d(TAG,"Device name: ${deviceInfo.name}\n" +
                "Device address: ${deviceInfo.address}\n" +
                "Device type: ${deviceInfo.type}\n" +
                "Device class: ${deviceInfo.bluetoothClass.deviceClass}\n" +
                "Device major class: ${deviceInfo.bluetoothClass.majorDeviceClass}")

        // perform connection
        bluetoothLeService?.let { bluetooth ->
            bluetooth.connect(targetDeviceAddress!!, this@ConnectHrmActivity)
        }
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

    private fun makeGattUpdateIntentFilter(): IntentFilter? {
        return IntentFilter().apply {
            addAction(BluetoothLeService.ACTION_GATT_CONNECTED)
            addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED)
        }
    }

    override fun onRestart() {
        super.onRestart()
        Log.i(TAG, "Lifecycle: onRestart()")
    }

    override fun onResume() {
        super.onResume()
        registerReceiver(gattUpdateReceiver, makeGattUpdateIntentFilter())
        if (bluetoothLeService != null) {
            val result = bluetoothLeService!!
                .connect(targetDeviceAddress!!, this)
            Log.d(TAG, "Connect request result=$result")
        }
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(gattUpdateReceiver)
    }

    override fun onStop() {
        super.onStop()
        Log.i(TAG, "Lifecycle: onStop()")
        // stop search
        BluetoothService.stopLeDeviceSearch(this, this)
        // unregister receiver
//        unregisterReceiver(bluetoothDeviceFinderReceiver)
//        unregisterReceiver(bluetoothDiscoveryStatusReceiver)
        unregisterReceiver(gattUpdateReceiver)
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "Lifecycle: onDestroy()")
        // stop search
        BluetoothService.stopLeDeviceSearch(this, this)
        // unregister receiver
//        unregisterReceiver(bluetoothDeviceFinderReceiver)
//        unregisterReceiver(bluetoothDiscoveryStatusReceiver)
        unregisterReceiver(gattUpdateReceiver)
    }

    companion object {
        private const val TAG = "Mobile.ConnectHrmActivity"
    }
}