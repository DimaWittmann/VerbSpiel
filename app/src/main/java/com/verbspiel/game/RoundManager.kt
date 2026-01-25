package com.verbspiel.game

import android.content.res.Resources
import com.verbspiel.R
import com.verbspiel.Word
import com.verbspiel.WordRepository
import com.verbspiel.formatRoot
import com.verbspiel.formatWord
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

interface RoundUi {
    fun showToast(message: String)
    fun render(state: RoundState)
}

data class RoundState(
    val nextWord: Word?,
    val lastWord: Word?,
    val translation: String,
    val example: String,
    val statusLabel: String,
    val statusColorRes: Int?,
    val roundEnded: Boolean,
    val progressMax: Int,
    val progressValue: Int,
    val leftItems: Array<String>,
    val leftDisplayItems: Array<String>,
    val rightItems: Array<String>,
    val rightDisplayItems: Array<String>
)

data class RoundFilter(val type: RoundFilterType, val value: String)

enum class RoundFilterType { PREFIX, ROOT, FAVORITES }

class RoundManager(
    private val repo: WordRepository,
    private val scope: CoroutineScope,
    private val ui: RoundUi,
    private val resources: Resources
) {
    private var selectedWords: MutableList<Word> = mutableListOf()
    private var wordIndex: Int = 0
    private var numberOfTries: Int = 0
    private var leftItems: Array<String> = emptyArray()
    private var leftDisplayItems: Array<String> = emptyArray()
    private var rightItems: Array<String> = emptyArray()
    private var rightDisplayItems: Array<String> = emptyArray()
    private var lastResultTranslation: String = ""
    private var lastResultExample: String = ""
    private var statusLabel: String = ""
    private var statusColorRes: Int? = null
    private var roundEnded: Boolean = true
    private var lastWord: Word? = null
    private var nextWord: Word? = null
    private var translation: String = ""
    private var example: String = ""

    fun startRound(filter: RoundFilter?, roundSize: Int, excludeLearned: Boolean) {
        scope.launch {
            val pool = when (filter?.type) {
                RoundFilterType.PREFIX -> repo.getPrefixPool(filter.value, roundSize)
                RoundFilterType.ROOT -> repo.getRootPool(filter.value, roundSize)
                RoundFilterType.FAVORITES -> repo.getFavoritesPool(roundSize)
                null -> repo.getMixedPool(roundSize)
            }
            resetRound(pool, excludeLearned)
        }
    }

    fun handleSkip() {
        if (selectedWords.isEmpty() || selectedWords.size <= wordIndex) {
            return
        }
        val currentIndex = wordIndex
        val currentWord = selectedWords[currentIndex]
        val combinedWord = formatWord(currentWord)
        val currentTranslation = currentWord.translation
        val currentExample = currentWord.example

        scope.launch {
            selectedWords[currentIndex] = recordAttempt(currentWord, false)
        }

        wordIndex += 1
        numberOfTries = 0
        lastResultTranslation = "$combinedWord\n$currentTranslation"
        lastResultExample = currentExample
        lastWord = currentWord
        translation = lastResultTranslation
        example = lastResultExample

        if (wordIndex >= selectedWords.size) {
            nextWord = null
            statusLabel = resources.getString(R.string.status_done)
            statusColorRes = R.color.gray
            translation = "$combinedWord\n$currentTranslation"
            example = currentExample
            ui.showToast(resources.getString(R.string.toast_round_done))
            roundEnded = true
            render()
            return
        }

        val next = selectedWords[wordIndex]
        nextWord = next
        statusLabel = resources.getString(R.string.status_skipped)
        statusColorRes = R.color.orange
        roundEnded = false
        ui.showToast(resources.getString(R.string.toast_skipped_next, next.translation))
        render()

        val nextIndex = wordIndex
        scope.launch {
            selectedWords[nextIndex] = recordShown(next)
        }
    }

    fun handleCombine(leftIndex: Int, rightIndex: Int) {
        if (leftItems.isEmpty() || rightItems.isEmpty()) {
            return
        }
        if (selectedWords.isEmpty() || selectedWords.size <= wordIndex) {
            return
        }
        if (leftIndex !in leftItems.indices || rightIndex !in rightItems.indices) {
            return
        }

        val currentLeft = leftItems[leftIndex]
        val currentRight = rightItems[rightIndex]

        val currentIndex = wordIndex
        val currentWord = selectedWords[currentIndex]
        val combinedWord = formatWord(currentWord)
        val currentTranslation = currentWord.translation
        val currentExample = currentWord.example

        val isCorrect =
            (currentLeft == selectedWords[currentIndex].prefix && currentRight == rootLabel(selectedWords[currentIndex]))
        scope.launch {
            selectedWords[currentIndex] = recordAttempt(currentWord, isCorrect)
        }

        if (isCorrect) {
            wordIndex += 1
            numberOfTries = 0
            lastResultTranslation = "$combinedWord\n$currentTranslation"
            lastResultExample = currentExample
            lastWord = currentWord
            translation = lastResultTranslation
            example = currentExample

            if (selectedWords.size <= wordIndex) {
                nextWord = null
                statusLabel = resources.getString(R.string.status_done)
                statusColorRes = R.color.gray
                translation = "$combinedWord\n$currentTranslation"
                example = currentExample
                ui.showToast(resources.getString(R.string.toast_round_done))
                roundEnded = true
                render()
                return
            }

            val next = selectedWords[wordIndex]
            nextWord = next
            statusLabel = resources.getString(R.string.status_correct)
            statusColorRes = R.color.teal_700
            roundEnded = false
            ui.showToast(resources.getString(R.string.toast_correct_next, next.translation))

            translation = lastResultTranslation
            example = lastResultExample
            render()

            val nextIndex = wordIndex
            scope.launch {
                selectedWords[nextIndex] = recordShown(next)
            }
        } else {
            numberOfTries += 1

            if (numberOfTries >= selectedWords.size) {
                wordIndex += 1
                lastResultTranslation = "$combinedWord\n$currentTranslation"
                lastResultExample = currentExample
                lastWord = currentWord
                translation = lastResultTranslation
                example = lastResultExample

                if (wordIndex >= selectedWords.size) {
                    nextWord = null
                    statusLabel = resources.getString(R.string.status_done)
                    statusColorRes = R.color.gray
                    ui.showToast(resources.getString(R.string.toast_round_done))
                    roundEnded = true
                    numberOfTries = 0
                    render()
                } else {
                    val next = selectedWords[wordIndex]
                    nextWord = next
                    statusLabel = resources.getString(R.string.status_forced)
                    statusColorRes = R.color.orange
                    roundEnded = false
                    ui.showToast(
                        resources.getString(
                            R.string.toast_forced_advance,
                            numberOfTries,
                            next.translation
                        )
                    )
                    numberOfTries = 0
                    render()

                    val nextIndex = wordIndex
                    scope.launch {
                        selectedWords[nextIndex] = recordShown(next)
                    }
                }
            } else {
                nextWord = currentWord
                statusLabel = resources.getString(R.string.status_wrong)
                statusColorRes = R.color.red
                roundEnded = false
                translation = lastResultTranslation
                example = lastResultExample
                ui.showToast(resources.getString(R.string.toast_wrong_try, currentLeft, currentRight))
                render()
            }
        }
    }

    private fun resetRound(pool: List<Word>, excludeLearned: Boolean) {
        val filtered = if (excludeLearned) {
            pool.filterNot { it.isLearned }
        } else {
            pool
        }
        if (filtered.isEmpty()) {
            selectedWords = mutableListOf()
            leftItems = emptyArray()
            leftDisplayItems = emptyArray()
            rightItems = emptyArray()
            rightDisplayItems = emptyArray()
            wordIndex = 0
            numberOfTries = 0
            lastWord = null
            nextWord = null
            translation = ""
            example = ""
            statusLabel = ""
            statusColorRes = null
            roundEnded = true
            render()
            return
        }

        roundEnded = false
        statusLabel = ""
        statusColorRes = null
        selectedWords = filtered.toMutableList()
        wordIndex = 0
        numberOfTries = 0

        buildPickers()

        if (selectedWords.isNotEmpty()) {
            val first = selectedWords[0]
            ui.showToast(resources.getString(R.string.toast_new_round_next, first.translation))
            nextWord = first
            lastWord = null
            translation = ""
            example = resources.getString(R.string.round_select_prompt)
            render()

            scope.launch {
                selectedWords[0] = recordShown(first)
            }
        }
    }

    private fun buildPickers() {
        val leftAll = selectedWords.map { it.prefix }.shuffled()
        val leftCounts = leftAll.groupingBy { it }.eachCount()
        leftItems = leftAll.distinct().toTypedArray()
        leftDisplayItems = leftItems.map { prefix ->
            val label = if (prefix.isBlank()) resources.getString(R.string.no_prefix) else prefix
            val count = leftCounts[prefix] ?: 0
            if (count > 1) "$label (x$count)" else label
        }.toTypedArray()

        val rightAll = selectedWords.map { rootLabel(it) }.shuffled()
        val rightCounts = rightAll.groupingBy { it }.eachCount()
        rightItems = rightAll.distinct().toTypedArray()
        rightDisplayItems = rightItems.map { root ->
            val count = rightCounts[root] ?: 0
            if (count > 1) "$root (x$count)" else root
        }.toTypedArray()
    }

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

    private fun render() {
        ui.render(
            RoundState(
                nextWord = nextWord,
                lastWord = lastWord,
                translation = translation,
                example = example,
                statusLabel = statusLabel,
                statusColorRes = statusColorRes,
                roundEnded = roundEnded,
                progressMax = selectedWords.size,
                progressValue = numberOfTries,
                leftItems = leftItems,
                leftDisplayItems = leftDisplayItems,
                rightItems = rightItems,
                rightDisplayItems = rightDisplayItems
            )
        )
    }
}
