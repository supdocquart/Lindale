package com.metrolist.music

import androidx.compose.material3.LocalContentColor

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import com.metrolist.music.platform.TitleBarInteraction
import com.metrolist.music.platform.WindowChrome
import com.metrolist.music.platform.titleBarInteractive
import com.metrolist.music.ui.player.DesktopPlayerBarHeight

import androidx.compose.foundation.layout.fillMaxWidth

import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalFocusManager
// DESKTOP: link clicks are routed through the scheme-checking handler below.
import androidx.compose.ui.platform.LocalUriHandler
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
import com.metrolist.music.platform.SafeUriHandler

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
    syncUtils: SyncUtils = remember(database) { SyncUtils.get(AppContext(), database) },
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

    // DESKTOP: with JBR the native caption is replaced by our own single-row title bar.
    val customTitleBar = WindowChrome.customTitleBar

    // DESKTOP: theme seed = chosen palette color, or the playing song's cover when "Dynamic" is
    // selected (same rules as upstream MainActivity).
    val enableDynamicTheme by rememberPreference(com.metrolist.music.constants.DynamicThemeKey, defaultValue = true)
    val selectedThemeColorInt by rememberPreference(
        com.metrolist.music.constants.SelectedThemeColorKey,
        defaultValue = com.metrolist.music.ui.theme.DefaultThemeColor.toArgb(),
    )
    val selectedThemeColor = Color(selectedThemeColorInt)
    val themeColor = com.metrolist.music.ui.theme.rememberThemeSeedColor(playerConnection, enableDynamicTheme, selectedThemeColor)

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

        // DESKTOP: the player sheet lives inside the content area, which starts below the title row.
        val titleBarHeight = if (customTitleBar) 48.dp else 0.dp
        val playerBottomSheetState = rememberBottomSheetState(
            dismissedBound = 0.dp,
            collapsedBound = if (showRail) DesktopPlayerBarHeight else (DesktopPlayerBarHeight + NavigationBarHeight),
            expandedBound = maxHeight - titleBarHeight,
        )
        // DESKTOP: search results and suggestions draw under the expanded player, so fold it away.
        LaunchedEffect(searchOpen, currentRoute) {
            val onSearchPage = currentRoute?.startsWith("search") == true
            if ((searchOpen || onSearchPage) && playerBottomSheetState.isExpanded) playerBottomSheetState.collapseSoft()
        }

        // DESKTOP: the Home action is shared by the rail, the bottom bar and the title bar.
        val navigateHome: () -> Unit = {
            if (playerBottomSheetState.isExpanded) {
                playerBottomSheetState.collapseSoft()
            }
            // Home is the start destination; restoring its saved state would bring back the page
            // we just left (Settings/Stats), so just pop to it.
            if (!navController.popBackStack(Screens.Home.route, inclusive = false)) {
                navController.navigate(Screens.Home.route) { launchSingleTop = true }
            }
        }
        // DESKTOP: Library button of the title bar (the side rail is hidden with the custom title bar).
        val navigateLibrary: () -> Unit = {
            if (playerBottomSheetState.isExpanded) playerBottomSheetState.collapseSoft()
            // DESKTOP: always land on the Library page itself. Restoring the tab's saved state brought
            // back the playlist/album opened from it, so the button seemed to do nothing.
            if (!navController.popBackStack(Screens.Library.route, inclusive = false)) {
                navController.navigate(Screens.Library.route) {
                    popUpTo(navController.graph.startDestinationId)
                    launchSingleTop = true
                }
            }
        }

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

        // DESKTOP: Home/Library used to get their 64 dp bar from App.kt; the title bar replaces it.
        val appBarTopInset = if (customTitleBar && currentTitleRes != null) 0.dp else AppBarHeight
        // DESKTOP: shared by the title bar and (without the custom title bar) the rail.
        val openSettings: () -> Unit = {
            if (playerBottomSheetState.isExpanded) playerBottomSheetState.collapseSoft()
            navController.navigate("settings") { launchSingleTop = true }
        }
        val playerAwareWindowInsets = remember(showRail, playerBottomSheetState.isDismissed, appBarTopInset) {
            WindowInsets(
                left = 0.dp,
                top = appBarTopInset,
                right = 0.dp,
                bottom = if (!playerBottomSheetState.isDismissed) DesktopPlayerBarHeight else 0.dp,
            )
        }

        MetrolistTheme(
            darkTheme = isDark,
            pureBlack = pureBlack,
            themeColor = themeColor,
        ) {
            // DESKTOP: paint the native title bar in the top bar's color so they read as one piece.
            val chromeColor = if (pureBlack) Color.Black else MaterialTheme.colorScheme.surfaceContainer
            androidx.compose.runtime.SideEffect {
                com.metrolist.music.platform.WindowChrome.set(chromeColor.toArgb(), isDark)
            }
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
                // DESKTOP: Compose's desktop default opens any scheme; this one is restricted to http/https/mailto.
                LocalUriHandler provides SafeUriHandler,
            ) {
                Scaffold(
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                    topBar = {
                        if (customTitleBar) {
                            DesktopTitleBar(
                                pureBlack = pureBlack,
                                showSearch = currentTitleRes != null,
                                searchText = searchText,
                                onSearchTextChange = {
                                    searchText = it
                                    searchOpen = true
                                },
                                onSearchSubmit = onSearchSubmit,
                                onSearchFocus = { searchOpen = true },
                                searchFocusRequester = searchFocusRequester,
                                onBack = { BackDispatcher.dispatch() },
                                onHome = navigateHome,
                                onLibrary = navigateLibrary,
                                currentRoute = currentRoute,
                                onAccount = { showAccountDialog = true },
                                onSettings = openSettings,
                            )
                        }
                    },
                    bottomBar = {
                        if (!showRail && currentRoute != "wrapped") {
                            AppNavigationBar(
                                navigationItems = navigationItems,
                                currentRoute = currentRoute,
                                onItemClick = { screen, isSelected ->
                                    if (screen.route == Screens.Home.route) {
                                        navigateHome()
                                    } else {
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
                                top = if (customTitleBar) innerPadding.calculateTopPadding() else 0.dp,
                                bottom = if (!showRail) innerPadding.calculateBottomPadding() else 0.dp
                            )
                    ) {
                        // DESKTOP: the custom title bar carries Home/Library/Settings/Account, so no rail.
                        if (showRail && !customTitleBar && currentRoute != "wrapped") {
                            AppNavigationRail(
                                navigationItems = navigationItems,
                                currentRoute = currentRoute,
                                onItemClick = { screen, isSelected ->
                                    if (screen.route == Screens.Home.route) {
                                        navigateHome()
                                    } else {
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
                                    }
                                },
                                pureBlack = pureBlack,
                                // DESKTOP: with the custom title bar, Settings and Account live in the title row.
                                onAccountClick = if (customTitleBar) null else ({ showAccountDialog = true }),
                                onSettingsClick = if (customTitleBar) null else openSettings,
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
                                            .padding(top = if (customTitleBar) 0.dp else 64.dp)
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
                                if (!customTitleBar) {
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

/**
 * DESKTOP: the one-row JBR title bar. Empty space keeps native drag/double-click/snap behaviour;
 * interactive children register themselves so JBR hands their mouse input to Compose instead.
 */
@Composable
private fun DesktopTitleBar(
    pureBlack: Boolean,
    showSearch: Boolean,
    searchText: TextFieldValue,
    onSearchTextChange: (TextFieldValue) -> Unit,
    onSearchSubmit: (String) -> Unit,
    onSearchFocus: () -> Unit,
    searchFocusRequester: FocusRequester,
    onBack: () -> Unit,
    onHome: () -> Unit,
    onAccount: () -> Unit,
    onSettings: () -> Unit,
    onLibrary: () -> Unit,
    currentRoute: String?,
) {
    val interaction = remember { TitleBarInteraction() }
    DisposableEffect(interaction) {
        onDispose { interaction.reset() }
    }
    var rowOrigin by remember { mutableStateOf(Offset.Zero) }
    val background = if (pureBlack) Color.Black else MaterialTheme.colorScheme.surfaceContainer
    val leftInset = WindowChrome.titleBarLeftInset
    val rightInset = WindowChrome.titleBarRightInset

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(background)
            .onGloballyPositioned { rowOrigin = it.positionInRoot() }
            .pointerInput(interaction) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull() ?: continue
                        interaction.onPointer(event.type, rowOrigin + change.position, change.pressed)
                    }
                }
            },
    ) {
        Row(
            modifier = Modifier.align(Alignment.CenterStart).padding(start = leftInset + 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // DESKTOP: no logo here (the old Metrolist mark); the window/taskbar icon shows Lindale's.
            IconButton(
                onClick = onBack,
                modifier = Modifier.titleBarInteractive(interaction),
            ) {
                Icon(
                    painter = painterResource(Res.drawable.arrow_back),
                    contentDescription = stringResource(Res.string.back),
                )
            }
            val onHomeRoute = currentRoute == Screens.Home.route
            IconButton(
                onClick = onHome,
                modifier = Modifier.titleBarInteractive(interaction),
            ) {
                Icon(
                    painter = painterResource(if (onHomeRoute) Res.drawable.home_filled else Res.drawable.home_outlined),
                    contentDescription = stringResource(Res.string.home),
                    tint = if (onHomeRoute) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                )
            }
            // DESKTOP: Library moved here from the removed side rail; History and Stats are Library chips.
            val onLibraryRoute = currentRoute == Screens.Library.route
            IconButton(
                onClick = onLibrary,
                modifier = Modifier.titleBarInteractive(interaction),
            ) {
                Icon(
                    painter = painterResource(
                        if (onLibraryRoute) Res.drawable.library_music_filled else Res.drawable.library_music_outlined,
                    ),
                    contentDescription = stringResource(Res.string.filter_library),
                    tint = if (onLibraryRoute) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                )
            }
        }

        if (showSearch) {
            val sideReserve = maxOf(leftInset + 184.dp, rightInset + 112.dp)
            val searchWidth = (maxWidth - sideReserve * 2f).coerceIn(160.dp, 480.dp)
            DesktopSearchField(
                value = searchText,
                onValueChange = onSearchTextChange,
                onSubmit = onSearchSubmit,
                onFocused = onSearchFocus,
                focusRequester = searchFocusRequester,
                modifier = Modifier
                    .align(Alignment.Center)
                    .width(searchWidth)
                    .titleBarInteractive(interaction),
            )
        }

        Row(
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = rightInset + 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = onSettings,
                modifier = Modifier.titleBarInteractive(interaction),
            ) {
                Icon(
                    painter = painterResource(Res.drawable.settings),
                    contentDescription = stringResource(Res.string.settings),
                )
            }
            IconButton(
                onClick = onAccount,
                modifier = Modifier.titleBarInteractive(interaction),
            ) {
                Icon(
                    painter = painterResource(Res.drawable.account),
                    contentDescription = stringResource(Res.string.account),
                )
            }
        }
    }
}
