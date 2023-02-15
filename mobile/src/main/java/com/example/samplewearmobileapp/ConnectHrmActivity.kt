package com.example.samplewearmobileapp

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
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

    private val bluetoothDeviceFinderReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    // Discovery has found a device. Get the BluetoothDevice
                    // object and its info from the Intent.
                    val device: BluetoothDevice? =
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    if (device != null) {
                        BluetoothService.checkBluetoothPermission(
                            applicationContext,
                            this@ConnectHrmActivity
                        )
                        devicesNewInfo.add(device)
                        listNewDevicesAdapter.add(device.name)
                        listNewDevicesAdapter.notifyDataSetChanged()

                        Log.d(TAG,"Device name: ${device.name}\n" +
                                "Device address: ${device.address}\n" +
                                "Device type: ${device.type}\n" +
                                "Device class: ${device.bluetoothClass.deviceClass}\n" +
                                "Device major class: ${device.bluetoothClass.majorDeviceClass}")
                    }
                }
            }
        }
    }

    private val bluetoothDiscoveryStatusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    runOnUiThread {
                        spinnerStatus.visibility = View.INVISIBLE
                        buttonSearch.visibility = View.VISIBLE
                        textStatus.text = getString(R.string.connect_status_stopped)
                    }
                }
                BluetoothAdapter.ACTION_DISCOVERY_STARTED -> {
                    runOnUiThread {
                        spinnerStatus.visibility = View.VISIBLE
                        buttonSearch.visibility = View.GONE
                        textStatus.text = getString(R.string.connect_status_searching)
                    }
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

        // register bluetooth device finder broadcast receiver
        registerReceiver(bluetoothDeviceFinderReceiver, BluetoothService.DEVICE_FINDER_FILTER)

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
        BluetoothService.startSearchDevice(this, this)
        registerReceiver(bluetoothDiscoveryStatusReceiver,
            BluetoothService.BLUETOOTH_DISCOVERY_STATE_FILTER)
    }

    private fun attemptConnection(deviceInfo: BluetoothDevice) {
        // TODO: Connect to Bluetooth Device
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
            devicesAcquaintedInfo.add(device)
            listAcquaintedDevicesAdapter.add(device.name)
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
        // unregister receiver
        unregisterReceiver(bluetoothDeviceFinderReceiver)
        unregisterReceiver(bluetoothDiscoveryStatusReceiver)
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "Lifecycle: onDestroy()")
        // unregister receiver
        unregisterReceiver(bluetoothDeviceFinderReceiver)
        unregisterReceiver(bluetoothDiscoveryStatusReceiver)
    }

    companion object {
        private const val TAG = "Mobile.ConnectHrmActivity"
    }
}