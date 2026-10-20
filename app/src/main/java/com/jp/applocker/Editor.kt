@file:OptIn(ExperimentalMaterial3Api::class)

package com.jp.applocker

import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlin.math.roundToInt

private val DAY_LETTERS = listOf("M", "T", "W", "T", "F", "S", "S")

@Composable
fun RuleEditor(pkg: String, existing: Rule?, onBack: () -> Unit, onSave: (Rule) -> Unit, onDelete: () -> Unit) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val name = remember(pkg) { appLabel(ctx, pkg) }
    var limitOn by remember { mutableStateOf(existing?.hasLimit ?: true) }
    var limit by remember { mutableFloatStateOf((existing?.limitMin?.takeIf { it > 0 } ?: 30).toFloat()) }
    var winOn by remember { mutableStateOf(existing?.hasWindow ?: false) }
    var from by remember { mutableIntStateOf(existing?.from?.takeIf { it >= 0 } ?: (22 * 60)) }
    var to by remember { mutableIntStateOf(existing?.to?.takeIf { it >= 0 } ?: (7 * 60)) }
    var days by remember { mutableIntStateOf(existing?.days ?: Rule.ALL_DAYS) }
    var picking by remember { mutableIntStateOf(0) }
    val valid = (limitOn || (winOn && from != to)) && (!winOn || days != 0)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (existing == null) stringResource(R.string.new_rule) else stringResource(R.string.edit_rule)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, stringResource(R.string.back)) } },
                actions = {
                    if (existing != null) IconButton(onClick = onDelete) { Icon(Icons.Rounded.Delete, stringResource(R.string.remove)) }
                }
            )
        },
        bottomBar = {
            Button(
                onClick = {
                    onSave(
                        Rule(
                            pkg,
                            if (limitOn) limit.roundToInt() else 0,
                            if (winOn) from else -1,
                            if (winOn) to else -1,
                            days
                        )
                    )
                },
                enabled = valid,
                modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp).height(52.dp)
            ) { Text(stringResource(R.string.save), style = MaterialTheme.typography.titleMedium) }
        }
    ) { pad ->
        Column(
            Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppIcon(pkg, 64.dp)
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text(pkg, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                }
            }

            SectionCard(stringResource(R.string.daily_limit)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.daily_limit_desc), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = limitOn, onCheckedChange = { limitOn = it })
                }
                if (limitOn) {
                    Text(fmtDuration(limit.roundToInt()), style = MaterialTheme.typography.displaySmall, color = cs.primary, fontWeight = FontWeight.SemiBold)
                    Slider(value = limit, onValueChange = { limit = it }, valueRange = 5f..240f, steps = 46)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(15, 30, 60, 120).forEach { m ->
                            FilterChip(
                                selected = limit.roundToInt() == m,
                                onClick = { limit = m.toFloat() },
                                label = { Text(fmtDuration(m)) }
                            )
                        }
                    }
                }
            }

            SectionCard(stringResource(R.string.block_by_schedule)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.block_by_schedule_desc), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = winOn, onCheckedChange = { winOn = it })
                }
                if (winOn) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        TimeBox(stringResource(R.string.from), from, Modifier.weight(1f)) { picking = 1 }
                        TimeBox(stringResource(R.string.until), to, Modifier.weight(1f)) { picking = 2 }
                    }
                    Text(stringResource(R.string.on_days), style = MaterialTheme.typography.titleSmall)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        DAY_LETTERS.forEachIndexed { i, l ->
                            val sel = (days and (1 shl i)) != 0
                            Box(
                                Modifier.size(40.dp).clip(CircleShape)
                                    .background(if (sel) cs.primary else cs.surfaceVariant)
                                    .clickable { days = days xor (1 shl i) },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(l, style = MaterialTheme.typography.labelLarge, color = if (sel) cs.onPrimary else cs.onSurfaceVariant)
                            }
                        }
                    }
                    val note = when {
                        from == to -> stringResource(R.string.pick_different_times)
                        days == 0 -> stringResource(R.string.choose_at_least_one_day)
                        from > to -> "Blocked ${fmtTime(from)} to ${fmtTime(to)} next morning · ${daysLabel(days)}"
                        else -> "Blocked ${fmtTime(from)} to ${fmtTime(to)} · ${daysLabel(days)}"
                    }
                    Text(note, style = MaterialTheme.typography.bodySmall, color = if (valid) cs.onSurfaceVariant else cs.error)
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }

    if (picking != 0) {
        val init = if (picking == 1) from else to
        val st = rememberTimePickerState(initialHour = init / 60, initialMinute = init % 60, is24Hour = true)
        Dialog(onDismissRequest = { picking = 0 }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Surface(
                shape = MaterialTheme.shapes.extraLarge, tonalElevation = 6.dp,
                modifier = Modifier.width(IntrinsicSize.Min)
            ) {
                Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        if (picking == 1) stringResource(R.string.block_from) else stringResource(R.string.block_until),
                        Modifier.fillMaxWidth().padding(bottom = 20.dp), style = MaterialTheme.typography.labelMedium
                    )
                    TimePicker(state = st)
                    Row(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = { picking = 0 }) { Text(stringResource(R.string.cancel)) }
                        TextButton(onClick = {
                            val v = st.hour * 60 + st.minute
                            if (picking == 1) from = v else to = v
                            picking = 0
                        }) { Text(stringResource(R.string.ok)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun TimeBox(label: String, minutes: Int, modifier: Modifier, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column(
        modifier.clip(RoundedCornerShape(18.dp)).background(cs.surfaceVariant).clickable(onClick = onClick).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
        Text(fmtTime(minutes), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
    }
}
