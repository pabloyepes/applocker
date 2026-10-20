package com.jp.applocker

import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlin.math.roundToInt

val PuzzleKinds = listOf("Sort", "Drag & drop", "Math", "Tap order", "Code")

fun pickKinds(ctx: Context, total: Int): List<Int> {
    val mask = Store.puzzleMask(ctx)
    val on = (0..4).filter { (mask and (1 shl it)) != 0 }.ifEmpty { (0..4).toList() }
    return List(total) { on.random() }
}

@Composable
private fun Prompt(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
}

// ---------- 1. Sort by dragging ----------
@Composable
fun SortPuzzle(onDone: () -> Unit) {
    val items = remember {
        mutableStateListOf<Int>().apply {
            var n = (1..99).shuffled().take(4)
            while (n == n.sorted()) n = (1..99).shuffled().take(4)
            addAll(n)
        }
    }
    val cs = MaterialTheme.colorScheme
    val finished by rememberUpdatedState(onDone)
    val tile = 60.dp
    val gap = 8.dp
    val stepPx = with(LocalDensity.current) { (tile + gap).toPx() }
    var dragging by remember { mutableStateOf<Int?>(null) }
    var offX by remember { mutableFloatStateOf(0f) }
    var offY by remember { mutableFloatStateOf(0f) }
    var solved by remember { mutableStateOf(false) }

    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Prompt(stringResource(R.string.prompt_sort))
        Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
            items.toList().forEach { v ->
                key(v) {
                    val isDrag = dragging == v
                    Box(
                        Modifier.size(tile)
                            .zIndex(if (isDrag) 1f else 0f)
                            .offset { if (isDrag) IntOffset(offX.roundToInt(), offY.roundToInt()) else IntOffset.Zero }
                            .shadow(if (isDrag) 10.dp else 0.dp, RoundedCornerShape(16.dp))
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (isDrag) cs.primary else cs.primaryContainer)
                            .pointerInput(v) {
                                detectDragGestures(
                                    onDragStart = { dragging = v; offX = 0f; offY = 0f },
                                    onDrag = { change, amount -> change.consume(); offX += amount.x; offY += amount.y },
                                    onDragEnd = {
                                        val from = items.indexOf(v)
                                        val dest = (from + (offX / stepPx).roundToInt()).coerceIn(0, items.lastIndex)
                                        if (dest != from) { items.removeAt(from); items.add(dest, v) }
                                        dragging = null; offX = 0f; offY = 0f
                                        if (!solved && items.toList() == items.sorted()) { solved = true; finished() }
                                    },
                                    onDragCancel = { dragging = null; offX = 0f; offY = 0f }
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            v.toString(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold,
                            color = if (isDrag) cs.onPrimary else cs.onPrimaryContainer
                        )
                    }
                }
            }
        }
    }
}

