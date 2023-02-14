package com.example.samplewearmobileapp

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.example.samplewearmobileapp.databinding.ActivityConnectHrmBinding

class ConnectHrmActivity : AppCompatActivity() {
    // TODO: Create an activity for finding bluetooth device
    private lateinit var binding: ActivityConnectHrmBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityConnectHrmBinding.inflate(layoutInflater)
        setContentView(binding.root)
        Log.i(TAG, "Lifecycle: onCreate()")
    }

    override fun onRestart() {
        super.onRestart()
        Log.i(TAG, "Lifecycle: onRestart()")
    }

    override fun onStop() {
        super.onStop()
        Log.i(TAG, "Lifecycle: onStop()")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "Lifecycle: onDestroy()")
    }

    companion object {
        private const val TAG = "Mobile.ConnectHrmActivity"
    }
}