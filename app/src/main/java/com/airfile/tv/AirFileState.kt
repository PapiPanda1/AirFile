package com.airfile.tv

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

data class ReceivedFile(val name: String, val bytes: Long)

/** Process-wide state shared between the upload service and the UI. */
object AirFileState {
    /** Full upload URL (including session token) or null when no network is available. */
    val url = MutableStateFlow<String?>(null)

    /** Newest first. */
    val received = MutableStateFlow<List<ReceivedFile>>(emptyList())

    /** Human-readable description of where files are saved. */
    val location = MutableStateFlow("")

    /** Number of uploads currently in progress. */
    val active = MutableStateFlow(0)

    val error = MutableStateFlow<String?>(null)

    fun addReceived(file: ReceivedFile) {
        received.update { (listOf(file) + it).take(50) }
    }

    fun uploadStarted() = active.update { it + 1 }

    fun uploadFinished() = active.update { maxOf(0, it - 1) }
}
