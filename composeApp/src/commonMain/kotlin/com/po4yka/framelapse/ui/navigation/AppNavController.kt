package com.po4yka.framelapse.ui.navigation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Simple navigation controller for managing app navigation.
 */
class AppNavController {
    private val _currentRoute = MutableStateFlow<Route>(Route.ProjectList)
    val currentRoute: StateFlow<Route> = _currentRoute.asStateFlow()

    private val backStack = mutableListOf<Route>()

    /**
     * Navigate to a new route.
     */
    fun navigate(route: Route) {
        backStack.add(_currentRoute.value)
        _currentRoute.value = route
    }

    /**
     * Navigate back to the previous route.
     * @return true if navigation occurred, false if back stack was empty
     */
    fun navigateBack(): Boolean {
        if (backStack.isEmpty()) return false
        _currentRoute.value = backStack.removeLast()
        return true
    }

    /**
     * Navigate to a route and clear the back stack.
     */
    fun navigateAndClearStack(route: Route) {
        backStack.clear()
        _currentRoute.value = route
    }

    /**
     * Check if the back stack is empty.
     */
    fun canNavigateBack(): Boolean = backStack.isNotEmpty()
}
