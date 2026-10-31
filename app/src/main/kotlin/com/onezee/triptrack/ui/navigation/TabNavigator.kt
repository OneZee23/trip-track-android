package com.onezee.triptrack.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.CreationExtras
import com.onezee.triptrack.designsystem.theme.LocalReduceMotion
import java.util.UUID
import kotlin.reflect.KClass
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * A destination of a tab's stack. [key] decides «the same screen»: pushing a destination whose key
 * is already on top does nothing (iOS: ProfileView.push and PlacesDest, a double tap on one card
 * must not stack two identical screens).
 */
interface NavDestination {
    val key: Any
}

/** One screen of a stack: its destination and the view models that live exactly as long as it. */
class NavEntry<D : NavDestination> internal constructor(val destination: D) {
    val id: String = UUID.randomUUID().toString()
    internal val viewModels = ViewModelStore()
}

/**
 * iOS: a tab's `NavigationStack(path:)` with a typed path (FeedView, `[MeDest]`). Held outside the
 * composition (in the app graph), so the stack and its screens' view models survive a tab switch
 * and a configuration change, as the iOS path state does.
 *
 * Main thread only. A popped screen's view models are cleared once its exit has played.
 */
@Stable
class TabNavigator<D : NavDestination> {
    private val entries = mutableStateListOf<NavEntry<D>>()
    private val clearing = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** True after the last change was a pop: the host slides the other way. */
    internal var lastWasPop by mutableStateOf(false)
        private set

    val stack: List<D> get() = entries.map { it.destination }
    val top: D? get() = entries.lastOrNull()?.destination
    val canPop: Boolean get() = entries.isNotEmpty()
    internal val topEntry: NavEntry<D>? get() = entries.lastOrNull()
    internal val entryIds: List<String> get() = entries.map { it.id }

    /** Idempotent: the destination already on top stays the only one. */
    fun push(destination: D) {
        if (entries.lastOrNull()?.destination?.key == destination.key) return
        lastWasPop = false
        entries += NavEntry(destination)
    }

    fun pop(): Boolean {
        val removed = entries.removeLastOrNull() ?: return false
        lastWasPop = true
        retire(listOf(removed))
        return true
    }

    /** Pops every screen above the first one whose destination matches [predicate] (iOS `removeLast(k)`). */
    fun popTo(predicate: (D) -> Boolean) {
        val index = entries.indexOfLast { predicate(it.destination) }
        if (index < 0 || index == entries.lastIndex) return
        val removed = entries.subList(index + 1, entries.size).toList()
        repeat(removed.size) { entries.removeAt(entries.lastIndex) }
        lastWasPop = true
        retire(removed)
    }

    fun popToRoot() {
        if (entries.isEmpty()) return
        val removed = entries.toList()
        entries.clear()
        lastWasPop = true
        retire(removed)
    }

    /** Removes a screen anywhere in the stack, e.g. a deleted trip under the screen that deleted it. */
    fun remove(predicate: (D) -> Boolean) {
        val removed = entries.filter { predicate(it.destination) }
        if (removed.isEmpty()) return
        val topRemoved = removed.lastOrNull() === entries.lastOrNull()
        entries.removeAll(removed)
        if (topRemoved) lastWasPop = true
        retire(removed)
    }

    internal fun <VM : ViewModel> viewModel(entry: NavEntry<D>, key: String, type: KClass<VM>, create: () -> VM): VM {
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: KClass<T>, extras: CreationExtras): T = create() as T
        }
        return ViewModelProvider.create(entry.viewModels, factory)[key, type]
    }

    private fun retire(removed: List<NavEntry<D>>) {
        clearing.launch {
            // The screen slides out with its last state; nothing may run after it is gone.
            delay(PUSH_MILLIS.toLong() + 100)
            removed.forEach { it.viewModels.clear() }
        }
    }

    companion object {
        /** iOS: the push of a UINavigationController, about 0.35 s. */
        const val PUSH_MILLIS = 350
    }
}

/** The entry a screen is composed in: what [entryViewModel] stores its view models in. */
internal class EntryScope(val owner: (String, KClass<out ViewModel>, () -> ViewModel) -> ViewModel)

private val LocalEntryScope = staticCompositionLocalOf<EntryScope?> { null }

