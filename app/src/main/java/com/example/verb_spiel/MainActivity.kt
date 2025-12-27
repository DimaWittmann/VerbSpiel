package com.example.verb_spiel

import android.os.Bundle
import android.util.Log
import android.content.Intent
import android.net.Uri
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.TextView
import android.text.Html
import androidx.appcompat.app.AppCompatActivity
import android.widget.ProgressBar
import androidx.appcompat.app.AlertDialog
import android.widget.Toast
import android.os.Build
import android.util.TypedValue
import android.widget.NumberPicker
import android.graphics.drawable.ColorDrawable
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import java.io.BufferedReader

class MainActivity : AppCompatActivity() {

    private lateinit var messageText: TextView
    private lateinit var translationText: TextView
    private lateinit var exampleText: TextView

    private lateinit var listLeft: NumberPicker
    private lateinit var listCenter: ListView
    private lateinit var listRight: NumberPicker
    private lateinit var buttonCombine: Button
    private lateinit var buttonStats: Button
    private lateinit var buttonFilter: Button
    private lateinit var buttonSkip: Button
    private lateinit var buttonResetPrimary: Button
    private lateinit var buttonReset: Button
    private lateinit var filterStatus: TextView
    private lateinit var progressBar: ProgressBar

    // Variables to track the currently selected items in each list.
    private var selectedLeft: String? = null
    private var selectedRight: String? = null
    private lateinit var selectedWords: MutableList<Word>
    private lateinit var allWords: List<Word>
    private var wordI: Int = 0
    private var centerWords: MutableList<String> = mutableListOf()
    private lateinit var centerAdapter: ArrayAdapter<String>
    private var leftItems: Array<String> = emptyArray()
    private var rightItems: Array<String> = emptyArray()
    private var numberOfTries: Int = 0
    private var activeFilter: Filter? = null
    private var lastResultTranslation: String = ""
    private var lastResultExample: String = ""

    private val repo by lazy { WordRepository.getInstance(this) }
    private val activityScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private data class Filter(val type: FilterType, val value: String)
    private enum class FilterType { PREFIX, ROOT }

    private fun rootLabel(word: Word): String {
        return formatRoot(word.root, word.isReflexive)
    }

    private suspend fun recordShown(word: Word): Word {
        val latest = repo.getWordById(word.id) ?: word
        val updated = latest.copy(
            timesShown = latest.timesShown + 1,
            lastShownAt = System.currentTimeMillis()
        )
        repo.updateWordStats(updated)
        return updated
    }

    private fun resetRound(pool: List<Word>) {
        val filtered = if (activeFilter == null) {
            pool.filterNot { isRetiredWord(it) }
        } else {
            pool
        }
        if (filtered.isEmpty()) {
            selectedWords = mutableListOf()
            leftItems = emptyArray()
            rightItems = emptyArray()
            messageText.text = getString(R.string.filter_no_matches)
            translationText.text = ""
            exampleText.text = ""
            centerWords.clear()
            centerAdapter.notifyDataSetChanged()
            setRoundEnded(true)
            return
        }

        setRoundEnded(false)
        selectedWords = filtered.shuffled().take(10.coerceAtMost(filtered.size)).toMutableList()
        wordI = 0
        numberOfTries = 0
        centerWords.clear()
        centerAdapter.notifyDataSetChanged()

        leftItems = selectedWords.map { it.prefix }.shuffled().toTypedArray()
        rightItems = selectedWords.map { rootLabel(it) }.shuffled().toTypedArray()
        createNumberPicker(listLeft, leftItems)
        createNumberPicker(listRight, rightItems)
        selectedLeft = leftItems.firstOrNull()
        selectedRight = rightItems.firstOrNull()

        progressBar.max = selectedWords.size
        progressBar.setProgress(0, true)

        if (selectedWords.isNotEmpty()) {
            val first = selectedWords[0]
            messageText.text = Html.fromHtml(
                buildNextMessage(getString(R.string.msg_greeting), first.translation),
                Html.FROM_HTML_MODE_LEGACY
            )
            translationText.text = ""
            exampleText.text = "Select correct prefix and root"

            activityScope.launch {
                selectedWords[0] = recordShown(first)
            }
        }
    }

