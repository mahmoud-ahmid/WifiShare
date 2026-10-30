package com.example.wifishare.ui.settings

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import androidx.navigation.fragment.findNavController
import com.example.wifishare.R
import com.example.wifishare.core.ThemeManager
import com.example.wifishare.databinding.FragmentColorCustomizationBinding
import com.example.wifishare.ui.common.BaseFragment

class ColorCustomizationFragment : BaseFragment<FragmentColorCustomizationBinding>(
    { i, p, a -> FragmentColorCustomizationBinding.inflate(i, p, a) }
) {
    private val buttonColors = listOf("#5B5BD6", "#1A73E8", "#2E7D32", "#E65100", "#B00020", "#00897B", "#C2185B", "#3949AB")
    private val textColors = listOf("#FFFFFF", "#000000", "#F44336", "#4CAF50", "#2196F3", "#FFEB3B", "#9C27B0")
    private val bgModes = listOf("default", "dark", "light", "grad_sunset", "grad_ocean", "grad_forest", "grad_lavender", "grad_candy")

    private var selectedBaseColor = Color.parseColor("#5B5BD6")

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.toolbar.setNavigationOnClickListener { findNavController().popBackStack() }

        buildButtonColors()
        buildTextColors()
        buildBgModes()
        setupCustomControls()
    }

    private fun buildButtonColors() {
        binding.layoutButtonColors.removeAllViews()
        val current = ThemeManager.getAccent(requireContext())
        for (hex in buttonColors) {
            val v = createColorView(hex, hex == current)
            v.setOnClickListener {
                selectedBaseColor = Color.parseColor(hex)
                updatePreview()
                ThemeManager.setAccent(requireContext(), hex)
                buildButtonColors() // لتحديث علامة الصح
                requireActivity().recreate()
            }
            binding.layoutButtonColors.addView(v)
        }
    }

    private fun buildTextColors() {
        binding.layoutTextColors.removeAllViews()
        val current = ThemeManager.getTextColor(requireContext())
        for (hex in textColors) {
            val v = createColorView(hex, hex == current)
            v.setOnClickListener {
                ThemeManager.setTextColor(requireContext(), hex)
                buildTextColors()
                requireActivity().recreate()
            }
            binding.layoutTextColors.addView(v)
        }
    }

    private fun buildBgModes() {
        binding.layoutBgModes.removeAllViews()
        val current = ThemeManager.getBgMode(requireContext())
        for (mode in bgModes) {
            val v = View(requireContext()).apply {
                val size = dp(64)
                layoutParams = LinearLayout.LayoutParams(size, size).apply { setMargins(0, 0, dp(8), 0) }
                if (mode == "default") setBackgroundColor(Color.parseColor("#888888"))
                else if (mode == "dark") setBackgroundColor(Color.parseColor("#121212"))
                else if (mode == "light") setBackgroundColor(Color.parseColor("#FAFAFA"))
                else background = ContextCompat.getDrawable(requireContext(), resources.getIdentifier(mode, "drawable", requireContext().packageName))
                if (mode == current) foreground = ContextCompat.getDrawable(requireContext(), R.drawable.ic_check)
            }
            v.setOnClickListener {
                ThemeManager.setBgMode(requireContext(), mode)
                buildBgModes()
                requireActivity().recreate()
            }
            binding.layoutBgModes.addView(v)
        }
    }

    private fun setupCustomControls() {
        // تحديث المعاينة عند تحريك أشرطة التمرير
        val update = {
            updatePreview()
        }
        binding.seekOpacity.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) { update() }
            override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {}
        })
        binding.seekBrightness.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) { update() }
            override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {}
        })

        binding.btnApplyCustom.setOnClickListener {
            val hsv = FloatArray(3)
            Color.colorToHSV(selectedBaseColor, hsv)
            // ضبط السطوع بناءً على شريط التمرير (0-100 يتحول إلى 0.0-1.0)
            hsv[2] = binding.seekBrightness.progress / 100f
            val alpha = binding.seekOpacity.progress
            val finalColor = Color.HSVToColor(alpha, hsv)
            val hex = String.format("#%08X", finalColor)
            
            ThemeManager.setAccent(requireContext(), hex)
            requireActivity().recreate()
        }
        
        updatePreview()
    }

    private fun updatePreview() {
        val hsv = FloatArray(3)
        Color.colorToHSV(selectedBaseColor, hsv)
        hsv[2] = binding.seekBrightness.progress / 100f
        val alpha = binding.seekOpacity.progress
        val finalColor = Color.HSVToColor(alpha, hsv)
        binding.viewPreview.setBackgroundColor(finalColor)
    }

    private fun createColorView(hex: String, isSelected: Boolean): View {
        return View(requireContext()).apply {
            val size = dp(48)
            layoutParams = LinearLayout.LayoutParams(size, size).apply { setMargins(0, 0, dp(8), 0) }
            background = ContextCompat.getDrawable(requireContext(), R.drawable.bg_circle)
            setBackgroundColor(Color.parseColor(hex))
            if (isSelected) foreground = ContextCompat.getDrawable(requireContext(), R.drawable.ic_check)
        }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
