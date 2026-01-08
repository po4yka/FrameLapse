package com.po4yka.framelapse.presentation.main

import androidx.lifecycle.viewModelScope
import com.po4yka.framelapse.domain.repository.SettingsRepository
import com.po4yka.framelapse.presentation.base.BaseViewModel
import kotlinx.coroutines.launch

/**
 * ViewModel for the main app screen.
 * Handles app initialization and permission management.
 */
class MainViewModel(private val settingsRepository: SettingsRepository) :
    BaseViewModel<MainState, MainEvent, MainEffect>(MainState()) {

    override fun onEvent(event: MainEvent) {
        when (event) {
            is MainEvent.Initialize -> initialize()
            is MainEvent.PermissionsGranted -> permissionsGranted()
            is MainEvent.PermissionsDenied -> permissionsDenied()
        }
    }

    private fun initialize() {
        viewModelScope.launch {
            updateState { copy(isLoading = true) }

            // Check if this is first launch or permissions need to be requested
            val hasCompletedOnboardingResult = settingsRepository.getBoolean(KEY_ONBOARDING_COMPLETE, false)
            val hasCompletedOnboarding = hasCompletedOnboardingResult.getOrNull() ?: false

            updateState { copy(isLoading = false, isInitialized = true) }

            if (!hasCompletedOnboarding) {
                // Request permissions on first launch
                sendEffect(MainEffect.RequestPermissions)
            } else {
                // Navigate to project list
                sendEffect(MainEffect.NavigateTo(ROUTE_PROJECT_LIST))
            }
        }
    }

    private fun permissionsGranted() {
        viewModelScope.launch {
            updateState { copy(hasPermissions = true) }
            settingsRepository.setBoolean(KEY_ONBOARDING_COMPLETE, true)
            sendEffect(MainEffect.NavigateTo(ROUTE_PROJECT_LIST))
        }
    }

    private fun permissionsDenied() {
        updateState { copy(hasPermissions = false) }
        sendEffect(MainEffect.ShowError("Camera permission is required for capturing photos"))
    }

    companion object {
        private const val KEY_ONBOARDING_COMPLETE = "onboarding_complete"

        // Navigation routes
        const val ROUTE_PROJECT_LIST = "project_list"
        const val ROUTE_CAPTURE = "capture"
        const val ROUTE_GALLERY = "gallery"
        const val ROUTE_EXPORT = "export"
        const val ROUTE_SETTINGS = "settings"
    }
}
