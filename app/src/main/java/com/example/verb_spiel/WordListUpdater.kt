package com.example.verb_spiel

interface WordListUpdater {
    fun updateWord(updated: Word)
    fun removeWord(wordId: Int)
}
