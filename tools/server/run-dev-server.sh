#!/usr/bin/env bash
# Starts the dev server in run/dev-server with a Vivecraft jar of this checkout, see tools/README.md.
# usage: tools/server/run-dev-server.sh [--background] [path/to/vivecraft.jar]
#   without a jar it uses the newest one in build/libs, build it with: tools/nix-gradle.sh :fabric:assemble
#   --background  runs it detached, the console goes to run/dev-server/console.log, commands go through
#                 tools/server/dev-server-cmd.sh and tools/server/stop-dev-server.sh stops it
# env: SERVER_MEM (default 3G)
set -euo pipefail
source "$(dirname "$0")/lib.sh"

BACKGROUND=false
JAR=""
for arg in "$@"; do
    case $arg in
    --background) BACKGROUND=true ;;
    -h | --help)
        sed -n '2,8p' "$0" | sed 's/^# \{0,1\}//'
        exit 0
        ;;
    *) JAR=$arg ;;
    esac
done

if pid=$(server_pid); then
    echo "the dev server is already running (PID $pid), stop it with tools/server/stop-dev-server.sh"
    print_address
    exit 1
fi

[ -f "$SERVER_DIR/fabric-server-launch.jar" ] || "$(dirname "$0")/setup-dev-server.sh"

JAR=${JAR:-$(newest_vivecraft_jar)}
if [ -z "$JAR" ] || ! [ -f "$JAR" ]; then
    echo "no Vivecraft jar found, build one with: tools/nix-gradle.sh :fabric:assemble" >&2
    exit 1
fi
install_vivecraft_jar "$JAR" "$SERVER_DIR/mods"
echo "Vivecraft: $(basename "$JAR"), built $(date -r "$JAR" '+%F %T'), md5 $(md5sum "$JAR" | cut -c1-12)"

JAVA=$(java25)
CMD=("$JAVA" "-Xmx${SERVER_MEM:-3G}" -jar fabric-server-launch.jar nogui)
cd "$SERVER_DIR"

if ! $BACKGROUND; then
    print_address
    echo "type 'op <name>' to make a player operator, 'stop' to stop the server"
    echo $$ >server.pid
    exec "${CMD[@]}"
fi

# Detached: stdin is a fifo opened read-write, so it never sees EOF and dev-server-cmd.sh can write to it.
[ -p "$FIFO" ] || { rm -f "$FIFO" && mkfifo "$FIFO"; }
rm -f server.pid
: >console.log
setsid sh -c 'echo $$ >server.pid; exec "$@"' sh "${CMD[@]}" <>"$FIFO" >console.log 2>&1 &

echo "starting the dev server in the background, console: $SERVER_DIR/console.log"
for _ in $(seq 600); do
    if grep -q -E '\]: Done \([0-9.]+s\)!' console.log; then
        echo "started (PID $(cat server.pid))"
        print_address
        echo "send console commands like 'op <name>' with tools/server/dev-server-cmd.sh, stop it with tools/server/stop-dev-server.sh"
        exit 0
    fi
    if [ -f server.pid ] && ! kill -0 "$(cat server.pid)" 2>/dev/null; then
        echo "the server exited during startup:" >&2
        tail -30 console.log >&2
        exit 1
    fi
    sleep 0.5
done
echo "the server didn't finish starting in 5 minutes, see $SERVER_DIR/console.log" >&2
exit 1
