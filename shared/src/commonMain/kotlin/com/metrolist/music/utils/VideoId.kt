package com.metrolist.music.utils

// DESKTOP: ids are used in file names and on the local stream proxy; accept only real YouTube ids.
object VideoId {
    private val PATTERN = Regex("^[A-Za-z0-9_-]{11}$")
    fun isValid(id: String?): Boolean = id != null && PATTERN.matches(id)
}
