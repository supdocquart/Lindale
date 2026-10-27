# Working on Lindale (humans and AI agents)

Lindale is a Windows desktop port of the Metrolist YouTube Music client, written in Kotlin with
Compose Multiplatform (JVM desktop).

## Rules

1. Keep upstream merges cheap: Kotlin package names stay `com.metrolist.*`, and changes to code that
   also exists upstream are marked with a `// DESKTOP:` comment explaining why.
2. Desktop-only UI strings go in `shared/src/commonMain/composeResources/values/desktop_strings.xml`
   (English) with translations in the matching `values-xx/desktop_strings.xml`. Do not hand-edit the
   imported upstream `strings.xml` / `metrolist_strings.xml` files.
3. Do not change the Room database schema version or the app version without a maintainer's approval.
4. Never log or commit cookies, tokens, API keys or e-mail addresses.
5. Commit messages: short imperative summary, e.g. `Player: keep tempo after track change`.

## Build and test

```bat
gradlew.bat :desktop:compileKotlin   :: compile check
gradlew.bat :desktop:smokeTest       :: headless checks in an isolated data folder
gradlew.bat :desktop:run             :: run the app (needs a 64-bit VLC 3.0 install)
```

See `DESKTOP_PORT_NOTES.txt` for how the port differs from Android Metrolist and how upstream
updates are brought over.
