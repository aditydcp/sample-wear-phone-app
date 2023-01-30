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
    private var wearMessage: Message? = null
    private var stateNum = 0

    private lateinit var textWearStatus: TextView
    private lateinit var textWearHr: TextView
    private lateinit var textWearIbi: TextView
    private lateinit var textHrmStatus: TextView
    private lateinit var textHrmHr: TextView
    private lateinit var textHrmIbi: TextView
    private lateinit var textTooltip: TextView
    private lateinit var button: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        message.sender = Entity.PHONE_APP

        // set UI vars
        textWearStatus = binding.wearStatus
        textWearHr = binding.wearHr
        textWearIbi = binding.wearIbi
        textHrmStatus = binding.hrmStatus
        textHrmHr = binding.hrmHr
        textHrmIbi = binding.hrmIbi
        textTooltip = binding.buttonTooltip
        button = binding.button

        textTooltip.text = getString(R.string.click_me_text)
        button.text = getString(R.string.button_start)

        // build Google API Client with access to Wearable API
        client = GoogleApiClient.Builder(this)
            .addApi(Wearable.API)
            .addConnectionCallbacks(this)
            .build()
        client.connect()

        // set click listener
        button.setOnClickListener {
            message.content = getString(R.string.message_on_button_click)
            setMessageCode()
            toggleState()
            textTooltip.text = getString(R.string.clicked_text)
            sendMessage(message, MessagePath.COMMAND)
        }
    }

    override fun onConnected(p0: Bundle?) {
        Wearable.NodeApi.getConnectedNodes(client).setResultCallback {
            connectedNode = it.nodes
            Log.d("Mobile","Node connected")
            Toast.makeText(applicationContext, "Node connected!", Toast.LENGTH_LONG).show()

            // request Wear App current state
            // request on connection to ensure message is sent
            message.content = "Requesting Wear App current state"
            message.code = ActivityCode.START_ACTIVITY
            sendMessage(message, MessagePath.REQUEST)
        }
        Wearable.MessageApi.addListener(client) { messageEvent ->
            wearMessage = Gson().fromJson(String(messageEvent.data), Message::class.java)
            onMessageArrived(messageEvent.path)
        }
    }

    override fun onConnectionSuspended(p0: Int) {
        connectedNode = null
    }

    private fun onMessageArrived(messagePath: String) {
        wearMessage?.let {
            when (messagePath) {
                MessagePath.COMMAND -> {
                    if (it.code == ActivityCode.START_ACTIVITY) { // reset this module's state
                        toggleState(0)
                    }
                }
                MessagePath.REQUEST -> {
                    TODO("Not yet implemented")
                }
                MessagePath.INFO -> {
                    when (it.code) {
                        ActivityCode.START_ACTIVITY -> { // get Wear's current state
                            toggleState(1)
                        }
                    }
                }
            }
        }
    }

    private fun sendMessage(message: Message, path: String) {
        val gson = Gson()
        connectedNode?.forEach { node ->
            val bytes = gson.toJson(message).toByteArray()
            Wearable.MessageApi.sendMessage(client, node.id, path, bytes)
            Toast.makeText(applicationContext, "Message sent!", Toast.LENGTH_LONG).show()
        }
    }

    private fun setMessageCode(forceCode: Int = 99) {
        if (forceCode != 99) {
            message.code = forceCode
        }
        else if (stateNum == 0) {
            message.code = ActivityCode.START_ACTIVITY
        }
        else if (stateNum == 1) {
            message.code = ActivityCode.STOP_ACTIVITY
        }
    }

    private fun toggleState(forceCode: Int = 99) {
        if (forceCode != 99) {
            stateNum = forceCode
        }
        else if (stateNum == 0) {
            stateNum = 1
            button.text = getString(R.string.button_start)
        }
        else if (stateNum == 1) {
            stateNum = 0
            button.text = getString(R.string.button_stop)
        }
        textTooltip.text = stateNum.toString()
        Log.d("Mobile","stateNum changed to: $stateNum")
    }
}