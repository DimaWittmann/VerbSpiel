package com.verbspiel

import android.os.Bundle
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.widget.Toast
import android.util.Log
import kotlinx.coroutines.*
import java.io.BufferedReader
import com.verbspiel.game.RoundFilter
import com.verbspiel.game.RoundFilterType
import com.verbspiel.game.RoundManager
import com.verbspiel.game.RoundState
import com.verbspiel.game.RoundUi
import com.verbspiel.controller.ButtonsBarController
import com.verbspiel.controller.RoundCardController
import com.verbspiel.view.ButtonsBarView
import com.verbspiel.view.RoundCardView
import com.verbspiel.view.WordPickersView

class MainActivity : AppCompatActivity() {

    private lateinit var roundCardView: RoundCardView
    private lateinit var roundCardController: RoundCardController
    private lateinit var buttonsBarView: ButtonsBarView
    private lateinit var buttonsBarController: ButtonsBarController
    private lateinit var wordPickersView: WordPickersView

    private var activeFilter: RoundFilter? = null
    private var statusLabel: String = ""
    private var roundSize: Int = DEFAULT_ROUND_SIZE

    private val repo by lazy { WordRepository.getInstance(this) }
    private val activityScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val prefs by lazy { getSharedPreferences(PREFS_NAME, MODE_PRIVATE) }
    private lateinit var roundManager: RoundManager

    private fun applyDifficulty(size: Int) {
        val wasUnset = !prefs.getBoolean(PREFS_KEY_DIFFICULTY_SET, false)
        val changed = roundSize != size
        roundSize = size
        roundCardController.initProgress(roundSize)
        if (changed || wasUnset) {
            fetchPoolAndReset(activeFilter)
        }
        prefs.edit()
            .putInt(PREFS_KEY_DIFFICULTY_SIZE, roundSize)
            .putBoolean(PREFS_KEY_DIFFICULTY_SET, true)
            .apply()
    }

    private fun fetchPoolAndReset(filter: RoundFilter?) {
        roundManager.startRound(filter, roundSize, excludeLearned = filter == null)
    }

    private fun showTopToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun applyFilter(filter: RoundFilter?) {
        activeFilter = filter
        fetchPoolAndReset(filter)
    }

    private fun renderRoundState(state: RoundState) {
        statusLabel = state.statusLabel
        setRoundEnded(state.roundEnded)
        roundCardController.render(state, filterStatusText())
        wordPickersView.updatePickers(
            state.leftItems,
            state.leftDisplayItems,
            state.rightItems,
            state.rightDisplayItems
        )
    }

    private fun setRoundEnded(ended: Boolean) {
        buttonsBarView.setRoundEnded(ended)
    }


    private data class WordFile(val version: Int, val words: List<Word>)

    private fun parseWordFile(): WordFile {
        val records = mutableListOf<Word>()
        var version = 0
        try {
            val inputStream = resources.openRawResource(R.raw.data)
            val reader: BufferedReader = inputStream.bufferedReader()
            val lines = reader.readLines()
            reader.close()

            if (lines.isNotEmpty() && lines[0].startsWith("version:")) {
                version = lines[0].removePrefix("version:").trim().toIntOrNull() ?: 0
            }

            val startIndex = if (lines.firstOrNull()?.startsWith("version:") == true) 2 else 1

            for (line in lines.drop(startIndex)) {
                val tokens = line.split(";")
                if (tokens.size == 4) {
                    val rootData = parseRootField(tokens[1].trim())
                    val record = Word(
                        prefix = tokens[0].trim(),
                        root = rootData.root,
                        isReflexive = rootData.isReflexive,
                        translation = tokens[2].trim(),
                        example = tokens[3].trim()
                    )
                    records.add(record)
                } else {
                    Log.w("CSV", "Skipping line (not enough tokens): $line")
                }
            }
        } catch (e: Exception) {
            Log.e("CSV", "Error reading CSV file", e)
        }
        return WordFile(version, records)
    }

