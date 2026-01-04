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
                3 -> getString(R.string.tab_retired_words)
                else -> getString(R.string.tab_favorites)
            }
        }.attach()

        lifecycleScope.launch {
            val allWords = repo.getAllWords()
            val totalWords = allWords.size
            val correctWords = allWords.count { it.correctCount > 0 }
            val failedWords = allWords.count { it.timesShown > 0 && it.correctCount == 0 }

            summaryText.text = getString(
                R.string.stats_summary,
                totalWords,
                formatCountWithPercent(correctWords, totalWords),
                formatCountWithPercent(failedWords, totalWords)
            )
        }
    }

    private fun formatCountWithPercent(count: Int, total: Int): String {
        val percent = if (total == 0) 0 else (count * 100 / total)
        return getString(R.string.stats_count_with_percent, count, percent)
    }

}
