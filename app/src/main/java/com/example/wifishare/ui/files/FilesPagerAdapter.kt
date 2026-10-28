package com.example.wifishare.ui.files

import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.example.wifishare.data.files.Source

class FilesPagerAdapter(fragment: Fragment) : FragmentStateAdapter(fragment) {

    private val sources = listOf(
        Source.RECEIVED,
        Source.IMAGES,
        Source.VIDEOS,
        Source.AUDIO,
        Source.DOCUMENTS
    )

    override fun getItemCount(): Int = sources.size

    override fun createFragment(position: Int): Fragment {
        return MediaListFragment().apply {
            arguments = Bundle().apply {
                putString(MediaListFragment.ARG_SOURCE, sources[position].name)
            }
        }
    }
}
