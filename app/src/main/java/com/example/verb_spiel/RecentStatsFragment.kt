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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RecentStatsFragment : Fragment() {

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

        val type = arguments?.getString(ARG_TYPE) ?: TYPE_FAILURES
        val dateFormat = SimpleDateFormat("dd MMM yy", Locale.getDefault())

        viewLifecycleOwner.lifecycleScope.launch {
            val words = when (type) {
                TYPE_CORRECT -> repo.getRecentCorrect(50)
                else -> repo.getRecentFailures(50)
            }.toMutableList()

            adapter = StatsWordAdapter(
                requireContext(),
                words,
                formatter = { word ->
                    val stamp = when (type) {
                        TYPE_CORRECT -> word.lastCorrectAt
                        else -> word.lastFailedAt
                    }
                    val formattedDate = if (stamp > 0) dateFormat.format(Date(stamp)) else ""
                    val attempts = if (type == TYPE_CORRECT) word.correctCount else word.failedCount
                    "${formatWord(word)} • ${getString(R.string.stats_attempts, attempts)} • $formattedDate"
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
            list.adapter = adapter
        }
    }

    companion object {
        const val TYPE_FAILURES = "failures"
        const val TYPE_CORRECT = "correct"
        private const val ARG_TYPE = "type"

        fun newInstance(type: String): RecentStatsFragment {
            val fragment = RecentStatsFragment()
            fragment.arguments = Bundle().apply { putString(ARG_TYPE, type) }
            return fragment
        }
    }
}
