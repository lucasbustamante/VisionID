package com.example.laranjinhaqrwebview

import android.content.Intent
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
}
