package io.github.alexpp90.daemonsapprentice.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The game's editor (`edit file`): the phone has no
 * nano. Done, Back, Esc and Ctrl+S all save and return to the terminal. The cursor starts at
 * the end of the file, where the tasks ask for a new line.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun EditorScreen(name: String, initial: String, onDone: (String) -> Unit) {
    var value by remember(initial) { mutableStateOf(TextFieldValue(initial, TextRange(initial.length))) }
    val focus = remember { FocusRequester() }
    var closed by remember { mutableStateOf(false) }
    val done = { if (!closed) { closed = true; onDone(value.text) } }
    BackHandler { done() }
    Column(
        Modifier.fillMaxSize().background(C.bg).safeDrawingPadding().imePadding()
            .semantics { testTagsAsResourceId = true },
    ) {
        Row(Modifier.fillMaxWidth().background(C.bg2).padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("edit $name", color = C.amber, fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.weight(1f))
            Text("Done", color = C.bg, fontWeight = FontWeight.Bold, fontSize = 15.sp,
                modifier = Modifier.background(C.green).clickable { done() }.padding(horizontal = 18.dp, vertical = 8.dp).testTag("editor-done"))
        }
        Text("Done or Back saves and returns to the terminal. With a keyboard: Esc or Ctrl+S.",
            color = C.muted, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
        BasicTextField(
            value = value,
            onValueChange = { value = it },
            textStyle = TextStyle(color = C.text, fontFamily = Mono, fontSize = 15.sp),
            cursorBrush = SolidColor(C.amber),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false, keyboardType = KeyboardType.Ascii,
            ),
            modifier = Modifier.weight(1f).fillMaxWidth().background(C.card).padding(12.dp)
                .verticalScroll(rememberScrollState())
                .focusRequester(focus).testTag("editor-text")
                .onPreviewKeyEvent { e ->
                    if (e.type == KeyEventType.KeyDown && (e.key == Key.Escape || (e.isCtrlPressed && e.key == Key.S))) { done(); true }
                    else false
                },
        )
    }
    LaunchedEffect(Unit) { focus.requestFocus() }
}
