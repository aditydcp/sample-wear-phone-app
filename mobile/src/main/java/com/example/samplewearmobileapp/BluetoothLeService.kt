package com.example.samplewearmobileapp

import android.Manifest
import android.app.Activity
import android.app.Service
import android.bluetooth.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.nfc.NfcAdapter.EXTRA_DATA
import android.os.Binder
import android.os.IBinder
import android.util.Log
import androidx.core.app.ActivityCompat
import com.example.samplewearmobileapp.BluetoothService.ACTION_DATA_AVAILABLE
import java.util.UUID

class BluetoothLeService : Service() {
    private val binder = LocalBinder()
    private var bluetoothGatt: BluetoothGatt? = null
    private var connectionState = STATE_DISCONNECTED

    private val bluetoothGattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                // successfully connected to the GATT Server
                connectionState = STATE_CONNECTED
                broadcastUpdate(ACTION_GATT_CONNECTED)
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                // disconnected from the GATT Server
                connectionState = STATE_DISCONNECTED
                broadcastUpdate(ACTION_GATT_DISCONNECTED)
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.i(TAG, "onServicesDiscovered received: $status\n" +
                        "Success!")
                broadcastUpdate(ACTION_GATT_SERVICES_DISCOVERED)
            } else {
                Log.w(TAG, "onServicesDiscovered received: $status")
            }
        }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            Log.d(TAG,"Characteristic read! Status: $status\n" +
                    "UUID: ${characteristic.uuid}")
            if (status == BluetoothGatt.GATT_SUCCESS) {
                broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic)
            }
        }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            Log.d(TAG,"Characteristic changed.\n" +
                    "UUID: ${characteristic.uuid}")
            broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic)
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

    fun discoverGattServices(activity: Activity): Boolean? {
        Log.d(TAG, "Discovering Gatt services...")
        BluetoothService.checkBluetoothPermission(applicationContext, activity)
        return bluetoothGatt?.discoverServices()
    }

    fun getSupportedGattServices(): List<BluetoothGattService?>? {
        Log.d(TAG, "Getting supported Gatt services...")
        return bluetoothGatt?.services
    }

    fun connect(address: String, activity: Activity): Boolean {
        bluetoothAdapter?.let { adapter ->
            try {
                val device = adapter.getRemoteDevice(address)
                // connect to the GATT server on the device
                Log.i(TAG, "Performing connection...")
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

    /**
     * Enable/disable notification on characteristic according to `enabled` parameter.
     */
    fun setCharacteristicNotification(
        characteristic: BluetoothGattCharacteristic,
        enabled: Boolean,
        activity: Activity
    ) {
        Log.i(TAG,"Setting characteristic Notification for ${characteristic.uuid}")
        bluetoothGatt?.let { gatt ->
            BluetoothService.checkBluetoothPermission(applicationContext, activity)
            gatt.setCharacteristicNotification(characteristic, enabled)

            // This is specific to Heart Rate Measurement.
            if (toUUID(UUID_HEART_RATE_MEASUREMENT) == characteristic.uuid) {
                Log.i(TAG, "Setting up descriptor...")
                val descriptor = characteristic
                    .getDescriptor(UUID.fromString(
                        SampleGattAttributes.CLIENT_CHARACTERISTIC_CONFIG))
                descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                gatt.writeDescriptor(descriptor)
            }
        } ?: run {
            Log.w(TAG, "BluetoothGatt not initialized")
        }
    }

    /**
     * Asynchronously read the characteristic value.
     *
     * This will invoke onCharacteristicRead on BluetoothGattCallback.
     */
    fun readCharacteristic(characteristic: BluetoothGattCharacteristic, activity: Activity) {
        Log.i(TAG,"Attempting to read characteristic...\n" +
                "UUID: ${characteristic.uuid}")
        bluetoothGatt?.let { gatt ->
            BluetoothService.checkBluetoothPermission(applicationContext, activity)
            gatt.readCharacteristic(characteristic)
        } ?: run {
            Log.w(TAG, "BluetoothGatt not initialized")
            return
        }
    }

    /**
     * Get a BluetoothGattCharacteristic type by Service and Characteristic UUID.
     *
     * The parameters are the 16 bit UUID (4 digits HEX) in String.
     *
     * Returns null if any UUID not valid, supported or not a valid combination.
     */
    fun getCharacteristicFromService(
        serviceUUID: String,
        characteristicUUID: String)
    : BluetoothGattCharacteristic? {
        var service: BluetoothGattService? = null
        var characteristic: BluetoothGattCharacteristic? = null
        try {
            Log.d(TAG, "Getting service of UUID: ${toUUID(serviceUUID)}")
            service = bluetoothGatt?.getService(toUUID(serviceUUID))
            if (service != null) {
                Log.d(TAG, "Service correct!")

                Log.d(TAG, "Getting characteristic of UUID: ${toUUID(characteristicUUID)}")
                characteristic = service.getCharacteristic(toUUID(characteristicUUID))
            }
            else Log.w(TAG, "Service is still null")

//            characteristic = bluetoothGatt?.getService(
//                toUUID(serviceUUID))?.getCharacteristic(
//                toUUID(characteristicUUID))
        } catch (e: Exception) {
            Log.w(TAG, "Service and Characteristics did not match", e)
        }

        if (characteristic != null) Log.d(TAG, "Characteristic returned successfully.")
        else Log.w(TAG, "Returned characteristic is null")
        return characteristic
    }

    private fun broadcastUpdate(action: String) {
        val intent = Intent(action)
        Log.d(TAG, "Broadcasting $action")
        sendBroadcast(intent)
    }

    private fun broadcastUpdate(action: String, characteristic: BluetoothGattCharacteristic) {
        val intent = Intent(action)

        Log.d(TAG,"Characteristic UUID: ${characteristic.uuid}")
        Log.d(TAG,"Validator: ${toUUID(UUID_HEART_RATE_MEASUREMENT)}")

        // This is special handling for the Heart Rate Measurement profile. Data
        // parsing is carried out as per profile specifications.
        when (characteristic.uuid) {
            toUUID(UUID_HEART_RATE_MEASUREMENT) -> {
                val flag = characteristic.properties
                val format = when (flag and 0x01) {
                    0x01 -> {
                        Log.d(TAG, "Heart rate format UINT16.")
                        BluetoothGattCharacteristic.FORMAT_UINT16
                    }
                    else -> {
                        Log.d(TAG, "Heart rate format UINT8.")
                        BluetoothGattCharacteristic.FORMAT_UINT8
                    }
                }
                val hasEE = when (flag and 0x08 shr 3) {
                    0x01 -> {
                        Log.d(TAG,"Energy Expended field is present. Units: kilo Joules")
                        true
                    }
                    else -> {
                        Log.d(TAG,"Energy Expended field is not present.")
                        false
                    }
                }
                val hasRR = when (flag and 0x10 shr 4) {
                    0x01 -> {
                        Log.d(TAG,"One or more RR-Interval values are present.")
                        true
                    }
                    else -> {
                        Log.d(TAG,"RR-Interval values are not present.")
                        false
                    }
                }
                // raw data
                val data: ByteArray? = characteristic.value
                var hexString: String? = null
                if (data?.isNotEmpty() == true) {
                    hexString = data.joinToString(separator = " ") {
                        String.format("%02X", it)
                    }
                }
                Log.d(TAG, "Raw characteristic value: $data\n" +
                        "HEX: $hexString")

                val heartRate = characteristic.getIntValue(format, 1)
                Log.d(TAG, String.format("Received heart rate: %d", heartRate))
                intent.putExtra(EXTRA_DATA, (heartRate).toString())
//                if (hasRR) {
//                    if (!hasEE) {
//
//                    }
//                }
            }
            else -> {
                // For all other profiles, writes the data formatted in HEX.
                Log.d(TAG,"Unknown profile arrived.")
                val data: ByteArray? = characteristic.value
                if (data?.isNotEmpty() == true) {
                    val hexString: String = data.joinToString(separator = " ") {
                        String.format("%02X", it)
                    }
                    intent.putExtra(EXTRA_DATA, "$data\n$hexString")
                }
            }
        }
        sendBroadcast(intent)
    }

    private fun toUUID(uuid: String) : UUID {
        val length = uuid.length

        var uuidString = ""
        for (i in 8 downTo length + 1) {
            uuidString = uuidString.plus("0")
        }
        uuidString = uuidString.plus(uuid)
            .plus("-0000-1000-8000-00805F9B34FB")

        Log.d(TAG, "Converting string $uuidString to UUID...")

        return UUID.fromString(uuidString)
    }

    override fun onUnbind(intent: Intent?): Boolean {
        close()
        return super.onUnbind(intent)
    }

    private fun close() {
        bluetoothGatt?.let { gatt ->
            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                // TODO: Consider calling
                //    ActivityCompat#requestPermissions
                // here to request the missing permissions, and then overriding
                //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                //                                          int[] grantResults)
                // to handle the case where the user grants the permission. See the documentation
                // for ActivityCompat#requestPermissions for more details.
                return
            }
            gatt.close()
            bluetoothGatt = null
        }
    }

    companion object {
        private const val TAG = "BluetoothLeService class"

        private const val STATE_DISCONNECTED = 0
        private const val STATE_CONNECTED = 2

        const val ACTION_GATT_CONNECTED =
            "com.example.bluetooth.le.ACTION_GATT_CONNECTED"
        const val ACTION_GATT_DISCONNECTED =
            "com.example.bluetooth.le.ACTION_GATT_DISCONNECTED"
        const val ACTION_GATT_SERVICES_DISCOVERED =
            "com.example.bluetooth.le.ACTION_GATT_SERVICES_DISCOVERED"

        const val UUID_HEART_RATE_MEASUREMENT = "2A37"
        const val UUID_HEART_RATE_SERVICE = "180D"
        const val UUID_BATTERY_LEVEL = "2A19"
        const val UUID_BATTERY_SERVICE = "180F"
    }
}