    private fun showFilterChooser() {
        val options = arrayOf(
            getString(R.string.filter_type_prefix),
            getString(R.string.filter_type_root),
            getString(R.string.filter_type_clear)
        )
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.filter_choose_type))
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showValueChooser(FilterType.PREFIX)
                    1 -> showValueChooser(FilterType.ROOT)
                    else -> applyFilter(null)
                }
            }
            .show()
    }

    private fun showValueChooser(type: FilterType) {
        val values = when (type) {
            FilterType.PREFIX -> allWords.map { it.prefix }.distinct().sorted()
            FilterType.ROOT -> allWords.map { rootLabel(it) }.distinct().sorted()
        }
        if (values.isEmpty()) {
            Toast.makeText(this, R.string.filter_no_matches, Toast.LENGTH_SHORT).show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.filter_choose_value))
            .setItems(values.toTypedArray()) { _, which ->
                val chosen = values[which]
                applyFilter(Filter(type, chosen))
            }
            .show()
    }

    private fun applyFilter(filter: Filter?) {
        activeFilter = filter
        val filteredPool = when (filter?.type) {
            FilterType.PREFIX -> allWords.filter { it.prefix == filter.value }
            FilterType.ROOT -> allWords.filter { rootLabel(it) == filter.value }
            else -> allWords
        }
        updateFilterStatus()
        resetRound(filteredPool)
    }

    private fun updateFilterStatus() {
        val text = when (val f = activeFilter) {
            null -> getString(R.string.filter_mode_mixed)
            else -> when (f.type) {
                FilterType.PREFIX -> getString(R.string.filter_mode_prefix, f.value)
                FilterType.ROOT -> getString(R.string.filter_mode_root, f.value)
            }
        }
        filterStatus.text = text
    }

    private fun buildNextMessage(status: String, translation: String): String {
        val highlighted = "<font color=\"#00897B\"><b>$translation</b></font>"
        return "$status<br>${getString(R.string.msg_next_word)}: $highlighted"
    }

    private fun getCurrentPool(): List<Word> {
        return when (val f = activeFilter) {
            null -> allWords
            else -> when (f.type) {
                FilterType.PREFIX -> allWords.filter { it.prefix == f.value }
                FilterType.ROOT -> allWords.filter { rootLabel(it) == f.value }
            }
        }
    }

    private fun setRoundEnded(ended: Boolean) {
        buttonCombine.visibility = if (ended) android.view.View.GONE else android.view.View.VISIBLE
        buttonSkip.visibility = if (ended) android.view.View.GONE else android.view.View.VISIBLE
        buttonReset.visibility = if (ended) android.view.View.GONE else android.view.View.VISIBLE
        buttonResetPrimary.visibility = if (ended) android.view.View.VISIBLE else android.view.View.GONE
    }

    private suspend fun recordAttempt(word: Word, isCorrect: Boolean): Word {
        val latest = repo.getWordById(word.id) ?: word
        val updatedWord = latest.copy(
            triesCount = latest.triesCount + 1,
            correctCount = if (isCorrect) latest.correctCount + 1 else latest.correctCount,
            failedCount = if (!isCorrect) latest.failedCount + 1 else latest.failedCount,
            lastCorrectAt = if (isCorrect) System.currentTimeMillis() else latest.lastCorrectAt,
            lastFailedAt = if (!isCorrect) System.currentTimeMillis() else latest.lastFailedAt
        )
        repo.updateWordStats(updatedWord)
        return updatedWord
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

        allWords = runBlocking {
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
            repo.getAllWords()
        }

        // Find views by their IDs.
        messageText = findViewById(R.id.message_text)
        translationText = findViewById(R.id.translation_text)
        exampleText = findViewById(R.id.example_text)
        listLeft = findViewById(R.id.list_left)
        listCenter = findViewById(R.id.list_center)
        listRight = findViewById(R.id.list_right)
        buttonCombine = findViewById(R.id.button_combine)
        buttonStats = findViewById(R.id.button_stats)
        buttonFilter = findViewById(R.id.button_filter)
        buttonSkip = findViewById(R.id.button_skip)
        buttonResetPrimary = findViewById(R.id.button_reset_primary)
        buttonReset = findViewById(R.id.button_reset)
        filterStatus = findViewById(R.id.filter_status)
        progressBar = findViewById(R.id.progress_bar)

        progressBar.max = 10
        progressBar.min = 0
        progressBar.setProgress(0)

        centerAdapter =
            ArrayAdapter(this, R.layout.list_item_center, R.id.list_item_text, centerWords)
        listCenter.adapter = centerAdapter

        updateFilterStatus()
        resetRound(allWords)

        listLeft.setOnValueChangedListener { _, _, newValue ->
            if (leftItems.isNotEmpty()) {
                selectedLeft = leftItems[newValue]
            }
        }

        listRight.setOnValueChangedListener { _, _, newValue ->
            if (rightItems.isNotEmpty()) {
                selectedRight = rightItems[newValue]
            }
        }

        listCenter.setOnItemClickListener { _, _, position, _ ->
            // Get the clicked item from your backing list (centerWords)
            val selectedItem = centerWords[position]
            // Build a URL from the selected item.
            // For example, if you want to search the item on Example.com:
            val url = "https://de.wiktionary.org/wiki/" + Uri.encode(selectedItem)
            // Create an intent to view the URL.
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
        }

        buttonStats.setOnClickListener {
            startActivity(Intent(this, StatsActivity::class.java))
        }

        buttonFilter.setOnClickListener { showFilterChooser() }
        buttonResetPrimary.setOnClickListener { resetRound(getCurrentPool()) }
        buttonReset.setOnClickListener { resetRound(getCurrentPool()) }
        buttonSkip.setOnClickListener {
            if (selectedWords.isEmpty() || selectedWords.size <= wordI) {
                return@setOnClickListener
            }
            val currentIndex = wordI
            val currentWord = selectedWords[currentIndex]
            val combinedWord = formatWord(currentWord)
            val currentTranslation = currentWord.translation
            val currentExample = currentWord.example

            activityScope.launch {
                selectedWords[currentIndex] = recordAttempt(currentWord, false)
            }

            wordI += 1
            numberOfTries = 0
            translationText.text = currentTranslation
            exampleText.text = currentExample

            if (wordI >= selectedWords.size) {
                messageText.text = Html.fromHtml(getString(R.string.msg_done), Html.FROM_HTML_MODE_LEGACY)
                translationText.text = "$combinedWord\n$currentTranslation"
                exampleText.text = "$currentExample"
                setRoundEnded(true)
                return@setOnClickListener
            }

            val nextWord = selectedWords[wordI]
            messageText.text = Html.fromHtml(
                buildNextMessage(getString(R.string.msg_skipped), nextWord.translation),
                Html.FROM_HTML_MODE_LEGACY
            )

            val nextIndex = wordI
            activityScope.launch {
                selectedWords[nextIndex] = recordShown(nextWord)
            }
            progressBar.setProgress(numberOfTries, true)
        }

        // When the "Combine" button is clicked, combine the selected items.
        buttonCombine.setOnClickListener {
            if (selectedLeft == null || selectedRight == null) {
                return@setOnClickListener
            }

            if (selectedWords.isEmpty() || selectedWords.size <= wordI) {
                return@setOnClickListener
            }

            val currentIndex = wordI
            val currentWord = selectedWords[currentIndex]
            val combinedWord = formatWord(currentWord)
            val currentTranslation = currentWord.translation
            val currentExample = currentWord.example

            val isCorrect =
                (selectedLeft == selectedWords[currentIndex].prefix && selectedRight == rootLabel(selectedWords[currentIndex]))
            activityScope.launch {
                selectedWords[currentIndex] = recordAttempt(currentWord, isCorrect)
            }

            if (isCorrect) {
                wordI += 1
                numberOfTries = 0
                centerAdapter.insert(combinedWord, 0)

                lastResultTranslation = currentTranslation
                lastResultExample = currentExample
                translationText.text = currentTranslation
                exampleText.text = currentExample

                if (selectedWords.size <= wordI) {
                    messageText.text = Html.fromHtml(getString(R.string.msg_done), Html.FROM_HTML_MODE_LEGACY)
                    translationText.text = "$combinedWord\n$currentTranslation"
                    exampleText.text = "$currentExample"
                    setRoundEnded(true)
                    return@setOnClickListener
                }

                val nextWord = selectedWords[wordI]

                messageText.text = Html.fromHtml(
                    buildNextMessage(getString(R.string.msg_correct), nextWord.translation),
                    Html.FROM_HTML_MODE_LEGACY
                )
                translationText.text = lastResultTranslation
                exampleText.text = lastResultExample

                val nextIndex = wordI
                activityScope.launch {
                    selectedWords[nextIndex] = recordShown(nextWord)
                }
            } else {
                numberOfTries += 1

                if (numberOfTries >= selectedWords.size) {
                    wordI += 1
                    numberOfTries = 0

                    if (wordI >= selectedWords.size) {
                        messageText.text = Html.fromHtml(getString(R.string.msg_done), Html.FROM_HTML_MODE_LEGACY)
                        translationText.text = "$combinedWord\n$currentTranslation"
                        exampleText.text = "$currentExample"
                        setRoundEnded(true)
                    } else {
                        val nextWord = selectedWords[wordI]
                        messageText.text = Html.fromHtml(
                            buildNextMessage(
                                "Wrong $numberOfTries times! Let's try another",
                                nextWord.translation
                            ),
                            Html.FROM_HTML_MODE_LEGACY
                        )

                        translationText.text = currentTranslation
                        exampleText.text = currentExample

                        val nextIndex = wordI
                        activityScope.launch {
                            selectedWords[nextIndex] = recordShown(nextWord)
                        }
                    }
                } else {
                    messageText.text = Html.fromHtml(
                        buildNextMessage(getString(R.string.msg_wrong), currentTranslation),
                        Html.FROM_HTML_MODE_LEGACY
                    )
                    translationText.text = lastResultTranslation
                    exampleText.text = lastResultExample
                }
            }
            progressBar.setProgress(numberOfTries, true)
        }
    }

    private fun createNumberPicker(np: NumberPicker, items: Array<String>) {
        np.minValue = 0
        np.maxValue = items.size - 1
        np.wrapSelectorWheel = true
        np.displayedValues = items
        styleNumberPicker(np)
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

    override fun onDestroy() {
        super.onDestroy()
        activityScope.cancel()
    }
}
