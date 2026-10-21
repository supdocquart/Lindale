package com.metrolist.music
import metrolist.shared.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource
import androidx.compose.runtime.compositionLocalOf
import androidx.navigation.NavHostController
val LocalNavController = compositionLocalOf<NavHostController> { error("No NavController provided") }
