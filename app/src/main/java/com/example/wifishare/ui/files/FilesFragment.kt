package com.example.wifishare.ui.files

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.navigation.fragment.findNavController
import com.example.wifishare.R
import com.example.wifishare.databinding.FragmentFilesBinding
import com.example.wifishare.ui.common.BaseFragment
import com.google.android.material.tabs.TabLayoutMediator

class FilesFragment : BaseFragment<FragmentFilesBinding>(
    { i, p, a -> FragmentFilesBinding.inflate(i, p, a) }
) {
    private val permLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val titles = listOf(
            getString(R.string.tab_received),
            getString(R.string.tab_images),
            getString(R.string.tab_videos),
            getString(R.string.tab_audio),
            getString(R.string.tab_documents),
            getString(R.string.tab_apps)
        )
        val pagerAdapter = FilesPagerAdapter(this)
        binding.viewPager.adapter = pagerAdapter
        binding.viewPager.offscreenPageLimit = 1
        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, pos ->
            tab.text = titles[pos]
        }.attach()

        // زر "المنقول/المستلم" → الانتقال للسجل في BottomNav
        binding.cardReceived.setOnClickListener {
            findNavController().navigate(R.id.historyFragment)
        }

        // زر "مجلدات هاتفي" → فتح تبويب المجلدات
        binding.cardMyFolders.setOnClickListener {
            findNavController().navigate(R.id.folderBrowserFragment)
        }

        requestPermissionsIfNeeded()
    }

    override fun onResume() {
        super.onResume()
        // إجبار fragments التبويبات على إعادة التحميل
        try {
            val adapter = binding.viewPager.adapter ?: return
            val count = adapter.itemCount
            for (i in 0 until count) {
                val f = childFragmentManager.findFragmentByTag("f$i") as? MediaListFragment
                f?.onResume()
            }
        } catch (_: Exception) {}
    }

    private fun requestPermissionsIfNeeded() {
        val needed = if (Build.VERSION.SDK_INT >= 33) arrayOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO,
            Manifest.permission.READ_MEDIA_AUDIO
        ) else arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)

        val missing = needed.filter {
            ContextCompat.checkSelfPermission(requireContext(), it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) permLauncher.launch(missing.toTypedArray())
    }
}
