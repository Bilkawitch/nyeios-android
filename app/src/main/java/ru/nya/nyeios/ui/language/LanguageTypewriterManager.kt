package ru.nya.nyeios.ui.language

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class TypewriterPhase {
    IDLE,
    ERASING,
    TYPING
}

/**
 * Manages the top-to-bottom typewriter gimmick when switching UI language.
 *
 * When the language is changed:
 * 1. Erasing phase: existing text rapidly erases (backspaces).
 * 2. Typing phase: new language text types out character by character cascading from
 *    top to bottom based on each element's [order] coordinate (0.0f top to 1.0f bottom).
 * 3. Any interactive tap outside language selection skips the animation instantly.
 */
object LanguageTypewriterManager {
    var isAnimating by mutableStateOf(false)
        private set

    var phase by mutableStateOf(TypewriterPhase.IDLE)
        private set

    var progress by mutableFloatStateOf(1f)
        private set

    var wasLanguageOptionClick = false

    private var animJob: Job? = null
    private var pendingLanguage: AppLanguage? = null
    private var appContext: Context? = null

    fun triggerLanguageChange(context: Context, newLanguage: AppLanguage) {
        if (newLanguage == LanguageManager.currentLanguage && phase == TypewriterPhase.IDLE) {
            return
        }
        appContext = context.applicationContext
        wasLanguageOptionClick = true
        animJob?.cancel()
        pendingLanguage = newLanguage

        animJob = CoroutineScope(Dispatchers.Main).launch {
            isAnimating = true

            // 1. ERASING PHASE: ~160ms
            phase = TypewriterPhase.ERASING
            val eraseSteps = 10
            val eraseDelay = 16L
            for (i in 0..eraseSteps) {
                progress = i.toFloat() / eraseSteps
                delay(eraseDelay)
            }

            // Apply new language to system
            LanguageManager.setLanguage(context, newLanguage)
            pendingLanguage = null

            // 2. TYPING PHASE: ~750ms top-to-bottom cascade
            phase = TypewriterPhase.TYPING
            val typeSteps = 45
            val typeDelay = 16L
            for (i in 0..typeSteps) {
                progress = i.toFloat() / typeSteps
                delay(typeDelay)
            }

            // 3. COMPLETE
            phase = TypewriterPhase.IDLE
            progress = 1f
            isAnimating = false
        }
    }

    fun skip() {
        if (!isAnimating) return
        animJob?.cancel()
        pendingLanguage?.let { lang ->
            appContext?.let { ctx ->
                LanguageManager.setLanguage(ctx, lang)
            }
        }
        pendingLanguage = null
        phase = TypewriterPhase.IDLE
        progress = 1f
        isAnimating = false
    }

    internal fun setStateForTesting(animating: Boolean, testPhase: TypewriterPhase, testProgress: Float) {
        isAnimating = animating
        phase = testPhase
        progress = testProgress
    }

    fun getDisplayText(text: String, order: Float): String {
        if (!isAnimating || text.isEmpty()) return text
        return when (phase) {
            TypewriterPhase.IDLE -> text
            TypewriterPhase.ERASING -> {
                val remainingRatio = (1f - progress).coerceIn(0f, 1f)
                val remainingChars = (remainingRatio * text.length).toInt().coerceIn(0, text.length)
                text.take(remainingChars)
            }
            TypewriterPhase.TYPING -> {
                val start = (order * 0.65f).coerceIn(0f, 0.95f)
                val duration = 0.35f
                val end = (start + duration).coerceAtMost(1f)
                when {
                    progress <= start -> ""
                    progress >= end -> text
                    else -> {
                        val lineRatio = (progress - start) / (end - start)
                        val charCount = (lineRatio * text.length).toInt().coerceIn(0, text.length)
                        text.take(charCount)
                    }
                }
            }
        }
    }
}

/**
 * Composable helper returning the animated typewriter substring of [text].
 *
 * @param text The full target text.
 * @param order Vertical sequence order from 0.0f (top of screen) to 1.0f (bottom of screen).
 */
@Composable
fun typewriterText(text: String, order: Float = 0.5f): String {
    return LanguageTypewriterManager.getDisplayText(text, order)
}
