/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens
import metrolist.shared.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource


import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable

@Immutable
sealed class Screens(
    @StringRes val titleId: org.jetbrains.compose.resources.StringResource,
    @DrawableRes val iconIdInactive: org.jetbrains.compose.resources.DrawableResource,
    @DrawableRes val iconIdActive: org.jetbrains.compose.resources.DrawableResource,
    val route: String,
) {
    object Home : Screens(
        titleId = Res.string.home,
        iconIdInactive = Res.drawable.home_outlined,
        iconIdActive = Res.drawable.home_filled,
        route = "home"
    )

    object Search : Screens(
        titleId = Res.string.search,
        iconIdInactive = Res.drawable.search,
        iconIdActive = Res.drawable.search,
        route = "search_input"
    )

    object ListenTogether : Screens(
        titleId = Res.string.together,
        iconIdInactive = Res.drawable.group_outlined,
        iconIdActive = Res.drawable.group_filled,
        route = "listen_together"
    )

    object Library : Screens(
        titleId = Res.string.filter_library,
        iconIdInactive = Res.drawable.library_music_outlined,
        iconIdActive = Res.drawable.library_music_filled,
        route = "library"
    )

    companion object {
        val MainScreens: List<Screens>
            get() = listOfNotNull(Home, Search, ListenTogether, Library)
    }
}
