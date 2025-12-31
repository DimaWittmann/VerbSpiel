package com.example.verb_spiel

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ListView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class FavoritesWordsFragment : Fragment() {

    private val repo by lazy { WordRepository.getInstance(requireContext()) }
    private lateinit var adapter: StatsWordAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_stats_list, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val list: ListView = view.findViewById(R.id.stats_list)
        val empty: TextView = view.findViewById(R.id.stats_empty)
        list.emptyView = empty

        viewLifecycleOwner.lifecycleScope.launch {
            val words = repo.getAllWords()
                .filter { it.isFavorite }
                .sortedByDescending { it.correctCount - it.failedCount }
            val labelCounts = words.groupingBy { formatWord(it) }.eachCount()

            adapter = StatsWordAdapter(
                requireContext(),
                words.toMutableList(),
                formatter = { word ->
                    val label = formatWord(word)
                    val displayLabel = if ((labelCounts[label] ?: 0) > 1) {
                        "$label (${word.translation})"
                    } else {
                        label
                    }
                    val attempts = word.triesCount
                    val accuracy = if (attempts == 0) 0 else (word.correctCount * 100 / attempts)
                    "$displayLabel • ${getString(R.string.stats_correct, word.correctCount)} • ${accuracy}%"
                },
                onOptionsClick = { word ->
                    WordOptions.show(
                        requireContext(),
                        viewLifecycleOwner.lifecycleScope,
                        repo,
                        adapter,
                        word
                    ) { updated -> updated.isFavorite }
                }
            )
            list.adapter = adapter
        }
    }
}
