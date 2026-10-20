package com.jp.applocker

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.AppOpsManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.provider.Settings
import android.text.InputType
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var list: LinearLayout
    private lateinit var maxF: EditText
    private lateinit var minF: EditText

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
    private fun btn(t: String, f: () -> Unit) = Button(this).apply { text = t; setOnClickListener { f() } }
    private fun label(s: String, sp: Float) = TextView(this).apply { text = s; textSize = sp; setPadding(0, dp(8), 0, dp(4)) }
    private fun field(hint: String, v: String, type: Int = InputType.TYPE_CLASS_NUMBER) =
        EditText(this).apply { this.hint = hint; setText(v); inputType = type }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(32), dp(16), dp(16))
        }
        setContentView(ScrollView(this).apply { addView(root) })
        status = label("", 14f)
        maxF = field("Max puzzles per unlock", Store.maxPuzzles(this).toString())
        minF = field("Minutes unlocked after solving", Store.unlockMin(this).toString())
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        root.addView(label("App Locker", 26f))
        root.addView(status)
        root.addView(btn("1. Grant Usage Access") { startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) })
        root.addView(btn("2. Allow display over other apps") {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        })
        root.addView(btn("3. Allow notifications") {
            if (Build.VERSION.SDK_INT >= 33) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        })
        root.addView(label("Each unlock asks for a random 1 to N puzzles (N = first value). Second value = minutes the app stays unlocked.", 14f))
        root.addView(maxF)
        root.addView(minF)
        root.addView(btn("Save settings") {
            Store.setMaxPuzzles(this, maxOf(1, maxF.text.toString().toIntOrNull() ?: 3))
            Store.setUnlockMin(this, maxOf(1, minF.text.toString().toIntOrNull() ?: 10))
            toast("Saved")
        })
        root.addView(btn("Start protection") {
            Store.setEnabled(this, true)
            startForegroundService(Intent(this, MonitorService::class.java))
            refresh()
        })
        root.addView(btn("Stop protection") {
            Store.setEnabled(this, false)
            stopService(Intent(this, MonitorService::class.java))
            refresh()
        })
        root.addView(label("Locked apps (tap to edit)", 18f))
        root.addView(btn("+ Add app") { pickApp() })
        root.addView(list)
    }

    override fun onResume() { super.onResume(); refresh() }

    private fun hasUsage(): Boolean {
        val ops = getSystemService(APP_OPS_SERVICE) as AppOpsManager
        return ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), packageName) == AppOpsManager.MODE_ALLOWED
    }

    private fun name(pkg: String): String = try {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
    } catch (e: Exception) { pkg }

    private fun fmt(m: Int) = "%02d:%02d".format(m / 60, m % 60)

    private fun parseT(s: String): Int {
        val p = s.trim().split(":")
        if (p.size != 2) return -1
        val h = p[0].toIntOrNull() ?: return -1
        val m = p[1].toIntOrNull() ?: return -1
        return if (h in 0..23 && m in 0..59) h * 60 + m else -1
    }

    private fun desc(r: Rule): String {
        val parts = mutableListOf<String>()
        if (r.limitMin > 0) parts.add("${r.limitMin} min/day (used ${Store.usedSec(this, r.pkg) / 60}m today)")
        if (r.from >= 0 && r.to >= 0 && r.from != r.to) parts.add("blocked ${fmt(r.from)}–${fmt(r.to)}")
        return if (parts.isEmpty()) "no rule set" else parts.joinToString(", ")
    }

    private fun refresh() {
        status.text = "Usage access: ${if (hasUsage()) "✓" else "✗"}   Overlay: ${if (Settings.canDrawOverlays(this)) "✓" else "✗"}   Protection: ${if (Store.enabled(this)) "on" else "off"}"
        list.removeAllViews()
        Store.rules(this).forEach { r -> list.addView(btn("${name(r.pkg)}\n${desc(r)}") { ruleDialog(r.pkg) }) }
    }

    private fun pickApp() {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = packageManager.queryIntentActivities(intent, 0)
            .map { it.loadLabel(packageManager).toString() to it.activityInfo.packageName }
            .filter { it.second != packageName }
            .distinctBy { it.second }
            .sortedBy { it.first.lowercase() }
        AlertDialog.Builder(this).setTitle("Choose app")
            .setItems(apps.map { it.first }.toTypedArray()) { _, i -> ruleDialog(apps[i].second) }
            .show()
    }

    private fun ruleDialog(pkg: String) {
        val old = Store.rules(this).firstOrNull { it.pkg == pkg }
        val timeType = InputType.TYPE_CLASS_DATETIME or InputType.TYPE_DATETIME_VARIATION_TIME
        val lim = field("Daily limit in minutes (blank = none)", if (old != null && old.limitMin > 0) old.limitMin.toString() else "")
        val f = field("Blocked from HH:mm (blank = none)", old?.from?.takeIf { it >= 0 }?.let { fmt(it) } ?: "", timeType)
        val t = field("Blocked until HH:mm", old?.to?.takeIf { it >= 0 }?.let { fmt(it) } ?: "", timeType)
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), 0)
            addView(lim); addView(f); addView(t)
        }
        AlertDialog.Builder(this).setTitle(name(pkg)).setView(box)
            .setPositiveButton("Save") { _, _ ->
                val rules = Store.rules(this).filter { it.pkg != pkg }.toMutableList()
                rules.add(Rule(pkg, lim.text.toString().toIntOrNull() ?: 0, parseT(f.text.toString()), parseT(t.text.toString())))
                Store.saveRules(this, rules)
                refresh()
            }
            .setNegativeButton("Remove") { _, _ ->
                Store.saveRules(this, Store.rules(this).filter { it.pkg != pkg })
                refresh()
            }
            .setNeutralButton("Cancel", null)
            .show()
    }
}
