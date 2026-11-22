package io.github.alexpp90.daemonsapprentice

import android.content.res.Configuration
import android.view.KeyEvent
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.alexpp90.daemonsapprentice.engine.Guard
import io.github.alexpp90.daemonsapprentice.game.Level0
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Level 0 from a fresh install, played twice: with touch alone (chips, Run,
 * Next, no typing) and with a hardware keyboard alone (no taps at all).
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class Level0PlayTest {
    @get:Rule val rule = createEmptyComposeRule()
    private val instr = InstrumentationRegistry.getInstrumentation()
    private val ctx = instr.targetContext
    private var scenario: ActivityScenario<MainActivity>? = null
    private val guard = Guard("/g", "/s")

    /** Runs a shell command as the test and waits until it has finished. */
    private fun shell(cmd: String): String =
        android.os.ParcelFileDescriptor.AutoCloseInputStream(instr.uiAutomation.executeShellCommand(cmd))
            .bufferedReader().use { it.readText() }

    private fun freshStart() {
        ctx.filesDir.listFiles()?.forEach { it.deleteRecursively() }
        FilePickerHook.override = {}
        shell("input keyevent KEYCODE_WAKEUP"); shell("wm dismiss-keyguard")
        dismissSystemDialogs()
        scenario = ActivityScenario.launch(MainActivity::class.java)
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
        disabledImes.forEach { shell("ime enable $it") }
        if (defaultIme.isNotEmpty()) shell("ime set $defaultIme")
        FilePickerHook.override = null
        shell("settings put secure show_ime_with_hard_keyboard 1")
    }

    private fun exists(tag: String) = rule.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()
    private var step = "start"
    private fun snap(name: String) {
        val bmp = instr.uiAutomation.takeScreenshot() ?: return
        val dir = File(ctx.cacheDir, "shots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }
    private val device = androidx.test.uiautomator.UiDevice.getInstance(instr)
    /** The emulator's own "isn't responding" dialogs (a slow fresh boot) are not the game's: wait them out. */
    private fun dismissSystemDialogs() {
        (device.findObject(androidx.test.uiautomator.By.res("android", "aerr_wait"))
            ?: device.findObject(androidx.test.uiautomator.By.text("Wait").pkg("android")))?.click()
    }
    private fun waitFor(timeout: Long = 15_000, what: () -> Boolean) {
        var polls = 0
        try { rule.waitUntil(timeout) { if (++polls % 20 == 0) dismissSystemDialogs(); what() } } catch (e: Throwable) { snap("fail-$step"); throw AssertionError("timed out at $step", e) }
    }
    private fun check(ok: Boolean, msg: String) { if (!ok) { snap("fail-$step"); throw AssertionError(msg) } }
    private fun saveText() = File(ctx.filesDir, "home/.apprentice-save").takeIf { it.exists() }?.readText() ?: ""

    @Test fun touchOnly() {
        ensureSoftKeyboard()
        freshStart()
        for (t in Level0.level.tasks) {
            step = "touch-" + t.id
            val firstChip = "chip:" + guard.words(t.solution).first()
            waitFor(if (t.id == "t0_1") 40_000 else 15_000) { exists("next") || exists(firstChip) }
            while (!exists(firstChip)) {
                rule.onNodeWithTag("next").performClick()
                waitFor { exists("next") || exists(firstChip) }
            }
            for (w in guard.words(t.solution)) rule.onNodeWithTag("chip:$w").performClick()
            rule.onNodeWithTag("run").performClick()
            waitFor { exists("next") }
        }
        while (!rule.onAllNodes(hasText("Level 0 complete", substring = true)).fetchSemanticsNodes().isNotEmpty()) {
            waitFor { exists("next") || rule.onAllNodes(hasText("Level 0 complete", substring = true)).fetchSemanticsNodes().isNotEmpty() }
            if (exists("next")) rule.onNodeWithTag("next").performClick()
        }
        assertTrue(saveText().contains("done=0"))
    }

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
            waitFor { exists("prompt-ready") || exists("next") }
        }
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

    private fun inputText(): String {
        var text = ""
        scenario!!.onActivity { a -> text = a.window.decorView.findViewWithTag<android.widget.EditText>("command-input").text.toString() }
        return text
    }

    @Test fun hardwareKeyboardOnly() {
        val keyboard = ctx.resources.configuration.keyboard == Configuration.KEYBOARD_QWERTY
        assumeTrue("the device has a hardware keyboard configured (hw.keyboard=yes)", keyboard)
        // Only a hardware keyboard: the emulator's built-in keyboard does not count as external, so
        // Android would still show the soft keyboard. Switch the soft keyboard off for this test.
        // Every input method off (Gboard, and the voice input Android would fall back to); restored after.
        defaultIme = shell("settings get secure default_input_method").trim().takeIf { it != "null" } ?: ""
        disabledImes = enabledImes()
        disabledImes.forEach { shell("ime disable $it") }
        freshStart()
        for (t in Level0.level.tasks) {
            step = "keys-" + t.id
            toPrompt(if (t.id == "t0_1") 40_000 else 15_000)
            // With a hardware keyboard: no key bar, no chips (they come only after hint).
            // The rule (ANDROID_CONCEPT): the key bar shows only while a soft keyboard is visible, and
            // chips only with a soft keyboard or after hint. Android on an emulator sometimes brings a
            // soft keyboard back mid-test; that is counted and reported, not a failure of the game.
            val ime = imeVisible()
            if (ime) imeShownAnyway++
            check(ime || !exists("keybar"), "a key bar without a soft keyboard in ${t.id}")
            check(ime || !exists("run"), "chips without a soft keyboard in ${t.id}")
            when (t.id) {
                "t0_3" -> { // Ctrl-C stops a running command, then the task is solved
                    type("sleep 20"); enter()
                    waitFor { exists("prompt-busy") }
                    Thread.sleep(500)
                    key(KeyEvent.KEYCODE_C, KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON)
                    waitFor(8000) { exists("prompt-ready") }
                    assertTrue(rule.onAllNodes(hasText("Stopped with Ctrl-C", substring = true)).fetchSemanticsNodes().isNotEmpty())
                    type("pwd"); enter()
                }
                "t0_6" -> { // Tab completes the file name
                    type("cat ti"); key(KeyEvent.KEYCODE_TAB); instr.waitForIdleSync()
                    assertEquals("cat timeline.txt ", inputText())
                    enter()
                }
                "t0_8" -> { // the Up arrow brings back the last command
                    key(KeyEvent.KEYCODE_DPAD_UP); instr.waitForIdleSync()
                    assertEquals("grep Linux timeline.txt", inputText())
                    repeat(23) { key(KeyEvent.KEYCODE_DEL) }
                    type(t.solution); enter()
                }
                else -> { type(t.solution); enter() }
            }
            waitFor { exists("next") }
            // `-e shots 1`: keep a screenshot of every solved task (for the owner's review).
            if (androidx.test.platform.app.InstrumentationRegistry.getArguments().getString("shots") == "1") { Thread.sleep(800); snap("keys-${t.id}") }
        }
        while (!rule.onAllNodes(hasText("Level 0 complete", substring = true)).fetchSemanticsNodes().isNotEmpty()) {
            enter()
            Thread.sleep(300)
        }
        assertTrue(saveText().contains("done=0"))
        if (imeShownAnyway > 0) android.util.Log.w("Level0PlayTest", "Android showed a soft keyboard in $imeShownAnyway of 8 tasks despite the hardware-keyboard set-up")
    }
}
