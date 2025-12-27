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

class TopWordsFragment : Fragment() {

    private val repo by lazy { WordRepository.getInstance(requireContext()) }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_top_words, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val correctList: ListView = view.findViewById(R.id.top_correct_list)
        val failedList: ListView = view.findViewById(R.id.top_failed_list)
        val correctEmpty: TextView = view.findViewById(R.id.top_correct_empty)
        val failedEmpty: TextView = view.findViewById(R.id.top_failed_empty)
        correctList.emptyView = correctEmpty
        failedList.emptyView = failedEmpty

        viewLifecycleOwner.lifecycleScope.launch {
            val allShown = repo.getAllWords().filter { it.timesShown > 0 || it.triesCount > 0 }
            val topCorrect = allShown.sortedWith(
                compareByDescending<Word> { it.correctCount }
                    .thenByDescending {
                        if (it.triesCount == 0) 0 else (it.correctCount * 100 / it.triesCount)
                    }
                    .thenByDescending { it.triesCount }
            )
            val topFailed = allShown.sortedWith(
                compareByDescending<Word> { it.failedCount }
                    .thenByDescending {
                        if (it.triesCount == 0) 0 else (it.failedCount * 100 / it.triesCount)
                    }
                    .thenByDescending { it.triesCount }
            )

            val correctItems = topCorrect.map { word ->
                val accuracy =
                    if (word.triesCount == 0) 0 else (word.correctCount * 100 / word.triesCount)
                "${formatWord(word)} • ${getString(R.string.stats_correct, word.correctCount)} • ${accuracy}%"
            }

            val failedItems = topFailed.map { word ->
                val accuracy =
                    if (word.triesCount == 0) 0 else (word.correctCount * 100 / word.triesCount)
                "${formatWord(word)} • ${getString(R.string.stats_failed, word.failedCount)} • ${accuracy}%"
            }

            correctList.adapter = ArrayAdapter(
                requireContext(),
                android.R.layout.simple_list_item_1,
                correctItems
            )
            failedList.adapter = ArrayAdapter(
                requireContext(),
                android.R.layout.simple_list_item_1,
                failedItems
            )
        }
    }
}
