package com.jp.applocker

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class Rule(val pkg: String, val limitMin: Int, val from: Int, val to: Int)

fun Context.dp(x: Int) = (x * resources.displayMetrics.density).toInt()

object Store {
    private fun p(c: Context) = c.getSharedPreferences("lock", Context.MODE_PRIVATE)
    fun day(): String = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())

    fun maxPuzzles(c: Context) = p(c).getInt("maxPuzzles", 3)
    fun setMaxPuzzles(c: Context, v: Int) = p(c).edit().putInt("maxPuzzles", v).apply()
    fun unlockMin(c: Context) = p(c).getInt("unlockMin", 10)
    fun setUnlockMin(c: Context, v: Int) = p(c).edit().putInt("unlockMin", v).apply()
    fun enabled(c: Context) = p(c).getBoolean("enabled", false)
    fun setEnabled(c: Context, v: Boolean) = p(c).edit().putBoolean("enabled", v).apply()

    fun rules(c: Context): List<Rule> =
        (p(c).getString("rules", "") ?: "").lines().filter { it.isNotBlank() }.mapNotNull {
            val s = it.split(";")
            if (s.size == 4) Rule(s[0], s[1].toInt(), s[2].toInt(), s[3].toInt()) else null
        }

    fun saveRules(c: Context, l: List<Rule>) = p(c).edit()
        .putString("rules", l.joinToString("\n") { "${it.pkg};${it.limitMin};${it.from};${it.to}" }).apply()

    fun grantUntil(c: Context, pkg: String) = p(c).getLong("g_$pkg", 0L)
    fun grant(c: Context, pkg: String, until: Long) = p(c).edit().putLong("g_$pkg", until).apply()

    fun usedSec(c: Context, pkg: String) = p(c).getInt("u_${pkg}_${day()}", 0)
    fun setUsed(c: Context, pkg: String, v: Int) = p(c).edit().putInt("u_${pkg}_${day()}", v).apply()
}
