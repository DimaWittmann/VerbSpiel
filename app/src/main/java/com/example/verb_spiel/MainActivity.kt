package com.example.verb_spiel

import android.os.Bundle
import android.util.Log
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.TextView
import android.text.Html
import android.widget.EditText
import android.widget.NumberPicker
import androidx.appcompat.app.AppCompatActivity
import android.util.TypedValue
import android.widget.ProgressBar
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
    private lateinit var progressBar: ProgressBar

    // Variables to track the currently selected items in each list.
    private var selectedLeft: String? = null
    private var selectedRight: String? = null
    private lateinit var selectedWords: MutableList<Word>
    private var wordI: Int = 0
    private var centerWords: MutableList<String> = mutableListOf()
    private var numberOfTries: Int = 0

    private val repo by lazy { WordRepository.getInstance(this) }
    private val activityScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private suspend fun recordShown(word: Word): Word {
        val updated = word.copy(timesShown = word.timesShown + 1)
        repo.updateWordStats(updated)
        return updated
    }

    private suspend fun recordAttempt(word: Word, isCorrect: Boolean): Word {
        val updatedWord = word.copy(
            triesCount = word.triesCount + 1,
            correctCount = if (isCorrect) word.correctCount + 1 else word.correctCount,
            failedCount = if (!isCorrect) word.failedCount + 1 else word.failedCount
        )
        repo.updateWordStats(updatedWord)
        return updatedWord
    }

    private fun parseCsvFile(): List<Word> {
        val records = mutableListOf<Word>()
        try {
            // Open the CSV file from res/raw (ensure your file is named exactly my_csv_file.csv)
            val inputStream = resources.openRawResource(R.raw.data)
            val reader: BufferedReader = inputStream.bufferedReader()
            val lines = reader.readLines()
            reader.close()

            // Assuming the first line is a header, skip it
            for (line in lines.drop(1)) {
                // Split the line on semicolons.
                val tokens = line.split(";")
                // Ensure we have at least 4 tokens for the expected schema.
                if (tokens.size == 4) {
                    val record = Word(
                        prefix = tokens[0].trim(),
                        root = tokens[1].trim(),
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
        return records
    }

    private fun createNumberPicker(np: NumberPicker, items: Array<String>) {
        np.minValue = 0
        np.maxValue = items.size - 1
        np.wrapSelectorWheel = true
        np.displayedValues = items
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val desiredSp = 24f
            val sizeInPx = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_SP, desiredSp, resources.displayMetrics
            )
            np.setTextSize(sizeInPx)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Link this activity with its layout.
        setContentView(R.layout.activity_main)

        val words = runBlocking {
            if (repo.isEmpty()) {
                val words = parseCsvFile()
                repo.addWords(words)
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
        progressBar = findViewById(R.id.progress_bar)

        progressBar.max = 10
        progressBar.min = 0
        progressBar.setProgress(0)

        selectedWords = words.shuffled().take(10).toMutableList()

        val prefixes = selectedWords.map { it.prefix }.toTypedArray()
        val roots = selectedWords.map { it.root }.toTypedArray()

        // Sample data for the left and right lists.
        val leftItems = prefixes
        val rightItems = roots
        leftItems.shuffle()
        rightItems.shuffle()

        val centerAdapter =
            ArrayAdapter(this, R.layout.list_item_center, R.id.list_item_text, centerWords)
        listCenter.adapter = centerAdapter

        createNumberPicker(listLeft, leftItems)
        createNumberPicker(listRight, rightItems)
        selectedLeft = leftItems[0]
        selectedRight = rightItems[0]

        messageText.text = Html.fromHtml(
            "Greetings Madam or Sir<br>Next word: <b>${selectedWords[wordI].translation}</b>",
            Html.FROM_HTML_MODE_LEGACY
        )
        translationText.text = ""
        exampleText.text = "Select correct prefix and root"

        activityScope.launch {
            selectedWords[wordI] = recordShown(selectedWords[wordI])
        }

        listLeft.setOnValueChangedListener { _, _, newValue ->
            selectedLeft = leftItems[newValue]
        }

        // When an item in the right list is clicked, store the selection.
        listRight.setOnValueChangedListener { _, _, newValue ->
            selectedRight = rightItems[newValue]
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

        // When the "Combine" button is clicked, combine the selected items.
        buttonCombine.setOnClickListener {
            if (selectedLeft == null || selectedRight == null) {
                return@setOnClickListener
            }

            if (selectedWords.size <= wordI) {
                return@setOnClickListener
            }

            val currentIndex = wordI
            val currentWord = selectedWords[currentIndex]
            val combinedWord = currentWord.prefix + currentWord.root
            val currentTranslation = currentWord.translation
            val currentExample = currentWord.example

            val isCorrect =
                (selectedLeft == selectedWords[currentIndex].prefix && selectedRight == selectedWords[currentIndex].root)
            activityScope.launch {
                selectedWords[currentIndex] = recordAttempt(currentWord, isCorrect)
            }

            if (isCorrect) {
                wordI += 1
                numberOfTries = 0
                centerAdapter.insert(combinedWord, 0)

                if (selectedWords.size <= wordI) {
                    messageText.text = Html.fromHtml("Great Success!", Html.FROM_HTML_MODE_LEGACY)
                    translationText.text = "$combinedWord\n$currentTranslation"
                    exampleText.text = "$currentExample"
                    return@setOnClickListener
                }

                val nextWord = selectedWords[wordI]

                messageText.text = Html.fromHtml(
                    "Correct!<br>Next word: <b>${nextWord.translation}</b>",
                    Html.FROM_HTML_MODE_LEGACY
                )
                translationText.text = "$combinedWord\n$currentTranslation"
                exampleText.text = "$currentExample"

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
                        messageText.text = Html.fromHtml(
                            "This is the last word. Try harder!</b>",
                            Html.FROM_HTML_MODE_LEGACY
                        )
                        translationText.text = "$combinedWord\n$currentTranslation"
                        exampleText.text = "$currentExample"
                    } else {
                        val nextWord = selectedWords[wordI]
                        messageText.text = Html.fromHtml(
                            "Wrong $numberOfTries times!<b> Let's try another</b><br>Next word: <b>${nextWord.translation}</b>",
                            Html.FROM_HTML_MODE_LEGACY
                        )

                        translationText.text = "$combinedWord\n$currentTranslation"
                        exampleText.text = "$currentExample"

                        val nextIndex = wordI
                        activityScope.launch {
                            selectedWords[nextIndex] = recordShown(nextWord)
                        }
                    }
                } else {
                    messageText.text = Html.fromHtml(
                        "Wrong!<br>Next word: $currentTranslation", Html.FROM_HTML_MODE_LEGACY
                    )
                }
            }
            progressBar.setProgress(numberOfTries, true)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        activityScope.cancel()
    }
}
