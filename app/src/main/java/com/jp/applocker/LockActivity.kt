package com.jp.applocker

import android.app.Activity
import android.content.ClipData
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.view.DragEvent
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.util.Random

class LockActivity : Activity() {
    companion object { @Volatile var showing = false }

    private val rnd = Random()
    private lateinit var pkg: String
    private lateinit var root: LinearLayout
    private var total = 1
    private var idx = 0

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        showing = true
        pkg = intent.getStringExtra("pkg") ?: run { finish(); return }
        total = 1 + rnd.nextInt(maxOf(1, Store.maxPuzzles(this)))
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(24), dp(24), dp(24), dp(24))
            setBackgroundColor(0xFF111418.toInt())
        }
        setContentView(root)
        next()
    }

    private fun appName(): String = try {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
    } catch (e: Exception) { pkg }

    private fun next() {
        root.removeAllViews()
        if (idx >= total) { unlock(); return }
        idx++
        root.addView(label("🔒 ${appName()} is locked", 22f))
        root.addView(label("Puzzle $idx of $total", 16f))
        root.addView(
            when (rnd.nextInt(5)) {
                0 -> sortPuzzle()
                1 -> dropPuzzle()
                2 -> mathPuzzle()
                3 -> tapPuzzle()
                else -> codePuzzle()
            }
        )
    }

    private fun unlock() {
        Store.grant(this, pkg, System.currentTimeMillis() + Store.unlockMin(this) * 60000L)
        packageManager.getLaunchIntentForPackage(pkg)?.let { startActivity(it) }
        finish()
    }

    // ---------- helpers ----------
    private fun label(s: String, sp: Float) = TextView(this).apply {
        text = s; textSize = sp; setTextColor(Color.WHITE); gravity = Gravity.CENTER
        setPadding(0, dp(8), 0, dp(8))
    }

    private fun box(vararg v: View) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
        v.forEach { addView(it) }
    }

    private fun row(vararg v: View) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER
        v.forEach { addView(it) }
    }

    private fun pick(n: Int, lo: Int, hi: Int): List<Int> {
        val p = (lo..hi).toMutableList(); p.shuffle(rnd); return p.take(n)
    }

    private fun tile(s: Any) = TextView(this).apply {
        text = s.toString(); textSize = 24f; gravity = Gravity.CENTER
        setTextColor(Color.WHITE); setBackgroundColor(0xFF2E7D6B.toInt())
        layoutParams = LinearLayout.LayoutParams(dp(72), dp(72)).apply { setMargins(dp(6), dp(6), dp(6), dp(6)) }
    }

    private fun draggable(t: TextView): TextView {
        t.setOnTouchListener { v, e ->
            if (e.action == MotionEvent.ACTION_DOWN) {
                v.startDragAndDrop(ClipData.newPlainText("t", "t"), View.DragShadowBuilder(v), v, 0)
                true
            } else false
        }
        return t
    }

    // ---------- puzzles ----------
    private fun sortPuzzle(): View {
        var nums = pick(4, 1, 99)
        while (nums == nums.sorted()) nums = pick(4, 1, 99)
        val tiles = nums.map { draggable(tile(it)) }
        tiles.forEach { t ->
            t.setOnDragListener { v, ev ->
                if (ev.action == DragEvent.ACTION_DROP) {
                    val src = ev.localState as TextView
                    val dst = v as TextView
                    val a = src.text
                    src.text = dst.text
                    dst.text = a
                    val vals = tiles.map { it.text.toString().toInt() }
                    if (vals == vals.sorted()) next()
                }
                true
            }
        }
        return box(label("Drag tiles to sort smallest → largest", 18f), row(*tiles.toTypedArray()))
    }

    private fun dropPuzzle(): View {
        val n = pick(4, 10, 99)
        val target = n[0]
        val dropBox = TextView(this).apply {
            text = "Drop $target here"; textSize = 20f; gravity = Gravity.CENTER
            setTextColor(Color.WHITE); setBackgroundColor(0xFF444B55.toInt())
            layoutParams = LinearLayout.LayoutParams(dp(240), dp(110)).apply { setMargins(0, dp(16), 0, dp(16)) }
        }
        dropBox.setOnDragListener { v, ev ->
            if (ev.action == DragEvent.ACTION_DROP) {
                if ((ev.localState as TextView).text.toString() == target.toString()) {
                    next()
                } else {
                    (v as TextView).text = "✗ Wrong. Drop $target here"
                }
            }
            true
        }
        val tiles = n.shuffled(rnd).map { draggable(tile(it)) }
        return box(label("Drag the number $target into the box", 18f), dropBox, row(*tiles.toTypedArray()))
    }

    private fun mathPuzzle(): View {
        val mul = rnd.nextBoolean()
        val a = if (mul) 3 + rnd.nextInt(10) else 11 + rnd.nextInt(60)
        val b = if (mul) 3 + rnd.nextInt(10) else 11 + rnd.nextInt(60)
        val ans = if (mul) a * b else a + b
        return answerBox("Solve: $a ${if (mul) "×" else "+"} $b", InputType.TYPE_CLASS_NUMBER) { it.trim() == ans.toString() }
    }

    private fun codePuzzle(): View {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        val code = (1..5).map { chars[rnd.nextInt(chars.length)] }.joinToString("")
        return answerBox("Type this code:\n$code", InputType.TYPE_CLASS_TEXT) { it.trim().equals(code, true) }
    }

    private fun answerBox(q: String, type: Int, ok: (String) -> Boolean): View {
        val et = EditText(this).apply {
            inputType = type; setTextColor(Color.WHITE); textSize = 24f
            gravity = Gravity.CENTER; minWidth = dp(200)
        }
        val btn = Button(this).apply {
            text = "Check"
            setOnClickListener {
                if (ok(et.text.toString())) {
                    next()
                } else {
                    et.setText("")
                    Toast.makeText(context, "Wrong, try again", Toast.LENGTH_SHORT).show()
                }
            }
        }
        return box(label(q, 22f), et, btn)
    }

    private fun tapPuzzle(): View {
        val order = pick(6, 1, 6)
        var expect = 1
        val btns = order.map { n -> Button(this).apply { text = n.toString(); textSize = 22f } }
        btns.forEach { b ->
            b.setOnClickListener {
                if (b.text.toString() == expect.toString()) {
                    b.isEnabled = false
                    expect++
                    if (expect > 6) next()
                } else {
                    expect = 1
                    btns.forEach { it.isEnabled = true }
                }
            }
        }
        return box(
            label("Tap 1 to 6 in order (a mistake resets)", 18f),
            row(*btns.take(3).toTypedArray()),
            row(*btns.drop(3).toTypedArray())
        )
    }

    // ---------- leaving ----------
    override fun onBackPressed() {
        startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        finish()
    }

    override fun onStop() { super.onStop(); showing = false; finish() }
    override fun onDestroy() { showing = false; super.onDestroy() }
}
