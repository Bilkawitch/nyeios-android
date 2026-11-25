package ru.nya.nyeios.ui.onboarding

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned

@Stable
class OnboardingTargetRegistry {
    private val boundsMap = mutableStateMapOf<OnboardingTargetId, Rect>()

    fun update(id: OnboardingTargetId, bounds: Rect) {
        if (bounds.width > 0f && bounds.height > 0f) {
            boundsMap[id] = bounds
        }
    }

    fun remove(id: OnboardingTargetId) {
        boundsMap.remove(id)
    }

    fun boundsFor(id: OnboardingTargetId?): Rect? {
        if (id == null) return null
        return boundsMap[id]
    }
}

val LocalOnboardingRegistry = staticCompositionLocalOf<OnboardingTargetRegistry?> { null }

@Composable
fun Modifier.onboardingTarget(id: OnboardingTargetId, enabled: Boolean = true): Modifier {
    if (!enabled) return this
    val registry = LocalOnboardingRegistry.current ?: return this
    DisposableEffect(registry, id) {
        onDispose { registry.remove(id) }
    }
    return this.onGloballyPositioned { coords ->
        if (coords.isAttached) {
            registry.update(id, coords.boundsInWindow())
        }
    }
}
