@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.jp.applocker

import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun HomeShell(resume: Int, rules: List<Rule>, onAdd: () -> Unit, onEdit: (String) -> Unit) {
    var tab by rememberSaveable { mutableStateOf(0) }
    val snack = remember { SnackbarHostState() }
    Scaffold(
        snackbarHost = { SnackbarHost(snack) },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(selected = tab == 0, onClick = { tab = 0 },
                    icon = { Icon(Icons.Rounded.Lock, null) }, label = { Text(stringResource(R.string.nav_locked_apps)) })
                NavigationBarItem(selected = tab == 1, onClick = { tab = 1 },
                    icon = { Icon(Icons.Rounded.Settings, null) }, label = { Text(stringResource(R.string.nav_settings)) })
            }
        },
        floatingActionButton = {
            if (tab == 0) ExtendedFloatingActionButton(
                onClick = onAdd, icon = { Icon(Icons.Rounded.Add, null) }, text = { Text(stringResource(R.string.add_app)) })
        }
    ) { pad ->
        if (tab == 0) AppsTab(pad, resume, rules, snack, onEdit) else SettingsTab(pad, resume)
    }
}

@Composable
fun AppsTab(pad: PaddingValues, resume: Int, rules: List<Rule>, snack: SnackbarHostState, onEdit: (String) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val cs = MaterialTheme.colorScheme
    var enabled by remember { mutableStateOf(Store.enabled(ctx)) }
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while (true) { delay(3000); tick++ } }
    val usageOk = remember(resume) { Perms.usage(ctx) }
    val overlayOk = remember(resume) { Perms.overlay(ctx) }
    val notifOk = remember(resume) { Perms.notif(ctx) }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val practiceRun = stringResource(R.string.practice_run)
    val grantPermsMsg = stringResource(R.string.grant_permissions_first)

    LazyColumn(
        Modifier.fillMaxSize().padding(pad).statusBarsPadding(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Column {
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.app_tagline), style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
            }
        }
        item {
            Card(
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = if (enabled) cs.primaryContainer else cs.surfaceContainerHigh)
            ) {
                Row(Modifier.padding(horizontal = 20.dp, vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(48.dp).clip(CircleShape).background(if (enabled) cs.primary else cs.outline.copy(alpha = 0.25f)),
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Rounded.Shield, null, tint = if (enabled) cs.onPrimary else cs.onSurfaceVariant) }
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(if (enabled) stringResource(R.string.protection_on) else stringResource(R.string.protection_off), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            if (enabled) "Watching ${rules.size} ${if (rules.size == 1) "app" else "apps"}" else stringResource(R.string.turn_on_limits),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Switch(checked = enabled, onCheckedChange = { want ->
                        if (want) {
                            if (!Perms.ok(ctx)) {
                                scope.launch { snack.showSnackbar(grantPermsMsg) }
                            } else {
                                Store.setEnabled(ctx, true); enabled = true
                                ctx.startForegroundService(Intent(ctx, MonitorService::class.java))
                            }
                        } else {
                            Store.setEnabled(ctx, false); enabled = false
                            ctx.stopService(Intent(ctx, MonitorService::class.java))
                        }
                    })
                }
            }
        }
        if (!usageOk || !overlayOk || !notifOk) {
            item {
                Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = cs.errorContainer, contentColor = cs.onErrorContainer)) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Warning, null)
                            Spacer(Modifier.width(10.dp))
                            Text(stringResource(R.string.setup_needed), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        }
                        if (!usageOk) PermissionRow("Usage access", "Lets the app see which app is open", false) { Perms.requestUsage(ctx) }
                        if (!overlayOk) PermissionRow("Display over other apps", "Needed to show the lock screen", false) { Perms.requestOverlay(ctx) }
                        if (!notifOk) PermissionRow("Notifications", "Shows the running status", false) {
                            notifLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }
                }
            }
        }
        if (rules.isEmpty()) {
            item {
                Column(Modifier.fillMaxWidth().padding(top = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(88.dp).clip(CircleShape).background(cs.secondaryContainer), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Apps, null, Modifier.size(40.dp), tint = cs.onSecondaryContainer)
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(stringResource(R.string.no_locked_apps), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        stringResource(R.string.no_locked_apps_hint),
                        style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant, textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 32.dp, vertical = 6.dp)
                    )
                }
            }
        } else {
            items(rules, key = { it.pkg }) { r -> RuleCard(r, tick) { onEdit(r.pkg) } }
        }
    }
}

