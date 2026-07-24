package com.example.laranjinhaqrwebview

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.laranjinhaqrwebview.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.versionText.text = "V${BuildConfig.VERSION_NAME}"

        binding.openCameraButton.setOnClickListener {
            startActivity(Intent(this, QrScannerActivity::class.java))
        }
    }
}