    private data class RootData(val root: String, val isReflexive: Boolean)

    private fun parseRootField(raw: String): RootData {
        val parts = raw.split('|').map { it.trim() }.filter { it.isNotEmpty() }
        val rootPart = parts.firstOrNull().orEmpty()
        val reflexive = parts.any { it.contains("sich") } || rootPart.contains("(sich)")
        val cleanedRoot = rootPart.replace(" (sich)", "").replace("(sich)", "").trim()
        return RootData(cleanedRoot, reflexive)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Link this activity with its layout.
        setContentView(R.layout.activity_main)

        runBlocking {
            val parsed = parseWordFile()
            if (repo.isEmpty()) {
                repo.addWords(parsed.words)
                repo.setWordVersion(parsed.version)
            } else {
                val currentVersion = repo.getWordVersion()
                if (currentVersion != parsed.version) {
                    repo.syncWords(parsed.words)
                    repo.setWordVersion(parsed.version)
                }
            }
        }

        roundSize = prefs.getInt(PREFS_KEY_DIFFICULTY_SIZE, DEFAULT_ROUND_SIZE)

        roundManager = RoundManager(
            repo = repo,
            scope = activityScope,
            ui = object : RoundUi {
                override fun showToast(message: String) = showTopToast(message)
                override fun render(state: RoundState) = renderRoundState(state)
            },
            resources = resources
        )

        roundCardView = RoundCardView(findViewById(android.R.id.content))
        roundCardController = RoundCardController(
            activity = this,
            view = roundCardView,
            repo = repo,
            scope = activityScope,
            strings = { resId -> getString(resId) }
        )
        roundCardController.bind()
        roundCardController.initProgress(roundSize)

        buttonsBarView = ButtonsBarView(findViewById(R.id.buttons_row), resources)
        buttonsBarController = ButtonsBarController(
            activity = this,
            view = buttonsBarView,
            roundManager = roundManager,
            repo = repo,
            scope = activityScope,
            onOpenStats = { startActivity(Intent(this, StatsActivity::class.java)) },
            onApplyFilter = { applyFilter(it) },
            onDifficultySelected = { applyDifficulty(it) },
            onResetRound = { fetchPoolAndReset(activeFilter) },
            onShowToast = { showTopToast(it) },
            getRoundSize = { roundSize },
            getLeftIndex = { wordPickersView.leftIndex() },
            getRightIndex = { wordPickersView.rightIndex() }
        )
        buttonsBarView.setupScroll()
        buttonsBarController.bind()

        wordPickersView = WordPickersView(findViewById(R.id.pickers_row), resources)

        if (!prefs.getBoolean(PREFS_KEY_DIFFICULTY_SET, false)) {
            buttonsBarController.showDifficultyChooser(force = true)
        } else {
            fetchPoolAndReset(activeFilter)
        }
    }

    companion object {
        private const val PREFS_NAME = "verbspiel_prefs"
        private const val PREFS_KEY_DIFFICULTY_SIZE = "difficulty_size"
        private const val PREFS_KEY_DIFFICULTY_SET = "difficulty_set"
        private const val DEFAULT_ROUND_SIZE = 5
    }

    override fun onDestroy() {
        super.onDestroy()
        activityScope.cancel()
    }

    private fun filterStatusText(): String {
        val modeText = when (val f = activeFilter) {
            null -> getString(R.string.filter_mode_mixed)
            else -> when (f.type) {
                RoundFilterType.PREFIX -> {
                    val label = if (f.value.isBlank()) getString(R.string.no_prefix) else f.value
                    getString(R.string.filter_mode_prefix, label)
                }
                RoundFilterType.ROOT -> getString(R.string.filter_mode_root, f.value)
                RoundFilterType.FAVORITES -> getString(R.string.filter_mode_favorites)
            }
        }
        return if (statusLabel.isBlank()) {
            modeText
        } else {
            getString(R.string.status_with_mode, statusLabel, modeText)
        }
    }
}
