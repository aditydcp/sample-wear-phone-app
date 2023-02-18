package com.example.samplewearmobileapp

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.content.*
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.ActivityCompat.startActivityForResult

/**
 * Singleton object for providing Bluetooth functionalities.
 */
object BluetoothService {
    private const val TAG = "BluetoothService"
    private var isLeScanning = false
    private val handler = Handler()

    const val REQUEST_CODE_ENABLE_BLUETOOTH = 1
//    val DEVICE_FINDER_FILTER = IntentFilter(BluetoothDevice.ACTION_FOUND)
    val BLUETOOTH_STATE_FILTER = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
//    val BLUETOOTH_DISCOVERY_STATE_FILTER = IntentFilter().apply {
//        this.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
//        this.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED)
//    }
    const val ACTION_DATA_AVAILABLE =
        "com.example.bluetooth.le.ACTION_DATA_AVAILABLE"

    /**
     * Scan duration for LE Scan in ms.
     */
    val LE_SCAN_TIME: Long = 10000

    lateinit var manager: BluetoothManager
//    fun getManager(): BluetoothManager? {
//        return manager
//    }
//    fun buildManager(context: Context) {
//        manager = getSystemService(context, BluetoothManager::class.java)
//    }

    var adapter: BluetoothAdapter? = null
//    fun getAdapter(): BluetoothAdapter? {
//        return adapter
//    }
//    fun setAdapter(adapter: BluetoothAdapter?) {
//        this.adapter = adapter
//    }

    /**
     * Property to access Bluetooth LE services
     */
    var bluetoothLeService : BluetoothLeService? = null

    /**
     * ServiceConnection object to the Bluetooth LE Service
     */
    val serviceConnection: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(
            name: ComponentName,
            service: IBinder
        ) {
            bluetoothLeService = (service as BluetoothLeService.LocalBinder).getService()
            Log.d(TAG, "BluetoothLeService online")
            bluetoothLeService?.let { bluetooth ->
                if (!bluetooth.initialize()) {
                    Log.e(TAG, "Unable to initialize Bluetooth")
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName) {
            bluetoothLeService = null
            Log.d(TAG, "BluetoothLeService disconnected")
        }
    }

    /**
     * Object property for ScanCallback.
     *
     * Instantiate a new ScanCallback object and override methods you want to use.
     * Remember to set this back to null after use.
     */
    var leScanCallback: ScanCallback? = null

    /**
     * Send out prompt to enable Bluetooth on the device
     * on top of current activity.
     */
    fun enableBluetooth(context: Context, activity: Activity) {
        if (adapter?.isEnabled == false) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            checkBluetoothPermission(context, activity)
            startActivityForResult(activity, enableBtIntent, REQUEST_CODE_ENABLE_BLUETOOTH, null)
        }
    }

    /**
     * Toggle search for LE Bluetooth devices asynchronously.
     * Start search if it is not currently running.
     * Stop search if it is currently running.
     *
     * Automatically stop search after a period of time
     * defined by property LE_SCAN_TIME.
     *
     * (Optional) Function can be passed to act upon current scan status:
     * whether scan is currently running or not.
     *
     * Use property leScanCallback to access the scan callbacks.
     */
    fun toggleLeDeviceSearch(context: Context, activity: Activity,
                             function:
                                 (isScanning: Boolean) -> Unit = {
                                 Log.d(TAG,"Hi I'm default value\n" +
                                         "parameter value: $it")
                             }) {
        val runnable = Runnable {
            isLeScanning = false
            adapter?.bluetoothLeScanner?.stopScan(leScanCallback)
            function(isLeScanning)
        }

        checkBluetoothPermission(context, activity)
        if (leScanCallback != null) {
            if (!isLeScanning) {
                // stop scan after a period of time
                handler.postDelayed(runnable, LE_SCAN_TIME)

                // start scan
                isLeScanning = true
                adapter?.bluetoothLeScanner?.startScan(leScanCallback)
            } else {
                // stop scan if invoked when scan is running
                handler.removeCallbacks(runnable)
                isLeScanning = false
                adapter?.bluetoothLeScanner?.stopScan(leScanCallback)
            }
            function(isLeScanning)
        } else {
            Log.e(TAG, "Error: ScanCallback is null!")
        }
    }

    /**
     * Force stop LE Bluetooth device search if it is currently running.
     */
    fun stopLeDeviceSearch(context: Context, activity: Activity) {
        checkBluetoothPermission(context, activity)
        if (isLeScanning && leScanCallback != null)
            adapter?.bluetoothLeScanner?.stopScan(leScanCallback)
        else {
            Log.e(TAG, "Error: stopDeviceSearch() encountered an error!\n" +
                    "Possible cause:\n" +
                    "\t(1) LE device search has not started yet.\n" +
                    "\t(2) property leScanCallback is null.")
        }
    }

    /**
     * Checks for Bluetooth connectivity permissions.
     *
     * Checks for BLUETOOTH on API level 30 and below.
     * Checks for BLUETOOTH_CONNECT on API level 31 and above.
     */
    fun checkBluetoothPermission(context: Context, activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    activity,
                    arrayOf(Manifest.permission.BLUETOOTH_CONNECT),
                    MainActivity.REQUEST_CODE_PERMISSIONS
                )
                return
            }
        }
        else {
            if (ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.BLUETOOTH
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    activity,
                    arrayOf(Manifest.permission.BLUETOOTH),
                    MainActivity.REQUEST_CODE_PERMISSIONS
                )
                return
            }
        }
    }
}