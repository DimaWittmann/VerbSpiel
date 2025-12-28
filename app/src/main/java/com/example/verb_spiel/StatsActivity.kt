package com.example.verb_spiel

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import kotlinx.coroutines.launch

class StatsActivity : AppCompatActivity() {

    private val repo by lazy { WordRepository.getInstance(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_stats)

        val summaryText: TextView = findViewById(R.id.stats_summary)
        val tabLayout: TabLayout = findViewById(R.id.stats_tabs)
        val viewPager: ViewPager2 = findViewById(R.id.stats_pager)

        viewPager.adapter = StatsPagerAdapter(this)
        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> getString(R.string.tab_recent_failures)
                1 -> getString(R.string.tab_recent_correct)
                2 -> getString(R.string.tab_top_words)
                else -> getString(R.string.tab_retired_words)
            }
        }.attach()

        lifecycleScope.launch {
            val wordsWithStats =
                repo.getAllWords().filter { it.timesShown > 0 || it.triesCount > 0 }
            if (wordsWithStats.isEmpty()) {
                summaryText.text = getString(R.string.stats_empty_state)
                return@launch
            }
            val totalShown = wordsWithStats.sumOf { it.timesShown }
            val totalCorrect = wordsWithStats.sumOf { it.correctCount }
            val totalFailed = wordsWithStats.sumOf { it.failedCount }
            val totalAttempts = wordsWithStats.sumOf { it.triesCount }
            val accuracy = if (totalAttempts == 0) 0 else (totalCorrect * 100 / totalAttempts)

            summaryText.text = getString(
                R.string.stats_summary,
                totalShown,
                totalCorrect,
                totalFailed,
                accuracy
            )
        }
    }

}
