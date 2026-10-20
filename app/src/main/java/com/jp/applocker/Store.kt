package com.jp.applocker

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** days = bitmask, bit 0 = Monday ... bit 6 = Sunday. from/to = minutes since midnight, -1 = no schedule. */
data class Rule(val pkg: String, val limitMin: Int, val from: Int, val to: Int, val days: Int = ALL_DAYS) {
    val hasLimit: Boolean get() = limitMin > 0
    val hasWindow: Boolean get() = from >= 0 && to >= 0 && from != to
    companion object { const val ALL_DAYS = 127 }
}

fun fmtTime(m: Int): String = String.format(Locale.US, "%02d:%02d", m / 60, m % 60)

fun fmtDuration(min: Int): String = when {
    min < 60 -> "$min min"
    min % 60 == 0 -> "${min / 60} h"
    else -> "${min / 60} h ${min % 60} min"
}

private val DAY_SHORT = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

fun daysLabel(d: Int): String = when (d) {
    Rule.ALL_DAYS -> "Every day"
    31 -> "Weekdays"
    96 -> "Weekends"
    else -> DAY_SHORT.filterIndexed { i, _ -> (d and (1 shl i)) != 0 }.joinToString(", ")
}

fun Rule.windowActive(): Boolean {
    if (!hasWindow) return false
    val c = Calendar.getInstance()
    val m = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
    val today = (c.get(Calendar.DAY_OF_WEEK) + 5) % 7
    val yesterday = (today + 6) % 7
    fun on(d: Int) = (days and (1 shl d)) != 0
    return if (from < to) on(today) && m >= from && m < to
    else (m >= from && on(today)) || (m < to && on(yesterday))
}

object Store {
    private fun p(c: Context) = c.getSharedPreferences("lock", Context.MODE_PRIVATE)
    fun day(): String = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())

    fun maxPuzzles(c: Context) = p(c).getInt("maxPuzzles", 3)
    fun setMaxPuzzles(c: Context, v: Int) = p(c).edit().putInt("maxPuzzles", v).apply()
    fun unlockMin(c: Context) = p(c).getInt("unlockMin", 10)
    fun setUnlockMin(c: Context, v: Int) = p(c).edit().putInt("unlockMin", v).apply()
    fun puzzleMask(c: Context) = p(c).getInt("puzzleMask", 31)
    fun setPuzzleMask(c: Context, v: Int) = p(c).edit().putInt("puzzleMask", v).apply()
    fun enabled(c: Context) = p(c).getBoolean("enabled", false)
    fun setEnabled(c: Context, v: Boolean) = p(c).edit().putBoolean("enabled", v).apply()

    fun rules(c: Context): List<Rule> =
        (p(c).getString("rules", "") ?: "").lines().filter { it.isNotBlank() }.mapNotNull {
            val s = it.split(";")
            try {
                when (s.size) {
                    4 -> Rule(s[0], s[1].toInt(), s[2].toInt(), s[3].toInt())
                    5 -> Rule(s[0], s[1].toInt(), s[2].toInt(), s[3].toInt(), s[4].toInt())
                    else -> null
                }
            } catch (e: Exception) { null }
        }

    fun saveRules(c: Context, l: List<Rule>) = p(c).edit()
        .putString("rules", l.joinToString("\n") { "${it.pkg};${it.limitMin};${it.from};${it.to};${it.days}" }).apply()

    fun grantUntil(c: Context, pkg: String) = p(c).getLong("g_$pkg", 0L)
    fun grant(c: Context, pkg: String, until: Long) = p(c).edit().putLong("g_$pkg", until).apply()

    fun usedSec(c: Context, pkg: String) = p(c).getInt("u_${pkg}_${day()}", 0)
    fun setUsed(c: Context, pkg: String, v: Int) = p(c).edit().putInt("u_${pkg}_${day()}", v).apply()
}
