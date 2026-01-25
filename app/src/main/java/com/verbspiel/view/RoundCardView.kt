package com.verbspiel.view

import android.view.View
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.widget.TooltipCompat
import androidx.core.content.ContextCompat
import com.verbspiel.R

class RoundCardView(
    root: View
) {
    private val nextWordText: TextView = root.findViewById(R.id.next_word_text)
    private val translateButton: ImageButton = root.findViewById(R.id.button_translate_next)
    private val translationText: TextView = root.findViewById(R.id.translation_text)
    private val exampleText: TextView = root.findViewById(R.id.example_text)
    private val filterStatus: TextView = root.findViewById(R.id.filter_status)
    private val buttonLastFavorite: ImageButton = root.findViewById(R.id.button_last_favorite)
    private val buttonLastLearned: ImageButton = root.findViewById(R.id.button_last_learned)
    private val buttonLastWiki: ImageButton = root.findViewById(R.id.button_last_wiki)
    private val progressBar: ProgressBar = root.findViewById(R.id.progress_bar)

    fun setNextWord(text: String) {
        setTextWithTooltip(nextWordText, text)
    }

    fun setTranslation(text: String) {
        setTextWithTooltip(translationText, text)
    }

    fun setExample(text: String) {
        setTextWithTooltip(exampleText, text)
    }

    fun setStatus(text: String, colorRes: Int?) {
        filterStatus.text = text
        val color = if (colorRes != null) {
            ContextCompat.getColor(filterStatus.context, colorRes)
        } else {
            ContextCompat.getColor(filterStatus.context, R.color.black)
        }
        filterStatus.setTextColor(color)
        TooltipCompat.setTooltipText(filterStatus, text)
    }

    fun setOnTranslate(action: () -> Unit) {
        nextWordText.setOnClickListener { action() }
        translateButton.setOnClickListener { action() }
    }

    fun setOnFavorite(action: () -> Unit) {
        buttonLastFavorite.setOnClickListener { action() }
    }

    fun setOnLearned(action: () -> Unit) {
        buttonLastLearned.setOnClickListener { action() }
    }

    fun setOnWiki(action: () -> Unit) {
        buttonLastWiki.setOnClickListener { action() }
    }

    fun setFavoriteIcon(resId: Int) {
        buttonLastFavorite.setImageResource(resId)
    }

    fun setLearnedIcon(resId: Int) {
        buttonLastLearned.setImageResource(resId)
    }

    fun setFavoriteHint(text: String) {
        TooltipCompat.setTooltipText(buttonLastFavorite, text)
        buttonLastFavorite.contentDescription = text
    }

    fun setLearnedHint(text: String) {
        TooltipCompat.setTooltipText(buttonLastLearned, text)
        buttonLastLearned.contentDescription = text
    }

    fun setWikiHint(text: String) {
        TooltipCompat.setTooltipText(buttonLastWiki, text)
        buttonLastWiki.contentDescription = text
    }

    fun setLastButtonsEnabled(enabled: Boolean) {
        buttonLastFavorite.isEnabled = enabled
        buttonLastLearned.isEnabled = enabled
        buttonLastWiki.isEnabled = enabled
    }

    fun setProgress(max: Int, value: Int) {
        progressBar.max = max
        progressBar.setProgress(value, true)
    }

    fun initProgress(max: Int) {
        progressBar.max = max
        progressBar.min = 0
        progressBar.setProgress(0)
    }

    private fun setTextWithTooltip(view: TextView, text: String) {
        view.text = text
        TooltipCompat.setTooltipText(view, text)
    }
}
