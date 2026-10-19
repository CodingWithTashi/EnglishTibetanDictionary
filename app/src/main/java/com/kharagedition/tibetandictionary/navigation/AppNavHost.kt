package com.kharagedition.tibetandictionary.navigation

import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModelStoreOwner
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navDeepLink
import androidx.navigation.toRoute
import com.kharagedition.tibetandictionary.ui.about.AboutScreen
import com.kharagedition.tibetandictionary.ui.entry.EntryScreen
import com.kharagedition.tibetandictionary.ui.main.MainScaffold
import com.kharagedition.tibetandictionary.ui.main.MainViewModel
import com.kharagedition.tibetandictionary.ui.onboarding.OnboardingScreen
import com.kharagedition.tibetandictionary.ui.search.SearchScreen

private typealias Scope = AnimatedContentTransitionScope<NavBackStackEntry>

private const val PUSH_MS = 200

/** Pushed screens slide in from a quarter of the width while fading. */
private const val SLIDE_DIVISOR = 4

private val slide = tween<IntOffset>(PUSH_MS)
private val fade = tween<Float>(PUSH_MS)

/** Search opens and closes with a fade only; every other push slides. */
private fun NavBackStackEntry.fadesOnly(): Boolean = destination.hasRoute<Search>()

private fun Scope.pushEnter(): EnterTransition =
    if (targetState.fadesOnly()) fadeIn(fade) else slideInHorizontally(slide) { it / SLIDE_DIVISOR } + fadeIn(fade)

private fun Scope.pushExit(): ExitTransition =
    if (targetState.fadesOnly()) fadeOut(fade) else slideOutHorizontally(slide) { -it / SLIDE_DIVISOR } + fadeOut(fade)

private fun Scope.popEnter(): EnterTransition =
    if (initialState.fadesOnly()) fadeIn(fade) else slideInHorizontally(slide) { -it / SLIDE_DIVISOR } + fadeIn(fade)

private fun Scope.popExit(): ExitTransition =
    if (initialState.fadesOnly()) fadeOut(fade) else slideOutHorizontally(slide) { it / SLIDE_DIVISOR } + fadeOut(fade)

/**
 * The app's nav graph: [Onboarding] → [Main] (bottom-bar tabs), with [Search], [Entry] and [About] pushed on top
 * without the bar. Links from [NavigationEventBus] are followed once onboarding is done.
 */
@Composable
fun AppNavHost(
    startDestination: Any,
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    val activity = LocalActivity.current
    // Activity-scoped so deep links and pushed routes can switch the tab shown by the scaffold.
    val mainViewModel: MainViewModel = hiltViewModel(
        viewModelStoreOwner = checkNotNull(activity as? ViewModelStoreOwner) { "Host must be a ViewModelStoreOwner" },
    )
    val eventBus = hiltViewModel<NavigationEventsViewModel>().bus

    LaunchedEffect(navController) {
        eventBus.events.collect { link ->
            val current = navController.currentDestination
            // Links are dropped until onboarding is done.
            if (current != null && !current.hasRoute<Onboarding>()) {
                when (link) {
                    is DeepLink.OpenEntry -> navController.navigate(Entry(link.entryId))
                    is DeepLink.OpenSearch -> navController.navigate(Search(query = link.query))
                }
            }
            eventBus.consumed()
        }
    }

    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
        enterTransition = { pushEnter() },
        exitTransition = { pushExit() },
        popEnterTransition = { popEnter() },
        popExitTransition = { popExit() },
    ) {
        composable<Onboarding> {
            OnboardingScreen(onFinished = {
                navController.navigate(Main) { popUpTo<Onboarding> { inclusive = true } }
            })
        }
        composable<Main> {
            MainScaffold(
                onOpenSearch = { query, voice -> navController.navigate(Search(query = query.orEmpty(), voice = voice)) },
                onOpenEntry = { navController.navigate(Entry(it)) },
                onOpenAbout = { navController.navigate(About) },
                viewModel = mainViewModel,
            )
        }
        composable<Search>(deepLinks = listOf(navDeepLink { uriPattern = DeepLinks.SEARCH_PATTERN })) { entry ->
            val route = entry.toRoute<Search>()
            SearchScreen(
                initialQuery = route.query,
                voice = route.voice,
                onBack = { navController.popBackStack() },
                onOpenEntry = { navController.navigate(Entry(it)) },
            )
        }
        composable<Entry>(deepLinks = listOf(navDeepLink { uriPattern = DeepLinks.ENTRY_PATTERN })) { entry ->
            EntryScreen(
                entryId = entry.toRoute<Entry>().entryId,
                onBack = { navController.popBackStack() },
                // See-also pushes another entry, so back returns to this one.
                onOpenEntry = { navController.navigate(Entry(it)) },
                onOpenSearch = { navController.navigate(Search(query = it)) },
            )
        }
        composable<About> {
            AboutScreen(onBack = { navController.popBackStack() })
        }
    }
}
