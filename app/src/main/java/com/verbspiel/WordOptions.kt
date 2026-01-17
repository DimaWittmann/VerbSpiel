package com.verbspiel

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.appcompat.app.AlertDialog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

object WordOptions {
    fun show(
        context: Context,
        scope: CoroutineScope,
        repo: WordRepository,
        updater: WordListUpdater,
        word: Word,
        shouldKeep: ((Word) -> Boolean)? = null
    ) {
        val favoriteLabel = if (word.isFavorite) {
            context.getString(R.string.remove_from_favorites)
        } else {
            context.getString(R.string.add_to_favorites)
        }
        val learnedLabel = if (word.isLearned) {
            context.getString(R.string.remove_from_learned)
        } else {
            context.getString(R.string.add_to_learned)
        }
        val options = arrayOf(
            favoriteLabel,
            learnedLabel,
            context.getString(R.string.open_word_info)
        )
        AlertDialog.Builder(context)
            .setTitle(formatWord(word))
            .setItems(options) { _, which ->
                when (which) {
                    0 -> toggleFavorite(scope, repo, updater, word, shouldKeep)
                    1 -> toggleLearned(context, scope, repo, updater, word, shouldKeep)
                    2 -> openWordInfo(context, word)
                }
            }
            .show()
    }

    private fun toggleFavorite(
        scope: CoroutineScope,
        repo: WordRepository,
        updater: WordListUpdater,
        word: Word,
        shouldKeep: ((Word) -> Boolean)?
    ) {
        val updated = word.copy(isFavorite = !word.isFavorite)
        scope.launch {
            repo.updateWordStats(updated)
            if (shouldKeep != null && !shouldKeep(updated)) {
                updater.removeWord(updated.id)
            } else {
                updater.updateWord(updated)
            }
        }
    }

    private fun toggleLearned(
        context: Context,
        scope: CoroutineScope,
        repo: WordRepository,
        updater: WordListUpdater,
        word: Word,
        shouldKeep: ((Word) -> Boolean)?
    ) {
        val updated = word.copy(isLearned = !word.isLearned)
        scope.launch {
            repo.updateWordStats(updated)
            if (shouldKeep != null && !shouldKeep(updated)) {
                updater.removeWord(updated.id)
            } else {
                updater.updateWord(updated)
            }
        }
    }

    private fun openWordInfo(context: Context, word: Word) {
        val url = "https://de.wiktionary.org/wiki/" + Uri.encode(word.prefix + word.root)
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        context.startActivity(intent)
    }
}
