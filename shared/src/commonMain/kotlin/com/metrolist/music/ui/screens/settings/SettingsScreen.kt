/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens.settings
import metrolist.shared.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Build
import android.provider.Settings
import com.metrolist.music.utils.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.navigation.NavController
import com.metrolist.music.BuildConfig
import com.metrolist.music.LocalPlayerAwareWindowInsets
import com.metrolist.music.ui.component.IconButton
import com.metrolist.music.ui.component.Material3SettingsGroup
import com.metrolist.music.ui.component.Material3SettingsItem
import com.metrolist.music.ui.component.ReleaseNotesCard
import com.metrolist.music.ui.utils.backToMain
import com.metrolist.music.utils.Updater
import com.metrolist.innertube.utils.parseCookieString
import com.metrolist.music.constants.AccountNameKey
import com.metrolist.music.constants.InnerTubeCookieKey
import com.metrolist.music.utils.rememberPreference
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.fillMaxWidth

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavController,
    latestVersionName: String,
) {
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    val isAndroid12OrLater = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    var showAccountDialog by remember { mutableStateOf(false) }
    val (innerTubeCookie, _) = rememberPreference(InnerTubeCookieKey, "")
    val (accountNamePref, _) = rememberPreference(AccountNameKey, "")
    val isLoggedIn = remember(innerTubeCookie) {
        "SAPISID" in parseCookieString(innerTubeCookie)
    }
    val hasAndroidAuto = remember {
        try {
            context.packageManager.getPackageInfo(
                "com.google.android.projection.gearhead", 0
            )
            true
        } catch (e: Exception) {
            false
        }
    }

    if (showAccountDialog) {
        BasicAlertDialog(
            onDismissRequest = { showAccountDialog = false },
        ) {
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation = 6.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                AccountSettings(
                    navController = navController,
                    onClose = { showAccountDialog = false },
                    latestVersionName = latestVersionName,
                )
            }
        }
    }

    Column(
        Modifier
            .windowInsetsPadding(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        Spacer(
            Modifier.windowInsetsPadding(
                LocalPlayerAwareWindowInsets.current.only(
                    WindowInsetsSides.Top
                )
            )
        )

        // Account & Integrations Section
        Material3SettingsGroup(
            title = stringResource(Res.string.account),
            items = listOf(
                Material3SettingsItem(
                    icon = painterResource(if (isLoggedIn) Res.drawable.account else Res.drawable.login),
                    title = {
                        Text(
                            if (isLoggedIn && accountNamePref.isNotBlank()) {
                                accountNamePref
                            } else {
                                stringResource(Res.string.login)
                            }
                        )
                    },
                    description = {
                        Text(
                            if (isLoggedIn) {
                                stringResource(Res.string.account)
                            } else {
                                stringResource(Res.string.advanced_login)
                            }
                        )
                    },
                    onClick = { showAccountDialog = true }
                ),
                Material3SettingsItem(
                    icon = painterResource(Res.drawable.integration),
                    title = { Text(stringResource(Res.string.integrations)) },
                    onClick = { navController.navigate("settings/integrations") }
                )
            )
        )

        Spacer(modifier = Modifier.height(16.dp))

        // User Interface Section
        Material3SettingsGroup(
            title = stringResource(Res.string.settings_section_ui),
            items = listOf(
                Material3SettingsItem(
                    icon = painterResource(Res.drawable.palette),
                    title = { Text(stringResource(Res.string.appearance)) },
                    onClick = { navController.navigate("settings/appearance") }
                )
            )
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Player & Content Section (moved up and combined with content)
        Material3SettingsGroup(
            title = stringResource(Res.string.settings_section_player_content),
            items = listOf(
                Material3SettingsItem(
                    icon = painterResource(Res.drawable.play),
                    title = { Text(stringResource(Res.string.player_and_audio)) },
                    onClick = { navController.navigate("settings/player") }
                ),
                Material3SettingsItem(
                    icon = painterResource(Res.drawable.language),
                    title = { Text(stringResource(Res.string.content)) },
                    onClick = { navController.navigate("settings/content") }
                ),
                Material3SettingsItem(
                    icon = painterResource(Res.drawable.translate),
                    title = { Text(stringResource(Res.string.ai_lyrics_translation)) },
                    onClick = { navController.navigate("settings/ai") }
                )
            )
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Android Auto Section — only shown if Android Auto is installed
        if (hasAndroidAuto) {
            Material3SettingsGroup(
                title = "Android Auto",
                items = listOf(
                    Material3SettingsItem(
                        icon = painterResource(Res.drawable.ic_android_auto),
                        title = { Text(stringResource(Res.string.android_auto)) },
                        onClick = { navController.navigate("settings/android_auto") }
                    )
                )
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
        
        // Privacy & Security Section
        Material3SettingsGroup(
            title = stringResource(Res.string.settings_section_privacy),
            items = listOf(
                Material3SettingsItem(
                    icon = painterResource(Res.drawable.security),
                    title = { Text(stringResource(Res.string.privacy)) },
                    onClick = { navController.navigate("settings/privacy") }
                )
            )
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Storage & Data Section
        Material3SettingsGroup(
            title = stringResource(Res.string.settings_section_storage),
            items = listOf(
                Material3SettingsItem(
                    icon = painterResource(Res.drawable.storage),
                    title = { Text(stringResource(Res.string.storage)) },
                    onClick = { navController.navigate("settings/storage") }
                ),
                Material3SettingsItem(
                    icon = painterResource(Res.drawable.restore),
                    title = { Text(stringResource(Res.string.backup_restore)) },
                    onClick = { navController.navigate("settings/backup_restore") }
                )
            )
        )

        Spacer(modifier = Modifier.height(16.dp))

        // System & About Section
        Material3SettingsGroup(
            title = stringResource(Res.string.settings_section_system),
            items = buildList {
                if (isAndroid12OrLater) {
                    add(
                        Material3SettingsItem(
                            icon = painterResource(Res.drawable.link),
                            title = { Text(stringResource(Res.string.default_links)) },
                            onClick = {
                                try {
                                    val intent = Intent(
                                        Settings.ACTION_APP_OPEN_BY_DEFAULT_SETTINGS,
                                        "package:${context.packageName}".toUri()
                                    )
                                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    when (e) {
                                        is ActivityNotFoundException -> {
                                            Toast.makeText(
                                                context,
                                                Res.string.open_app_settings_error,
                                                Toast.LENGTH_LONG
                                            ).show()
                                        }

                                        is SecurityException -> {
                                            Toast.makeText(
                                                context,
                                                Res.string.open_app_settings_error,
                                                Toast.LENGTH_LONG
                                            ).show()
                                        }

                                        else -> {
                                            Toast.makeText(
                                                context,
                                                Res.string.open_app_settings_error,
                                                Toast.LENGTH_LONG
                                            ).show()
                                        }
                                    }
                                }
                            }
                        )
                    )
                }
                if (BuildConfig.UPDATER_AVAILABLE) {
                    add(
                        Material3SettingsItem(
                            icon = painterResource(Res.drawable.update),
                            title = { Text(stringResource(Res.string.updater)) },
                            onClick = { navController.navigate("settings/updater") }
                        )
                    )
                }
                val showChangelog = com.metrolist.music.LocalChangelogState.current
                add(
                    Material3SettingsItem(
                        icon = painterResource(Res.drawable.newspaper),
                        title = { Text(stringResource(Res.string.changelog)) },
                        onClick = { showChangelog.value = true }
                    )
                )
                add(
                    Material3SettingsItem(
                        icon = painterResource(Res.drawable.info),
                        title = { Text(stringResource(Res.string.about)) },
                        onClick = { navController.navigate("settings/about") }
                    )
                )
                if (BuildConfig.UPDATER_AVAILABLE && latestVersionName != BuildConfig.BASE_VERSION_NAME) {
                    val releaseInfo = Updater.getCachedLatestRelease()
                    val downloadUrl = releaseInfo?.let { Updater.getDownloadUrlForCurrentVariant(it) }

                    if (downloadUrl != null) {
                        add(
                            Material3SettingsItem(
                                icon = painterResource(Res.drawable.update),
                                title = { 
                                    Text(
                                        text = stringResource(Res.string.new_version_available),
                                    )
                                },
                                description = {
                                    Text(
                                        text = latestVersionName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                showBadge = true,
                                onClick = { uriHandler.openUri(downloadUrl) }
                            )
                        )
                    }
                }
            }
        )
    if (BuildConfig.UPDATER_AVAILABLE && latestVersionName != BuildConfig.BASE_VERSION_NAME) {
            Spacer(modifier = Modifier.height(16.dp))
            ReleaseNotesCard()
        }

        Spacer(modifier = Modifier.height(16.dp))
    }

    TopAppBar(
        title = { Text(stringResource(Res.string.settings)) },
        navigationIcon = {
            IconButton(
                onClick = navController::navigateUp,
                onLongClick = navController::backToMain
            ) {
                Icon(
                    painterResource(Res.drawable.arrow_back),
                    contentDescription = null
                )
            }
        }
    )
}
