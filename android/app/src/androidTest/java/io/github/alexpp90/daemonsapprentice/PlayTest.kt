package io.github.alexpp90.daemonsapprentice

import android.content.res.Configuration
import android.view.KeyEvent
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.alexpp90.daemonsapprentice.engine.Guard
import io.github.alexpp90.daemonsapprentice.game.ChipWords
import io.github.alexpp90.daemonsapprentice.game.GameDirs
import io.github.alexpp90.daemonsapprentice.game.Levels
import io.github.alexpp90.daemonsapprentice.game.solutionNow
import androidx.compose.ui.test.performTextInput
import io.github.alexpp90.daemonsapprentice.game.Task
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Every level from a fresh install, played twice: with touch alone (chips, Run, Next, Ctrl-C;
 * from Level 2 on the chips come after tapping `hint`, as for a player) and with a hardware
 * keyboard alone (no taps at all: typing, Enter, Tab, the arrows, Ctrl-C).
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class PlayTest {
    @get:Rule val rule = createEmptyComposeRule()
    private val instr = InstrumentationRegistry.getInstrumentation()
    private val ctx = instr.targetContext
    private var scenario: ActivityScenario<MainActivity>? = null
    private val guard = Guard("/g", "/s")
    private val tasks: List<Task> = Levels.all.flatMap { it.tasks }
    private val lastLevel = Levels.all.last().number
    private val dirs by lazy { GameDirs(ctx.filesDir) }
    /** Editor tasks: the text the player adds at the end of the file. */
    private fun appended(t: Task) = t.drive?.takeIf { it.startsWith("edit-append:") }?.removePrefix("edit-append:")

    /** Runs a shell command as the test and waits until it has finished. */
    private fun shell(cmd: String): String =
        android.os.ParcelFileDescriptor.AutoCloseInputStream(instr.uiAutomation.executeShellCommand(cmd))
            .bufferedReader().use { it.readText() }

    private val device = androidx.test.uiautomator.UiDevice.getInstance(instr)

    /** The emulator's own "isn't responding" dialogs (a slow fresh boot) are not the game's: wait them out. */
    private fun dismissSystemDialogs() {
        (device.findObject(androidx.test.uiautomator.By.res("android", "aerr_wait"))
            ?: device.findObject(androidx.test.uiautomator.By.text("Wait").pkg("android")))?.click()
    }

    @org.junit.Before fun patience() {
        // A slow emulator (software rendering, a big tablet screen, another emulator next to it)
        // can need longer than Espresso's default to settle after a keyboard animation.
        androidx.test.espresso.IdlingPolicies.setMasterPolicyTimeout(120, java.util.concurrent.TimeUnit.SECONDS)
        androidx.test.espresso.IdlingPolicies.setIdlingResourceTimeout(120, java.util.concurrent.TimeUnit.SECONDS)
    }

    private fun freshStart() {
        ctx.filesDir.listFiles()?.forEach { it.deleteRecursively() }
        FilePickerHook.override = {}
        shell("input keyevent KEYCODE_WAKEUP"); shell("wm dismiss-keyguard")
        dismissSystemDialogs()
        scenario = ActivityScenario.launch(MainActivity::class.java)
        // A cold, busy emulator sometimes never brings the app to the front: launch it once more.
        val up = try { rule.waitUntil(40_000) { exists("menu") }; true } catch (_: Throwable) { false }
        if (!up) {
            snap("relaunch")
            scenario?.close()
            dismissSystemDialogs()
            scenario = ActivityScenario.launch(MainActivity::class.java)
        }
    }

    /** Where the node is now (null if it is not there). */
    private fun bounds(tag: String, index: Int) =
        rule.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().getOrNull(index)?.boundsInRoot

    /**
     * A real tap that says where it failed. It waits until the target stops moving first: on a
     * slow device the soft keyboard is still sliding in, and everything above it moves up.
     */
    private fun tap(tag: String, index: Int = 0) {
        var last = bounds(tag, index)
        val until = System.currentTimeMillis() + 5_000
        while (System.currentTimeMillis() < until) {
            Thread.sleep(250)
            val now = bounds(tag, index)
            if (now != null && now == last) break
            last = now
        }
        try { rule.onAllNodes(hasTestTag(tag))[index].performClick() }
        catch (e: Throwable) { snap("fail-$step"); throw AssertionError("tapping $tag failed at $step", e) }
    }

    private var disabledImes = emptyList<String>()
    private var defaultIme = ""
    private fun enabledImes() = shell("ime list -s").lines().map { it.trim() }.filter { it.isNotEmpty() }

    /** Touch: the phone's soft keyboard must be there (an earlier test may have switched it off). */
    private fun ensureSoftKeyboard() {
        val all = shell("ime list -a -s").lines().map { it.trim() }.filter { it.isNotEmpty() }
        val gboard = all.firstOrNull { it.contains("inputmethod.latin") } ?: all.firstOrNull() ?: return
        shell("ime enable $gboard"); shell("ime set $gboard")
        shell("settings put secure show_ime_with_hard_keyboard 1")
    }

    @After fun tearDown() {
        scenario?.close()
        FilePickerHook.override = null
        disabledImes.forEach { shell("ime enable $it") }
        if (defaultIme.isNotEmpty()) shell("ime set $defaultIme")
        shell("settings put secure show_ime_with_hard_keyboard 1")
    }

    private fun exists(tag: String) = rule.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()
    private fun hasTextNode(s: String) = rule.onAllNodes(hasText(s, substring = true)).fetchSemanticsNodes().isNotEmpty()
    private var step = "start"
    private fun snap(name: String) {
        val bmp = instr.uiAutomation.takeScreenshot() ?: return
        val dir = File(ctx.cacheDir, "shots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }
    private fun waitFor(timeout: Long = 15_000, what: () -> Boolean) {
        var polls = 0
        try { rule.waitUntil(timeout) { if (++polls % 20 == 0) dismissSystemDialogs(); what() } }
        catch (e: Throwable) { snap("fail-$step"); throw AssertionError("timed out at $step", e) }
    }
    private fun check(ok: Boolean, msg: String) { if (!ok) { snap("fail-$step"); throw AssertionError(msg) } }
    private fun saveText() = File(ctx.filesDir, "home/.apprentice-save").takeIf { it.exists() }?.readText() ?: ""
    private val shots get() = InstrumentationRegistry.getArguments().getString("shots") == "1"
    private val finished get() = hasTextNode("Level $lastLevel complete")

    // ------------------------------------------------------------------ touch

    @Test fun touchOnly() {
        ensureSoftKeyboard()
        freshStart()
        for ((i, t) in tasks.withIndex()) {
            step = "touch-${t.id}"
            val d = t.drive ?: ""
            val lines = if (d == "lines") t.solution.lines() else listOf("")
            val firstChip = "chip:" + ChipWords.split(if (d == "lines") lines[0] else t.solution).first()
            // Next until the prompt; from Level 2 on, a player taps `hint` to get the pieces.
            waitFor(if (i == 0) 40_000 else 15_000) { exists("next") || exists("prompt-ready") }
            // Ready means: the prompt, and no Next left on the screen (a busy emulator may still
            // show the page before for a moment).
            while (!(exists("prompt-ready") && !exists("next"))) {
                if (exists("next")) tap("next")
                waitFor { exists("next") || exists("prompt-ready") }
            }
            if (!exists(firstChip)) {
                tap("hint")
                waitFor { exists(firstChip) }
            }
            // The live solution (a PID) is read only now, after the task has set things up.
            val all = if (d == "lines") lines else listOf(t.solutionNow(dirs))
            for ((n, line) in all.withIndex()) {
                for (w in ChipWords.split(line)) { waitFor { exists("chip:$w") }; tap("chip:$w") }
                waitFor { exists("run") }
                tap("run")
                if (n < all.lastIndex) waitFor { exists("prompt-more") }
            }
            when {
                d == "ctrlc" -> { waitFor { exists("prompt-busy") }; Thread.sleep(500); tap("key:Ctrl-C") }
                d == "more-ctrlc" -> { waitFor { exists("prompt-more") }; Thread.sleep(500); tap("key:Ctrl-C") }
                appended(t) != null -> {
                    waitFor { exists("editor-text") }
                    rule.onNodeWithTag("editor-text").performTextInput(appended(t)!!)
                    tap("editor-done")
                }
            }
            waitFor(if (d == "ctrlc") 20_000 else 60_000) { exists("next") }
            if (shots) snap("touch-${t.id}")
        }
        while (!finished) {
            waitFor { exists("next") || finished }
            if (exists("next")) tap("next")
        }
        val s = saveText()
        assertTrue(s, s.contains("done=" + Levels.all.joinToString(",") { it.number.toString() }))
    }

    // --------------------------------------------------------------- keyboard

    /** The device's own physical keyboard: keys from it count as a hardware keyboard for Android and Gboard. */
    private val keyboardId: Int by lazy {
        android.view.InputDevice.getDeviceIds().map { android.view.InputDevice.getDevice(it) }
            .firstOrNull { it != null && !it.isVirtual && it.keyboardType == android.view.InputDevice.KEYBOARD_TYPE_ALPHABETIC }
            ?.id ?: android.view.KeyCharacterMap.VIRTUAL_KEYBOARD
    }
    private fun send(e: KeyEvent) {
        instr.sendKeySync(KeyEvent(e.downTime, e.eventTime, e.action, e.keyCode, e.repeatCount, e.metaState,
            keyboardId, 0, 0, android.view.InputDevice.SOURCE_KEYBOARD))
    }
    private fun key(code: Int, meta: Int = 0) {
        val now = android.os.SystemClock.uptimeMillis()
        send(KeyEvent(now, now, KeyEvent.ACTION_DOWN, code, 0, meta))
        send(KeyEvent(now, now, KeyEvent.ACTION_UP, code, 0, meta))
    }
    private fun type(s: String) {
        val map = android.view.KeyCharacterMap.load(android.view.KeyCharacterMap.VIRTUAL_KEYBOARD)
        map.getEvents(s.toCharArray())?.forEach { send(it) }
        instr.waitForIdleSync()
    }
    private fun enter() { key(KeyEvent.KEYCODE_ENTER); instr.waitForIdleSync() }

    /** Enter on an empty line goes on until the game waits for a command. */
    private fun toPrompt(timeout: Long = 15_000) {
        waitFor(timeout) { exists("prompt-ready") || exists("next") }
        while (!exists("prompt-ready")) {
            enter()
            waitFor(timeout) { exists("prompt-ready") || exists("next") }
        }
    }

    private fun inputText(): String {
        var text = ""
        scenario!!.onActivity { a -> text = a.window.decorView.findViewWithTag<android.widget.EditText>("command-input").text.toString() }
        return text
    }

    private var imeShownAnyway = 0
    private fun imeVisible(): Boolean {
        var v = false
        scenario!!.onActivity { a ->
            v = androidx.core.view.ViewCompat.getRootWindowInsets(a.window.decorView)
                ?.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime()) == true
        }
        return v
    }

    @Test fun hardwareKeyboardOnly() {
        val keyboard = ctx.resources.configuration.keyboard == Configuration.KEYBOARD_QWERTY
        assumeTrue("the device has a hardware keyboard configured (hw.keyboard=yes)", keyboard)
        // Only a hardware keyboard: the emulator's built-in keyboard does not count as external, so
        // Android would still show the soft keyboard. Switch the soft keyboards off for this test.
        defaultIme = shell("settings get secure default_input_method").trim().takeIf { it != "null" } ?: ""
        disabledImes = enabledImes()
        disabledImes.forEach { shell("ime disable $it") }
        freshStart()
        for ((i, t) in tasks.withIndex()) {
            step = "keys-${t.id}"
            toPrompt(if (i == 0) 40_000 else 15_000)
            // The rule (ANDROID_CONCEPT): the key bar shows only while a soft keyboard is visible, and
            // chips only with a soft keyboard or after hint. Android on an emulator sometimes brings a
            // soft keyboard back mid-test; that is counted and reported, not a failure of the game.
            val ime = imeVisible()
            if (ime) imeShownAnyway++
            check(ime || !exists("keybar"), "a key bar without a soft keyboard in ${t.id}")
            check(ime || !exists("run"), "chips without a soft keyboard in ${t.id}")
            val d = t.drive ?: ""
            when {
                d == "ctrlc" -> {
                    type(t.solution); enter()
                    waitFor { exists("prompt-busy") }
                    Thread.sleep(500)
                    key(KeyEvent.KEYCODE_C, KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON)
                }
                d.startsWith("tab:") -> { // Tab completes the name
                    type(d.removePrefix("tab:")); key(KeyEvent.KEYCODE_TAB); instr.waitForIdleSync()
                    val now = inputText()
                    check(now.startsWith(t.solution), "Tab gave '$now' in ${t.id}")
                    enter()
                }
                d == "more-ctrlc" -> {
                    type(t.solution); enter()
                    waitFor { exists("prompt-more") }
                    Thread.sleep(500)
                    key(KeyEvent.KEYCODE_C, KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON)
                }
                d == "lines" -> t.solution.lines().forEachIndexed { n, line ->
                    type(line); enter()
                    if (n < t.solution.lines().lastIndex) waitFor { exists("prompt-more") }
                }
                appended(t) != null -> {
                    type(t.solution); enter()
                    waitFor { exists("editor-text") }
                    Thread.sleep(500)
                    type(appended(t)!!)
                    key(KeyEvent.KEYCODE_ESCAPE)
                }
                d == "up-fix" -> { // ↑ brings back "ehco hello"; fix it with the arrows
                    key(KeyEvent.KEYCODE_DPAD_UP); instr.waitForIdleSync()
                    check(inputText() == "ehco hello", "Up gave '${inputText()}' in ${t.id}")
                    key(KeyEvent.KEYCODE_MOVE_HOME); key(KeyEvent.KEYCODE_DPAD_RIGHT)
                    key(KeyEvent.KEYCODE_FORWARD_DEL); key(KeyEvent.KEYCODE_FORWARD_DEL)
                    type("ch")
                    check(inputText() == "echo hello", "fixed line is '${inputText()}' in ${t.id}")
                    enter()
                }
                t.id == "t0_8" -> { // the Up arrow brings back the last command
                    key(KeyEvent.KEYCODE_DPAD_UP); instr.waitForIdleSync()
                    check(inputText() == "grep Linux timeline.txt", "Up gave '${inputText()}' in ${t.id}")
                    repeat(23) { key(KeyEvent.KEYCODE_DEL) }
                    type(t.solution); enter()
                }
                else -> { type(t.solutionNow(dirs)); enter() }
            }
            waitFor(if (d == "ctrlc") 20_000 else 60_000) { exists("next") }
            // `-e shots 1`: keep a screenshot of every solved task (for the owner's review).
            if (shots) { Thread.sleep(800); snap("keys-${t.id}") }
        }
        while (!finished) { enter(); Thread.sleep(300) }
        val s = saveText()
        assertTrue(s, s.contains("done=" + Levels.all.joinToString(",") { it.number.toString() }))
        if (imeShownAnyway > 0) android.util.Log.w("PlayTest", "Android showed a soft keyboard in $imeShownAnyway of ${tasks.size} tasks despite the hardware-keyboard set-up")
    }
}