// ---------- 2. Drag a number into the box ----------
@Composable
fun DropPuzzle(onDone: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val target = remember { (10..99).random() }
    val options = remember { ((10..99).filter { it != target }.shuffled().take(3) + target).shuffled() }
    var targetRect by remember { mutableStateOf(Rect.Zero) }
    var msg by remember { mutableStateOf<String?>(null) }
    val wrongOrder = stringResource(R.string.wrong_order)
    val notThatOne = stringResource(R.string.not_that_one)
    val dropHere = stringResource(R.string.drop_here)
    val finished by rememberUpdatedState(onDone)

    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Prompt("Drag the number $target into the box")
        Box(
            Modifier.size(width = 200.dp, height = 90.dp)
                .onGloballyPositioned { targetRect = it.boundsInRoot() }
                .clip(RoundedCornerShape(22.dp))
                .background(cs.surfaceVariant)
                .border(2.dp, if (msg != null) cs.error else cs.primary, RoundedCornerShape(22.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(msg ?: dropHere, color = if (msg != null) cs.error else cs.onSurfaceVariant, style = MaterialTheme.typography.titleMedium)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { v ->
                key(v) {
                    DragChip(v, targetRect) { hit ->
                        if (hit) { if (v == target) finished() else msg = notThatOne }
                    }
                }
            }
        }
    }
}

@Composable
private fun DragChip(value: Int, target: Rect, onDrop: (Boolean) -> Unit) {
    val tgt by rememberUpdatedState(target)
    val drop by rememberUpdatedState(onDrop)
    var center by remember { mutableStateOf(Offset.Zero) }
    var off by remember { mutableStateOf(Offset.Zero) }
    var active by remember { mutableStateOf(false) }
    val cs = MaterialTheme.colorScheme
    Box(
        Modifier.size(60.dp)
            .onGloballyPositioned { center = it.boundsInRoot().center }
            .zIndex(if (active) 1f else 0f)
            .offset { IntOffset(off.x.roundToInt(), off.y.roundToInt()) }
            .clip(RoundedCornerShape(16.dp))
            .background(if (active) cs.primary else cs.secondaryContainer)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { active = true },
                    onDrag = { change, amount -> change.consume(); off += amount },
                    onDragEnd = {
                        val hit = tgt.contains(center + off)
                        active = false; off = Offset.Zero
                        drop(hit)
                    },
                    onDragCancel = { active = false; off = Offset.Zero }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            value.toString(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold,
            color = if (active) cs.onPrimary else cs.onSecondaryContainer
        )
    }
}

// ---------- 3 & 5. Typed answers ----------
@Composable
private fun AnswerPuzzle(numeric: Boolean, check: (String) -> Boolean, onDone: () -> Unit, header: @Composable () -> Unit) {
    var text by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }
    val submit: () -> Unit = {
        if (check(text.trim())) { onDone() } else { wrong = true; text = "" }
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        header()
        OutlinedTextField(
            value = text, onValueChange = { text = it; wrong = false },
            singleLine = true, isError = wrong,
            supportingText = { if (wrong) Text(stringResource(R.string.not_quite)) },
            keyboardOptions = KeyboardOptions(
                keyboardType = if (numeric) KeyboardType.Number else KeyboardType.Ascii,
                capitalization = if (numeric) KeyboardCapitalization.None else KeyboardCapitalization.Characters,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            textStyle = TextStyle(fontSize = 24.sp, textAlign = TextAlign.Center),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        )
        Button(onClick = submit, modifier = Modifier.fillMaxWidth().height(48.dp)) { Text(stringResource(R.string.check)) }
    }
}

@Composable
fun MathPuzzle(onDone: () -> Unit) {
    val mul = remember { (0..1).random() == 1 }
    val a = remember { if (mul) (3..12).random() else (11..70).random() }
    val b = remember { if (mul) (3..12).random() else (11..70).random() }
    val ans = if (mul) a * b else a + b
    AnswerPuzzle(true, { it == ans.toString() }, onDone) {
        Prompt(stringResource(R.string.prompt_math))
        Text("$a ${if (mul) "×" else "+"} $b", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun CodePuzzle(onDone: () -> Unit) {
    val code = remember {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        (1..5).map { chars.random() }.joinToString("")
    }
    AnswerPuzzle(false, { it.equals(code, ignoreCase = true) }, onDone) {
        Prompt(stringResource(R.string.prompt_code))
        Text(
            code, fontFamily = FontFamily.Monospace, fontSize = 38.sp, letterSpacing = 8.sp,
            fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary
        )
    }
}

// ---------- 4. Tap in order ----------
@Composable
fun TapPuzzle(onDone: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val order = remember { (1..6).shuffled() }
    var next by remember { mutableIntStateOf(1) }
    var msg by remember { mutableStateOf<String?>(null) }
    val wrongOrder = stringResource(R.string.wrong_order)
    val notThatOne = stringResource(R.string.not_that_one)
    val dropHere = stringResource(R.string.drop_here)
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Prompt(stringResource(R.string.prompt_tap))
        order.chunked(3).forEach { rowNums ->
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                rowNums.forEach { n ->
                    val tapped = n < next
                    Box(
                        Modifier.size(72.dp).clip(CircleShape)
                            .background(if (tapped) cs.primary else cs.secondaryContainer)
                            .clickable(enabled = !tapped) {
                                if (n == next) {
                                    msg = null
                                    next++
                                    if (next > 6) onDone()
                                } else {
                                    next = 1
                                    msg = wrongOrder
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (tapped) Icon(Icons.Rounded.Check, null, tint = cs.onPrimary)
                        else Text(n.toString(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, color = cs.onSecondaryContainer)
                    }
                }
            }
        }
        Text(msg ?: " ", color = cs.error, style = MaterialTheme.typography.bodySmall)
    }
}
