# Changelog

## Unreleased

- **Linux** (preview): .deb package and `kur.sh`; system VLC for playback, MPRIS media controls, Discord Rich Presence over unix sockets (regular, Flatpak and Snap Discord), Google sign-in window, XDG folders readable only by the user
- **System tray**: play/pause, next, previous, show and quit; optional "keep playing in the tray when closed"
- **Mini player**: small always-on-top window with cover, title, controls and progress
- **Update check**: finds new (pre-)releases, shows "new version available" with formatted release notes and opens the download page
- **Up to date with Metrolist** (main of 2026-11-03): library sync keeps local likes and songs and no longer brings back songs deleted on YouTube; songs hidden by the explicit/video filters no longer shift which song a list starts with; artist, owner and byline parsing fixes; "Remove from queue" in the queue menu; translated search section titles; playlist sort by artist then album; translation updates
- **Playback**: InnerTubeX 0.11.6 (sturdier stream resolution, faster first play)
- **YouTube Music history**: songs played in Lindale show up in your YouTube Music history and recommendations (signed in, after the history duration)
- **Shuffle** really reorders the queue (next/previous follow it) and turning it off restores the order, also after a restart; "Persistent shuffle" and "Shuffle playlist or album first" work
- Player settings that did nothing on desktop now work: history duration, pause listen history, pause on mute, prevent duplicate tracks in queue, similar content, automatic sleep timer, image cache size
- Mouse-wheel scrolling no longer leaves the pull-to-refresh indicator stuck on Home and library pages
- "Download on like" and "Remember shuffle and repeat" now work on desktop
- Welcome guide, import, tray and other new texts translated into all 52 languages
- Experimental mpv engine (`tools\mpv-dene.bat`), VLC stays the default
- Removed ~1,250 lines of unused phone player code
- Linux end-to-end UI test (`tools/ui-test/run.sh`); every push is now checked on GitHub: Windows build that starts, searches and plays a song (with a downloadable test build), Linux UI test, and upstream's unit tests (82)
- `tools/upstream_sync.py` for bringing over Metrolist updates

## 0.2.0-beta

- Windows knows Lindale as a music app: the volume/lock-screen media panel shows the song, and keyboard and headset media keys control it
- Downloads are saved as normal music files ("Artist - Title") in Music\Lindale; existing downloads move there
- Storage screen shows download count, size and folder; the unused song cache section is gone
- "Disable screenshot" hides the window from screenshots, recordings and screen sharing
- Appearance hides settings that do nothing on desktop and explains the rest
- Integrations moved under Account
- First-run walkthrough: sign in, import from Spotify, shortcuts
- Backup & Restore: new "Import from other apps" section (Spotify via Exportify, M3U, CSV)
- Search suggestions show over the full-screen player; clicking outside closes them without losing the click
- Under the hood: playback goes through an engine interface (groundwork for mpv), player code split into smaller files

## 0.1.1-beta

- Space, arrows and other shortcuts no longer act on the player while you type in a text field
- Shortcuts work everywhere in the window (Ctrl+K/F search, Space, Ctrl+arrows, Shift+arrows, Ctrl+S/R/M)
- Searching folds away the full-screen player so results are visible; the search field stays reachable while the player is open
- Clicking the search field always reopens history and suggestions
- Search field says "Search Lindale"
- Full-screen player: "Change lyrics source" and "Sync lyrics" buttons above the lyrics
- About screen rebuilt for Lindale, with a bug-report link and Metrolist credited in its own section

## 0.1.0-beta

First public beta of Lindale, a Windows desktop port of Metrolist 13.7.0.

- Desktop interface with a single Spotify-style title bar (Home, Library, search, settings, account)
- Sign in with Google in an embedded window, or with a browser cookie
- Lyrics source picker that checks every provider, and tap-to-sync for lyrics that are out of time
- Offline downloads, persistent queue, remembered window size and position
- Equalizer, tempo and pitch, loudness normalization, Discord Rich Presence
- "Hide clean duplicates" setting
- Settings encrypted with Windows DPAPI
- 50+ interface languages
