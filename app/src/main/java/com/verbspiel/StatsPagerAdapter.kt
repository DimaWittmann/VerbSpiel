package com.verbspiel

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter

class StatsPagerAdapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {
    override fun getItemCount(): Int = 4

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> RecentCombinedStatsFragment()
            1 -> TopWordsFragment()
            2 -> RetiredWordsFragment()
            else -> FavoritesWordsFragment()
        }
    }
}
