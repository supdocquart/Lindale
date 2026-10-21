package com.metrolist.music

import com.metrolist.music.ui.utils.BackDispatcher
import androidx.compose.runtime.DisposableEffect

import androidx.compose.animation.*
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.metrolist.music.constants.*
import com.metrolist.music.db.MusicDatabase
import com.metrolist.music.playback.PlayerConnection
import com.metrolist.music.ui.component.*
import com.metrolist.music.ui.player.BottomSheetPlayer
import com.metrolist.music.ui.screens.Screens
import com.metrolist.music.ui.screens.navigationBuilder
import com.metrolist.music.ui.screens.settings.AccountSettings
import com.metrolist.music.ui.screens.settings.ChangelogScreen
import com.metrolist.music.ui.theme.MetrolistTheme
import com.metrolist.music.utils.*
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import metrolist.shared.generated.resources.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MetrolistApp(
    database: MusicDatabase,
    playerConnection: PlayerConnection = remember { object : PlayerConnection {} },
    downloadUtil: DownloadUtil = remember { DownloadUtil() },
    syncUtils: SyncUtils = remember(database) { SyncUtils(android.content.Context(), database) },
) {
    val navController = rememberNavController()
    DisposableEffect(navController) {
        BackDispatcher.fallback = { navController.previousBackStackEntry != null && navController.popBackStack() }
        onDispose { BackDispatcher.fallback = null }
    }
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    LaunchedEffect(navBackStackEntry) {
        val entry = navBackStackEntry ?: return@LaunchedEffect
        val route = entry.destination.route ?: "?"
        UiTrace.currentRoute = route
        val args = entry.arguments?.let { a ->
            runCatching {
                val field = a.javaClass.getDeclaredField("map").apply { isAccessible = true }
                field.get(a).toString()
            }.getOrElse { a.toString() }
        }
        UiTrace.event("NAV", "route=$route args=$args")
    }

    val darkThemePref by rememberEnumPreference(DarkModeKey, defaultValue = com.metrolist.music.ui.screens.settings.DarkMode.AUTO)
    val isSystemDark = isSystemInDarkTheme()
    val isDark = when (darkThemePref) {
        com.metrolist.music.ui.screens.settings.DarkMode.AUTO -> isSystemDark
        com.metrolist.music.ui.screens.settings.DarkMode.ON -> true
        com.metrolist.music.ui.screens.settings.DarkMode.OFF -> false
    }
    val pureBlackPref by rememberPreference(PureBlackKey, defaultValue = false)
    val pureBlack = pureBlackPref && isDark

    val showChangelog = remember { mutableStateOf(false) }
    var showAccountDialog by remember { mutableStateOf(false) }
    val menuState = remember { MenuState() }
    val bottomSheetPageState = remember { BottomSheetPageState() }
    val topAppBarScrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val snackbarHostState = remember { SnackbarHostState() }

    val navigationItems = remember {
        listOfNotNull(Screens.Home, Screens.Search, Screens.Library)
    }

    val currentTitleRes = remember(currentRoute) {
        when (currentRoute) {
            Screens.Home.route -> Res.string.home
            Screens.Search.route -> Res.string.search
            Screens.Library.route -> Res.string.filter_library
            else -> null
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isLandscape = maxWidth > maxHeight
        val isLargeScreen = maxWidth >= 600.dp
        val showRail = isLargeScreen || isLandscape

        val playerBottomSheetState = rememberBottomSheetState(
            dismissedBound = 0.dp,
            collapsedBound = if (showRail) MiniPlayerHeight else (MiniPlayerHeight + NavigationBarHeight),
            expandedBound = maxHeight,
        )

        val mediaMetadata by playerConnection.mediaMetadata.collectAsState()
        LaunchedEffect(mediaMetadata) {
            mediaMetadata?.let { m ->
                UiTrace.event("NOW_PLAYING", "id=${m.id} title=${m.title} artists=${m.artists.joinToString { it.name }}")
            }
            if (mediaMetadata != null) {
                if (playerBottomSheetState.isDismissed) {
                    playerBottomSheetState.collapseSoft()
                }
            } else {
                if (!playerBottomSheetState.isDismissed) {
                    playerBottomSheetState.dismiss()
                }
            }
        }

        LaunchedEffect(playerBottomSheetState.isExpanded, playerBottomSheetState.isDismissed) {
            val sheet = when {
                playerBottomSheetState.isDismissed -> "dismissed"
                playerBottomSheetState.isExpanded -> "expanded"
                else -> "mini"
            }
            if (sheet != UiTrace.playerSheet) {
                UiTrace.playerSheet = sheet
                UiTrace.event("PLAYER_SHEET", sheet)
            }
        }

        val playerAwareWindowInsets = remember(showRail, playerBottomSheetState.isDismissed) {
            WindowInsets(
                left = 0.dp,
                top = AppBarHeight,
                right = 0.dp,
                bottom = if (!playerBottomSheetState.isDismissed) MiniPlayerHeight else 0.dp,
            )
        }

        MetrolistTheme(
            darkTheme = isDark,
            pureBlack = pureBlack,
        ) {
            CompositionLocalProvider(
                LocalDatabase provides database,
                LocalNavController provides navController,
                LocalPlayerConnection provides playerConnection,
                LocalPlayerAwareWindowInsets provides playerAwareWindowInsets,
                LocalDownloadUtil provides downloadUtil,
                LocalSyncUtils provides syncUtils,
                LocalChangelogState provides showChangelog,
                LocalMenuState provides menuState,
                LocalBottomSheetPageState provides bottomSheetPageState,
            ) {
                Scaffold(
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                    bottomBar = {
                        if (!showRail && currentRoute != "wrapped") {
                            AppNavigationBar(
                                navigationItems = navigationItems,
                                currentRoute = currentRoute,
                                onItemClick = { screen, isSelected ->
                                    if (playerBottomSheetState.isExpanded) {
                                        playerBottomSheetState.collapseSoft()
                                    }
                                    if (!isSelected) {
                                        navController.navigate(screen.route) {
                                            popUpTo(navController.graph.startDestinationId) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    }
                                },
                                pureBlack = pureBlack,
                            )
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                ) { innerPadding ->
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(
                                bottom = if (!showRail) innerPadding.calculateBottomPadding() else 0.dp
                            )
                    ) {
                        if (showRail && currentRoute != "wrapped") {
                            AppNavigationRail(
                                navigationItems = navigationItems,
                                currentRoute = currentRoute,
                                onItemClick = { screen, isSelected ->
                                    if (playerBottomSheetState.isExpanded) {
                                        playerBottomSheetState.collapseSoft()
                                    }
                                    if (!isSelected) {
                                        navController.navigate(screen.route) {
                                            popUpTo(navController.graph.startDestinationId) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    }
                                },
                                pureBlack = pureBlack,
                                onAccountClick = { showAccountDialog = true },
                                onSettingsClick = {
                                    if (playerBottomSheetState.isExpanded) {
                                        playerBottomSheetState.collapseSoft()
                                    }
                                    navController.navigate("settings") {
                                        launchSingleTop = true
                                    }
                                },
                            )
                        }

                        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                            NavHost(
                                navController = navController,
                                startDestination = Screens.Home.route,
                                modifier = Modifier.nestedScroll(topAppBarScrollBehavior.nestedScrollConnection),
                            ) {
                                navigationBuilder(
                                    navController = navController,
                                    scrollBehavior = topAppBarScrollBehavior,
                                    latestVersionName = "1.0.0-desktop",
                                    activity = android.app.Activity(),
                                    snackbarHostState = snackbarHostState,
                                )
                            }

                            if (currentTitleRes != null) {
                                TopAppBar(
                                    title = {
                                        Text(
                                            text = stringResource(currentTitleRes),
                                            style = MaterialTheme.typography.titleLarge,
                                        )
                                    },
                                    actions = {
                                        IconButton(onClick = { navController.navigate("history") }) {
                                            Icon(
                                                painter = painterResource(Res.drawable.history),
                                                contentDescription = stringResource(Res.string.history),
                                            )
                                        }
                                        IconButton(onClick = { navController.navigate("stats") }) {
                                            Icon(
                                                painter = painterResource(Res.drawable.stats),
                                                contentDescription = stringResource(Res.string.stats),
                                            )
                                        }
                                        IconButton(onClick = { showAccountDialog = true }) {
                                            Icon(
                                                painter = painterResource(Res.drawable.account),
                                                contentDescription = stringResource(Res.string.account),
                                            )
                                        }
                                        IconButton(onClick = { navController.navigate("settings") }) {
                                            Icon(
                                                painter = painterResource(Res.drawable.settings),
                                                contentDescription = stringResource(Res.string.settings),
                                            )
                                        }
                                    },
                                    scrollBehavior = topAppBarScrollBehavior,
                                    colors = TopAppBarDefaults.topAppBarColors(
                                        containerColor = if (pureBlack) Color.Black else MaterialTheme.colorScheme.surfaceContainer,
                                        scrolledContainerColor = if (pureBlack) Color.Black else MaterialTheme.colorScheme.surfaceContainer,
                                    )
                                )
                            }

                            // Bottom sheet player
                            BottomSheetPlayer(
                                state = playerBottomSheetState,
                                navController = navController,
                                pureBlack = pureBlack,
                            )
                        }
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
                                latestVersionName = "1.0.0-desktop",
                            )
                        }
                    }
                }

                if (showChangelog.value) {
                    ChangelogScreen(
                        onDismiss = { showChangelog.value = false }
                    )
                }

                BottomSheetMenu(
                    state = menuState,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )

                BottomSheetPage(
                    state = bottomSheetPageState,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )

                ToastOverlay(
                    bottomPadding = if (playerBottomSheetState.isDismissed) 32.dp else MiniPlayerHeight + 24.dp,
                )
            }
        }
    }
}
