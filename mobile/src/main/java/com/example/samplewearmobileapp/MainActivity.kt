package com.example.samplewearmobileapp

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Button
import com.example.samplewearmobileapp.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.root.findViewById<Button>(R.id.button).setOnClickListener {
            onButtonClicked()
        }
    }

    fun onButtonClicked() {
        TODO("Handle button click by sending message to the Wear module")
    }
}