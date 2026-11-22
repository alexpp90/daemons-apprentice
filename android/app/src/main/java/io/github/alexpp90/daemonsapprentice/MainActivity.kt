package io.github.alexpp90.daemonsapprentice

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.alexpp90.daemonsapprentice.game.UiEvent
import androidx.compose.foundation.clickable
import io.github.alexpp90.daemonsapprentice.ui.ApprenticeTheme
import io.github.alexpp90.daemonsapprentice.ui.EditorScreen
import io.github.alexpp90.daemonsapprentice.ui.GameScreen
import io.github.alexpp90.daemonsapprentice.ui.GameViewModel
import io.github.alexpp90.daemonsapprentice.ui.ScreenActions

/** Tests replace the system's file picker with this (it cannot be driven reliably). */
object FilePickerHook { @Volatile var override: (() -> Unit)? = null }

class MainActivity : ComponentActivity() {

    private val vm: GameViewModel by viewModels()

    private val picker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { vm.filePickerClosed() }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = androidx.activity.SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = androidx.activity.SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        val actions = object : ScreenActions {
            override fun submit(text: String) { vm.submit(text) }
            override fun next() { vm.next() }
            override fun interrupt() = vm.interrupt()
            override fun complete(text: String): String {
                val c = vm.complete(text)
                if (c.options.size > 1) vm.showOptions(c.options)
                return c.text
            }
            override fun resize(rows: Int, cols: Int) = vm.resize(rows, cols)
            override fun resetGame() = vm.resetGame()
            override fun jumpTo(level: Int) { vm.jumpTo(level) }
        }
        setContent {
            ApprenticeTheme {
                val state by vm.state.collectAsStateWithLifecycle()
                var menu by remember { mutableStateOf(false) }
                var editor by remember { mutableStateOf<UiEvent.OpenEditor?>(null) }
                LaunchedEffect(Unit) {
                    vm.events.collect { e ->
                        when (e) {
                            UiEvent.OpenFilePicker -> {
                                val hook = FilePickerHook.override
                                if (hook != null) { hook(); vm.filePickerClosed() }
                                else try { picker.launch(arrayOf("*/*")) } catch (_: Exception) { vm.filePickerClosed() }
                            }
                            is UiEvent.OpenEditor -> editor = e
                            UiEvent.OpenMenu -> menu = true
                            UiEvent.Quit -> finish()
                        }
                    }
                }
                androidx.compose.foundation.layout.Box {
                    GameScreen(state, actions, menu) { menu = it }
                    // The editor lies over the terminal, which keeps its input and history meanwhile.
                    editor?.let { e ->
                        androidx.compose.foundation.layout.Box(
                            androidx.compose.ui.Modifier.clickable(
                                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                indication = null,
                            ) {},
                        ) {
                            EditorScreen(e.name, e.text) { text -> editor = null; vm.editorClosed(text) }
                        }
                    }
                }
            }
        }
    }
}
