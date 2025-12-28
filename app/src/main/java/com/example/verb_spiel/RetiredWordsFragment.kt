package com.example.verb_spiel

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class RetiredWordsFragment : Fragment() {

    private val repo by lazy { WordRepository.getInstance(requireContext()) }

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
                .filter { word -> word.isLearned || isRetiredWord(word) }
                .sortedByDescending { it.correctCount - it.failedCount }

            val items = words.map { word ->
                val delta = word.correctCount - word.failedCount
                "${formatWord(word)} • +$delta • ${getString(R.string.stats_correct, word.correctCount)}"
            }

            list.adapter = ArrayAdapter(
                requireContext(),
                android.R.layout.simple_list_item_1,
                items
            )
        }
    }

}
