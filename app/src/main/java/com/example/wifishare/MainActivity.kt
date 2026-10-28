package com.example.wifishare

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.example.wifishare.core.Logger
import com.example.wifishare.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val navHost = supportFragmentManager
            .findFragmentById(R.id.navHost) as NavHostFragment
        val navController = navHost.navController
        binding.bottomNav.setupWithNavController(navController)

        val topLevel = setOf(
            R.id.homeFragment,
            R.id.filesFragment,
            R.id.historyFragment,
            R.id.settingsFragment
        )
        navController.addOnDestinationChangedListener { _, d, _ ->
            binding.bottomNav.isVisible = d.id in topLevel
        }

        Logger.i("MainActivity created (v${BuildConfig.VERSION_NAME})")
    }
}
