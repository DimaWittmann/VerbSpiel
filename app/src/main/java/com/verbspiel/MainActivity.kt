package com.verbspiel

import android.os.Bundle
import android.content.Intent
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import android.widget.ProgressBar
import android.widget.Toast
import android.widget.ImageView
import android.widget.ImageButton
import android.os.Build
import android.util.TypedValue
import android.util.Log
import android.widget.NumberPicker
import android.graphics.drawable.ColorDrawable
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import java.io.BufferedReader
import android.net.Uri
import androidx.appcompat.widget.TooltipCompat
import android.content.ActivityNotFoundException
import com.verbspiel.game.RoundFilter
import com.verbspiel.game.RoundFilterType
import com.verbspiel.game.RoundManager
import com.verbspiel.game.RoundState
import com.verbspiel.game.RoundUi
import com.verbspiel.controller.ButtonsBarController
import com.verbspiel.view.ButtonsBarView

class MainActivity : AppCompatActivity() {

    private lateinit var nextWordText: TextView
    private lateinit var translationText: TextView
    private lateinit var exampleText: TextView
    private lateinit var buttonLastFavorite: ImageButton
    private lateinit var buttonLastLearned: ImageButton
    private lateinit var buttonLastWiki: ImageButton
    private lateinit var buttonTranslateNext: ImageButton

    private lateinit var listLeft: NumberPicker
    private lateinit var listRight: NumberPicker
    private lateinit var filterStatus: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var buttonsBarView: ButtonsBarView
    private lateinit var buttonsBarController: ButtonsBarController

