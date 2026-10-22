package com.fitcal.app

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Something the app was opened to do from outside, such as the widget's Scan button. */
enum class LaunchAction { SCAN }

/**
 * Hands a launch action from the platform (an Android intent) to the UI. The UI takes it once
 * it can navigate, so the action survives the consent screens and the launch animation.
 */
object PendingLaunchAction {
    private val state = MutableStateFlow<LaunchAction?>(null)
    val action: StateFlow<LaunchAction?> = state.asStateFlow()

    fun post(action: LaunchAction) {
        state.value = action
    }

    /** Returns the pending action and clears it, so it runs once. */
    fun take(): LaunchAction? = state.value.also { state.value = null }
}
