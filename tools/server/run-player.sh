#!/usr/bin/env bash
# Starts an extra Vivecraft player on this laptop that joins the dev server, see tools/README.md.
# usage: tools/server/run-player.sh [options] <name>
#   <name>             offline player name, each name gets its own game dir in run/dev-players/<name>
#   --vr               play with NullVR (seated, the controllers follow the mouse), default is a flat 2D player
#   --server <host[:port]>  server to join, default localhost and the port of the dev server
#   --xvfb[=:N]        run on a hidden Xvfb display instead of the desktop, without sound, for tests
#   --jar <path>       Vivecraft jar, default the one the dev server uses, else the newest in build/libs
#   --refresh          ask gradle for the client classpath again, it's cached in run/dev-players/.launch
# env: CLIENT_MEM (default 4G), SIZE (window size, default 1280x720)
# The client is the Loom dev client of this checkout, but it runs without gradle and without the dev launcher, and
# loads Vivecraft from the jar like the server does, so rebuilding the checkout doesn't affect a running player.
set -euo pipefail
source "$(dirname "$0")/lib.sh"

VR=false
SERVER=""
XVFB=""
JAR=""
REFRESH=false
NAME=""
while [ $# -gt 0 ]; do
    case $1 in
    --vr) VR=true ;;
    --server) SERVER=$2 && shift ;;
    --xvfb) XVFB=auto ;;
    --xvfb=*) XVFB=${1#--xvfb=} ;;
    --jar) JAR=$2 && shift ;;
    --refresh) REFRESH=true ;;
    -h | --help)
        sed -n '2,13p' "$0" | sed 's/^# \{0,1\}//'
        exit 0
        ;;
    -*) echo "unknown option $1" >&2 && exit 1 ;;
    *) NAME=$1 ;;
    esac
    shift
done
if ! [[ $NAME =~ ^[A-Za-z0-9_]{3,16}$ ]]; then
    echo "usage: tools/server/run-player.sh [--vr] [--server host:port] [--xvfb] <name>, the name needs 3 to 16 letters, digits or _" >&2
    exit 1
fi
SERVER=${SERVER:-localhost:$(server_port)}
SIZE=${SIZE:-1280x720}

# client classpath and arguments of the Loom runClient task, cached, regenerated when a file in it is gone
LAUNCH=$PLAYERS_DIR/.launch
stale() {
    ! [ -f "$LAUNCH/classpath.txt" ] && return 0
    while IFS= read -r f; do [ -e "$f" ] || return 0; done <"$LAUNCH/classpath.txt"
    return 1
}
if $REFRESH || stale; then
    echo "asking gradle for the client classpath"
    "$ROOT/tools/nix-gradle.sh" -I "$ROOT/tools/server/client-launch.init.gradle" :fabric:writeClientLaunch \
        -PclientLaunchDir="$LAUNCH" --console=plain -q
fi

JAR=${JAR:-$(server_vivecraft_jar)}
JAR=${JAR:-$(newest_vivecraft_jar)}
if [ -z "$JAR" ] || ! [ -f "$JAR" ]; then
    echo "no Vivecraft jar found, build one with: tools/nix-gradle.sh :fabric:assemble" >&2
    exit 1
fi

# game dir with its own options and Vivecraft config
DIR=$PLAYERS_DIR/$NAME
mkdir -p "$DIR/mods" "$DIR/config"
install_vivecraft_jar "$JAR" "$DIR/mods"
CONFIG=$DIR/config/vivecraft-client-config.json
[ -f "$CONFIG" ] ||
    printf '{\n  "stereoProviderPluginID": "NULLVR",\n  "vrEnabled": "false",\n  "seated": "true"\n}\n' >"$CONFIG"
sed -i -E "s/\"vrEnabled\": *\"[a-z]*\"/\"vrEnabled\": \"$VR\"/" "$CONFIG"
[ -f "$DIR/options.txt" ] || printf '%s\n' onboardAccessibility:false skipMultiplayerWarning:true joinedFirstServer:true \
    pauseOnLostFocus:false tutorialStep:none >"$DIR/options.txt"

# native libraries that LWJGL, GLFW and OpenAL load at runtime, same as tools/run-client.sh
LD=/run/opengl-driver/lib
for p in libx11 libxcursor libxrandr libxi libxxf86vm libxext libxrender libpulseaudio alsa-lib wayland \
    libxkbcommon vulkan-loader systemdLibs libGL; do
    LD="$LD:$(nix_pkg "$p")/lib"
done
export LD_LIBRARY_PATH=$LD

XVFB_PID=""
if [ -n "$XVFB" ]; then
    XVFB_BIN=$(nix_pkg xvfb)/bin/Xvfb
    # players started at the same time must not pick the same free display
    exec 9>"$PLAYERS_DIR/.xvfb.lock"
    flock 9
    if [ "$XVFB" = auto ]; then
        N=80
        while [ -e "/tmp/.X11-unix/X$N" ] || [ -e "/tmp/.X$N-lock" ]; do N=$((N + 1)); done
        XVFB=:$N
    fi
    if ! [ -e "/tmp/.X11-unix/X${XVFB#:}" ]; then
        "$XVFB_BIN" "$XVFB" -screen 0 "${SIZE}x24" +extension GLX -nolisten tcp >"$DIR/xvfb.log" 2>&1 9>&- &
        XVFB_PID=$!
        for _ in $(seq 50); do [ -e "/tmp/.X11-unix/X${XVFB#:}" ] && break; sleep 0.1; done
    fi
    exec 9>&-
    export DISPLAY=$XVFB
    unset WAYLAND_DISPLAY
    # OpenAL's null output, a hidden player shouldn't make noise
    export ALSOFT_DRIVERS=null
fi

mapfile -t JVM_ARGS <"$LAUNCH/jvm-args.txt"
mapfile -t GAME_ARGS <"$LAUNCH/args.txt"
CLASSPATH=$(paste -sd: "$LAUNCH/classpath.txt")
cd "$DIR"
echo "starting $NAME ($($VR && echo NullVR || echo 2D)) joining $SERVER${XVFB:+ on display $XVFB}, log: $DIR/logs/latest.log"
"$(java25)" "-Xmx${CLIENT_MEM:-4G}" -XX:+UseZGC "${JVM_ARGS[@]}" -cp "$CLASSPATH" "$(cat "$LAUNCH/main-class.txt")" \
    "${GAME_ARGS[@]}" --gameDir "$DIR" --username "$NAME" --quickPlayMultiplayer "$SERVER" \
    --width "${SIZE%x*}" --height "${SIZE#*x}" &
CLIENT_PID=$!
echo "$CLIENT_PID" >client.pid
trap 'kill "$CLIENT_PID" 2>/dev/null' INT TERM
status=0
wait "$CLIENT_PID" || status=$?
# wait again in case a signal interrupted the first wait
kill -0 "$CLIENT_PID" 2>/dev/null && { wait "$CLIENT_PID" || status=$?; }
rm -f client.pid
[ -z "$XVFB_PID" ] || kill "$XVFB_PID" 2>/dev/null || true
exit "$status"
