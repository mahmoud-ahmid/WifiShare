package com.example.wifishare.ui.settings

import android.app.Dialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.RadioButton
import android.widget.RadioGroup
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.navigation.fragment.findNavController
import androidx.lifecycle.lifecycleScope
import com.example.wifishare.R
import com.example.wifishare.BuildConfig
import com.example.wifishare.WifiShareApp
import com.example.wifishare.core.ThemeManager
import com.example.wifishare.core.toast
import com.example.wifishare.databinding.FragmentSettingsBinding
import com.example.wifishare.databinding.DialogChoiceBinding
import com.example.wifishare.ui.common.BaseFragment
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

class SettingsFragment : BaseFragment<FragmentSettingsBinding>(
    { i, p, a -> FragmentSettingsBinding.inflate(i, p, a) }
) {
    private var testing = false
    private val container by lazy { (requireActivity().application as WifiShareApp).container }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.itemTheme.setOnClickListener { showThemeModeDialog() }
        binding.itemAccent.setOnClickListener { findNavController().navigate(R.id.action_settings_to_colors) }
        binding.itemDownloadFolder.setOnClickListener { openDownloadFolder() }
        binding.itemNotifications.setOnClickListener {
            requireContext().toast(getString(R.string.coming_soon))
        }
        binding.itemClearHistory.setOnClickListener { confirmClearHistory() }
        binding.itemAbout.setOnClickListener {
            requireContext().toast(getString(R.string.settings_about_desc))
        }
        binding.itemPrivacy.setOnClickListener {
            requireContext().toast(getString(R.string.settings_privacy_desc))
        }
        binding.itemSelfTest.setOnClickListener { runSelfTest() }
        binding.tvAppVersion.text = "الإصدار ${BuildConfig.VERSION_NAME}"
        
        binding.itemNotifications.setOnClickListener { showNotificationsDialog() }
        binding.itemAbout.setOnClickListener { showAboutDialog() }
        binding.itemPrivacy.setOnClickListener { showPrivacyDialog() }
        refreshThemeLabels()
    }

    override fun onResume() {
        super.onResume()
        refreshThemeLabels()
    }

    private fun refreshThemeLabels() {
        val ctx = requireContext()
        binding.tvThemeMode.text = when (ThemeManager.getMode(ctx)) {
            ThemeManager.MODE_LIGHT -> getString(R.string.theme_light)
            ThemeManager.MODE_DARK -> getString(R.string.theme_dark)
            else -> getString(R.string.theme_system)
        }
        binding.tvAccent.text = when (ThemeManager.getAccent(ctx)) {
            ThemeManager.ACCENT_BLUE -> getString(R.string.accent_blue)
            ThemeManager.ACCENT_GREEN -> getString(R.string.accent_green)
            ThemeManager.ACCENT_ORANGE -> getString(R.string.accent_orange)
            ThemeManager.ACCENT_RED -> getString(R.string.accent_red)
            else -> getString(R.string.accent_purple)
        }
        val colorRes = when (ThemeManager.getAccent(ctx)) {
            ThemeManager.ACCENT_BLUE -> R.color.accent_blue
            ThemeManager.ACCENT_GREEN -> R.color.accent_green
            ThemeManager.ACCENT_ORANGE -> R.color.accent_orange
            ThemeManager.ACCENT_RED -> R.color.accent_red
            ThemeManager.ACCENT_TEAL -> R.color.accent_teal
            ThemeManager.ACCENT_PINK -> R.color.accent_pink
            ThemeManager.ACCENT_INDIGO -> R.color.accent_indigo
            else -> R.color.accent_purple
        }
        binding.viewAccentDot.setBackgroundColor(ContextCompat.getColor(ctx, colorRes))
    }

    // ────────────── حوار مخصص للوضع ──────────────
    private fun showThemeModeDialog() {
        val ctx = requireContext()
        val values = arrayOf(ThemeManager.MODE_LIGHT, ThemeManager.MODE_DARK, ThemeManager.MODE_SYSTEM)
        val labels = arrayOf(
            getString(R.string.theme_light),
            getString(R.string.theme_dark),
            getString(R.string.theme_system)
        )
        val current = values.indexOf(ThemeManager.getMode(ctx)).coerceAtLeast(0)
        showCustomChoiceDialog(
            getString(R.string.settings_theme_mode), labels, current
        ) { which ->
            ThemeManager.setMode(ctx, values[which])
            // لا حاجة لـ recreate يدوي، setDefaultNightMode يفعلها
            refreshThemeLabels()
        }
    }

    // ────────────── حوار مخصص للون ──────────────
    private fun showAccentDialog() {
        val ctx = requireContext()
        val values = arrayOf(
            ThemeManager.ACCENT_BLUE,
            ThemeManager.ACCENT_GREEN,
            ThemeManager.ACCENT_ORANGE,
            ThemeManager.ACCENT_PURPLE,
            ThemeManager.ACCENT_RED,
            ThemeManager.ACCENT_TEAL,
            ThemeManager.ACCENT_PINK,
            ThemeManager.ACCENT_INDIGO
        )
        val labels = arrayOf(
            getString(R.string.accent_blue),
            getString(R.string.accent_green),
            getString(R.string.accent_orange),
            getString(R.string.accent_purple),
            getString(R.string.accent_red),
            getString(R.string.accent_teal),
            getString(R.string.accent_pink),
            getString(R.string.accent_indigo)
        )
        val current = values.indexOf(ThemeManager.getAccent(ctx)).coerceAtLeast(0)
        AlertDialog.Builder(ctx)
            .setTitle(R.string.settings_accent)
            .setSingleChoiceItems(labels, current) { dialog, which ->
                dialog.dismiss()
                ThemeManager.setAccent(ctx, values[which])
                requireActivity().recreate()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    // ────────────── دالة الحوار المخصص ──────────────
    private fun showCustomChoiceDialog(
        title: String,
        labels: Array<String>,
        currentIndex: Int,
        onSelect: (Int) -> Unit
    ) {
        val bindingDialog = DialogChoiceBinding.inflate(LayoutInflater.from(requireContext()))
        bindingDialog.tvTitle.text = title

        // إنشاء RadioButtons ديناميكيًا
        for (i in labels.indices) {
            val rb = RadioButton(requireContext()).apply {
                text = labels[i]
                id = View.generateViewId()
                textSize = 16f
                setTextColor(ContextCompat.getColor(requireContext(), R.color.on_surface))
                setPadding(16, 24, 16, 24)
                isChecked = (i == currentIndex)
                buttonTintList = ContextCompat.getColorStateList(requireContext(), R.color.selector_radio)
            }
            bindingDialog.radioGroup.addView(rb)
        }

        val dialog = AlertDialog.Builder(requireContext())
            .setView(bindingDialog.root)
            .setNegativeButton(android.R.string.cancel, null)
            .create()

        dialog.setOnShowListener {
            // عند تغيير التحديد، نطبّق فورًا ونغلق
            bindingDialog.radioGroup.setOnCheckedChangeListener { group, checkedId ->
                for (i in 0 until group.childCount) {
                    val rb = group.getChildAt(i) as RadioButton
                    if (rb.id == checkedId) {
                        dialog.dismiss()
                        onSelect(i)
                        break
                    }
                }
            }
        }
        dialog.show()
    }

    // ────────────── باقي الوظائف ──────────────

    private fun showNotificationsDialog() {
        val prefs = requireContext().getSharedPreferences("settings", android.content.Context.MODE_PRIVATE)
        val enabled = prefs.getBoolean("notifications", true)
        android.app.AlertDialog.Builder(requireContext())
            .setTitle(R.string.settings_notifications)
            .setMessage("هل تريد إظهار إشعارات تقدم النقل؟")
            .setPositiveButton("نعم") { _, _ -> prefs.edit().putBoolean("notifications", true).apply() }
            .setNegativeButton("لا") { _, _ -> prefs.edit().putBoolean("notifications", false).apply() }
            .setNeutralButton("إلغاء", null)
            .show()
    }

    private fun showAboutDialog() {
        android.app.AlertDialog.Builder(requireContext())
            .setTitle(R.string.settings_about)
            .setMessage("WifiShare\n\nالإصدار: ${BuildConfig.VERSION_NAME}\n\nتطبيق مفتوح المصدر لنقل الملفات عبر الواي فاي المحلي.")
            .setPositiveButton("حسناً", null)
            .show()
    }

    private fun showPrivacyDialog() {
        android.app.AlertDialog.Builder(requireContext())
            .setTitle(R.string.settings_privacy)
            .setMessage("نحن لا نجمع أي بيانات.\nلا تتبع.\nلا إعلانات.\nكل شيء يحدث على جهازك.")
            .setPositiveButton("حسناً", null)
            .show()
    }

    private fun openDownloadFolder() {
        try {
            val dir = container.transferServer.receiveDir()
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(Uri.parse("content://com.android.externalstorage.documents/root/primary"), "resource/folder")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            try { startActivity(intent) } catch (_: Exception) { requireContext().toast(dir.absolutePath) }
        } catch (e: Exception) { requireContext().toast("Error: ${e.message}") }
    }

    private fun confirmClearHistory() {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.settings_clear_history)
            .setMessage(R.string.settings_clear_history_desc)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                container.historyRepository.clear()
                requireContext().toast(getString(R.string.settings_clear_done))
            }
            .setNegativeButton(android.R.string.cancel, null).show()
    }

    private fun runSelfTest() {
        if (testing) return
        testing = true
        binding.tvSelfTestStatus.text = getString(R.string.settings_selftest_running)
        viewLifecycleOwner.lifecycleScope.launch {
            val startedAt = System.currentTimeMillis()
            try {
                val ctx = requireContext()
                val testFile = File(ctx.cacheDir, "selftest_${System.currentTimeMillis()}.txt")
                testFile.writeText("WifiShare self-test\n${System.currentTimeMillis()}\n" + "X".repeat(100_000))
                val sentSize = testFile.length()
                val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", testFile)
                val testToken = com.example.wifishare.core.SessionToken.regenerate()
                val receiveDeferred = async { delay(300); container.transferServer.receiveOnce(testToken) }
                delay(900)
                val sendResult = container.transferClient.send("127.0.0.1", listOf(uri), testToken)
                val receiveResult = receiveDeferred.await()
                val elapsed = System.currentTimeMillis() - startedAt
                when {
                    sendResult.isFailure -> binding.tvSelfTestStatus.text =
                        getString(R.string.settings_selftest_failed, "Send: " + sendResult.exceptionOrNull()?.message)
                    receiveResult.isFailure -> binding.tvSelfTestStatus.text =
                        getString(R.string.settings_selftest_failed, "Recv: " + receiveResult.exceptionOrNull()?.message)
                    else -> {
                        val files = receiveResult.getOrNull().orEmpty()
                        val recvSize = files.firstOrNull()?.length() ?: 0L
                        binding.tvSelfTestStatus.text = if (recvSize != sentSize)
                            getString(R.string.settings_selftest_failed, "Size $sentSize vs $recvSize")
                        else getString(R.string.settings_selftest_passed, sentSize.toInt(), elapsed.toInt())
                    }
                }
                testFile.delete()
            } catch (e: Exception) {
                binding.tvSelfTestStatus.text = getString(R.string.settings_selftest_failed, e.message ?: "")
            } finally { testing = false }
        }
    }
}
