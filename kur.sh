#!/bin/sh
# Builds Lindale on Linux and installs the .deb (Debian, Ubuntu, Mint, Pop!_OS...).
# Playback uses the system VLC, so the vlc / libvlc5 package is installed too.
# Settings and library: ~/.local/share/lindale, cache and logs: ~/.cache/lindale.
set -e
cd "$(dirname "$0")"
if ! command -v apt-get >/dev/null; then
    echo "This script installs a .deb package; on other distributions run: ./gradlew :desktop:run"
    exit 1
fi
echo "[1/3] Packages needed for building and playback"
sudo apt-get install -y fakeroot libvlc5 vlc-plugin-base
echo "[2/3] Building (the first build downloads Gradle and the JetBrains Runtime)"
./gradlew :desktop:packageDeb --no-configuration-cache --console=plain
echo "[3/3] Installing"
sudo apt-get install -y --reinstall "$(ls -t desktop/build/compose/binaries/main/deb/*.deb | head -1)"
echo "Installed. Start Lindale from your app menu or run /opt/lindale/bin/Lindale"
