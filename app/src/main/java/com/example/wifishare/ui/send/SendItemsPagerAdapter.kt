package com.example.wifishare.ui.send

import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.example.wifishare.data.send.SendItem

class SendItemsPagerAdapter(fragment: Fragment) : FragmentStateAdapter(fragment) {
    private val cats = listOf(
        SendItem.Category.APPLICATIONS,
        SendItem.Category.AUDIO,
        SendItem.Category.IMAGES,
        SendItem.Category.DOCUMENTS,
        SendItem.Category.FILES
    )
    override fun getItemCount(): Int = cats.size
    override fun createFragment(position: Int): Fragment {
        return SendCategoryFragment().apply {
            arguments = Bundle().apply {
                putString(SendCategoryFragment.ARG_CATEGORY, cats[position].name)
            }
        }
    }
}
