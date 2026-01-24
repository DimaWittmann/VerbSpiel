package com.verbspiel

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ListView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class TopWordsFragment : Fragment() {

    private val repo by lazy { WordRepository.getInstance(requireContext()) }
    private var currentSort: SortState = SortState.Default
    private lateinit var adapter: PrefixStatsAdapter
    private var allStats: List<PrefixStat> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_top_words, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val list: ListView = view.findViewById(R.id.prefix_stats_list)
        val empty: TextView = view.findViewById(R.id.prefix_stats_empty)
        list.emptyView = empty
        wireHeaderSorting(view)

        viewLifecycleOwner.lifecycleScope.launch {
            val words = repo.getAllWords()
            val prefixStats = words
                .groupBy { it.prefix }
                .map { (prefix, group) ->
                    val correct = group.count { it.lastCorrectAt > 0 && it.lastCorrectAt > it.lastFailedAt }
                    val failed = group.count { it.lastFailedAt > 0 && it.lastFailedAt > it.lastCorrectAt }
                    val total = group.size
                    val progress = if (total == 0) 0 else (correct * 100 / total)
                    PrefixStat(prefix, correct, failed, total, progress)
                }
                .filter { it.correct > 0 || it.failed > 0 }
                .sortedWith(compareBy<PrefixStat> { it.prefix }.thenBy { it.total })

            allStats = prefixStats
            adapter = PrefixStatsAdapter(
                requireContext(),
                prefixStats.toMutableList(),
                emptyPrefixLabel = getString(R.string.no_prefix)
            )
            list.adapter = adapter
        }
    }

    private data class PrefixStat(
        val prefix: String,
        val correct: Int,
        val failed: Int,
        val total: Int,
        val progress: Int
    )

    private enum class SortColumn {
        Prefix,
        Correct,
        Failed,
        Total,
        Progress
    }

    private sealed class SortState {
        data object Default : SortState()
        data class By(val column: SortColumn, val direction: SortDirection) : SortState()
    }

    private enum class SortDirection {
        Desc,
        Asc
    }

    private fun wireHeaderSorting(root: View) {
        val prefixHeader: TextView = root.findViewById(R.id.prefix_header_label)
        val correctHeader: TextView = root.findViewById(R.id.prefix_header_correct)
        val failedHeader: TextView = root.findViewById(R.id.prefix_header_failed)
        val totalHeader: TextView = root.findViewById(R.id.prefix_header_all)
        val progressHeader: TextView = root.findViewById(R.id.prefix_header_progress)

        prefixHeader.setOnClickListener { onHeaderClicked(SortColumn.Prefix) }
        correctHeader.setOnClickListener { onHeaderClicked(SortColumn.Correct) }
        failedHeader.setOnClickListener { onHeaderClicked(SortColumn.Failed) }
        totalHeader.setOnClickListener { onHeaderClicked(SortColumn.Total) }
        progressHeader.setOnClickListener { onHeaderClicked(SortColumn.Progress) }
    }

    private fun onHeaderClicked(column: SortColumn) {
        currentSort = when (val state = currentSort) {
            is SortState.By -> {
                if (state.column == column) {
                    if (column == SortColumn.Prefix) {
                        when (state.direction) {
                            SortDirection.Asc -> SortState.By(column, SortDirection.Desc)
                            SortDirection.Desc -> SortState.By(column, SortDirection.Asc)
                        }
                    } else {
                        when (state.direction) {
                            SortDirection.Desc -> SortState.By(column, SortDirection.Asc)
                            SortDirection.Asc -> SortState.Default
                        }
                    }
                } else {
                    if (column == SortColumn.Prefix) {
                        SortState.By(column, SortDirection.Asc)
                    } else {
                        SortState.By(column, SortDirection.Desc)
                    }
                }
            }
            SortState.Default -> {
                if (column == SortColumn.Prefix) {
                    SortState.By(column, SortDirection.Asc)
                } else {
                    SortState.By(column, SortDirection.Desc)
                }
            }
        }
        applySorting()
    }

    private fun applySorting() {
        if (!::adapter.isInitialized) return
        val sorted = when (val state = currentSort) {
            SortState.Default -> allStats.sortedWith(compareBy<PrefixStat> { it.prefix }.thenBy { it.total })
            is SortState.By -> when (state.column) {
                SortColumn.Prefix -> {
                    when (state.direction) {
                        SortDirection.Asc -> allStats.sortedBy { it.prefix }
                        SortDirection.Desc -> allStats.sortedByDescending { it.prefix }
                    }
                }
                SortColumn.Correct -> {
                    when (state.direction) {
                        SortDirection.Desc -> allStats.sortedWith(
                            compareByDescending<PrefixStat> { it.correct }
                                .thenBy { it.prefix }
                        )
                        SortDirection.Asc -> allStats.sortedWith(
                            compareBy<PrefixStat> { it.correct }
                                .thenBy { it.prefix }
                        )
                    }
                }
                SortColumn.Failed -> {
                    when (state.direction) {
                        SortDirection.Desc -> allStats.sortedWith(
                            compareByDescending<PrefixStat> { it.failed }
                                .thenBy { it.prefix }
                        )
                        SortDirection.Asc -> allStats.sortedWith(
                            compareBy<PrefixStat> { it.failed }
                                .thenBy { it.prefix }
                        )
                    }
                }
                SortColumn.Total -> {
                    when (state.direction) {
                        SortDirection.Desc -> allStats.sortedWith(
                            compareByDescending<PrefixStat> { it.total }
                                .thenBy { it.prefix }
                        )
                        SortDirection.Asc -> allStats.sortedWith(
                            compareBy<PrefixStat> { it.total }
                                .thenBy { it.prefix }
                        )
                    }
                }
                SortColumn.Progress -> {
                    when (state.direction) {
                        SortDirection.Desc -> allStats.sortedWith(
                            compareByDescending<PrefixStat> { it.progress }
                                .thenBy { it.prefix }
                        )
                        SortDirection.Asc -> allStats.sortedWith(
                            compareBy<PrefixStat> { it.progress }
                                .thenBy { it.prefix }
                        )
                    }
                }
            }
        }
        adapter.replaceAll(sorted)
    }

    private class PrefixStatsAdapter(
        private val context: Context,
        private val items: MutableList<PrefixStat>,
        private val emptyPrefixLabel: String
    ) : BaseAdapter() {

        override fun getCount(): Int = items.size

        override fun getItem(position: Int): PrefixStat = items[position]

        override fun getItemId(position: Int): Long = position.toLong()

        fun replaceAll(updated: List<PrefixStat>) {
            items.clear()
            items.addAll(updated)
            notifyDataSetChanged()
        }

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val row = convertView ?: LayoutInflater.from(context)
                .inflate(R.layout.item_prefix_stats_row, parent, false)

            val label: TextView = row.findViewById(R.id.prefix_row_label)
            val correct: TextView = row.findViewById(R.id.prefix_row_correct)
            val failed: TextView = row.findViewById(R.id.prefix_row_failed)
            val total: TextView = row.findViewById(R.id.prefix_row_all)
            val progress: TextView = row.findViewById(R.id.prefix_row_progress)

            val item = getItem(position)
            val displayPrefix = if (item.prefix.isBlank()) emptyPrefixLabel else item.prefix

            label.text = displayPrefix
            correct.text = item.correct.toString()
            failed.text = item.failed.toString()
            total.text = item.total.toString()
            progress.text = context.getString(R.string.stats_percent, item.progress)

            return row
        }
    }
}
