package com.example.verb_spiel

import android.os.Bundle
import android.content.Intent
import android.widget.Button
import android.widget.ListView
import android.widget.TextView
import android.text.Html
import androidx.appcompat.app.AppCompatActivity
import android.widget.ProgressBar
import androidx.appcompat.app.AlertDialog
import android.widget.Toast
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.EditText
import android.widget.ArrayAdapter
import android.os.Build
import android.util.TypedValue
import android.util.Log
import android.widget.NumberPicker
import android.graphics.drawable.ColorDrawable
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import android.text.Editable
import android.text.TextWatcher
import java.io.BufferedReader
import android.net.Uri

class MainActivity : AppCompatActivity() {

    private lateinit var messageText: TextView
    private lateinit var nextWordText: TextView
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
    private var selectedWords: MutableList<Word> = mutableListOf()
    private var wordI: Int = 0
    private var centerWords: MutableList<Word> = mutableListOf()
    private lateinit var centerAdapter: CenterWordAdapter
    private var leftItems: Array<String> = emptyArray()
    private var leftDisplayItems: Array<String> = emptyArray()
    private var rightItems: Array<String> = emptyArray()
    private var numberOfTries: Int = 0
    private var activeFilter: Filter? = null
    private var lastResultTranslation: String = ""
    private var lastResultExample: String = ""
    private var currentNextWord: Word? = null

    private val repo by lazy { WordRepository.getInstance(this) }
    private val activityScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val mainWordUpdater = object : WordListUpdater {
        override fun updateWord(updated: Word) {
            centerAdapter.updateWord(updated)
        }

        override fun removeWord(wordId: Int) {
            centerAdapter.removeWord(wordId)
        }
    }

    private data class Filter(val type: FilterType, val value: String)
    private enum class FilterType { PREFIX, ROOT, FAVORITES }

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

    private fun fetchPoolAndReset(filter: Filter?) {
        activityScope.launch {
            val pool = when (filter?.type) {
                FilterType.PREFIX -> repo.getPrefixPool(filter.value, 10)
                FilterType.ROOT -> repo.getRootPool(filter.value, 10)
                FilterType.FAVORITES -> repo.getFavoritesPool(10)
                null -> repo.getMixedPool(10)
            }
            resetRound(pool)
        }
    }

    private fun showPrefixChooser() {
        activityScope.launch {
            val values = repo.getAllPrefixes()
            if (values.isEmpty()) {
                showTopToast(getString(R.string.filter_no_matches))
                return@launch
            }
            val prefixCounts = repo.getAllWords().groupingBy { it.prefix }.eachCount()
            val displayValues = values.map { prefix ->
                val label = if (prefix.isBlank()) getString(R.string.no_prefix) else prefix
                val count = prefixCounts[prefix] ?: 0
                "$label (${getString(R.string.count_verbs, count)})"
            }
            showValueChooserWithFilter(
                title = getString(R.string.filter_choose_value),
                values = values,
                displayValues = displayValues
            ) { chosen ->
                applyFilter(Filter(FilterType.PREFIX, chosen))
            }
        }
    }

    private fun showRootChooser() {
        activityScope.launch {
            val values = repo.getAllRoots()
            if (values.isEmpty()) {
                showTopToast(getString(R.string.filter_no_matches))
                return@launch
            }
            val rootCounts = repo.getAllWords().groupingBy { it.root }.eachCount()
            val displayValues = values.map { root ->
                val count = rootCounts[root] ?: 0
                "$root (${getString(R.string.count_verbs, count)})"
            }
            showValueChooserWithFilter(
                title = getString(R.string.filter_choose_value),
                values = values,
                displayValues = displayValues
            ) { chosen ->
                applyFilter(Filter(FilterType.ROOT, chosen))
            }
        }
    }

