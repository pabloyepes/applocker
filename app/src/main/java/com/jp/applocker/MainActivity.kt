package com.jp.applocker

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.runtime.*

sealed interface Screen {
    object Home : Screen
    object Picker : Screen
    data class Editor(val pkg: String) : Screen
}

class MainActivity : ComponentActivity() {
    private var resumeCount by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { AppTheme { AppRoot(resumeCount) } }
    }

    override fun onResume() {
        super.onResume()
        resumeCount++
        try {
            if (Store.enabled(this) && Perms.ok(this)) startForegroundService(Intent(this, MonitorService::class.java))
        } catch (e: Exception) { }
    }
}

@Composable
fun AppRoot(resume: Int) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var screen by remember { mutableStateOf<Screen>(Screen.Home) }
    var rules by remember { mutableStateOf(Store.rules(ctx)) }
    fun save(new: List<Rule>) { Store.saveRules(ctx, new); rules = new }

    BackHandler(enabled = screen != Screen.Home) { screen = Screen.Home }

    Crossfade(targetState = screen, label = "nav") { s ->
        when (s) {
            Screen.Home -> HomeShell(resume, rules, onAdd = { screen = Screen.Picker }, onEdit = { screen = Screen.Editor(it) })
            Screen.Picker -> AppPicker(
                locked = rules.map { it.pkg }.toSet(),
                onBack = { screen = Screen.Home },
                onPick = { screen = Screen.Editor(it) }
            )
            is Screen.Editor -> RuleEditor(
                pkg = s.pkg,
                existing = rules.firstOrNull { it.pkg == s.pkg },
                onBack = { screen = Screen.Home },
                onSave = { r -> save(rules.filter { it.pkg != r.pkg } + r); screen = Screen.Home },
                onDelete = { save(rules.filter { it.pkg != s.pkg }); screen = Screen.Home }
            )
        }
    }
}
