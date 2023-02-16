package com.example.samplewearmobileapp

import android.app.Activity
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothProfile
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log

class BluetoothLeService : Service() {
    private val binder = LocalBinder()
    private var bluetoothGatt: BluetoothGatt? = null

    private val bluetoothGattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                // successfully connected to the GATT Server
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                // disconnected from the GATT Server
            }
        }
    }

    private var bluetoothAdapter: BluetoothAdapter? = null

    fun initialize(): Boolean {
        bluetoothAdapter = BluetoothService.adapter
        if (bluetoothAdapter == null) {
            Log.e(TAG, "Unable to obtain a BluetoothAdapter.")
            return false
        }
        return true
    }

    override fun onBind(intent: Intent): IBinder {
        return binder
    }

    inner class LocalBinder : Binder() {
        fun getService() : BluetoothLeService {
            return this@BluetoothLeService
        }
    }

    fun connect(address: String, activity: Activity): Boolean {
        bluetoothAdapter?.let { adapter ->
            try {
                val device = adapter.getRemoteDevice(address)
                // connect to the GATT server on the device
                BluetoothService.checkBluetoothPermission(this, activity)
                bluetoothGatt = device.connectGatt(this,
                    false, bluetoothGattCallback)
                return true
            } catch (exception: IllegalArgumentException) {
                Log.w(TAG, "Device not found with provided address. Unable to connect.")
                return false
            }
        // connect to the GATT server on the device
        } ?: run {
            Log.w(TAG, "BluetoothAdapter not initialized")
            return false
        }
    }

    companion object {
        private const val TAG = "BluetoothLeService class"
    }
}