package com.metrolist.music.listentogether

object MessageTypes {
    const val CREATE_ROOM = "create_room"
    const val JOIN_ROOM = "join_room"
    const val LEAVE_ROOM = "leave_room"
    const val APPROVE_JOIN = "approve_join"
    const val REJECT_JOIN = "reject_join"
    const val PLAYBACK_ACTION = "playback_action"
    const val BUFFER_READY = "buffer_ready"
    const val KICK_USER = "kick_user"
    const val TRANSFER_HOST = "transfer_host"
    const val PING = "ping"
    const val CHAT = "chat"
    const val REQUEST_SYNC = "request_sync"
    const val RECONNECT = "reconnect"
    const val SUGGEST_TRACK = "suggest_track"
    const val APPROVE_SUGGESTION = "approve_suggestion"
    const val REJECT_SUGGESTION = "reject_suggestion"
    const val ROOM_CREATED = "room_created"
    const val JOIN_REQUEST = "join_request"
    const val JOIN_APPROVED = "join_approved"
    const val JOIN_REJECTED = "join_rejected"
    const val USER_JOINED = "user_joined"
    const val USER_LEFT = "user_left"
    const val SYNC_PLAYBACK = "sync_playback"
    const val BUFFER_WAIT = "buffer_wait"
    const val BUFFER_COMPLETE = "buffer_complete"
    const val ERROR = "error"
    const val PONG = "pong"
    const val HOST_CHANGED = "host_changed"
    const val KICKED = "kicked"
    const val SYNC_STATE = "sync_state"
    const val RECONNECTED = "reconnected"
    const val USER_RECONNECTED = "user_reconnected"
    const val USER_DISCONNECTED = "user_disconnected"
    const val SUGGESTION_RECEIVED = "suggestion_received"
    const val SUGGESTION_APPROVED = "suggestion_approved"
    const val SUGGESTION_REJECTED = "suggestion_rejected"
}

object PlaybackActions {
    const val PLAY = "play"
    const val PAUSE = "pause"
    const val SEEK = "seek"
    const val SKIP_NEXT = "skip_next"
    const val SKIP_PREV = "skip_prev"
    const val CHANGE_TRACK = "change_track"
    const val QUEUE_ADD = "queue_add"
    const val QUEUE_REMOVE = "queue_remove"
    const val QUEUE_CLEAR = "queue_clear"
    const val SYNC_QUEUE = "sync_queue"
    const val SET_VOLUME = "set_volume"
}

data class TrackInfo(
    val id: String = "",
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val duration: Long = 0L,
    val thumbnail: String = "",
    val suggestedBy: String = "",
)

data class UserInfo(
    val userId: String = "",
    val username: String = "",
    val isHost: Boolean = false,
    val isConnected: Boolean = true,
)

data class RoomState(
    val roomCode: String = "",
    val hostId: String = "",
    val usersList: List<UserInfo> = emptyList(),
    val currentTrack: TrackInfo? = null,
    val isPlaying: Boolean = false,
    val position: Long = 0L,
    val lastUpdate: Long = 0L,
    val volume: Float = 1f,
    val queueList: List<TrackInfo> = emptyList(),
    val revision: Long = 0L,
) {
    val users: List<UserInfo> get() = usersList
    val queue: List<TrackInfo> get() = queueList
    val currentTrackOrNull: TrackInfo? get() = currentTrack
    fun hasCurrentTrack(): Boolean = currentTrack != null
}

