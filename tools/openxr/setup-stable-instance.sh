#!/usr/bin/env bash
# Keeps a separate "stable" Prism instance on the Steam Frame, for playing a known good build while the dev instance
# gets test builds, and adds it to the Steam library.
# The first run copies the dev instance, including its worlds, after that only the jar is replaced, so the worlds of
# the two instances stay independent.
# usage: tools/openxr/setup-stable-instance.sh [jar]
#   the default jar is the one from build-headset-jar.sh --committed
# env: FRAME (default steamos@frame.lan), DEV_INSTANCE (default vivecraft-tabletop-26.2),
#      STABLE_INSTANCE (default vivecraft-tabletop-stable), XR_WORKTREE (default ../VivecraftMod-openxr)
set -euo pipefail
TOOLS=$(cd "$(dirname "$0")" && pwd)
MAIN=$(git -C "$TOOLS" rev-parse --show-toplevel)
FRAME=${FRAME:-steamos@frame.lan}
DEV_INSTANCE=${DEV_INSTANCE:-vivecraft-tabletop-26.2}
STABLE_INSTANCE=${STABLE_INSTANCE:-vivecraft-tabletop-stable}
XR_WORKTREE=${XR_WORKTREE:-$(dirname "$MAIN")/VivecraftMod-openxr}
JAR=${1:-$XR_WORKTREE/build/stable/vivecraft-tabletop-stable.jar}
echo "stable build $JAR ($(md5sum "$JAR" | cut -c1-12)) for $STABLE_INSTANCE on $FRAME"

ssh -o BatchMode=yes "$FRAME" 'mkdir -p ~/vivecraft-tools'
scp -q -o BatchMode=yes "$JAR" "$FRAME:vivecraft-tools/vivecraft-tabletop-stable.jar"
scp -q -o BatchMode=yes "$TOOLS/frame-launch.sh" "$FRAME:vivecraft-tools/launch.sh"
scp -q -o BatchMode=yes "$MAIN/common/src/main/resources/vivecraft_icon.png" "$FRAME:vivecraft-tools/vivecraft_icon.png"

ssh -o BatchMode=yes "$FRAME" DEV="$DEV_INSTANCE" STABLE="$STABLE_INSTANCE" 'sh -s' <<'REMOTE'
set -e
chmod +x ~/vivecraft-tools/launch.sh
INSTANCES=~/.local/share/PrismLauncher/instances
# the game runs as java from the minecraft runtime that Prism downloads, [a] keeps pgrep from matching this command
if pgrep -f "java-runtime-[a-z]*/bin/jav[a]" >/dev/null; then
    echo "the game is running, quit it first, so the worlds are copied in a consistent state"
    exit 1
fi

if [ ! -d "$INSTANCES/$STABLE" ]; then
    cp -a "$INSTANCES/$DEV" "$INSTANCES/$STABLE"
    rm -rf "$INSTANCES/$STABLE/minecraft/logs" "$INSTANCES/$STABLE/minecraft/crash-reports"
    CFG="$INSTANCES/$STABLE/instance.cfg"
    sed -i -e '/^name=/d' -e '/^uuid=/d' -e '/^OverrideMiscellaneous=/d' -e '/^CloseAfterLaunch=/d' \
        -e '/^QuitAfterGameStop=/d' "$CFG"
    # quit Prism when the game stops, so Steam sees the game end
    sed -i "/^\[General\]/a name=Vivecraft Tabletop (stable)\nuuid=$(cat /proc/sys/kernel/random/uuid | tr -d -)\nOverrideMiscellaneous=true\nCloseAfterLaunch=false\nQuitAfterGameStop=true" "$CFG"
    echo "created $STABLE as a copy of $DEV, worlds: $(ls "$INSTANCES/$STABLE/minecraft/saves" | tr '\n' ',')"
fi

MODS="$INSTANCES/$STABLE/minecraft/mods"
cp ~/vivecraft-tools/vivecraft-tabletop-stable.jar "$MODS/vivecraft-26.2-tabletop-xr.jar"
cmp -s ~/vivecraft-tools/vivecraft-tabletop-stable.jar "$MODS/vivecraft-26.2-tabletop-xr.jar"
echo "installed $(md5sum "$MODS/vivecraft-26.2-tabletop-xr.jar" | cut -c1-12) into $STABLE"

DESKTOP=~/vivecraft-tools/$STABLE.desktop
cat > "$DESKTOP" <<DESKTOP
[Desktop Entry]
Type=Application
Name=Minecraft Tabletop
Comment=Vivecraft tabletop, stable build
Exec=$HOME/vivecraft-tools/launch.sh $STABLE
Icon=$HOME/vivecraft-tools/vivecraft_icon.png
Terminal=false
DESKTOP
chmod +x "$DESKTOP"
if [ ! -e ~/vivecraft-tools/.$STABLE-in-steam ]; then
    export DISPLAY=:0 XDG_RUNTIME_DIR=/run/user/$(id -u) DBUS_SESSION_BUS_ADDRESS=unix:path=/run/user/$(id -u)/bus
    steamos-add-to-steam "$DESKTOP"
    touch ~/vivecraft-tools/.$STABLE-in-steam
    echo "added 'Minecraft Tabletop' to the Steam library"
fi
REMOTE
