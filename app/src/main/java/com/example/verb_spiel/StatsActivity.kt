package com.example.verb_spiel

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class StatsActivity : AppCompatActivity() {

    private val repo by lazy { WordRepository.getInstance(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_stats)

        val summaryText: TextView = findViewById(R.id.stats_summary)
        val listView: ListView = findViewById(R.id.stats_list)
        val emptyView: TextView = findViewById(R.id.stats_empty)
        listView.emptyView = emptyView

        lifecycleScope.launch {
            val words = repo.getAllWords()
            val totalShown = words.sumOf { it.timesShown }
            val totalCorrect = words.sumOf { it.correctCount }
            val totalFailed = words.sumOf { it.failedCount }
            val totalAttempts = words.sumOf { it.triesCount }
            val accuracy = if (totalAttempts == 0) 0 else (totalCorrect * 100 / totalAttempts)

            summaryText.text = getString(
                R.string.stats_summary,
                totalShown,
                totalCorrect,
                totalFailed,
                accuracy
            )

            val items = words
                .sortedByDescending { it.timesShown }
                .map { word ->
                    val wordAccuracy =
                        if (word.triesCount == 0) 0 else (word.correctCount * 100 / word.triesCount)
                    "${word.prefix}${word.root} | shown ${word.timesShown} | correct ${word.correctCount} | wrong ${word.failedCount} | ${wordAccuracy}%"
                }

            val adapter = ArrayAdapter(
                this@StatsActivity,
                android.R.layout.simple_list_item_1,
                items
            )
            listView.adapter = adapter
        }
    }
}
