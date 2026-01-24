package com.verbspiel

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ListView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RecentCombinedStatsFragment : Fragment() {

    private val repo by lazy { WordRepository.getInstance(requireContext()) }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_stats_recent_combined, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val failuresList: ListView = view.findViewById(R.id.stats_list_failures)
        val failuresEmpty: TextView = view.findViewById(R.id.stats_empty_failures)
        failuresList.emptyView = failuresEmpty

        val correctList: ListView = view.findViewById(R.id.stats_list_correct)
        val correctEmpty: TextView = view.findViewById(R.id.stats_empty_correct)
        correctList.emptyView = correctEmpty

        val dateFormat = SimpleDateFormat("dd MMM yy", Locale.getDefault())

        viewLifecycleOwner.lifecycleScope.launch {
            val failures = repo.getRecentFailures(50).toMutableList()
            val correct = repo.getRecentCorrect(50).toMutableList()

            failuresList.adapter = buildAdapter(
                words = failures,
                isCorrect = false,
                dateFormat = dateFormat
            )

            correctList.adapter = buildAdapter(
                words = correct,
                isCorrect = true,
                dateFormat = dateFormat
            )
        }
    }

    private fun buildAdapter(
        words: MutableList<Word>,
        isCorrect: Boolean,
        dateFormat: SimpleDateFormat
    ): StatsWordAdapter {
        val labelCounts = words.groupingBy { formatWord(it) }.eachCount()
        lateinit var adapter: StatsWordAdapter
        adapter = StatsWordAdapter(
            requireContext(),
            words,
            formatter = { word ->
                val label = formatWord(word)
                val displayLabel = if ((labelCounts[label] ?: 0) > 1) {
                    "$label (${word.translation})"
                } else {
                    label
                }
                val stamp = if (isCorrect) word.lastCorrectAt else word.lastFailedAt
                val formattedDate = if (stamp > 0) dateFormat.format(Date(stamp)) else ""
                val attempts = if (isCorrect) word.correctCount else word.failedCount
                "$displayLabel • ${getString(R.string.stats_attempts, attempts)} • $formattedDate"
            },
            onOptionsClick = { word ->
                WordOptions.show(
                    requireContext(),
                    viewLifecycleOwner.lifecycleScope,
                    repo,
                    adapter,
                    word
                )
            }
        )
        return adapter
    }
}
