package com.example.verb_spiel

import android.content.Context
import android.util.Log
import java.io.BufferedReader

data class WordFile(val version: Int, val words: List<Word>)

object WordFileLoader {
    fun load(context: Context): WordFile {
        val records = mutableListOf<Word>()
        var version = 0
        try {
            val inputStream = context.resources.openRawResource(R.raw.data)
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
}
