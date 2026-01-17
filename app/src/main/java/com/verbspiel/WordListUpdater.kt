package com.verbspiel

interface WordListUpdater {
    fun updateWord(updated: Word)
    fun removeWord(wordId: Int)
}
