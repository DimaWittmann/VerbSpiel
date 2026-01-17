package com.verbspiel

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter

class StatsPagerAdapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {
    override fun getItemCount(): Int = 5

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> RecentStatsFragment.newInstance(RecentStatsFragment.TYPE_FAILURES)
            1 -> RecentStatsFragment.newInstance(RecentStatsFragment.TYPE_CORRECT)
            2 -> TopWordsFragment()
            3 -> RetiredWordsFragment()
            else -> FavoritesWordsFragment()
        }
    }
}
