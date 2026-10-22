package com.metrolist.music

import com.metrolist.music.ui.player.DesktopPlayerBarHeight

import androidx.compose.foundation.layout.fillMaxWidth

import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Box
import com.metrolist.music.ui.utils.BackHandler
import com.metrolist.music.ui.component.DesktopSearchField
import com.metrolist.music.ui.component.DesktopSearchPanel
import com.metrolist.music.ui.component.GlobalSearch
import com.metrolist.music.ui.component.rememberSearchSubmit
import com.metrolist.music.platform.AppContext

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
    syncUtils: SyncUtils = remember(database) { SyncUtils(AppContext(), database) },
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
        // Search lives in the top bar on desktop (Ctrl+K), not in the rail.
        listOfNotNull(Screens.Home, Screens.Library)
    }

    val currentTitleRes = remember(currentRoute) {
        when (currentRoute) {
            Screens.Home.route -> Res.string.home
            // The search screen draws its own bar with the input field.
            Screens.Library.route -> Res.string.filter_library
            else -> null
        }
    }

    var searchText by remember { mutableStateOf(TextFieldValue()) }
    var searchOpen by remember { mutableStateOf(false) }
    val searchFocusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val submitSearch = rememberSearchSubmit(navController, database, playerConnection)
    val closeSearch = {
        searchOpen = false
        focusManager.clearFocus()
    }
    val onSearchSubmit: (String) -> Unit = { query ->
        if (query.isNotBlank()) {
            searchText = TextFieldValue()
            closeSearch()
            submitSearch(query)
        }
    }
    LaunchedEffect(currentRoute) { searchOpen = false }
    val hasSearchBar by rememberUpdatedState(currentTitleRes != null)
    DisposableEffect(navController) {
        GlobalSearch.focusHandler = {
            if (hasSearchBar) {
                searchOpen = true
                runCatching { searchFocusRequester.requestFocus() }
            } else {
                navController.navigate(Screens.Search.route) { launchSingleTop = true }
            }
        }
        onDispose { GlobalSearch.focusHandler = null }
    }
    BackHandler(enabled = searchOpen) { closeSearch() }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isLandscape = maxWidth > maxHeight
        val isLargeScreen = maxWidth >= 600.dp
        val showRail = isLargeScreen || isLandscape

        val playerBottomSheetState = rememberBottomSheetState(
            dismissedBound = 0.dp,
            collapsedBound = if (showRail) DesktopPlayerBarHeight else (DesktopPlayerBarHeight + NavigationBarHeight),
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
                bottom = if (!playerBottomSheetState.isDismissed) DesktopPlayerBarHeight else 0.dp,
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
                    // DESKTOP: remember where the user clicked so menus open at the cursor.
                    modifier = Modifier.fillMaxSize().trackMenuAnchor()
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
                                onHistoryClick = {
                                    if (playerBottomSheetState.isExpanded) playerBottomSheetState.collapseSoft()
                                    navController.navigate("history") { launchSingleTop = true }
                                },
                                onStatsClick = {
                                    if (playerBottomSheetState.isExpanded) playerBottomSheetState.collapseSoft()
                                    navController.navigate("stats") { launchSingleTop = true }
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
                                    snackbarHostState = snackbarHostState,
                                )
                            }

                            if (currentTitleRes != null) {
                                if (searchOpen) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(top = 64.dp)
                                            .clickable(
                                                interactionSource = remember { MutableInteractionSource() },
                                                indication = null,
                                                onClick = closeSearch,
                                            ),
                                    ) {
                                        DesktopSearchPanel(
                                            value = searchText,
                                            onValueChange = { searchText = it },
                                            onSubmit = onSearchSubmit,
                                            pureBlack = pureBlack,
                                            modifier = Modifier.align(Alignment.TopCenter).padding(top = 4.dp, start = 16.dp, end = 16.dp),
                                        )
                                    }
                                }
                                TopAppBar(
                                    title = {
                                        Box(Modifier.fillMaxWidth().padding(end = 16.dp), contentAlignment = Alignment.Center) {
                                            DesktopSearchField(
                                                value = searchText,
                                                onValueChange = {
                                                    searchText = it
                                                    searchOpen = true
                                                },
                                                onSubmit = onSearchSubmit,
                                                onFocused = { searchOpen = true },
                                                focusRequester = searchFocusRequester,
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
                    bottomPadding = if (playerBottomSheetState.isDismissed) 32.dp else DesktopPlayerBarHeight + 24.dp,
                )
            }
        }
    }
}
