package com.example.verb_spiel

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter

class StatsPagerAdapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {
    override fun getItemCount(): Int = 3

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> RecentStatsFragment.newInstance(RecentStatsFragment.TYPE_FAILURES)
            1 -> RecentStatsFragment.newInstance(RecentStatsFragment.TYPE_CORRECT)
            else -> TopWordsFragment()
        }
    }
}
