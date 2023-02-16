package com.example.samplewearmobileapp

import android.bluetooth.BluetoothDevice
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.example.samplewearmobileapp.databinding.ActivityConnectHrmBinding

class ConnectHrmActivity : AppCompatActivity() {
    // TODO: Create an activity for finding bluetooth device
    private lateinit var binding: ActivityConnectHrmBinding
    private lateinit var devicesAcquaintedInfo: ArrayList<BluetoothDevice>
    private lateinit var devicesNewInfo: ArrayList<BluetoothDevice>
    private lateinit var listAcquaintedDevicesAdapter: ArrayAdapter<String>
    private lateinit var listNewDevicesAdapter: ArrayAdapter<String>

    private lateinit var textStatus: TextView
    private lateinit var spinnerStatus: ProgressBar
    private lateinit var buttonSearch: TextView
    private lateinit var textAcquaintedDevices: TextView
    private lateinit var listAcquaintedDevices: ListView
    private lateinit var textNewDevices: TextView
    private lateinit var listNewDevices: ListView

//    private val bluetoothDeviceFinderReceiver = object : BroadcastReceiver() {
//        override fun onReceive(context: Context, intent: Intent) {
//            when (intent.action) {
//                BluetoothDevice.ACTION_FOUND -> {
//                    // Discovery has found a device. Get the BluetoothDevice
//                    // object and its info from the Intent.
//                    val device: BluetoothDevice? =
//                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
//                    if (device != null) {
//                        BluetoothService.checkBluetoothPermission(
//                            applicationContext,
//                            this@ConnectHrmActivity
//                        )
//                        devicesNewInfo.add(device)
//                        listNewDevicesAdapter.add(device.name)
//                        listNewDevicesAdapter.notifyDataSetChanged()
//
//                        Log.d(TAG,"Device name: ${device.name}\n" +
//                                "Device address: ${device.address}\n" +
//                                "Device type: ${device.type}\n" +
//                                "Device class: ${device.bluetoothClass.deviceClass}\n" +
//                                "Device major class: ${device.bluetoothClass.majorDeviceClass}")
//                    }
//                }
//            }
//        }
//    }

//    private val bluetoothDiscoveryStatusReceiver = object : BroadcastReceiver() {
//        override fun onReceive(context: Context, intent: Intent) {
//            when (intent.action) {
//                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
//                    runOnUiThread {
//                        spinnerStatus.visibility = View.INVISIBLE
//                        buttonSearch.visibility = View.VISIBLE
//                        textStatus.text = getString(R.string.connect_status_stopped)
//                    }
//                }
//                BluetoothAdapter.ACTION_DISCOVERY_STARTED -> {
//                    runOnUiThread {
//                        spinnerStatus.visibility = View.VISIBLE
//                        buttonSearch.visibility = View.GONE
//                        textStatus.text = getString(R.string.connect_status_searching)
//                    }
//                }
//            }
//        }
//    }

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
        BluetoothService.checkBluetoothPermission(this, this)
        Log.d(TAG,"Device name: ${deviceInfo.name}\n" +
                "Device address: ${deviceInfo.address}\n" +
                "Device type: ${deviceInfo.type}\n" +
                "Device class: ${deviceInfo.bluetoothClass.deviceClass}\n" +
                "Device major class: ${deviceInfo.bluetoothClass.majorDeviceClass}")
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

    override fun onRestart() {
        super.onRestart()
        Log.i(TAG, "Lifecycle: onRestart()")
    }

    override fun onStop() {
        super.onStop()
        Log.i(TAG, "Lifecycle: onStop()")
        // stop search
        BluetoothService.stopLeDeviceSearch(this, this)
        // unregister receiver
//        unregisterReceiver(bluetoothDeviceFinderReceiver)
//        unregisterReceiver(bluetoothDiscoveryStatusReceiver)
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "Lifecycle: onDestroy()")
        // stop search
        BluetoothService.stopLeDeviceSearch(this, this)
        // unregister receiver
//        unregisterReceiver(bluetoothDeviceFinderReceiver)
//        unregisterReceiver(bluetoothDiscoveryStatusReceiver)
    }

    companion object {
        private const val TAG = "Mobile.ConnectHrmActivity"
    }
}