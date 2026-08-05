package com.example.laranjinhaqrwebview

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.laranjinhaqrwebview.databinding.ActivityTechnicalAreaBinding

class TechnicalAreaActivity : AppCompatActivity() {
    private lateinit var binding: ActivityTechnicalAreaBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTechnicalAreaBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.technicalVersionText.text = "VisionID V${BuildConfig.VERSION_NAME}"

        val enabled = QrSecurityPreferences.isDomainLockEnabled(this)
        binding.qrSecuritySwitch.isChecked = enabled
        updateQrSecurityVisual(enabled)

        binding.qrSecuritySwitch.setOnCheckedChangeListener { _, isChecked ->
            QrSecurityPreferences.setDomainLockEnabled(this, isChecked)
            updateQrSecurityVisual(isChecked)
        }

        binding.qrSecurityCard.setOnClickListener {
            binding.qrSecuritySwitch.isChecked = !binding.qrSecuritySwitch.isChecked
        }

        binding.openLogsButton.setOnClickListener {
            AppLog.info(
                this,
                category = "TECNICO",
                event = "TECHNICAL_LOGS_SELECTED",
                message = "Logs selecionados na área técnica"
            )
            startActivity(Intent(this, LogsActivity::class.java))
        }

        binding.openCameraTestButton.setOnClickListener {
            AppLog.info(
                this,
                category = "TECNICO",
                event = "CAMERA_TEST_SELECTED",
                message = "Teste de câmera selecionado na área técnica"
            )
            startActivity(Intent(this, CameraTestActivity::class.java))
        }

        binding.backButton.setOnClickListener { finish() }
    }

    private fun updateQrSecurityVisual(enabled: Boolean) {
        binding.qrSecurityStatusText.text = if (enabled) "ATIVADA" else "DESATIVADA"
        binding.qrSecurityStatusText.setTextColor(
            Color.parseColor(if (enabled) "#0B7A3E" else "#A33A00")
        )
        binding.qrSecurityStatusText.backgroundTintList = ColorStateList.valueOf(
            Color.parseColor(if (enabled) "#DDF7E8" else "#FFF0E6")
        )

        val states = arrayOf(
            intArrayOf(android.R.attr.state_checked),
            intArrayOf()
        )
        binding.qrSecuritySwitch.thumbTintList = ColorStateList(
            states,
            intArrayOf(Color.WHITE, Color.WHITE)
        )
        binding.qrSecuritySwitch.trackTintList = ColorStateList(
            states,
            intArrayOf(Color.parseColor("#16A05D"), Color.parseColor("#8C8C8C"))
        )
    }
}
