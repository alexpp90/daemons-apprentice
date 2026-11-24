package io.github.alexpp90.daemonsapprentice.ui

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import io.github.alexpp90.daemonsapprentice.game.GameState
import io.github.alexpp90.daemonsapprentice.game.HelpText
import io.github.alexpp90.daemonsapprentice.game.LevelInfo
import io.github.alexpp90.daemonsapprentice.game.Line
import io.github.alexpp90.daemonsapprentice.game.Wait

/** Everything the screen can ask the game for. */
interface ScreenActions {
    fun submit(text: String)
    fun next()
    fun interrupt()
    fun complete(text: String): String
    fun resize(rows: Int, cols: Int)
    fun resetGame()
    fun jumpTo(level: Int)
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalComposeUiApi::class)
@Composable
fun GameScreen(state: GameState, actions: ScreenActions, showMenu: Boolean, onMenu: (Boolean) -> Unit) {
    val config = LocalConfiguration.current
    val imeVisible = WindowInsets.isImeVisible
    val hardKeyboard = config.keyboard == Configuration.KEYBOARD_QWERTY &&
        config.hardKeyboardHidden == Configuration.HARDKEYBOARDHIDDEN_NO
    val hardwareMode = hardKeyboard && !imeVisible
    var input by remember { mutableStateOf<CommandEditText?>(null) }
    var taskOpen by remember { mutableStateOf(false) }

    val listener = remember {
        object : CommandListener {
            override fun onSubmit(text: String) = actions.submit(text)
            override fun onTab(text: String) = actions.complete(text)
            override fun onInterrupt() = actions.interrupt()
            override fun onHint() = actions.submit("hint")
            override fun onClear() = actions.submit("clear")
        }
    }

    // At a prompt the game asks for the soft keyboard. Android decides: with a hardware keyboard
    // it stays hidden unless the player chose "show on-screen keyboard" in the system settings.
    LaunchedEffect(state.wait, input) {
        val i = input ?: return@LaunchedEffect
        if (state.wait == Wait.Editing) return@LaunchedEffect // the editor has the keyboard
        i.requestFocus()
        if (state.wait == Wait.Prompt || state.wait == Wait.More) i.showKeyboard()
    }

    BoxWithConstraints(
        Modifier.fillMaxSize().background(C.bg).safeDrawingPadding().imePadding()
            .semantics { testTagsAsResourceId = true },
    ) {
        val wide = maxWidth >= 600.dp
        val consoleWidth = if (wide) maxWidth - 340.dp else maxWidth
        LaunchedEffect(consoleWidth, maxHeight) {
            val cols = (consoleWidth.value / 8f).toInt().coerceIn(20, 200)
            val rows = (maxHeight.value / 18f).toInt().coerceIn(10, 100)
            actions.resize(rows, cols)
        }
        Column(Modifier.fillMaxSize()) {
            TopBar(state) { onMenu(true) }
            Row(Modifier.weight(1f)) {
                if (wide) TaskPane(state, Modifier.width(340.dp).fillMaxHeight())
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    if (!wide && imeVisible && state.pinned.isNotEmpty()) PinnedTask(state, taskOpen) { taskOpen = !taskOpen }
                    Transcript(state, Modifier.weight(1f))
                    Bottom(state, actions, hardwareMode, imeVisible, wide, listener, onInput = { input = it }) { input }
                }
            }
        }
    }
    if (showMenu) MenuDialog(state, onClose = { onMenu(false) }, onReset = { onMenu(false); actions.resetGame() },
        onLevel = { onMenu(false); actions.jumpTo(it) })
}

@Composable
private fun TopBar(state: GameState, onMenu: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(C.bg2).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(state.levelLabel, color = C.blue, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Text(if (state.taskCount > 0) "Task ${state.task.coerceAtMost(state.taskCount)}/${state.taskCount}" else state.levelTitle,
            color = C.text, fontSize = 13.sp, modifier = Modifier.testTag("progress"))
        Box(Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp)).background(C.line)) {
            val f = if (state.taskCount == 0) 0f else (state.task - 1).coerceAtLeast(0).toFloat() / state.taskCount
            Box(Modifier.fillMaxWidth(if (state.wait == Wait.Done) 1f else f).height(6.dp).background(C.green))
        }
        Text("☰ menu", color = C.muted, fontFamily = Mono, fontSize = 13.sp,
            modifier = Modifier.clickable(onClick = onMenu).padding(4.dp).testTag("menu"))
    }
}

