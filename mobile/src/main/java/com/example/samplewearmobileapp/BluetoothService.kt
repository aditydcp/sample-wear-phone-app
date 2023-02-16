package com.example.samplewearmobileapp

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.ActivityCompat.startActivityForResult

/**
 * Singleton object for providing Bluetooth functionalities.
 */
object BluetoothService {
    const val REQUEST_CODE_ENABLE_BLUETOOTH = 1
    val DEVICE_FINDER_FILTER = IntentFilter(BluetoothDevice.ACTION_FOUND)
    val BLUETOOTH_STATE_FILTER = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
    val BLUETOOTH_DISCOVERY_STATE_FILTER = IntentFilter().apply {
        this.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        this.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED)
    }

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
     * Start searching for Bluetooth devices asynchronously.
     */
    fun startDeviceSearch(context: Context, activity: Activity) {
        checkBluetoothPermission(context, activity)
        adapter?.startDiscovery()
    }

    /**
     * Stop Bluetooth device search if it is currently running.
     */
    fun stopDeviceSearch(context: Context, activity: Activity) {
        checkBluetoothPermission(context, activity)
        if(adapter?.isDiscovering == true)
            adapter?.cancelDiscovery()
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