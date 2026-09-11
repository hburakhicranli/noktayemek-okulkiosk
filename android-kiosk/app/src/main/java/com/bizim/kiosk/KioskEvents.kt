package com.bizim.kiosk

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** Commands coming from the panel, translated into in-process events for MainActivity. */
sealed class KioskEvent {
    data object RelaunchTarget : KioskEvent()
    data object Unlock : KioskEvent()
    data object Lock : KioskEvent()
    data object RequestExitPin : KioskEvent()
}

object KioskEvents {
    private val _events = MutableSharedFlow<KioskEvent>(extraBufferCapacity = 8)
    val events = _events.asSharedFlow()

    suspend fun emit(event: KioskEvent) {
        _events.emit(event)
    }
}
