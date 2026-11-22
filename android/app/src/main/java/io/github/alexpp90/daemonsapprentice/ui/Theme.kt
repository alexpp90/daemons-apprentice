package io.github.alexpp90.daemonsapprentice.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle

/** The download page's palette (R35). */
object C {
    val bg = Color(0xFF0B0D14)
    val bg2 = Color(0xFF11141F)
    val card = Color(0xFF161A28)
    val line = Color(0xFF262B3F)
    val text = Color(0xFFE6E9F5)
    val muted = Color(0xFF9AA3C2)
    val green = Color(0xFF9ECE6A)
    val blue = Color(0xFF7AA2F7)
    val amber = Color(0xFFE0AF68)
    val pink = Color(0xFFF7768E)
    val violet = Color(0xFFBB9AF7)
    val key = Color(0xFF2A2F45)
    val keyText = Color(0xFFDFE3F3)
    val kbd = Color(0xFF1B1F2E)
}

val Mono = FontFamily.Monospace

@Composable
fun ApprenticeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = C.bg, surface = C.bg2, primary = C.blue, onPrimary = C.bg,
            onBackground = C.text, onSurface = C.text, secondary = C.green,
        ),
        content = content,
    )
}

/** `**bold**` and `` `code` `` in the game's texts. */
fun markup(s: String, code: Color = C.green): AnnotatedString = buildAnnotatedString {
    var i = 0
    var bold = false
    while (i < s.length) {
        when {
            s.startsWith("**", i) -> { bold = !bold; i += 2 }
            s[i] == '`' -> {
                val end = s.indexOf('`', i + 1)
                if (end < 0) { append(s[i]); i++ } else {
                    withStyle(SpanStyle(fontFamily = Mono, color = code, fontWeight = if (bold) FontWeight.Bold else null)) {
                        append(s.substring(i + 1, end))
                    }
                    i = end + 1
                }
            }
            else -> {
                if (bold) withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(s[i]) } else append(s[i])
                i++
            }
        }
    }
}
