package com.example.verb_spiel

import android.os.Bundle
import android.util.Log
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.io.BufferedReader


data class Word (
    val prefix: String,
    val root: String,
    val translation: String,
    val example: String,
    )

class MainActivity : AppCompatActivity() {

    private lateinit var topText: TextView
    private lateinit var listLeft: ListView
    private lateinit var listRight: ListView
    private lateinit var buttonCombine: Button

    // Variables to track the currently selected items in each list.
    private var selectedLeft: String? = null
    private var selectedRight: String? = null
    private lateinit var selectedWords: Array<Word>
    private var wordI: Int = 0


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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Link this activity with its layout.
        setContentView(R.layout.activity_main)

        // Find views by their IDs.
        topText = findViewById(R.id.top_text)
        listLeft = findViewById(R.id.list_left)
        listRight = findViewById(R.id.list_right)
        buttonCombine = findViewById(R.id.button_combine)

        val words = parseCsvFile()
        val selected_words = words.shuffled().take(10)

        val prefixes = selected_words.map { it.prefix }.toTypedArray()
        val roots = selected_words.map { it.root }.toTypedArray()

        // Sample data for the left and right lists.
        val leftItems = prefixes
        val rightItems = roots

        // Create adapters using the standard Android layout for single-choice items.
        val leftAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_single_choice, leftItems)
        val rightAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_single_choice, rightItems)

        listLeft.adapter = leftAdapter
        listRight.adapter = rightAdapter
        topText.text = "Next word: ${selected_words[wordI].translation}"

        // When an item in the left list is clicked, store the selection.
        listLeft.setOnItemClickListener { _, _, position, _ ->
            selectedLeft = leftItems[position]
        }

        // When an item in the right list is clicked, store the selection.
        listRight.setOnItemClickListener { _, _, position, _ ->
            selectedRight = rightItems[position]
        }

        // When the "Combine" button is clicked, combine the selected items.
        buttonCombine.setOnClickListener {
            if (selectedLeft == null || selectedRight == null) {
                return@setOnClickListener
            }

            val currentWord = selected_words[wordI].prefix + selected_words[wordI].root
            val currentTranslation = selected_words[wordI].translation
            val currentExample = selected_words[wordI].example

            if (selectedLeft == selected_words[wordI].prefix && selectedRight == selected_words[wordI].root) {
                wordI += 1

                if (selected_words.size <= wordI) {
                    topText.text =
                        "Great Success!\n$currentWord\n$currentTranslation\n$currentExample"
                }

                val nextTranslation = selected_words[wordI].translation;

                topText.text =
                    "Correct!\nNext word: $nextTranslation \n $currentWord \n $currentTranslation\n $currentExample"
            } else {
                topText.text = "Selected: $currentTranslation"
            }
        }
    }
}