@Composable
private fun PinnedTask(state: GameState, open: Boolean, onToggle: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(8.dp)).background(C.card).border(1.dp, C.line, RoundedCornerShape(8.dp))
            .clickable(onClick = onToggle).padding(10.dp).testTag("pinned"),
    ) {
        Text(markup(state.pinned), color = C.text, fontSize = 13.sp, maxLines = if (open) 50 else 1,
            overflow = TextOverflow.Ellipsis)
        if (open) {
            Spacer(Modifier.height(6.dp))
            Text(markup(state.taskText), color = C.text, fontSize = 13.sp)
            Parts(state)
        } else Text("show all ▾", color = C.blue, fontSize = 11.sp)
    }
}

@Composable
private fun Parts(state: GameState) {
    if (state.parts.isEmpty()) return
    Spacer(Modifier.height(6.dp))
    state.parts.forEach { (p, w) ->
        Row { Text(p, color = C.green, fontFamily = Mono, fontSize = 13.sp, modifier = Modifier.widthIn(min = 110.dp)); Text(w, color = C.text, fontSize = 13.sp) }
    }
}

@Composable
private fun TaskPane(state: GameState, modifier: Modifier) {
    Column(
        modifier.background(C.bg2).verticalScroll(rememberScrollState()).padding(16.dp).testTag("taskpane"),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(state.taskTitle, color = C.text, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Text(markup(state.taskText), color = C.text, fontSize = 14.sp)
        Parts(state)
        Spacer(Modifier.height(8.dp))
        Text("Tab  complete a name    ↑  last command", color = C.muted, fontSize = 12.sp, fontFamily = Mono)
        Text("Ctrl+C  stop    F1  hint    Ctrl+L  clear", color = C.muted, fontSize = 12.sp, fontFamily = Mono)
    }
}

@Composable
private fun Transcript(state: GameState, modifier: Modifier) {
    val list = rememberLazyListState()
    val last = state.transcript.lastOrNull()
    val lastLen = when (last) { is Line.Output -> last.text.length; else -> 0 }
    LaunchedEffect(state.transcript.size, lastLen) {
        if (state.transcript.isNotEmpty()) list.scrollToItem(state.transcript.lastIndex, Int.MAX_VALUE / 2)
    }
    LazyColumn(
        modifier.fillMaxWidth().padding(horizontal = 12.dp).testTag("transcript"), state = list,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { Spacer(Modifier.height(4.dp)) }
        items(state.transcript, key = { it.id }) { TranscriptLine(it) }
        item { Spacer(Modifier.height(4.dp)) }
    }
}

@Composable
private fun TranscriptLine(l: Line) {
    when (l) {
        is Line.Text -> {
            val (color, bg, size) = when (l.kind) {
                Line.Kind.Card -> Triple(C.text, C.card, 14)
                Line.Kind.Pid1 -> Triple(C.violet, Color.Transparent, 15)
                Line.Kind.Fact -> Triple(C.muted, Color.Transparent, 14)
                Line.Kind.Note -> Triple(C.amber, Color.Transparent, 14)
                Line.Kind.Warn -> Triple(C.pink, Color.Transparent, 14)
                Line.Kind.Help -> Triple(C.text, C.card, 13)
                Line.Kind.Title -> Triple(C.text, Color.Transparent, 18)
                Line.Kind.Normal -> Triple(C.text, Color.Transparent, 15)
            }
            val mod = if (bg != Color.Transparent) Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(bg).padding(10.dp) else Modifier
            val live = l.kind == Line.Kind.Note || l.kind == Line.Kind.Pid1 || l.kind == Line.Kind.Warn
            Text(markup(l.text), color = color, fontSize = size.sp, lineHeight = (size * 1.4f).sp,
                fontWeight = if (l.kind == Line.Kind.Title) FontWeight.Bold else null,
                fontFamily = if (l.kind == Line.Kind.Help) Mono else null,
                modifier = mod.then(if (live) Modifier.semantics { liveRegion = LiveRegionMode.Polite } else Modifier))
        }
        is Line.Command -> Text(
            (if (l.continuation) "> " else if (l.input) "  " else "$ ") + l.text,
            color = if (l.input) C.muted else C.green, fontFamily = Mono, fontSize = 15.sp,
        )
        is Line.Output -> Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            Box(Modifier.width(2.dp).fillMaxHeight().background(if (l.failed) C.pink else C.line))
            Text(l.text, color = if (l.failed) C.pink else C.text, fontFamily = Mono, fontSize = 13.sp,
                lineHeight = 18.sp, modifier = Modifier.padding(start = 8.dp).testTag("output"))
        }
        is Line.Art -> Text(l.text, color = C.violet, fontFamily = Mono, fontSize = 15.sp)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Bottom(
    state: GameState, actions: ScreenActions, hardwareMode: Boolean, imeVisible: Boolean, wide: Boolean,
    listener: CommandListener, onInput: (CommandEditText) -> Unit, input: () -> CommandEditText?,
) {
    Column(Modifier.fillMaxWidth().background(C.bg)) {
        when (state.wait) {
            Wait.Continue -> Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp)) {
                Spacer(Modifier.weight(1f))
                Text("Next ▸", color = C.bg, fontWeight = FontWeight.Bold, fontSize = 15.sp,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(C.blue)
                        .clickable { actions.next() }.padding(horizontal = 22.dp, vertical = 10.dp).testTag("next"))
            }
            Wait.Done -> Row(Modifier.fillMaxWidth().padding(10.dp)) {
                Text("The slice ends here. Open the menu to play Level 0 again.", color = C.muted, fontSize = 13.sp)
            }
            else -> {}
        }
        // The input line stays in place in every state, so the keyboard does not jump.
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)
                .clip(RoundedCornerShape(8.dp)).background(C.bg2)
                .border(1.5.dp, if (state.wait == Wait.Prompt || state.wait == Wait.More) C.blue else C.line, RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(if (state.wait == Wait.More) "> " else if (state.wait == Wait.Busy) "… " else "$ ", color = C.muted, fontFamily = Mono, fontSize = 16.sp,
                modifier = Modifier.testTag(if (state.wait == Wait.Prompt) "prompt-ready" else "prompt-" + state.wait.name.lowercase()))
            AndroidView(
                factory = { ctx ->
                    CommandEditText(ctx, listener).apply {
                        setTextColor(android.graphics.Color.rgb(0xE6, 0xE9, 0xF5))
                        setHintTextColor(android.graphics.Color.rgb(0x6B, 0x72, 0x90))
                        hint = "type a command"
                        tag = "command-input"
                        contentDescription = "Command line"
                        onInput(this)
                    }
                },
                update = { it.listener = listener },
                modifier = Modifier.weight(1f).testTag("input"),
            )
            if (state.wait == Wait.Busy || state.wait == Wait.More)
                SmallKey("Ctrl-C", hot = true) { actions.interrupt() }
        }
        val chipsVisible = state.chips.isNotEmpty() && (state.wait == Wait.Prompt || state.wait == Wait.More) &&
            ((state.showChips && !hardwareMode) || state.hinted)
        if (chipsVisible) FlowRow(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            state.chips.forEach { c ->
                Text(c, color = C.blue, fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 15.sp,
                    modifier = Modifier.clip(RoundedCornerShape(18.dp)).background(Color(0xFF1D2A44))
                        .border(1.dp, Color(0xFF2F4470), RoundedCornerShape(18.dp))
                        .clickable {
                            val i = input() ?: return@clickable
                            val t = i.text.toString()
                            i.insert(if (t.isEmpty() || t.endsWith(" ")) c else " $c")
                        }
                        .padding(horizontal = 14.dp, vertical = 8.dp).testTag("chip:$c"))
            }
            Text("⏎ run", color = C.bg, fontWeight = FontWeight.Bold, fontSize = 15.sp,
                modifier = Modifier.clip(RoundedCornerShape(18.dp)).background(C.green)
                    .clickable { input()?.submit() }.padding(horizontal = 14.dp, vertical = 8.dp).testTag("run"))
        }
        if (imeVisible) KeyBar(state, actions, input)
        // Always there, in every mode: a player who taps (even on a tablet with a keyboard attached)
        // always reaches hint, and with it the command pieces (R4, R28).
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp)) {
            Text("Stuck? Tap ", color = C.muted, fontSize = 12.sp)
            Text("hint", color = C.amber, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable { actions.submit("hint") }.testTag("hint"))
            Text(if (hardwareMode) " (or press F1). All help: " else ". All help: ", color = C.muted, fontSize = 12.sp)
            Text("help", color = C.amber, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable { actions.submit("help") }.testTag("help"))
        }
    }
}

