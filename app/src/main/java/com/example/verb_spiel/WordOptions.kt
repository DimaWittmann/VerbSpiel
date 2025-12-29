package com.example.verb_spiel

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

object WordOptions {
    fun show(
        context: Context,
        scope: CoroutineScope,
        repo: WordRepository,
        updater: WordListUpdater,
        word: Word
    ) {
        val favoriteLabel = if (word.isFavorite) {
            context.getString(R.string.remove_from_favorites)
        } else {
            context.getString(R.string.add_to_favorites)
        }
        val options = arrayOf(
            favoriteLabel,
            context.getString(R.string.add_to_learned),
            context.getString(R.string.open_word_info)
        )
        AlertDialog.Builder(context)
            .setTitle(formatWord(word))
            .setItems(options) { _, which ->
                when (which) {
                    0 -> toggleFavorite(scope, repo, updater, word)
                    1 -> markLearned(context, scope, repo, updater, word)
                    2 -> openWordInfo(context, word)
                }
            }
            .show()
    }

    private fun toggleFavorite(
        scope: CoroutineScope,
        repo: WordRepository,
        updater: WordListUpdater,
        word: Word
    ) {
        val updated = word.copy(isFavorite = !word.isFavorite)
        scope.launch {
            repo.updateWordStats(updated)
            updater.updateWord(updated)
        }
    }

    private fun markLearned(
        context: Context,
        scope: CoroutineScope,
        repo: WordRepository,
        updater: WordListUpdater,
        word: Word
    ) {
        if (word.isLearned) {
            Toast.makeText(
                context,
                R.string.already_learned,
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        val updated = word.copy(isLearned = true)
        scope.launch {
            repo.updateWordStats(updated)
            updater.updateWord(updated)
        }
    }

    private fun openWordInfo(context: Context, word: Word) {
        val url = "https://de.wiktionary.org/wiki/" + Uri.encode(word.prefix + word.root)
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        context.startActivity(intent)
    }
}
