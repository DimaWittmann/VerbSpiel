package com.example.verb_spiel

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageButton
import android.widget.TextView

class StatsWordAdapter(
    context: Context,
    private val words: MutableList<Word>,
    private val formatter: (Word) -> String,
    private val onOptionsClick: (Word) -> Unit
) : ArrayAdapter<Word>(context, R.layout.list_item_stats_action, words) {

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: LayoutInflater.from(context)
            .inflate(R.layout.list_item_stats_action, parent, false)
        val word = words[position]

        val textView = view.findViewById<TextView>(R.id.list_item_text)
        val optionsButton = view.findViewById<ImageButton>(R.id.list_item_more)

        textView.text = formatter(word)
        optionsButton.setOnClickListener { onOptionsClick(word) }

        return view
    }

    fun updateWord(updated: Word) {
        val index = words.indexOfFirst { it.id == updated.id }
        if (index >= 0) {
            words[index] = updated
            notifyDataSetChanged()
        }
    }
}
