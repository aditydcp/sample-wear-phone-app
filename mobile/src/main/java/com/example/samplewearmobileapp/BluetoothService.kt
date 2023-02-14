package com.example.samplewearmobileapp

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import androidx.core.content.ContextCompat.getSystemService

object BluetoothService {
    private var manager: BluetoothManager? = null
    fun getManager(): BluetoothManager? {
        return manager
    }
    fun buildManager(context: Context) {
        manager = getSystemService(context, BluetoothManager::class.java)
    }

    private var adapter: BluetoothAdapter? = null
    fun getAdapter(): BluetoothAdapter? {
        return adapter
    }
    fun setAdapter(adapter: BluetoothAdapter?) {
        this.adapter = adapter
    }


}