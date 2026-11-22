package io.github.alexpp90.daemonsapprentice.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Typeface
import android.text.InputType
import android.util.TypedValue
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import android.widget.EditText

/** What the input line asks of the game. */
interface CommandListener {
    fun onSubmit(text: String)
    fun onTab(text: String): String
    fun onInterrupt()
    fun onHint()
    fun onClear()
}

/**
 * The command line. A stock text field lets the keyboard autocorrect,
 * suggest and learn; this one asks the keyboard for plain visible
 * characters with no suggestions and no learning (research C1), and takes
 * Tab, the arrows, Enter, Ctrl-C, Ctrl-L and F1 before the system's focus
 * navigation does (C2). Characters are read as text, never as key codes,
 * so every keyboard layout works.
 */
@SuppressLint("ViewConstructor")
class CommandEditText(context: Context, var listener: CommandListener?) : EditText(context) {

    private val history = mutableListOf<String>()
    private var historyPos = 0
    private var draft = ""

    init {
        isSingleLine = true
        typeface = Typeface.MONOSPACE
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        background = null
        setPadding(0, 0, 0, 0)
        inputType = INPUT_TYPE
        imeOptions = IME_OPTIONS
        isFocusableInTouchMode = true
        setOnEditorActionListener { _, actionId, event ->
            val enter = event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN
            if (actionId == EditorInfo.IME_ACTION_GO || actionId == EditorInfo.IME_ACTION_DONE || enter) { submit(); true } else false
        }
    }

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection? {
        val ic = super.onCreateInputConnection(outAttrs)
        outAttrs.inputType = INPUT_TYPE
        outAttrs.imeOptions = IME_OPTIONS
        return ic
    }

    fun submit() {
        val t = text.toString()
        if (t.isNotBlank()) {
            if (history.lastOrNull() != t) history += t
        }
        historyPos = history.size
        draft = ""
        setText("")
        listener?.onSubmit(t)
    }

    fun insert(s: String) {
        val start = selectionStart.coerceAtLeast(0)
        val end = selectionEnd.coerceAtLeast(0)
        text.replace(minOf(start, end), maxOf(start, end), s)
    }

    fun moveCursor(delta: Int) {
        setSelection((selectionStart + delta).coerceIn(0, text.length))
    }

    fun historyUp() {
        if (history.isEmpty()) return
        if (historyPos == history.size) draft = text.toString()
        historyPos = (historyPos - 1).coerceAtLeast(0)
        setText(history[historyPos]); setSelection(text.length)
    }

    fun historyDown() {
        if (historyPos >= history.size) return
        historyPos++
        setText(if (historyPos == history.size) draft else history[historyPos]); setSelection(text.length)
    }

    fun tab() {
        val l = listener ?: return
        val before = text.substring(0, selectionStart.coerceAtLeast(0))
        val after = text.substring(selectionStart.coerceAtLeast(0))
        val done = l.onTab(before)
        setText(done + after)
        setSelection(done.length)
    }

    fun showKeyboard() {
        requestFocus()
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_TAB -> { tab(); return true }
            KeyEvent.KEYCODE_DPAD_UP -> { historyUp(); return true }
            KeyEvent.KEYCODE_DPAD_DOWN -> { historyDown(); return true }
            KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> { submit(); return true }
            KeyEvent.KEYCODE_F1 -> { listener?.onHint(); return true }
            KeyEvent.KEYCODE_C -> if (event.isCtrlPressed && selectionStart == selectionEnd) { listener?.onInterrupt(); return true }
            KeyEvent.KEYCODE_L -> if (event.isCtrlPressed) { listener?.onClear(); return true }
        }
        return super.onKeyDown(keyCode, event)
    }

    // Tab and the arrows would otherwise move the focus away on key up, too.
    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean = when (keyCode) {
        KeyEvent.KEYCODE_TAB, KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN,
        KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> true
        else -> super.onKeyUp(keyCode, event)
    }

    companion object {
        const val INPUT_TYPE = InputType.TYPE_CLASS_TEXT or
            InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        const val IME_OPTIONS = EditorInfo.IME_ACTION_GO or EditorInfo.IME_FLAG_NO_EXTRACT_UI or
            EditorInfo.IME_FLAG_NO_FULLSCREEN or EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
    }
}
