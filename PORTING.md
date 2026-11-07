# Porting notes

How Lindale differs from Android Metrolist, and how upstream updates are brought over.

## Base

- Upstream: [MetrolistGroup/Metrolist](https://github.com/MetrolistGroup/Metrolist) `main` at commit
  `e6fea3b36c68841bad21daa6252e1d69ecd3afbe` (2026-11-03, after v13.7.0), InnerTubeX v0.11.6.
- A clean copy of those upstream sources lives on the **`upstream` branch** (reference only: not
  built, not edited by hand); its `VERSION.txt` names the commit. It is the base for the next sync.
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

1. Clone upstream next to this repo and run the merge tool from the repo root:
   `python3 tools/upstream_sync.py ../metrolist <commit in VERSION.txt> <new commit>`.
   It 3-way merges every changed Kotlin source, innertube test and string resource (base = the
   upstream revision closest to our file), keeps line endings, and removes duplicate string names.
2. Review `git diff`: resolve conflict markers with the API mapping above (e.g. `R.string` →
   `Res.string`, `Context` → `AppContext`) and revert changes that only concern the phone UI or
   Android APIs (see "Left out on purpose" below).
3. Port Android service / ExoPlayer changes (`MusicService`, `DownloadUtil`, ...) to
   `DesktopPlayerConnection` and `DownloadCenter` by hand; the tool lists those files as SKIP.
4. Build and test: `gradlew :desktop:compileKotlin :innertube:desktopTest :shared:desktopTest`,
   then push; the "Check" workflow builds, starts and plays a song on Windows and runs the Linux
   UI test.
5. Move the `upstream` branch to the new commit (same file set, `VERSION.txt` updated) and update
   the base at the top of this file.

### Left out on purpose (sync of 2026-11-08)

- `ui/player/Player.kt`: phone player layout (not used on desktop).
- `ui/component/PlayerSlider.kt`, `VolumeSlider.kt`: need a newer Material 3 `SliderState` API.
- `extensions/MediaItemExt.kt`: Android Auto extras. `api/OpenRouterService.kt`: AI translation is
  hidden on desktop. Widgets, Android Auto, Cast and task-clear fixes in `MusicService`.