    private var activeFilter: RoundFilter? = null
    private var lastResultWord: Word? = null
    private var currentNextWord: Word? = null
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
        progressBar.max = roundSize
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
        updateFilterStatus()
        fetchPoolAndReset(filter)
    }

    private fun updateFilterStatus() {
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
        val text = if (statusLabel.isBlank()) {
            modeText
        } else {
            getString(R.string.status_with_mode, statusLabel, modeText)
        }
        setTextWithTooltip(filterStatus, text)
    }

    private fun setStatus(label: String, colorRes: Int? = null) {
        statusLabel = label
        if (colorRes != null) {
            filterStatus.setTextColor(ContextCompat.getColor(this, colorRes))
        } else {
            filterStatus.setTextColor(ContextCompat.getColor(this, R.color.black))
        }
        updateFilterStatus()
    }

    private fun renderRoundState(state: RoundState) {
        setNextWord(state.nextWord)
        setLastWord(state.lastWord)
        setStatus(state.statusLabel, state.statusColorRes)
        setTextWithTooltip(translationText, state.translation)
        setTextWithTooltip(exampleText, state.example)
        setRoundEnded(state.roundEnded)
        progressBar.max = state.progressMax
        progressBar.setProgress(state.progressValue, true)
        createNumberPicker(listLeft, state.leftItems, state.leftDisplayItems)
        createNumberPicker(listRight, state.rightItems, state.rightDisplayItems)
    }

    private fun setNextWord(word: Word?) {
        currentNextWord = word
        setTextWithTooltip(nextWordText, word?.translation.orEmpty())
    }

    private fun setTextWithTooltip(view: TextView, text: String) {
        view.text = text
        TooltipCompat.setTooltipText(view, text)
    }

    private fun setLastWord(word: Word?) {
        lastResultWord = word
        buttonLastFavorite.isEnabled = true
        buttonLastLearned.isEnabled = true
        buttonLastWiki.isEnabled = true
        val favoriteIcon = if (word?.isFavorite == true) {
            R.drawable.ic_favorite
        } else {
            R.drawable.ic_favorite_border
        }
        val learnedIcon = if (word?.isLearned == true) {
            R.drawable.ic_learned_on
        } else {
            R.drawable.ic_learned_off
        }
        buttonLastFavorite.setImageResource(favoriteIcon)
        buttonLastLearned.setImageResource(learnedIcon)
        val favoriteHint = if (word == null) {
            getString(R.string.add_to_favorites)
        } else if (word.isFavorite) {
            getString(R.string.remove_from_favorites)
        } else {
            getString(R.string.add_to_favorites)
        }
        val learnedHint = if (word == null) {
            getString(R.string.add_to_learned)
        } else if (word.isLearned) {
            getString(R.string.remove_from_learned)
        } else {
            getString(R.string.add_to_learned)
        }
        TooltipCompat.setTooltipText(buttonLastFavorite, favoriteHint)
        TooltipCompat.setTooltipText(buttonLastLearned, learnedHint)
        buttonLastFavorite.contentDescription = favoriteHint
        buttonLastLearned.contentDescription = learnedHint
    }

    private fun openGermanWordInfo(word: Word) {
        val url = "https://de.wiktionary.org/wiki/" + Uri.encode(word.prefix + word.root)
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        startActivity(intent)
    }

    private fun toEnglishLookup(translation: String): String {
        val first = translation.split(",").firstOrNull().orEmpty().trim()
        val withoutTo = if (first.startsWith("to ")) first.removePrefix("to ").trim() else first
        val cleaned = withoutTo.replace(Regex("\\s*\\([^)]*\\)"), "").trim()
        return cleaned.replace(' ', '_')
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

        // Find views by their IDs.
        nextWordText = findViewById(R.id.next_word_text)
        translationText = findViewById(R.id.translation_text)
        exampleText = findViewById(R.id.example_text)
        buttonLastFavorite = findViewById(R.id.button_last_favorite)
        buttonLastLearned = findViewById(R.id.button_last_learned)
        buttonLastWiki = findViewById(R.id.button_last_wiki)
        buttonTranslateNext = findViewById(R.id.button_translate_next)
        TooltipCompat.setTooltipText(buttonLastWiki, getString(R.string.last_word_wiki))
        listLeft = findViewById(R.id.list_left)
        listRight = findViewById(R.id.list_right)
        filterStatus = findViewById(R.id.filter_status)
        progressBar = findViewById(R.id.progress_bar)
        val arrowPrefixUp: ImageView = findViewById(R.id.arrow_prefix_up)
        val arrowPrefixDown: ImageView = findViewById(R.id.arrow_prefix_down)
        val arrowRootUp: ImageView = findViewById(R.id.arrow_root_up)
        val arrowRootDown: ImageView = findViewById(R.id.arrow_root_down)

        roundSize = prefs.getInt(PREFS_KEY_DIFFICULTY_SIZE, DEFAULT_ROUND_SIZE)
        progressBar.max = roundSize
        progressBar.min = 0
        progressBar.setProgress(0)

        roundManager = RoundManager(
            repo = repo,
            scope = activityScope,
            ui = object : RoundUi {
                override fun showToast(message: String) = showTopToast(message)
                override fun render(state: RoundState) = renderRoundState(state)
            },
            resources = resources
        )

        updateFilterStatus()

        nextWordText.setOnClickListener { openTranslatorForCurrentWord() }
        buttonTranslateNext.setOnClickListener { openTranslatorForCurrentWord() }

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
            getLeftIndex = { listLeft.value },
            getRightIndex = { listRight.value }
        )
        buttonsBarView.setupScroll()
        buttonsBarController.bind()

        setLastWord(null)
        buttonLastFavorite.setOnClickListener {
            val word = lastResultWord ?: return@setOnClickListener
            val updated = word.copy(isFavorite = !word.isFavorite)
            activityScope.launch {
                repo.updateWordStats(updated)
            }
            setLastWord(updated)
        }
        buttonLastLearned.setOnClickListener {
            val word = lastResultWord ?: return@setOnClickListener
            val updated = word.copy(isLearned = !word.isLearned)
            activityScope.launch {
                repo.updateWordStats(updated)
            }
            setLastWord(updated)
        }
        buttonLastWiki.setOnClickListener {
            val word = lastResultWord ?: return@setOnClickListener
            openGermanWordInfo(word)
        }


        arrowPrefixUp.setOnClickListener { movePicker(listLeft, -1) }
        arrowPrefixDown.setOnClickListener { movePicker(listLeft, 1) }
        arrowRootUp.setOnClickListener { movePicker(listRight, -1) }
        arrowRootDown.setOnClickListener { movePicker(listRight, 1) }

        if (!prefs.getBoolean(PREFS_KEY_DIFFICULTY_SET, false)) {
            buttonsBarController.showDifficultyChooser(force = true)
        } else {
            fetchPoolAndReset(activeFilter)
        }
    }

    private fun openTranslatorForCurrentWord() {
        val word = currentNextWord ?: return
        val text = word.translation
        val url = "https://translate.google.com/?sl=en&tl=de&text=" +
            Uri.encode(text) + "&op=translate"
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            setPackage("com.google.android.apps.translate")
        }
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            val webIntent = Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://www.google.com/search?q=" + Uri.encode(text))
            )
            startActivity(webIntent)
        }
    }

    private fun createNumberPicker(
        np: NumberPicker,
        items: Array<String>,
        displayItems: Array<String>
    ) {
        np.displayedValues = null
        if (items.isEmpty()) {
            np.minValue = 0
            np.maxValue = 0
            np.wrapSelectorWheel = false
            return
        }
        np.minValue = 0
        np.maxValue = items.size - 1
        np.wrapSelectorWheel = items.size > 1
        np.displayedValues = displayItems
        styleNumberPicker(np)
    }

    private fun movePicker(np: NumberPicker, delta: Int) {
        if (np.maxValue < np.minValue) return
        val current = np.value
        val next = current + delta
        val wrapped = when {
            next < np.minValue && np.wrapSelectorWheel -> np.maxValue
            next > np.maxValue && np.wrapSelectorWheel -> np.minValue
            else -> next.coerceIn(np.minValue, np.maxValue)
        }
        np.value = wrapped
    }

    private fun styleNumberPicker(np: NumberPicker) {
        val desiredSp = 16f
        for (i in 0 until np.childCount) {
            val child = np.getChildAt(i)
            if (child is TextView) {
                child.setTextSize(TypedValue.COMPLEX_UNIT_SP, desiredSp)
                child.setTextColor(ContextCompat.getColor(this, R.color.black))
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val sizeInPx = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_SP, desiredSp, resources.displayMetrics
            )
            np.setTextSize(sizeInPx)
        } else {
            try {
                val fields = NumberPicker::class.java.declaredFields
                for (field in fields) {
                    if (field.name == "mSelectionDivider") {
                        field.isAccessible = true
                        field.set(np, ColorDrawable(ContextCompat.getColor(this, R.color.teal_700)))
                        break
                    }
                }
            } catch (ignored: Exception) {
            }
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
}
