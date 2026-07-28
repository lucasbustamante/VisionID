package com.example.laranjinhaqrwebview

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.laranjinhaqrwebview.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val handler = Handler(Looper.getMainLooper())
    private var logPromptOpened = false

    private val openLogsPrompt = Runnable {
        logPromptOpened = true
        AlertDialog.Builder(this)
            .setTitle("Área de logs")
            .setMessage("Deseja abrir a área de logs?")
            .setNegativeButton("Não") { _, _ ->
            }
            .setPositiveButton("Sim") { _, _ ->
                startActivity(Intent(this, LogsActivity::class.java))
            }
            .setOnDismissListener { logPromptOpened = false }
            .show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.versionText.text = "V${BuildConfig.VERSION_NAME}"
        binding.versionText.setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    if (!logPromptOpened) handler.postDelayed(openLogsPrompt, LOG_HOLD_DURATION_MS)
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    handler.removeCallbacks(openLogsPrompt)
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

    override fun onDestroy() {
        handler.removeCallbacks(openLogsPrompt)
        super.onDestroy()
    }

    companion object {
        private const val LOG_HOLD_DURATION_MS = 3000L
    }
}
