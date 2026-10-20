package com.jp.applocker

import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

class LockActivity : ComponentActivity() {
    companion object { @Volatile var showing = false }

    private var pkg = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showing = true
        pkg = intent.getStringExtra("pkg") ?: run { finish(); return }
        val reason = intent.getStringExtra("reason") ?: ""
        val total = 1 + (0 until maxOf(1, Store.maxPuzzles(this))).random()
        enableEdgeToEdge()
        setContent {
            AppTheme { LockScreen(pkg, reason, total, onSolved = { unlock() }, onExit = { goHome() }) }
        }
    }

    private fun unlock() {
        if (pkg != packageName) {
            Store.grant(this, pkg, System.currentTimeMillis() + Store.unlockMin(this) * 60000L)
            packageManager.getLaunchIntentForPackage(pkg)?.let { startActivity(it) }
        }
        finish()
    }

    private fun goHome() {
        if (pkg != packageName) {
            startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        finish()
    }

    override fun onStop() { super.onStop(); showing = false; finish() }
    override fun onDestroy() { showing = false; super.onDestroy() }
}

@Composable
fun LockScreen(pkg: String, reason: String, total: Int, onSolved: () -> Unit, onExit: () -> Unit) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val name = remember(pkg) { appLabel(ctx, pkg) }
    val kinds = remember { pickKinds(ctx, total) }
    var step by remember { mutableIntStateOf(0) }
    BackHandler { onExit() }

    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(cs.primaryContainer.copy(alpha = 0.45f), cs.surface))
        )
    ) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()
                .verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(24.dp))
            Box {
                AppIcon(pkg, 76.dp)
                Box(
                    Modifier.align(Alignment.BottomEnd).offset(6.dp, 6.dp).size(30.dp).clip(CircleShape).background(cs.primary),
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Rounded.Lock, null, Modifier.size(16.dp), tint = cs.onPrimary) }
            }
            Spacer(Modifier.height(18.dp))
            Text("$name is locked", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            if (reason.isNotEmpty()) {
                Text(reason, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(28.dp))
            Text(
                "Solve ${if (total == 1) "1 puzzle" else "$total puzzles"} to continue",
                style = MaterialTheme.typography.titleSmall
            )
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { step / total.toFloat() },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape)
            )
            Spacer(Modifier.height(6.dp))
            Text("Puzzle ${step + 1} of $total", style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant)
            Spacer(Modifier.height(16.dp))
            ElevatedCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp)) {
                Box(Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
                    Crossfade(targetState = step, label = "puzzle") { s ->
                        val done: () -> Unit = {
                            if (s + 1 >= total) { onSolved() } else { step = s + 1 }
                        }
                        when (kinds[s]) {
                            0 -> SortPuzzle(done)
                            1 -> DropPuzzle(done)
                            2 -> MathPuzzle(done)
                            3 -> TapPuzzle(done)
                            else -> CodePuzzle(done)
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
            TextButton(onClick = onExit) { Text(stringResource(R.string.go_back)) }
        }
    }
}