@Composable
private fun KeyBar(state: GameState, actions: ScreenActions, input: () -> CommandEditText?) {
    val chars = (state.keys + listOf("-", "/", ".", "|", ">", "$", "~", "*")).distinct()
    Row(
        Modifier.fillMaxWidth().background(C.kbd).horizontalScroll(rememberScrollState()).padding(5.dp).testTag("keybar"),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        SmallKey("Tab", accent = true) { input()?.tab() }
        SmallKey("↑") { input()?.historyUp() }
        SmallKey("↓") { input()?.historyDown() }
        SmallKey("←") { input()?.moveCursor(-1) }
        SmallKey("→") { input()?.moveCursor(1) }
        chars.forEach { k -> SmallKey(k, accent = k in state.keys) { input()?.insert(k) } }
        SmallKey("⏎", accent = true) { input()?.submit() }
        SmallKey("Ctrl-C", hot = true) { actions.interrupt() }
    }
}

@Composable
private fun SmallKey(label: String, accent: Boolean = false, hot: Boolean = false, onClick: () -> Unit) {
    val bg = when { hot -> Color(0xFF3A1F2A); accent -> Color(0xFF3B2F1A); else -> C.key }
    val fg = when { hot -> C.pink; accent -> C.amber; else -> C.keyText }
    Text(label, color = fg, fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 14.sp,
        modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(bg).clickable(onClick = onClick)
            .widthIn(min = 34.dp).padding(horizontal = 9.dp, vertical = 9.dp).testTag("key:$label"))
}

