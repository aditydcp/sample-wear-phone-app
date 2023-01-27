package com.example.samplewearmobileapp

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import com.example.samplewearmobileapp.databinding.ActivityMainBinding
import com.google.android.gms.common.api.GoogleApiClient
import com.google.android.gms.wearable.Wearable
import com.google.gson.Gson

class MainActivity : Activity(), GoogleApiClient.ConnectionCallbacks {

    private lateinit var binding: ActivityMainBinding
    private lateinit var client: GoogleApiClient
    private var currentMessage: Message? = null

    private lateinit var textStatus : TextView
    private lateinit var textTip : TextView
    private lateinit var container : LinearLayout
    private lateinit var textHeartRate : TextView
    private lateinit var textHeartRateStatus : TextView
    private lateinit var textIbi : TextView
    private lateinit var textIbiStatus : TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // requests permission
        if (ActivityCompat.checkSelfPermission(
                applicationContext,
                getString(R.string.BodySensors)
            ) == PackageManager.PERMISSION_DENIED
        ) requestPermissions(
            arrayOf(
                Manifest.permission.BODY_SENSORS
            ), 0
        )

        // set UI vars
        textStatus = binding.statusMsg
        textTip = binding.message
        container = binding.valueContainer
        textHeartRate = binding.heartRate
        textHeartRateStatus = binding.heartRateStatus
        textIbi = binding.ibi
        textIbiStatus = binding.ibiStatus

        // set initial UI
        textStatus.text = getString(R.string.default_status)
        textTip.text = getString(R.string.message_placeholder)
        textHeartRate.text = getString(R.string.default_heart_rate)
        textHeartRateStatus.text = getString(R.string.default_heart_rate_status)
        textIbi.text = getString(R.string.default_ibi)
        textIbiStatus.text = getString(R.string.default_ibi_status)

        textTip.visibility = View.VISIBLE
        container.visibility = View.GONE

        // build Google API Client with access to Wearable API
        client = GoogleApiClient.Builder(this)
            .addConnectionCallbacks(this)
            .addApi(Wearable.API)
            .build()
        client.connect()

    }

    override fun onConnected(bundle: Bundle?) {
        Wearable.MessageApi.addListener(client) { messageEvent ->
            currentMessage = Gson().fromJson(String(messageEvent.data), Message::class.java)
            onMessageArrived()
        }
    }

    override fun onConnectionSuspended(code: Int) {
        Log.w("Wear", "Google Api Client connection suspended!")
    }

    private fun onMessageArrived() {
        currentMessage?.let {
            when (it.code) {
                ActivityCode.START_ACTIVITY -> {
                    container.visibility = View.VISIBLE
                    textTip.visibility = View.GONE
                    textStatus.text = getString(R.string.status_running)
                }
                ActivityCode.STOP_ACTIVITY -> {
                    textStatus.text = getString(R.string.status_stopped)
                }
                ActivityCode.DO_NOTHING -> {
                    Toast.makeText(
                        applicationContext, getString(R.string.no_content), Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

}