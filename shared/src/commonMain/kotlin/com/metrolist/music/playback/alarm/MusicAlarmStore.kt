package com.metrolist.music.playback.alarm

import com.metrolist.music.PlatformContext
import java.util.UUID

data class MusicAlarmEntry(
    val id: String,
    val enabled: Boolean,
    val hour: Int,
    val minute: Int,
    val playlistId: String,
    val randomSong: Boolean,
    val nextTriggerAt: Long = -1L
)

object MusicAlarmStore {
    suspend fun load(context: PlatformContext): List<MusicAlarmEntry> = emptyList()
    fun loadBlocking(context: PlatformContext): List<MusicAlarmEntry> = emptyList()
    suspend fun save(context: PlatformContext, entries: List<MusicAlarmEntry>) {}
    fun saveBlocking(context: PlatformContext, entries: List<MusicAlarmEntry>) {}
    fun createEmpty(): MusicAlarmEntry = MusicAlarmEntry(
        id = UUID.randomUUID().toString(),
        enabled = true,
        hour = 7,
        minute = 0,
        playlistId = "",
        randomSong = false,
        nextTriggerAt = -1L
    )
}

object MusicAlarmScheduler {
    fun scheduleFromPreferences(context: PlatformContext) {}
    fun scheduleAll(context: PlatformContext, alarms: List<MusicAlarmEntry>) {}
    fun cancel(context: PlatformContext, alarmId: String) {}
}
