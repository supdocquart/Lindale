package com.metrolist.music.extensions
import metrolist.shared.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource

import androidx.room.RoomRawQuery

fun String.toSQLiteQuery(): RoomRawQuery = RoomRawQuery(this)


