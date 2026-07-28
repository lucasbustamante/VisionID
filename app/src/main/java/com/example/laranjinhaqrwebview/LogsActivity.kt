package com.example.laranjinhaqrwebview

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.laranjinhaqrwebview.databinding.ActivityLogsBinding

class LogsActivity : AppCompatActivity() {
    private lateinit var binding: ActivityLogsBinding
    private var rows: List<Row> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLogsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.backButton.setOnClickListener { finish() }
        binding.clearLogsButton.setOnClickListener { confirmClear() }
    }

    override fun onResume() {
        super.onResume()
        loadLogs()
    }

    private fun loadLogs() {
        val entries = AppLog.readAll(this)
        val result = mutableListOf<Row>()
        var lastHour: String? = null

        entries.forEach { entry ->
            val hour = AppLog.formatHour(entry.timestamp)
            if (hour != lastHour) {
                result += Row.Header(hour)
                lastHour = hour
            }
            result += Row.Entry(entry)
        }
        rows = result

        binding.emptyText.visibility = if (entries.isEmpty()) View.VISIBLE else View.GONE
        binding.logsList.visibility = if (entries.isEmpty()) View.GONE else View.VISIBLE
        binding.logCountText.text = "${entries.size} registro(s)"

        binding.logsList.adapter = ArrayAdapter(
            this,
            R.layout.item_log_row,
            android.R.id.text1,
            rows.map { row ->
                when (row) {
                    is Row.Header -> "▾ ${row.title}"
                    is Row.Entry -> "${AppLog.formatDateTime(row.value.timestamp).substringAfter(' ')}  [${row.value.level}] ${row.value.category} · ${row.value.message}"
                }
            }
        )

        binding.logsList.setOnItemClickListener { _, _, position, _ ->
            val row = rows[position]
            if (row is Row.Entry) {
                startActivity(Intent(this, LogDetailActivity::class.java).putExtra(LogDetailActivity.EXTRA_LOG_ID, row.value.id))
            }
        }
    }

    private fun confirmClear() {
        AlertDialog.Builder(this)
            .setTitle("Limpar logs")
            .setMessage("Deseja apagar todos os registros locais?")
            .setNegativeButton("Não", null)
            .setPositiveButton("Sim") { _, _ ->
                AppLog.clear(this)
                loadLogs()
            }
            .show()
    }

    private sealed class Row {
        data class Header(val title: String) : Row()
        data class Entry(val value: AppLogEntry) : Row()
    }
}
