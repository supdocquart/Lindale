#!/bin/sh
# Linux end-to-end UI test: starts Lindale on a virtual screen (Xvfb) with a null PulseAudio sink,
# a private D-Bus session and a fake Discord socket, then drives it with mouse and keyboard
# (tools/ui-test/drive.py). Needs: xvfb pulseaudio dbus libvlc5 vlc-plugin-base ffmpeg
# stalonetray python3-xlib (pip install python-xlib). Settings and cache go to throwaway folders under
# build/ui-test; one generated test tone is put in ~/Music/Lindale and removed afterwards.
# LINDALE_ENGINE=mpv tools/ui-test/run.sh runs the same checks on the mpv engine (needs libmpv2).
set -e
cd "$(dirname "$0")/../.."
W="$PWD/build/ui-test"
# The driver reads logs/latest.log, so an old one must not answer for this run.
rm -f logs/latest.log logs/previous.log
rm -rf "$W" && mkdir -p "$W/run" "$HOME/Music/Lindale" && chmod 700 "$W/run"
export XDG_RUNTIME_DIR="$W/run" XDG_DATA_HOME="$W/data" XDG_CACHE_HOME="$W/cache" DISPLAY=:97
Xvfb :97 -screen 0 1600x1000x24 >/dev/null 2>&1 &
XVFB=$!
trap 'kill $XVFB $TRAY $DISCORD 2>/dev/null; pulseaudio --kill 2>/dev/null; rm -f "$HOME/Music/Lindale/Lindale UI test ["*' EXIT
sleep 1
pulseaudio --daemonize=yes --exit-idle-time=-1 -n --load="module-null-sink sink_name=null" --load="module-native-protocol-unix" 2>/dev/null
stalonetray --geometry 4x1+1400+900 -bg white >/dev/null 2>&1 &
TRAY=$!
python3 tools/ui-test/fake_discord.py "$XDG_RUNTIME_DIR/discord-ipc-0" "$W/discord.log" &
DISCORD=$!
dbus-run-session -- sh -c "
    ./gradlew :desktop:run --console=plain > '$W/app.log' 2>&1 &
    python3 tools/ui-test/drive.py '$W'
"
