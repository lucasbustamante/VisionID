package com.example.laranjinhaqrwebview

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import androidx.appcompat.app.AppCompatActivity
import com.example.laranjinhaqrwebview.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val handler = Handler(Looper.getMainLooper())
    private var technicalAreaOpened = false

    private val openTechnicalArea = Runnable {
        if (isFinishing || isDestroyed || technicalAreaOpened) return@Runnable

        technicalAreaOpened = true
        startActivity(Intent(this, TechnicalAreaActivity::class.java))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.versionText.text = "V${BuildConfig.VERSION_NAME}"
        binding.versionText.setOnClickListener { /* Necessário para acessibilidade do toque prolongado. */ }
        binding.versionText.setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    if (!technicalAreaOpened) {
                        handler.postDelayed(openTechnicalArea, TECHNICAL_AREA_HOLD_DURATION_MS)
                    }
                    true
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    handler.removeCallbacks(openTechnicalArea)
                    view.performClick()
                    true
                }

                else -> true
            }
        }

        binding.openCameraButton.setOnClickListener {
            startActivity(Intent(this, QrScannerActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        technicalAreaOpened = false
    }

    override fun onDestroy() {
        handler.removeCallbacks(openTechnicalArea)
        super.onDestroy()
    }

    companion object {
        private const val TECHNICAL_AREA_HOLD_DURATION_MS = 3_000L
    }
}
