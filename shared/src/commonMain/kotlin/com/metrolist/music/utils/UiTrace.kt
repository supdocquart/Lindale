package com.metrolist.music.utils

object UiTrace {
    @Volatile
    var currentRoute: String = "?"

    @Volatile
    var playerSheet: String = "dismissed"

    fun event(tag: String, message: String) {
        println("[UI:$tag] $message")
    }
}
