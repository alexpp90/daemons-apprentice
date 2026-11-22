package io.github.alexpp90.daemonsapprentice.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.alexpp90.daemonsapprentice.engine.ShellSession
import io.github.alexpp90.daemonsapprentice.game.GameDirs
import io.github.alexpp90.daemonsapprentice.game.GameEngine
import io.github.alexpp90.daemonsapprentice.game.GameState
import io.github.alexpp90.daemonsapprentice.game.UiEvent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class GameViewModel(app: Application) : AndroidViewModel(app) {

    private val dirs = GameDirs(app.filesDir)
    private var shell = newShell()
    private var engine = GameEngine(dirs, shell)

    val state = MutableStateFlow(GameState())
    val events = MutableSharedFlow<UiEvent>(extraBufferCapacity = 8)
    val readyState: StateFlow<GameState> get() = state
    val eventFlow: SharedFlow<UiEvent> get() = events

    private fun newShell() = ShellSession(rcFile = dirs.rc, home = dirs.home, startFolder = dirs.playground)

    private val jobs = mutableListOf<kotlinx.coroutines.Job>()

    init { boot() }

    private fun boot() {
        val e = engine
        jobs.forEach { it.cancel() }
        jobs.clear()
        jobs += viewModelScope.launch { e.state.collect { state.value = it } }
        jobs += viewModelScope.launch { e.events.collect { events.emit(it) } }
        jobs += viewModelScope.launch {
            dirs.home.mkdirs(); dirs.playground.mkdirs(); dirs.game.mkdirs()
            shell.start()
            e.start()
        }
    }

    fun submit(text: String) = viewModelScope.launch { engine.submit(text) }
    fun next() = viewModelScope.launch { engine.onContinue() }
    fun interrupt() = engine.interrupt()
    fun complete(text: String) = engine.complete(text)
    fun showOptions(o: List<String>) = engine.showOptions(o)
    fun filePickerClosed() = viewModelScope.launch { engine.filePickerClosed() }
    fun resize(rows: Int, cols: Int) = shell.resize(rows, cols)

    /** Menu: start over (deletes the save file and the playground, never anything else). */
    fun resetGame() {
        shell.close()
        dirs.save.delete()
        dirs.playground.deleteRecursively()
        shell = newShell()
        engine = GameEngine(dirs, shell)
        boot()
    }

    override fun onCleared() { shell.close() }
}
