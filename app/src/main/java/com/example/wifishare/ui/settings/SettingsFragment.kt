package com.example.wifishare.ui.settings

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.example.wifishare.R
import com.example.wifishare.WifiShareApp
import com.example.wifishare.core.SessionToken
import com.example.wifishare.core.toast
import com.example.wifishare.databinding.FragmentSettingsBinding
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
    }

    private fun openDownloadFolder() {
        try {
            val dir = container.transferServer.receiveDir()
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(
                    Uri.parse("content://com.android.externalstorage.documents/root/primary"),
                    "resource/folder"
                )
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            try { startActivity(intent) }
            catch (_: Exception) { requireContext().toast(dir.absolutePath) }
        } catch (e: Exception) {
            requireContext().toast("Error: ${e.message}")
        }
    }

    private fun confirmClearHistory() {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.settings_clear_history)
            .setMessage(R.string.settings_clear_history_desc)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                container.historyRepository.clear()
                requireContext().toast(getString(R.string.settings_clear_done))
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
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
                val filler = "X".repeat(100_000)
                testFile.writeText("WifiShare self-test\n${System.currentTimeMillis()}\n$filler")
                val sentSize = testFile.length()
                val uri = FileProvider.getUriForFile(
                    ctx, "${ctx.packageName}.fileprovider", testFile
                )
                SessionToken.regenerate()
                val receiveDeferred = async {
                    delay(300)
                    container.transferServer.receiveOnce()
                }
                delay(900)
                val sendResult = container.transferClient.send("127.0.0.1", listOf(uri), SessionToken.current)
                val receiveResult = receiveDeferred.await()
                val elapsed = System.currentTimeMillis() - startedAt
                when {
                    sendResult.isFailure ->
                        fail("Send: ${sendResult.exceptionOrNull()?.message}")
                    receiveResult.isFailure ->
                        fail("Recv: ${receiveResult.exceptionOrNull()?.message}")
                    else -> {
                        val files = receiveResult.getOrNull().orEmpty()
                        val recvSize = files.firstOrNull()?.length() ?: 0L
                        if (recvSize != sentSize) fail("Size mismatch: $sentSize vs $recvSize")
                        else binding.tvSelfTestStatus.text =
                            getString(R.string.settings_selftest_passed, sentSize.toInt(), elapsed.toInt())
                    }
                }
                testFile.delete()
            } catch (e: Exception) {
                fail("Exception: ${e.message}")
            } finally {
                testing = false
            }
        }
    }

    private fun fail(msg: String) {
        binding.tvSelfTestStatus.text = getString(R.string.settings_selftest_failed, msg)
    }
}
