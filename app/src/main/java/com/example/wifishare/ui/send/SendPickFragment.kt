package com.example.wifishare.ui.send

import android.os.Bundle
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.core.os.bundleOf
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.viewpager2.widget.ViewPager2
import com.example.wifishare.R
import com.example.wifishare.core.toast
import com.example.wifishare.data.files.PendingSendHolder
import com.example.wifishare.data.send.SendItem
import com.example.wifishare.databinding.FragmentSendPickBinding
import com.example.wifishare.ui.common.BaseFragment
import com.google.android.material.tabs.TabLayoutMediator
import kotlinx.coroutines.launch

class SendPickFragment : BaseFragment<FragmentSendPickBinding>(
    { i, p, a -> FragmentSendPickBinding.inflate(i, p, a) }
) {
    private val viewModel: SendPickViewModel by activityViewModels {
        SendPickViewModel.Factory(requireActivity().application)
    }
    private lateinit var pagerAdapter: SendItemsPagerAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        pagerAdapter = SendItemsPagerAdapter(this)
        binding.viewPager.adapter = pagerAdapter
        binding.viewPager.offscreenPageLimit = 1

        val titles = listOf(
            getString(R.string.tab_apps),
            getString(R.string.tab_audio),
            getString(R.string.tab_images),
            getString(R.string.tab_documents),
            getString(R.string.tab_files)
        )
        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, pos ->
            tab.text = titles[pos]
        }.attach()

        binding.btnBack.setOnClickListener { handleBack() }
        binding.btnSelectAll.setOnClickListener { currentFrag()?.selectAll() }
        binding.btnSend.setOnClickListener { startSending() }

        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) { updateSelectAllLabel() }
        })

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.selection.countFlow.collect { updateBottomBar(it) }
        }

        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() { handleBack() }
            }
        )
    }

    private fun currentFrag(): SendCategoryFragment? {
        val pos = binding.viewPager.currentItem
        return childFragmentManager.findFragmentByTag("f$pos") as? SendCategoryFragment
    }

    private fun handleBack() {
        if (!viewModel.selection.isEmpty()) {
            viewModel.selection.clear(); return
        }
        if (currentFrag()?.onBackPressed() == true) return
        findNavController().popBackStack()
    }

    private fun updateBottomBar(count: Int) {
        binding.tvSelectedCount.text = if (count == 0)
            getString(R.string.send_selected_count_zero)
        else getString(R.string.send_selected_count, count)
        binding.btnSend.isEnabled = count > 0
        updateSelectAllLabel()
    }

    private fun updateSelectAllLabel() {
        val items = currentFrag()?.currentItems() ?: emptyList()
        val all = items.isNotEmpty() && viewModel.selection.allSelected(items)
        binding.btnSelectAll.text = if (all) getString(R.string.send_unselect_all)
        else getString(R.string.send_select_all)
    }

    private fun startSending() {
        val uris = viewModel.selection.uris()
        if (uris.isEmpty()) {
            requireContext().toast(getString(R.string.send_no_files)); return
        }
        PendingSendHolder.set(uris)
        val arr = uris.map { it.toString() }.toTypedArray()
        findNavController().navigate(
            R.id.action_sendPick_to_sendFind,
            bundleOf("uris" to arr)
        )
    }
}
