package com.metrolist.music

import com.metrolist.music.utils.ResourceText

fun PlatformContext.getString(id: Int): String = id.toString()
fun PlatformContext.getString(id: Any): String = ResourceText.resolve(id)
fun PlatformContext.getString(id: Int, vararg formatArgs: Any): String = id.toString()
fun PlatformContext.getString(id: Any, vararg formatArgs: Any): String = ResourceText.resolve(id, *formatArgs)
