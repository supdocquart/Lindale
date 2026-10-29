# Porting notes

How Lindale differs from Android Metrolist, and how upstream updates are brought over.

## Base

- Upstream: [MetrolistGroup/Metrolist](https://github.com/MetrolistGroup/Metrolist) v13.7.0
  (versionCode 153, commit `7c5ba3ad22cb562d337897f82163a2b7fce665f3`).
- A clean copy of those upstream sources lives on the **`upstream` branch** (reference only: not
  built, not edited by hand). It is the merge base for upstream updates.
- There is no shared git history with upstream (the port started from a source snapshot).

Guidelines:
- **Desktop UI:** desktop-specific screens and components (e.g. `DesktopPlayerBar`) replace the
  phone UI; the phone originals stay on the `upstream` branch.
- **Core** (innertube, stream resolution / `InnerTubeXPlayer`, database, view models, lyrics
  providers) stays as close to upstream as possible, so upstream fixes for YouTube changes apply
  directly.
- Small desktop changes in shared screens are marked with a `// DESKTOP:` comment.

## Path mapping (upstream → this repo)

```
app/src/main/kotlin/com/metrolist/...   -> shared/src/commonMain/kotlin/com/metrolist/...
app/src/main/res/values/*strings.xml    -> shared/src/commonMain/composeResources/values/
app/src/main/res/values-XX/             -> shared/src/commonMain/composeResources/values-XX/
app/src/main/res/drawable/*.xml         -> shared/src/commonMain/composeResources/drawable/
innertube/                              -> innertube/ (same module, JVM target only)
Android service / player code           -> shared/src/desktopMain/kotlin/... (desktop equivalents)
App entry (MainActivity)                -> desktop/src/main/kotlin/Main.kt + shared/.../App.kt
```

Resources: `R.string.x` → `Res.string.x` (Compose Multiplatform resources). Desktop-only strings
live in `values*/desktop_strings.xml`.

## Android API → desktop equivalent

```
Context / LocalContext                -> platform/AppContext.kt, LocalAppContext
                                         (cache: %LOCALAPPDATA%\Lindale\cache, files: %APPDATA%\Lindale)
SharedPreferences                     -> platform/KeyValueStore.kt (%APPDATA%\Lindale\shared_prefs)
DataStore / Room (schema 36)          -> same libraries, unchanged; settings encrypted with DPAPI
Intent ACTION_SEND / ACTION_VIEW      -> platform/DesktopShare.kt (clipboard, save dialog, browser)
ActivityResultContracts (pickers)     -> platform/FilePickers.kt
uCrop                                 -> FilePickers.kt ImageCrop.centerSquare
android.net.Uri                       -> platform/Uri.kt
FLAG_KEEP_SCREEN_ON                   -> platform/ScreenWake.kt + WindowsScreenWake
BackHandler                           -> ui/utils/BackHandler.kt + BackDispatcher (Esc, mouse back)
hiltViewModel                         -> viewmodels/MetrolistViewModel.kt metrolistViewModel()
ExoPlayer + MusicService              -> desktopMain playback/DesktopPlayerConnection.kt (libVLC via vlcj)
Media3 types (Player, MediaItem, ...) -> playback/PlayerModel.kt, playback/Download.kt
DownloadService                       -> ui/component/DownloadUtil.kt DownloadCenter
AudioEffect equalizer                 -> eq/EqualizerService.kt + VlcEqualizerBackend (10 bands)
PlaybackParameters (tempo/pitch)      -> VLC setRate (live); pitch rebuilds libVLC
Palette                               -> ui/theme/Palette.kt + DesktopPaletteReader
Toast                                 -> utils/Toast.kt ToastHost + ToastOverlay
Bitmap rendering (share images)       -> utils/ComposeToImage.kt (Java2D)
WebView Google sign-in                -> desktop JcefGoogleLogin (JCEF child process) or cookie paste
Discord OAuth redirect Activity       -> DiscordOAuthCallback + DiscordDesktopOAuth (lindalediscord://)
AndroidKeyStore token encryption      -> platform/SecretProtector + WindowsDpapi
Discord Rich Presence                 -> DesktopDiscordPresence (local Discord IPC first, else gateway)
Last.fm scrobbling                    -> DesktopScrobbler
DNS                                   -> desktop DohResolver (DNS-over-HTTPS, JVM wide)
Dynamic color (wallpaper)             -> Theme.kt: MaterialKolor seed; "Dynamic" = current cover art
Status bar color                      -> platform/WindowChrome + JbrTitleBar (WindowsTitleBar fallback)
Long press (onLongClick)              -> ui/utils/DesktopClickable.kt (right click also triggers it)
Android string escapes (\' \")        -> cleaned in shared/build.gradle.kts at build time
```

Kept on purpose: androidx compose / room / lifecycle / navigation / sqlite / datastore (they are
multiplatform), `androidx/room/util/RoomUtilCompat.kt` (called by generated DAO code), and
InnerTube client names such as `ANDROID_VR` (they identify the client to YouTube).

## Removed features

Android Auto, system equalizer menu, exact alarm / battery / notification permission prompts,
Android per-app language redirect (in-app language picker stays), "open by default" links,
status bar hiding, Android permission requests, and the floating shuffle button on Home.

## Bringing over upstream updates

Cadence: once a month. Exceptions, taken right away: YouTube changes that break playback,
search or sign-in, and security fixes.

1. Update the `upstream` branch to the new upstream release (same file set) and its `VERSION.txt`.
2. `git diff` the `upstream` branch against its previous commit to see what changed.
3. Apply the changes under `shared/` using the path mapping above (`git apply --3way`), resolving
   conflicts with the API mapping (e.g. `Context` → `AppContext`).
4. Port Android service / ExoPlayer changes to `DesktopPlayerConnection` by hand.
5. Build (`gradlew.bat :desktop:compileKotlin`), run `gradlew.bat :desktop:smokeTest`, test the
   app, and update the base version at the top of this file.
