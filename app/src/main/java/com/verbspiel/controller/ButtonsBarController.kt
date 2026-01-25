package com.verbspiel.controller

import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.text.Editable
import android.text.TextWatcher
import com.verbspiel.R
import com.verbspiel.WordRepository
import com.verbspiel.game.RoundFilter
import com.verbspiel.game.RoundFilterType
import com.verbspiel.game.RoundManager
import com.verbspiel.view.ButtonsBarView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class ButtonsBarController(
    private val activity: AppCompatActivity,
    private val view: ButtonsBarView,
    private val roundManager: RoundManager,
    private val repo: WordRepository,
    private val scope: CoroutineScope,
    private val onOpenStats: () -> Unit,
    private val onApplyFilter: (RoundFilter?) -> Unit,
    private val onDifficultySelected: (Int) -> Unit,
    private val onResetRound: () -> Unit,
    private val onShowToast: (String) -> Unit,
    private val getRoundSize: () -> Int,
    private val getLeftIndex: () -> Int,
    private val getRightIndex: () -> Int
) {
    private enum class Difficulty(val labelRes: Int, val size: Int) {
        EASY(R.string.difficulty_easy, 3),
        MEDIUM(R.string.difficulty_medium, 5),
        HARD(R.string.difficulty_hard, 10)
    }

    fun bind() {
        view.setOnCombine {
            roundManager.handleCombine(getLeftIndex(), getRightIndex())
        }
        view.setOnSkip { roundManager.handleSkip() }
        view.setOnStats { onOpenStats() }
        view.setOnFilter { showFilterChooser() }
        view.setOnDifficulty { showDifficultyChooser(force = false) }
        view.setOnResetPrimary { onResetRound() }
        view.setOnReset { onResetRound() }
    }

    fun showDifficultyChooser(force: Boolean) {
        val difficulties = Difficulty.values()
        val labels = difficulties.map { getDifficultyLabel(it) }.toTypedArray()
        val currentIndex = difficulties.indexOfFirst { it.size == getRoundSize() }.let {
            if (it == -1) 0 else it
        }

        var selectedIndex = currentIndex

        val dialog = AlertDialog.Builder(activity)
            .setTitle(R.string.difficulty_title)
            .setSingleChoiceItems(labels, currentIndex) { _, which ->
                selectedIndex = which
            }
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val chosen = difficulties[selectedIndex]
                onDifficultySelected(chosen.size)
            }
            .apply {
                if (!force) {
                    setNegativeButton(android.R.string.cancel, null)
                }
            }
            .create()

        dialog.setCancelable(!force)
        dialog.setCanceledOnTouchOutside(!force)
        dialog.show()
    }

    private fun getDifficultyLabel(difficulty: Difficulty): String {
        return activity.getString(difficulty.labelRes, difficulty.size)
    }

    private fun showFilterChooser() {
        val options = arrayOf(
            activity.getString(R.string.filter_type_prefix),
            activity.getString(R.string.filter_type_root),
            activity.getString(R.string.filter_type_favorites),
            activity.getString(R.string.filter_type_clear)
        )
        AlertDialog.Builder(activity)
            .setTitle(activity.getString(R.string.filter_choose_type))
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showValueChooser(RoundFilterType.PREFIX)
                    1 -> showValueChooser(RoundFilterType.ROOT)
                    2 -> onApplyFilter(RoundFilter(RoundFilterType.FAVORITES, ""))
                    else -> onApplyFilter(null)
                }
            }
            .show()
    }

    private fun showValueChooser(type: RoundFilterType) {
        when (type) {
            RoundFilterType.FAVORITES -> onApplyFilter(RoundFilter(RoundFilterType.FAVORITES, ""))
            RoundFilterType.PREFIX -> showPrefixChooser()
            RoundFilterType.ROOT -> showRootChooser()
        }
    }

    private fun showPrefixChooser() {
        scope.launch {
            val values = repo.getAllPrefixes()
            if (values.isEmpty()) {
                onShowToast(activity.getString(R.string.filter_no_matches))
                return@launch
            }
            val prefixCounts = repo.getAllWords().groupingBy { it.prefix }.eachCount()
            val displayValues = values.map { prefix ->
                val label = if (prefix.isBlank()) activity.getString(R.string.no_prefix) else prefix
                val count = prefixCounts[prefix] ?: 0
                "$label (${activity.getString(R.string.count_verbs, count)})"
            }
            showValueChooserWithFilter(
                title = activity.getString(R.string.filter_choose_value),
                values = values,
                displayValues = displayValues
            ) { chosen ->
                onApplyFilter(RoundFilter(RoundFilterType.PREFIX, chosen))
            }
        }
    }

    private fun showRootChooser() {
        scope.launch {
            val values = repo.getAllRoots()
            if (values.isEmpty()) {
                onShowToast(activity.getString(R.string.filter_no_matches))
                return@launch
            }
            val rootCounts = repo.getAllWords().groupingBy { it.root }.eachCount()
            val displayValues = values.map { root ->
                val count = rootCounts[root] ?: 0
                "$root (${activity.getString(R.string.count_verbs, count)})"
            }
            showValueChooserWithFilter(
                title = activity.getString(R.string.filter_choose_value),
                values = values,
                displayValues = displayValues
            ) { chosen ->
                onApplyFilter(RoundFilter(RoundFilterType.ROOT, chosen))
            }
        }
    }

    private fun showValueChooserWithFilter(
        title: String,
        values: List<String>,
        displayValues: List<String>,
        onSelect: (String) -> Unit
    ) {
        val input = EditText(activity)
        input.hint = activity.getString(R.string.filter_type_hint)
        val listView = ListView(activity)
        val adapter = ArrayAdapter(activity, android.R.layout.simple_list_item_1, displayValues.toMutableList())
        listView.adapter = adapter

        val container = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (activity.resources.displayMetrics.density * 16).toInt()
            setPadding(pad, pad, pad, pad)
            addView(input)
            addView(listView)
        }

        val dialog = AlertDialog.Builder(activity)
            .setTitle(title)
            .setView(container)
            .create()

        listView.setOnItemClickListener { _, _, position, _ ->
            val label = adapter.getItem(position) ?: return@setOnItemClickListener
            val index = displayValues.indexOf(label)
            if (index >= 0) {
                onSelect(values[index])
            }
            dialog.dismiss()
        }

        input.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun afterTextChanged(s: Editable?) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s?.toString().orEmpty().trim()
                val filtered = if (query.isEmpty()) {
                    displayValues
                } else {
                    val lower = query.lowercase()
                    displayValues.filter { it.lowercase().startsWith(lower) }
                }
                adapter.clear()
                adapter.addAll(filtered)
                adapter.notifyDataSetChanged()
            }
        })

        dialog.show()
    }
}
