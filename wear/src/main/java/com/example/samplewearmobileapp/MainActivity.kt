package com.example.samplewearmobileapp

import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import com.example.samplewearmobileapp.databinding.ActivityMainBinding
import com.google.android.gms.common.api.GoogleApiClient
import com.google.android.gms.wearable.Wearable
import com.google.gson.Gson

class MainActivity : Activity(), GoogleApiClient.ConnectionCallbacks {

    private lateinit var binding: ActivityMainBinding
    private lateinit var client: GoogleApiClient
    private var currentMessage: Message? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.root.findViewById<TextView>(R.id.text).text = getString(R.string.default_message)
        binding.root.findViewById<TextView>(R.id.message).text = getString(R.string.message_placeholder)

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
            updateView()
        }
    }

    override fun onConnectionSuspended(code: Int) {
        Log.w("Wear", "Google Api Client connection suspended!")
    }

    private fun updateView() {
        currentMessage?.let {
            binding.root.findViewById<TextView>(R.id.text).text = getString(R.string.updated_message)
            binding.root.findViewById<TextView>(R.id.message).text = it.content
        }
    }

}