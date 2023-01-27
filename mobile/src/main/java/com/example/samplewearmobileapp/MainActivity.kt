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

class MainActivity : AppCompatActivity(), GoogleApiClient.ConnectionCallbacks {
    private lateinit var binding: ActivityMainBinding
    private lateinit var client: GoogleApiClient
    private var connectedNode: List<Node>? = null
    private var message: Message = Message()
    private var stateNum = 0

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
        message.code = ActivityCode.START_ACTIVITY

        // set click listener
        binding.root.findViewById<Button>(R.id.button).setOnClickListener {
            setMessageCode()
            sendMessage(message)
        }
    }

    private fun sendMessage(message: Message) {
        binding.root.findViewById<TextView>(R.id.textView).text = getString(R.string.clicked_text)
        val gson = Gson()
        connectedNode?.forEach { node ->
            val bytes = gson.toJson(message).toByteArray()
            Wearable.MessageApi.sendMessage(client, node.id, "/message", bytes)
            Toast.makeText(applicationContext, "Message sent!", Toast.LENGTH_LONG).show()
        }
    }

    private fun setMessageCode(forceCode: Int = 99) {
        if (forceCode != 99) {
            message.code = forceCode
        }
        else if (stateNum == 0) {
            message.code = ActivityCode.START_ACTIVITY
            toggleState()
        }
        else if (stateNum == 1) {
            message.code = ActivityCode.STOP_ACTIVITY
            toggleState()
        }
    }

    private fun toggleState() {
        if (stateNum == 0) stateNum = 1
        else if (stateNum == 1) stateNum = 0
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