#!/usr/bin/env bash
# Copies the headset jar to the Steam Frame over ssh and installs it into the Prism instance.
# Never stops a running game: if the game runs, the jar is only uploaded, run this again after quitting it.
# usage: tools/openxr/deploy-to-frame.sh [--launch] [jar]
# env: FRAME (default steamos@frame.lan), PRISM_INSTANCE (default vivecraft-tabletop-26.2),
#      XR_WORKTREE (where build-headset-jar.sh builds, default ../VivecraftMod-openxr)
set -euo pipefail
TOOLS=$(cd "$(dirname "$0")" && pwd)
MAIN=$(git -C "$TOOLS" rev-parse --show-toplevel)
FRAME=${FRAME:-steamos@frame.lan}
INSTANCE=${PRISM_INSTANCE:-vivecraft-tabletop-26.2}
XR_WORKTREE=${XR_WORKTREE:-$(dirname "$MAIN")/VivecraftMod-openxr}

LAUNCH=false
if [ "${1:-}" = "--launch" ]; then
    LAUNCH=true
    shift
fi
JAR=${1:-$(ls "$XR_WORKTREE"/build/libs/*-fabric.jar | head -1)}
SUM=$(md5sum "$JAR" | cut -c1-12)
echo "deploying $JAR ($SUM) to $FRAME"

ssh -o BatchMode=yes "$FRAME" 'mkdir -p ~/vivecraft-tools'
scp -q -o BatchMode=yes "$JAR" "$FRAME:vivecraft-tools/vivecraft-tabletop-next.jar"
scp -q -o BatchMode=yes "$TOOLS/frame-launch.sh" "$FRAME:vivecraft-tools/launch.sh"

# the game runs as java from the minecraft runtime that Prism downloads, [a] keeps pgrep from matching this command
ssh -o BatchMode=yes "$FRAME" INSTANCE="$INSTANCE" LAUNCH="$LAUNCH" 'sh -s' <<'REMOTE'
set -e
chmod +x ~/vivecraft-tools/launch.sh
MODS=~/.local/share/PrismLauncher/instances/$INSTANCE/minecraft/mods
if pgrep -f "java-runtime-[a-z]*/bin/jav[a]" >/dev/null; then
    echo "the game is running, uploaded only, run this again after quitting it"
    exit 0
fi
cp ~/vivecraft-tools/vivecraft-tabletop-next.jar "$MODS/vivecraft-26.2-tabletop-xr.jar"
cmp -s ~/vivecraft-tools/vivecraft-tabletop-next.jar "$MODS/vivecraft-26.2-tabletop-xr.jar"
echo "installed $(md5sum "$MODS/vivecraft-26.2-tabletop-xr.jar" | cut -c1-12)"
if [ "$LAUNCH" = true ]; then
    setsid -f ~/vivecraft-tools/launch.sh "$INSTANCE"
    echo "launching $INSTANCE"
fi
REMOTE
