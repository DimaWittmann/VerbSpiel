package com.verbspiel.view

import android.content.res.Resources
import android.view.View
import android.widget.Button
import android.widget.HorizontalScrollView
import android.widget.ImageView
import com.verbspiel.R

class ButtonsBarView(
    root: View,
    private val resources: Resources
) {
    private val resetPrimaryButton: Button = root.findViewById(R.id.button_reset_primary)
    private val combineButton: Button = root.findViewById(R.id.button_combine)
    private val skipButton: Button = root.findViewById(R.id.button_skip)
    private val statsButton: Button = root.findViewById(R.id.button_stats)
    private val filterButton: Button = root.findViewById(R.id.button_filter)
    private val difficultyButton: Button = root.findViewById(R.id.button_difficulty)
    private val resetButton: Button = root.findViewById(R.id.button_reset)

    private val buttonsScroll: HorizontalScrollView = root.findViewById(R.id.buttons_scroll)
    private val arrowButtonsLeft: ImageView = root.findViewById(R.id.arrow_buttons_left)
    private val arrowButtonsRight: ImageView = root.findViewById(R.id.arrow_buttons_right)

    fun setupScroll() {
        buttonsScroll.post {
            val step = combineButton.width + resources.displayMetrics.density.times(12).toInt()
            arrowButtonsLeft.setOnClickListener { buttonsScroll.smoothScrollBy(-step, 0) }
            arrowButtonsRight.setOnClickListener { buttonsScroll.smoothScrollBy(step, 0) }
        }
    }

    fun setOnCombine(action: () -> Unit) {
        combineButton.setOnClickListener { action() }
    }

    fun setOnSkip(action: () -> Unit) {
        skipButton.setOnClickListener { action() }
    }

    fun setOnStats(action: () -> Unit) {
        statsButton.setOnClickListener { action() }
    }

    fun setOnFilter(action: () -> Unit) {
        filterButton.setOnClickListener { action() }
    }

    fun setOnDifficulty(action: () -> Unit) {
        difficultyButton.setOnClickListener { action() }
    }

    fun setOnResetPrimary(action: () -> Unit) {
        resetPrimaryButton.setOnClickListener { action() }
    }

    fun setOnReset(action: () -> Unit) {
        resetButton.setOnClickListener { action() }
    }

    fun setRoundEnded(ended: Boolean) {
        combineButton.visibility = if (ended) View.GONE else View.VISIBLE
        skipButton.visibility = if (ended) View.GONE else View.VISIBLE
        resetButton.visibility = if (ended) View.GONE else View.VISIBLE
        resetPrimaryButton.visibility = if (ended) View.VISIBLE else View.GONE
    }
}
