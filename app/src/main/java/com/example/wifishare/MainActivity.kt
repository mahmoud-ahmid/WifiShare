package com.example.wifishare

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.example.wifishare.core.Logger
import com.example.wifishare.core.ThemeManager
import com.example.wifishare.core.toastLong
import com.example.wifishare.data.files.IncomingShareHandler
import com.example.wifishare.databinding.ActivityMainBinding
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.applyToActivity(this)
        applyBgMode()
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val navHost = supportFragmentManager
            .findFragmentById(R.id.navHost) as NavHostFragment
        val navController = navHost.navController
        binding.bottomNav.setupWithNavController(navController)

        val topLevel = setOf(
            R.id.homeFragment, R.id.filesFragment,
            R.id.historyFragment, R.id.settingsFragment
        )
        navController.addOnDestinationChangedListener { _, d, _ ->
            binding.bottomNav.isVisible = d.id in topLevel
        }

        handleIncomingIntent(intent, navController)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val navHost = supportFragmentManager
            .findFragmentById(R.id.navHost) as NavHostFragment
        handleIncomingIntent(intent, navHost.navController)
    }

    private fun applyBgMode() {
        val mode = ThemeManager.getBgMode(this)
        val resId = when (mode) {
            "dark" -> 0 // افتراضي
            "light" -> 0
            "default" -> 0
            else -> resources.getIdentifier(mode, "drawable", packageName)
        }
        if (resId != 0) window.decorView.setBackgroundResource(resId)
    }

    private fun handleIncomingIntent(intent: Intent?, navController: androidx.navigation.NavController) {
        if (intent == null) return
        val action = intent.action ?: return
        if (action != Intent.ACTION_SEND && action != Intent.ACTION_SEND_MULTIPLE) return

        lifecycleScope.launch {
            val saved = IncomingShareHandler.handle(this@MainActivity, intent)
            if (saved.isEmpty()) {
                toastLong("لم يتم حفظ أي ملف — تحقق من الصلاحيات")
                return@launch
            }
            val paths = saved.joinToString("\n") { it.name }
            toastLong("تم حفظ ${saved.size} ملف:\n$paths")
            Logger.i("Saved ${saved.size} files to ${saved.first().parentFile}")
            try {
                navController.navigate(R.id.filesFragment)
            } catch (_: Exception) {}
        }
    }
}
