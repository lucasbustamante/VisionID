package com.example.laranjinhaqrwebview

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.laranjinhaqrwebview.databinding.ActivityLogDetailBinding

class LogDetailActivity : AppCompatActivity() {
    private lateinit var binding: ActivityLogDetailBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLogDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.backButton.setOnClickListener { finish() }

        val id = intent.getStringExtra(EXTRA_LOG_ID).orEmpty()
        val entry = AppLog.findById(this, id)
        if (entry == null) {
            binding.logTitle.text = "Log não encontrado"
            binding.logDetails.text = "O registro pode ter sido removido."
            return
        }

        binding.logTitle.text = entry.message
        binding.logDetails.text = buildString {
            appendLine("Data e hora")
            appendLine(AppLog.formatDateTime(entry.timestamp))
            appendLine()
            appendLine("Nível")
            appendLine(entry.level)
            appendLine()
            appendLine("Categoria")
            appendLine(entry.category)
            appendLine()
            appendLine("Evento")
            appendLine(entry.event)
            appendLine()
            appendLine("Mensagem")
            appendLine(entry.message)
            appendLine()
            appendLine("Detalhes")
            appendLine(entry.details.ifBlank { "Sem informações adicionais." })
        }
    }

    companion object {
        const val EXTRA_LOG_ID = "extra_log_id"
    }
}
