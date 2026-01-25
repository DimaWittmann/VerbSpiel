package com.verbspiel.controller

import com.verbspiel.R
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import com.verbspiel.Word
import com.verbspiel.WordRepository
import com.verbspiel.game.RoundState
import com.verbspiel.view.RoundCardView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class RoundCardController(
    private val activity: AppCompatActivity,
    private val view: RoundCardView,
    private val repo: WordRepository,
    private val scope: CoroutineScope,
    private val strings: (Int) -> String
) {
    private var lastWord: Word? = null
    private var nextWord: Word? = null

    fun bind() {
        view.setOnTranslate { openTranslatorForCurrentWord() }
        view.setOnFavorite { toggleFavorite() }
        view.setOnLearned { toggleLearned() }
        view.setOnWiki { openWiki() }
    }

    fun render(state: RoundState, statusText: String) {
        lastWord = state.lastWord
        nextWord = state.nextWord
        view.setStatus(statusText, state.statusColorRes)
        view.setNextWord(state.nextWord?.translation.orEmpty())
        view.setTranslation(state.translation)
        view.setExample(state.example)
        view.setProgress(state.progressMax, state.progressValue)
        updateLastWordUi()
    }

    fun initProgress(max: Int) {
        view.initProgress(max)
    }

    fun updateStatus(text: String, colorRes: Int?) {
        view.setStatus(text, colorRes)
    }

    private fun toggleFavorite() {
        val word = lastWord ?: return
        val updated = word.copy(isFavorite = !word.isFavorite)
        lastWord = updated
        scope.launch {
            repo.updateWordStats(updated)
        }
        updateLastWordUi()
    }

    private fun toggleLearned() {
        val word = lastWord ?: return
        val updated = word.copy(isLearned = !word.isLearned)
        lastWord = updated
        scope.launch {
            repo.updateWordStats(updated)
        }
        updateLastWordUi()
    }

    private fun openWiki() {
        val word = lastWord ?: return
        val url = "https://de.wiktionary.org/wiki/" + Uri.encode(word.prefix + word.root)
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        activity.startActivity(intent)
    }

    private fun openTranslatorForCurrentWord() {
        val word = nextWord ?: return
        val text = word.translation
        val url = "https://translate.google.com/?sl=en&tl=de&text=" +
            Uri.encode(text) + "&op=translate"
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            setPackage("com.google.android.apps.translate")
        }
        try {
            activity.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            val webIntent = Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://www.google.com/search?q=" + Uri.encode(text))
            )
            activity.startActivity(webIntent)
        }
    }

    private fun updateLastWordUi() {
        val word = lastWord
        view.setLastButtonsEnabled(true)
        view.setFavoriteIcon(
            if (word?.isFavorite == true) R.drawable.ic_favorite else R.drawable.ic_favorite_border
        )
        view.setLearnedIcon(
            if (word?.isLearned == true) R.drawable.ic_learned_on else R.drawable.ic_learned_off
        )

        val favoriteHint = if (word == null) {
            strings(R.string.add_to_favorites)
        } else if (word.isFavorite) {
            strings(R.string.remove_from_favorites)
        } else {
            strings(R.string.add_to_favorites)
        }
        val learnedHint = if (word == null) {
            strings(R.string.add_to_learned)
        } else if (word.isLearned) {
            strings(R.string.remove_from_learned)
        } else {
            strings(R.string.add_to_learned)
        }
        view.setFavoriteHint(favoriteHint)
        view.setLearnedHint(learnedHint)
        view.setWikiHint(strings(R.string.last_word_wiki))
    }
}