/**
 * iOS `viewDidAppear` of a pushed screen: false while the push plays, true once it has landed. A
 * live map mounts on it (CLAUDE.md «Trip detail first frame»: the static route first, MapKit after
 * the push, not after a fixed sleep).
 */
val LocalScreenAppeared = staticCompositionLocalOf { true }

/** The view model of the screen this is composed in, created once per screen and cleared with it. */
@Composable
inline fun <reified VM : ViewModel> entryViewModel(key: String = VM::class.java.name, noinline create: () -> VM): VM =
    entryViewModel(key, VM::class, create)

@PublishedApi
@Composable
internal fun <VM : ViewModel> entryViewModel(key: String, type: KClass<VM>, create: () -> VM): VM {
    val scope = LocalEntryScope.current ?: error("entryViewModel outside a TabNavHost screen")
    @Suppress("UNCHECKED_CAST")
    return remember(scope, key) { scope.owner(key, type, create) as VM }
}

/**
 * The stack of a tab over its [root]: the top screen slides in from the trailing edge like an iOS
 * push and back out on a pop; system back pops before it reaches the shell (which returns to
 * Лента). Each screen keeps its saveable state (scroll positions) while another lies above it.
 */
@Composable
fun <D : NavDestination> TabNavHost(
    navigator: TabNavigator<D>,
    modifier: Modifier = Modifier,
    root: @Composable () -> Unit,
    screen: @Composable (D) -> Unit,
) {
    val saveable = rememberSaveableStateHolder()
    val reduceMotion = LocalReduceMotion.current
    BackHandler(enabled = navigator.canPop) { navigator.pop() }
    // A popped screen's saved scroll positions go with it.
    val live = navigator.entryIds
    val known = remember { mutableSetOf<String>() }
    LaunchedEffect(live) {
        known.filterNot { it in live }.forEach(saveable::removeState)
        known.retainAll(live.toSet())
        known.addAll(live)
    }
    val top = navigator.topEntry
    AnimatedContent(
        targetState = top,
        modifier = modifier,
        contentKey = { it?.id ?: ROOT },
        transitionSpec = {
            val millis = if (reduceMotion) 0 else TabNavigator.PUSH_MILLIS
            val spec = tween<androidx.compose.ui.unit.IntOffset>(millis, easing = FastOutSlowInEasing)
            val fade = tween<Float>(millis, easing = FastOutSlowInEasing)
            if (navigator.lastWasPop) {
                (slideInHorizontally(spec) { -it / 3 } + fadeIn(fade, 0.6f)) togetherWith slideOutHorizontally(spec) { it }
            } else {
                slideInHorizontally(spec) { it } togetherWith (slideOutHorizontally(spec) { -it / 3 } + fadeOut(fade, 0.6f))
            }.apply { targetContentZIndex = if (navigator.lastWasPop) 0f else 1f }
        },
        label = "tabStack",
    ) { entry ->
        val appeared = transition.currentState == EnterExitState.Visible && transition.targetState == EnterExitState.Visible
        Box(Modifier.fillMaxSize()) {
            saveable.SaveableStateProvider(entry?.id ?: ROOT) {
                if (entry == null) {
                    root()
                } else {
                    val scope = remember(entry) {
                        EntryScope { key, type, create ->
                            @Suppress("UNCHECKED_CAST")
                            navigator.viewModel(entry, key, type as KClass<ViewModel>, create)
                        }
                    }
                    CompositionLocalProvider(LocalEntryScope provides scope, LocalScreenAppeared provides appeared) {
                        screen(entry.destination)
                    }
                }
            }
        }
    }
}

private const val ROOT = "root"

/**
 * A view model owned by the composition it is created in, for screens outside a stack entry (a
 * sheet opened from a tab's root): created once per [key], cleared when it leaves the composition.
 */
@Composable
inline fun <reified VM : ViewModel> rememberLocalViewModel(key: Any, noinline create: () -> VM): VM {
    val store = remember(key) { ViewModelStore() }
    androidx.compose.runtime.DisposableEffect(store) { onDispose { store.clear() } }
    return remember(store) {
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: KClass<T>, extras: CreationExtras): T = create() as T
        }
        ViewModelProvider.create(store, factory)[VM::class]
    }
}