data class CreateRoomPayload(val username: String = "")
data class JoinRoomPayload(val roomCode: String = "", val username: String = "")
data class ApproveJoinPayload(val userId: String = "")
data class RejectJoinPayload(val userId: String = "", val reason: String = "")
data class PlaybackActionPayload(
    val action: String = "",
    val trackId: String = "",
    val position: Long = 0L,
    val trackInfo: TrackInfo? = null,
    val insertNext: Boolean = false,
    val queueList: List<TrackInfo> = emptyList(),
    val queueTitle: String = "",
    val volume: Float = 1f,
    val serverTime: Long = 0L,
    val revision: Long = 0L,
    val capturedAtServerTime: Long = 0L,
) {
    val trackIdOrNull: String? get() = trackId.takeIf { it.isNotEmpty() }
    val positionOrNull: Long? get() = position.takeIf { it != 0L || action in listOf(PlaybackActions.PLAY, PlaybackActions.PAUSE, PlaybackActions.SEEK) }
    val trackInfoOrNull: TrackInfo? get() = trackInfo
    val queue: List<TrackInfo>? get() = queueList
    val queueTitleOrNull: String? get() = queueTitle.takeIf { it.isNotEmpty() }
    val volumeOrNull: Float? get() = volume.takeIf { action == PlaybackActions.SET_VOLUME }
    val serverTimeOrNull: Long? get() = serverTime.takeIf { it > 0L }
    val capturedAtServerTimeOrNull: Long? get() = capturedAtServerTime.takeIf { it > 0L }
    val insertNextOrNull: Boolean? get() = insertNext.takeIf { it }
    fun hasTrackInfo(): Boolean = trackInfo != null
}

data class PingPayload(val clientTime: Long = 0L, val sequence: Long = 0L)
data class BufferReadyPayload(val trackId: String = "")
data class KickUserPayload(val userId: String = "", val reason: String = "")
data class TransferHostPayload(val newHostId: String = "")
data class SuggestTrackPayload(val trackInfo: TrackInfo = TrackInfo())
data class ApproveSuggestionPayload(val suggestionId: String = "")
data class RejectSuggestionPayload(val suggestionId: String = "", val reason: String = "")
data class RoomCreatedPayload(val roomCode: String = "", val hostId: String = "")
data class JoinRequestPayload(val userId: String = "", val username: String = "")
data class JoinApprovedPayload(val roomState: RoomState = RoomState(), val userId: String = "")
data class JoinRejectedPayload(val reason: String = "")
data class UserJoinedPayload(val user: UserInfo = UserInfo())
data class UserLeftPayload(val userId: String = "")
data class BufferWaitPayload(val waitingForList: List<String> = emptyList()) {
    val waitingFor: List<String> get() = waitingForList
}
data class BufferCompletePayload(val trackId: String = "")
data class ErrorPayload(val message: String = "")
data class HostChangedPayload(val newHostId: String = "")
data class KickedPayload(val reason: String = "")
data class SyncStatePayload(
    val currentTrack: TrackInfo? = null,
    val isPlaying: Boolean = false,
    val position: Long = 0L,
    val lastUpdate: Long = 0L,
    val queueList: List<TrackInfo>? = null,
    val volume: Float? = null,
    val revision: Long = 0L,
) {
    val currentTrackOrNull: TrackInfo? get() = currentTrack
    val queue: List<TrackInfo>? get() = queueList
    fun hasCurrentTrack(): Boolean = currentTrack != null
}

data class PongPayload(val clientTime: Long = 0L, val serverTime: Long = 0L, val sequence: Long = 0L)
data class ReconnectPayload(val sessionToken: String = "")
data class ReconnectedPayload(val roomState: RoomState = RoomState(), val userId: String = "")
data class UserReconnectedPayload(val userId: String = "")
data class UserDisconnectedPayload(val userId: String = "")
data class SuggestionReceivedPayload(
    val suggestionId: String = "",
    val track: TrackInfo = TrackInfo(),
    val suggestedBy: String = "",
    val suggestedByUsername: String = "",
) {
    val trackInfo: TrackInfo get() = track
    val fromUsername: String get() = suggestedByUsername
}
data class SuggestionApprovedPayload(val suggestionId: String = "")
data class SuggestionRejectedPayload(val suggestionId: String = "", val reason: String = "") {
    val reasonOrNull: String? get() = reason.takeIf { it.isNotEmpty() }
}
