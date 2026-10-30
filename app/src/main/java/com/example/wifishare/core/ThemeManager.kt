package com.example.wifishare.core

import android.app.Activity
import android.content.Context
import android.graphics.Color
import androidx.appcompat.app.AppCompatDelegate
import com.example.wifishare.R

object ThemeManager {
    private const val PREFS = "wifishare_theme"
    private const val KEY_MODE = "mode"
    private const val KEY_ACCENT = "accent"
    private const val KEY_TEXT_COLOR = "text_color"
    private const val KEY_BG_MODE = "bg_mode"

    const val MODE_SYSTEM = "system"
    const val MODE_LIGHT = "light"
    const val MODE_DARK = "dark"

    // القيم الافتراضية للألوان
    const val ACCENT_PURPLE = "#5B5BD6"
    const val ACCENT_BLUE = "#1A73E8"
    const val ACCENT_GREEN = "#2E7D32"
    const val ACCENT_ORANGE = "#E65100"
    const val ACCENT_RED = "#B00020"
    const val ACCENT_TEAL = "#00897B"
    const val ACCENT_PINK = "#C2185B"
    const val ACCENT_INDIGO = "#3949AB"

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun getMode(ctx: Context): String = prefs(ctx).getString(KEY_MODE, MODE_SYSTEM) ?: MODE_SYSTEM
    fun setMode(ctx: Context, mode: String) {
        prefs(ctx).edit().putString(KEY_MODE, mode).commit()
        applyNightMode(mode)
    }

    fun getAccent(ctx: Context): String = prefs(ctx).getString(KEY_ACCENT, ACCENT_PURPLE) ?: ACCENT_PURPLE
    fun setAccent(ctx: Context, accentHex: String) {
        prefs(ctx).edit().putString(KEY_ACCENT, accentHex).commit()
    }

    fun getTextColor(ctx: Context): String = prefs(ctx).getString(KEY_TEXT_COLOR, "default") ?: "default"
    fun setTextColor(ctx: Context, colorHex: String) {
        prefs(ctx).edit().putString(KEY_TEXT_COLOR, colorHex).commit()
    }

    fun getBgMode(ctx: Context): String = prefs(ctx).getString(KEY_BG_MODE, "default") ?: "default"
    fun setBgMode(ctx: Context, mode: String) {
        prefs(ctx).edit().putString(KEY_BG_MODE, mode).commit()
    }

    fun applyNightMode(mode: String) {
        val m = when (mode) {
            MODE_LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
            MODE_DARK -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        if (AppCompatDelegate.getDefaultNightMode() != m) {
            AppCompatDelegate.setDefaultNightMode(m)
        }
    }

    fun applyToActivity(activity: Activity) {
        applyNightMode(getMode(activity))
    }
}
