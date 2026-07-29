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
        val printableContent = buildString {
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
        binding.logDetails.text = printableContent
        binding.printLogButton.setOnClickListener {
            binding.printLogButton.isEnabled = false
            val originalText = binding.printLogButton.text
            binding.printLogButton.text = "Imprimindo..."
            LogPrinter.printAsync(this, "VisionID - Detalhe do log", printableContent) { result ->
                binding.printLogButton.isEnabled = true
                binding.printLogButton.text = originalText
                android.widget.Toast.makeText(this, result.message, android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }

    companion object {
        const val EXTRA_LOG_ID = "extra_log_id"
    }
}