@Composable
fun RuleCard(rule: Rule, tick: Int, onClick: () -> Unit) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val name = remember(rule.pkg) { appLabel(ctx, rule.pkg) }
    val usedSec = remember(tick, rule.pkg) { Store.usedSec(ctx, rule.pkg) }
    val now = remember(tick) { System.currentTimeMillis() }
    val grantMs = remember(tick, rule.pkg) { Store.grantUntil(ctx, rule.pkg) - now }
    val windowOn = remember(tick, rule) { rule.windowActive() }
    val overLimit = rule.hasLimit && usedSec >= rule.limitMin * 60
    val (status, statusColor) = when {
        grantMs > 0 -> "Unlocked · ${maxOf(1, (grantMs / 60000f).roundToInt())} min left" to cs.tertiary
        windowOn || overLimit -> stringResource(R.string.status_locked_now) to cs.error
        else -> stringResource(R.string.status_active) to cs.primary
    }
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = cs.surfaceContainer)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppIcon(rule.pkg, 52.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(status, style = MaterialTheme.typography.labelLarge, color = statusColor)
                }
                Icon(Icons.Rounded.ChevronRight, null, tint = cs.onSurfaceVariant)
            }
            Spacer(Modifier.height(12.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (rule.hasLimit) InfoPill(Icons.Rounded.Timer, "${fmtDuration(rule.limitMin)} / day")
                if (rule.hasWindow) InfoPill(Icons.Rounded.Schedule, "${fmtTime(rule.from)} – ${fmtTime(rule.to)} · ${daysLabel(rule.days)}")
            }
            if (rule.hasLimit) {
                Spacer(Modifier.height(12.dp))
                val frac = (usedSec / (rule.limitMin * 60f)).coerceIn(0f, 1f)
                LinearProgressIndicator(
                    progress = { frac },
                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                    color = if (frac >= 1f) cs.error else cs.primary,
                    trackColor = cs.surfaceVariant
                )
                Spacer(Modifier.height(6.dp))
                Text("${usedSec / 60} of ${fmtDuration(rule.limitMin)} used today", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun SettingsTab(pad: PaddingValues, resume: Int) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    var maxP by remember { mutableFloatStateOf(Store.maxPuzzles(ctx).toFloat()) }
    var unlockM by remember { mutableFloatStateOf(Store.unlockMin(ctx).toFloat()) }
    var mask by remember { mutableIntStateOf(Store.puzzleMask(ctx)) }
    val usageOk = remember(resume) { Perms.usage(ctx) }
    val overlayOk = remember(resume) { Perms.overlay(ctx) }
    val notifOk = remember(resume) { Perms.notif(ctx) }
    val batteryOk = remember(resume) { Perms.battery(ctx) }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val practiceRun = stringResource(R.string.practice_run)
    val grantPermsMsg = stringResource(R.string.grant_permissions_first)

    Column(
        Modifier.fillMaxSize().padding(pad).statusBarsPadding().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(stringResource(R.string.nav_settings), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        SectionCard(stringResource(R.string.unlock_challenge)) {
            Text(
                stringResource(R.string.unlock_challenge_desc),
                style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant
            )
            LabeledSlider(stringResource(R.string.max_puzzles), "up to ${maxP.roundToInt()}", maxP, 1f..10f, 8) {
                maxP = it; Store.setMaxPuzzles(ctx, it.roundToInt())
            }
            LabeledSlider(stringResource(R.string.stays_unlocked_for), fmtDuration(unlockM.roundToInt()), unlockM, 1f..120f, 118) {
                unlockM = it; Store.setUnlockMin(ctx, it.roundToInt())
            }
            Text(stringResource(R.string.puzzle_types), style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PuzzleKinds.forEachIndexed { i, label ->
                    val sel = (mask and (1 shl i)) != 0
                    val lead: (@Composable () -> Unit)? = if (sel) ({ Icon(Icons.Rounded.Check, null, Modifier.size(18.dp)) }) else null
                    FilterChip(
                        selected = sel,
                        onClick = {
                            val nm = mask xor (1 shl i)
                            if (nm != 0) { mask = nm; Store.setPuzzleMask(ctx, nm) }
                        },
                        label = { Text(label) },
                        leadingIcon = lead
                    )
                }
            }
            FilledTonalButton(
                onClick = {
                    ctx.startActivity(
                        Intent(ctx, LockActivity::class.java).putExtra("pkg", ctx.packageName).putExtra("reason", practiceRun)
                    )
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Rounded.PlayArrow, null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.try_a_puzzle))
            }
        }
        SectionCard(stringResource(R.string.permissions_section)) {
            PermissionRow("Usage access", "See which app is in the foreground", usageOk) { Perms.requestUsage(ctx) }
            PermissionRow("Display over other apps", "Show the lock screen on top", overlayOk) { Perms.requestOverlay(ctx) }
            PermissionRow("Notifications", "Show the running status", notifOk) {
                notifLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
            PermissionRow("Unrestricted battery", "Keeps protection from being killed", batteryOk) { Perms.requestBattery(ctx) }
        }
        Text(
            stringResource(R.string.version_footer),
            style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
    }
}
