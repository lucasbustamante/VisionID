package com.example.laranjinhaqrwebview

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity

class SplashActivity : AppCompatActivity() {
    private val handler = Handler(Looper.getMainLooper())
    private val openApp = Runnable {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)
        handler.postDelayed(openApp, SPLASH_DURATION_MS)
    }

    override fun onDestroy() {
        handler.removeCallbacks(openApp)
        super.onDestroy()
    }

    companion object {
        private const val SPLASH_DURATION_MS = 1500L
    }
}