@Composable
private fun MenuDialog(state: GameState, onClose: () -> Unit, onReset: () -> Unit, onLevel: (Int) -> Unit) {
    var confirm by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onClose,
        containerColor = C.card,
        title = { Text("Menu", color = C.text) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
                val (sides, mains) = state.levels.partition { it.side }
                @Composable fun entry(l: LevelInfo, short: String) {
                    val mark = when { l.current -> "▸"; l.done -> "✓"; else -> " " }
                    Text("$mark  $short  ${l.title}", color = if (l.current) C.blue else C.text, fontFamily = Mono, fontSize = 14.sp,
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp))
                            .clickable(enabled = state.wait != Wait.Busy) { onLevel(l.number) }
                            .padding(vertical = 8.dp, horizontal = 4.dp).testTag("level:${l.number}"))
                }
                mains.forEach { entry(it, "Level ${it.number}") }
                if (sides.isNotEmpty()) {
                    Text("Side quests (optional)", color = C.muted, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
                    sides.forEach { entry(it, it.name.removePrefix("Side quest ")) }
                }
                if (confirm) Text("Start over? This deletes your progress and the playground, nothing else.", color = C.amber, fontSize = 13.sp)
            }
        },
        confirmButton = {
            TextButton(onClick = { if (confirm) onReset() else confirm = true }, modifier = Modifier.testTag("reset")) {
                Text(if (confirm) "Yes, start over" else "Start over", color = C.pink)
            }
        },
        dismissButton = { TextButton(onClick = onClose, modifier = Modifier.testTag("close")) { Text("Close", color = C.blue) } },
    )
}
