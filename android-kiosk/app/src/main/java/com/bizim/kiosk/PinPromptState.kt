package com.bizim.kiosk

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Whether the device is currently unlocked (PIN dialog up, or fully unlocked and browsing the
 * app list) -- covers the whole span from "PIN prompt requested" through "back to locked".
 * KioskForegroundService's right-half touch zone (which requests the PIN prompt in the first
 * place) has to get out of the way for all of it, not just the dialog itself: left active, it
 * sits on top of the dialog/keyboard/app list and eats taps in the right half of the screen,
 * including the dialog's own Onayla button, the keyboard's Done key, and scrolling the app list.
 */
object PinPromptState {
    private val _showing = MutableStateFlow(false)
    val showing = _showing.asStateFlow()

    fun set(value: Boolean) {
        _showing.value = value
    }
}