    private fun showValueChooserWithFilter(
        title: String,
        values: List<String>,
        displayValues: List<String>,
        onSelect: (String) -> Unit
    ) {
        val input = EditText(this)
        input.hint = getString(R.string.filter_type_hint)
        val listView = ListView(this)
        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, displayValues.toMutableList())
        listView.adapter = adapter

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (resources.displayMetrics.density * 16).toInt()
            setPadding(pad, pad, pad, pad)
            addView(input)
            addView(listView)
        }

        val dialog = AlertDialog.Builder(this)
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

    private fun showTopToast(message: String) {
        val toast = Toast(this)
        val view = layoutInflater.inflate(R.layout.toast_top, null) as LinearLayout
        view.findViewById<TextView>(R.id.toast_text).text = message
        toast.view = view
        toast.duration = Toast.LENGTH_LONG
        val offset = (resources.displayMetrics.density * 24).toInt()
        toast.setGravity(Gravity.TOP or Gravity.CENTER_HORIZONTAL, 0, offset)
        toast.show()
    }

    private fun resetRound(pool: List<Word>) {
        val filtered = if (activeFilter == null) {
            pool.filterNot { it.isLearned }
        } else {
            pool
        }
        if (filtered.isEmpty()) {
            selectedWords = mutableListOf()
            leftItems = emptyArray()
            leftDisplayItems = emptyArray()
            rightItems = emptyArray()
            messageText.text = getString(R.string.filter_no_matches)
            setNextWord(null)
            translationText.text = ""
            exampleText.text = ""
            centerWords.clear()
            centerAdapter.notifyDataSetChanged()
            setRoundEnded(true)
            return
        }

        setRoundEnded(false)
        selectedWords = filtered.toMutableList()
        wordI = 0
        numberOfTries = 0
        centerWords.clear()
        centerAdapter.notifyDataSetChanged()

        leftItems = selectedWords.map { it.prefix }.shuffled().toTypedArray()
        leftDisplayItems = leftItems.map { prefix ->
            if (prefix.isBlank()) getString(R.string.no_prefix) else prefix
        }.toTypedArray()
        rightItems = selectedWords.map { rootLabel(it) }.shuffled().toTypedArray()
        createNumberPicker(listLeft, leftItems, leftDisplayItems)
        createNumberPicker(listRight, rightItems, rightItems)
        selectedLeft = leftItems.firstOrNull()
        selectedRight = rightItems.firstOrNull()

        progressBar.max = selectedWords.size
        progressBar.setProgress(0, true)

        if (selectedWords.isNotEmpty()) {
            val first = selectedWords[0]
            showTopToast(getString(R.string.toast_new_round_next, first.translation))
            showNextMessage(getString(R.string.msg_greeting), first)
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
            getString(R.string.filter_type_favorites),
            getString(R.string.filter_type_clear)
        )
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.filter_choose_type))
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showValueChooser(FilterType.PREFIX)
                    1 -> showValueChooser(FilterType.ROOT)
                    2 -> applyFilter(Filter(FilterType.FAVORITES, ""))
                    else -> applyFilter(null)
                }
            }
            .show()
    }


    private fun showValueChooser(type: FilterType) {
        when (type) {
            FilterType.FAVORITES -> applyFilter(Filter(FilterType.FAVORITES, ""))
            FilterType.PREFIX -> showPrefixChooser()
            FilterType.ROOT -> showRootChooser()
        }
    }

    private fun applyFilter(filter: Filter?) {
        activeFilter = filter
        updateFilterStatus()
        fetchPoolAndReset(filter)
    }

    private fun updateFilterStatus() {
        val text = when (val f = activeFilter) {
            null -> getString(R.string.filter_mode_mixed)
            else -> when (f.type) {
                FilterType.PREFIX -> {
                    val label = if (f.value.isBlank()) getString(R.string.no_prefix) else f.value
                    getString(R.string.filter_mode_prefix, label)
                }
                FilterType.ROOT -> getString(R.string.filter_mode_root, f.value)
                FilterType.FAVORITES -> getString(R.string.filter_mode_favorites)
            }
        }
        filterStatus.text = text
    }

    private fun showNextMessage(status: String, nextWord: Word?) {
        messageText.text = status
        setNextWord(nextWord)
    }

    private fun setNextWord(word: Word?) {
        currentNextWord = word
        nextWordText.text = word?.translation.orEmpty()
    }

    private fun toEnglishLookup(translation: String): String {
        val first = translation.split(",").firstOrNull().orEmpty().trim()
        val withoutTo = if (first.startsWith("to ")) first.removePrefix("to ").trim() else first
        val cleaned = withoutTo.replace(Regex("\\s*\\([^)]*\\)"), "").trim()
        return cleaned.replace(' ', '_')
    }

    private fun getCurrentPool(): List<Word> = selectedWords

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
        messageText = findViewById(R.id.message_text)
        nextWordText = findViewById(R.id.next_word_text)
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
        val buttonsScroll: HorizontalScrollView = findViewById(R.id.buttons_scroll)
        val arrowButtonsLeft: ImageView = findViewById(R.id.arrow_buttons_left)
        val arrowButtonsRight: ImageView = findViewById(R.id.arrow_buttons_right)
        val arrowPrefixUp: ImageView = findViewById(R.id.arrow_prefix_up)
        val arrowPrefixDown: ImageView = findViewById(R.id.arrow_prefix_down)
        val arrowRootUp: ImageView = findViewById(R.id.arrow_root_up)
        val arrowRootDown: ImageView = findViewById(R.id.arrow_root_down)

        progressBar.max = 10
        progressBar.min = 0
        progressBar.setProgress(0)

        centerAdapter = CenterWordAdapter(this, centerWords) { word ->
            WordOptions.show(this, activityScope, repo, mainWordUpdater, word)
        }
        listCenter.adapter = centerAdapter

        updateFilterStatus()
        fetchPoolAndReset(activeFilter)

        nextWordText.setOnClickListener {
            val word = currentNextWord ?: return@setOnClickListener
            val url = "https://en.wiktionary.org/wiki/" + Uri.encode(toEnglishLookup(word.translation)) + "#English"
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
        }

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

        arrowPrefixUp.setOnClickListener { movePicker(listLeft, -1) }
        arrowPrefixDown.setOnClickListener { movePicker(listLeft, 1) }
        arrowRootUp.setOnClickListener { movePicker(listRight, -1) }
        arrowRootDown.setOnClickListener { movePicker(listRight, 1) }

        buttonsScroll.post {
            val step = buttonCombine.width + resources.displayMetrics.density.times(12).toInt()
            arrowButtonsLeft.setOnClickListener {
                buttonsScroll.smoothScrollBy(-step, 0)
            }
            arrowButtonsRight.setOnClickListener {
                buttonsScroll.smoothScrollBy(step, 0)
            }
        }

        buttonStats.setOnClickListener {
            startActivity(Intent(this, StatsActivity::class.java))
        }

        buttonFilter.setOnClickListener { showFilterChooser() }
        buttonResetPrimary.setOnClickListener { fetchPoolAndReset(activeFilter) }
        buttonReset.setOnClickListener { fetchPoolAndReset(activeFilter) }
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
            centerAdapter.insertWord(currentWord)
            lastResultTranslation = "$combinedWord\n$currentTranslation"
            lastResultExample = currentExample
            translationText.text = lastResultTranslation
            exampleText.text = lastResultExample

            if (wordI >= selectedWords.size) {
                messageText.text = Html.fromHtml(getString(R.string.msg_done), Html.FROM_HTML_MODE_LEGACY)
                setNextWord(null)
                translationText.text = "$combinedWord\n$currentTranslation"
                exampleText.text = "$currentExample"
                showTopToast(getString(R.string.toast_round_done))
                setRoundEnded(true)
                return@setOnClickListener
            }

            val nextWord = selectedWords[wordI]
            showTopToast(getString(R.string.toast_skipped_next, nextWord.translation))
            showNextMessage(getString(R.string.msg_skipped), nextWord)

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
                centerAdapter.insertWord(currentWord)

                lastResultTranslation = "$combinedWord\n$currentTranslation"
                lastResultExample = currentExample
                translationText.text = lastResultTranslation
                exampleText.text = currentExample

                if (selectedWords.size <= wordI) {
                    messageText.text = Html.fromHtml(getString(R.string.msg_done), Html.FROM_HTML_MODE_LEGACY)
                    setNextWord(null)
                    translationText.text = "$combinedWord\n$currentTranslation"
                    exampleText.text = "$currentExample"
                    showTopToast(getString(R.string.toast_round_done))
                    setRoundEnded(true)
                    return@setOnClickListener
                }

                val nextWord = selectedWords[wordI]
                showTopToast(getString(R.string.toast_correct_next, nextWord.translation))

                showNextMessage(getString(R.string.msg_correct), nextWord)
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
                    centerAdapter.insertWord(currentWord)
                    lastResultTranslation = "$combinedWord\n$currentTranslation"
                    lastResultExample = currentExample

                    if (wordI >= selectedWords.size) {
                        messageText.text = Html.fromHtml(getString(R.string.msg_done), Html.FROM_HTML_MODE_LEGACY)
                        setNextWord(null)
                        translationText.text = "$combinedWord\n$currentTranslation"
                        exampleText.text = "$currentExample"
                        showTopToast(getString(R.string.toast_round_done))
                        setRoundEnded(true)
                    } else {
                        val nextWord = selectedWords[wordI]
                        showTopToast(getString(R.string.toast_forced_advance, numberOfTries, nextWord.translation))
                        showNextMessage(
                            "Wrong $numberOfTries times! Let's try another",
                            nextWord
                        )

                        translationText.text = lastResultTranslation
                        exampleText.text = lastResultExample

                        val nextIndex = wordI
                        activityScope.launch {
                            selectedWords[nextIndex] = recordShown(nextWord)
                        }
                    }

                    numberOfTries = 0
                } else {
                    showNextMessage(getString(R.string.msg_wrong), currentWord)
                    translationText.text = lastResultTranslation
                    exampleText.text = lastResultExample
                }
            }
            progressBar.setProgress(numberOfTries, true)
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

    override fun onDestroy() {
        super.onDestroy()
        activityScope.cancel()
    }
}
