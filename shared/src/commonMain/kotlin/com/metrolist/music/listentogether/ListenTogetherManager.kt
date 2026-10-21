package com.metrolist.music.listentogether

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ConnectionState {
    DISCONNECTED, CONNECTING, CONNECTED, RECONNECTING, ERROR
}

enum class RoomRole {
    NONE, HOST, GUEST
}

enum class LogLevel {
    DEBUG, INFO, WARNING, ERROR
}

data class LogEntry(
    val level: LogLevel = LogLevel.INFO,
    val message: String = "",
    val details: String? = null,
    val timestamp: String = "",
)

sealed class ListenTogetherEvent {
    data class RoomCreated(val roomCode: String) : ListenTogetherEvent()
    data class JoinApproved(val roomCode: String) : ListenTogetherEvent()
    data class JoinRejected(val reason: String) : ListenTogetherEvent()
    data class JoinRequestReceived(val username: String = "", val request: JoinRequestPayload? = null) : ListenTogetherEvent()
    data class Kicked(val reason: String) : ListenTogetherEvent()
    data class ConnectionError(val error: String = "", val message: String = "") : ListenTogetherEvent()
    data class ServerError(val message: String) : ListenTogetherEvent()
    data class Error(val message: String) : ListenTogetherEvent()
    data class UserJoined(val username: String) : ListenTogetherEvent()
    data class UserLeft(val username: String) : ListenTogetherEvent()
}

open class ListenTogetherManager {
    val connectionState: StateFlow<ConnectionState> = MutableStateFlow(ConnectionState.DISCONNECTED).asStateFlow()
    val roomState: StateFlow<RoomState?> = MutableStateFlow<RoomState?>(null).asStateFlow()
    val role: StateFlow<RoomRole> = MutableStateFlow(RoomRole.NONE).asStateFlow()
    val userId: StateFlow<String> = MutableStateFlow("").asStateFlow()
    val pendingJoinRequests: StateFlow<List<JoinRequestPayload>> = MutableStateFlow<List<JoinRequestPayload>>(emptyList()).asStateFlow()
    val pendingSuggestions: StateFlow<List<SuggestionReceivedPayload>> = MutableStateFlow<List<SuggestionReceivedPayload>>(emptyList()).asStateFlow()
    val events: SharedFlow<ListenTogetherEvent> = MutableSharedFlow<ListenTogetherEvent>().asSharedFlow()
    val logs: StateFlow<List<LogEntry>> = MutableStateFlow<List<LogEntry>>(emptyList()).asStateFlow()
    val blockedUsernames: StateFlow<Set<String>> = MutableStateFlow<Set<String>>(emptySet()).asStateFlow()
    val isInRoom: Boolean get() = roomState.value != null
    val isHost: Boolean get() = role.value == RoomRole.HOST

    open fun connect() {}
    open fun disconnect() {}
    open fun forceReconnect() {}
    open fun createRoom(username: String) {}
    open fun joinRoom(roomCode: String, username: String) {}
    open fun leaveRoom() {}
    open fun approveJoin(userId: String) {}
    open fun rejectJoin(userId: String, reason: String = "") {}
    open fun approveSuggestion(suggestionId: String) {}
    open fun rejectSuggestion(suggestionId: String, reason: String = "") {}
    open fun requestSync() {}
    open fun kickUser(userId: String, reason: String = "") {}
    open fun blockUser(username: String) {}
    open fun unblockUser(username: String) {}
    open fun transferHost(userId: String) {}
    open fun suggestTrack(track: TrackInfo) {}
    open fun clearLogs() {}
}
