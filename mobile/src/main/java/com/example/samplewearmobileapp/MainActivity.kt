package com.example.samplewearmobileapp

import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.samplewearmobileapp.databinding.ActivityMainBinding
import com.google.android.gms.common.api.GoogleApiClient
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.Wearable
import com.google.gson.Gson
import com.example.samplewearmobileapp.Message

class MainActivity : AppCompatActivity(), GoogleApiClient.ConnectionCallbacks {
    private lateinit var binding: ActivityMainBinding
    private lateinit var client: GoogleApiClient
    private var connectedNode: List<Node>? = null
    private var message: Message = Message()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.root.findViewById<TextView>(R.id.textView).text = getString(R.string.click_me_text)

        // build Google API Client with access to Wearable API
        client = GoogleApiClient.Builder(this)
            .addApi(Wearable.API)
            .addConnectionCallbacks(this)
            .build()
        client.connect()

        // create a message object
        message.content = getString(R.string.message_content)

        // set click listener
        binding.root.findViewById<Button>(R.id.button).setOnClickListener {
            onButtonClicked(message)
        }
    }

    private fun onButtonClicked(message: Message) {
        binding.root.findViewById<TextView>(R.id.textView).text = getString(R.string.clicked_text)
        val gson = Gson()
        connectedNode?.forEach { node ->
            val bytes = gson.toJson(message).toByteArray()
            Wearable.MessageApi.sendMessage(client, node.id, "/message", bytes)
            Toast.makeText(applicationContext, "Message sent!", Toast.LENGTH_LONG).show()
        }
    }

    override fun onConnected(p0: Bundle?) {
        Wearable.NodeApi.getConnectedNodes(client).setResultCallback {
            connectedNode = it.nodes
            Log.d("Mobile","Node connected")
            Toast.makeText(applicationContext, "Node connected!", Toast.LENGTH_LONG).show()
        }
    }

    override fun onConnectionSuspended(p0: Int) {
        connectedNode = null
    }
}