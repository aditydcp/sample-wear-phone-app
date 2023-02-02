package com.example.samplewearmobileapp

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
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
    private lateinit var textWearTimestamp: TextView
    private lateinit var textHrmStatus: TextView
    private lateinit var textHrmHr: TextView
    private lateinit var textHrmIbi: TextView
    private lateinit var textHrmTimestamp: TextView
    private lateinit var textTooltip: TextView
    private lateinit var buttonMain: Button
    private lateinit var buttonView: TextView
    private lateinit var containerWear: LinearLayout
    private lateinit var containerHrm: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        message.sender = Entity.PHONE_APP

        // set UI vars
        textWearStatus = binding.wearStatus
        textWearHr = binding.wearHr
        textWearIbi = binding.wearIbi
        textWearTimestamp = binding.wearTime
        textHrmStatus = binding.hrmStatus
        textHrmHr = binding.hrmHr
        textHrmIbi = binding.hrmIbi
        textHrmTimestamp = binding.hrmTime
        textTooltip = binding.buttonTooltip
        buttonMain = binding.buttonMain
        buttonView = binding.buttonView
        containerWear = binding.wearContainer
        containerHrm = binding.hrmContainer

        runOnUiThread {
            textTooltip.text = getString(R.string.click_me_text)
            buttonMain.text = getString(R.string.button_start)
            buttonView.visibility = View.GONE
        }

        // build Google API Client with access to Wearable API
        client = GoogleApiClient.Builder(this)
            .addApi(Wearable.API)
            .addConnectionCallbacks(this)
            .build()
        client.connect()

        // set click listener
        buttonMain.setOnClickListener {
            message.content = getString(R.string.message_on_button_click)
            setMessageCode()
            toggleState()
            runOnUiThread {
                textTooltip.text = getString(R.string.clicked_text)
            }
            sendMessage(message, MessagePath.COMMAND)
        }

        containerWear.setOnClickListener {
            runOnUiThread {
                containerHrm.visibility = View.GONE
                buttonView.visibility = View.VISIBLE
            }
        }

        containerHrm.setOnClickListener {
            runOnUiThread {
                containerWear.visibility = View.GONE
                buttonView.visibility = View.VISIBLE
            }
        }

        buttonView.setOnClickListener {
            runOnUiThread {
                containerWear.visibility = View.VISIBLE
                containerHrm.visibility = View.VISIBLE
                buttonView.visibility = View.GONE
            }
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
        Wearable.DataApi.addListener(client) { data ->
            val receivedData = Gson().fromJson(String(data[0].dataItem.data), HeartData::class.java)
            runOnUiThread {
                textWearHr.text = receivedData.hr.toString()
                textWearIbi.text = receivedData.ibi.toString()
                textWearTimestamp.text = receivedData.timestamp
            }
        }
    }

    override fun onConnectionSuspended(p0: Int) {
        connectedNode = null
    }

    private fun onMessageArrived(messagePath: String) {
        wearMessage?.let {
            when (messagePath) {
                MessagePath.COMMAND -> {
                    if (it.code == ActivityCode.STOP_ACTIVITY) { // reset this module's state
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
                        ActivityCode.DO_NOTHING -> {
                            toggleState(0)
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
//            Toast.makeText(applicationContext, "Message sent!", Toast.LENGTH_LONG).show()
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
        }
        else if (stateNum == 1) {
            stateNum = 0
        }
        runOnUiThread {
            if (stateNum == 0) {
                buttonMain.text = getString(R.string.button_start)
                textWearStatus.text = getString(R.string.status_stopped)
            }
            if (stateNum == 1) {
                buttonMain.text = getString(R.string.button_stop)
                textWearStatus.text = getString(R.string.status_running)
            }
            textTooltip.text = stateNum.toString()
        }
        Log.d("Mobile","stateNum changed to: $stateNum")
    }
}