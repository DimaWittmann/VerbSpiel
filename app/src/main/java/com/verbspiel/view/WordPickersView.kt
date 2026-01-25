package com.verbspiel.view

import android.content.res.Resources
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.util.TypedValue
import android.view.View
import android.widget.ImageView
import android.widget.NumberPicker
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.verbspiel.R

class WordPickersView(
    root: View,
    private val resources: Resources
) {
    private val listLeft: NumberPicker = root.findViewById(R.id.list_left)
    private val listRight: NumberPicker = root.findViewById(R.id.list_right)
    private val arrowPrefixUp: ImageView = root.findViewById(R.id.arrow_prefix_up)
    private val arrowPrefixDown: ImageView = root.findViewById(R.id.arrow_prefix_down)
    private val arrowRootUp: ImageView = root.findViewById(R.id.arrow_root_up)
    private val arrowRootDown: ImageView = root.findViewById(R.id.arrow_root_down)

    init {
        arrowPrefixUp.setOnClickListener { moveLeft(-1) }
        arrowPrefixDown.setOnClickListener { moveLeft(1) }
        arrowRootUp.setOnClickListener { moveRight(-1) }
        arrowRootDown.setOnClickListener { moveRight(1) }
    }

    fun updatePickers(
        leftItems: Array<String>,
        leftDisplayItems: Array<String>,
        rightItems: Array<String>,
        rightDisplayItems: Array<String>
    ) {
        createNumberPicker(listLeft, leftItems, leftDisplayItems)
        createNumberPicker(listRight, rightItems, rightDisplayItems)
    }

    fun leftIndex(): Int = listLeft.value

    fun rightIndex(): Int = listRight.value

    fun moveLeft(delta: Int) {
        movePicker(listLeft, delta)
    }

    fun moveRight(delta: Int) {
        movePicker(listRight, delta)
    }

    private fun createNumberPicker(
        np: NumberPicker,
        items: Array<String>,
        displayItems: Array<String>
    ) {
        np.displayedValues = null
        if (items.isEmpty()) {
            np.minValue = 0
            np.maxValue = 0
            np.wrapSelectorWheel = false
            return
        }
        np.minValue = 0
        np.maxValue = items.size - 1
        np.wrapSelectorWheel = items.size > 1
        np.displayedValues = displayItems
        styleNumberPicker(np)
    }

    private fun movePicker(np: NumberPicker, delta: Int) {
        if (np.maxValue < np.minValue) return
        val current = np.value
        val next = current + delta
        val wrapped = when {
            next < np.minValue && np.wrapSelectorWheel -> np.maxValue
            next > np.maxValue && np.wrapSelectorWheel -> np.minValue
            else -> next.coerceIn(np.minValue, np.maxValue)
        }
        np.value = wrapped
    }

    private fun styleNumberPicker(np: NumberPicker) {
        val desiredSp = 16f
        for (i in 0 until np.childCount) {
            val child = np.getChildAt(i)
            if (child is TextView) {
                child.setTextSize(TypedValue.COMPLEX_UNIT_SP, desiredSp)
                child.setTextColor(ContextCompat.getColor(np.context, R.color.black))
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val sizeInPx = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_SP, desiredSp, resources.displayMetrics
            )
            np.setTextSize(sizeInPx)
        } else {
            try {
                val fields = NumberPicker::class.java.declaredFields
                for (field in fields) {
                    if (field.name == "mSelectionDivider") {
                        field.isAccessible = true
                        field.set(np, ColorDrawable(ContextCompat.getColor(np.context, R.color.teal_700)))
                        break
                    }
                }
            } catch (_: Exception) {
            }
        }
    }
}
