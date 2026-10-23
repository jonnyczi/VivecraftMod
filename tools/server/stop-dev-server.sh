#!/usr/bin/env bash
# Stops the dev server and waits until the world is saved. A server started with --background gets the 'stop'
# command, one in the foreground of another terminal gets SIGTERM, which also saves the world.
set -euo pipefail
source "$(dirname "$0")/lib.sh"

if ! pid=$(server_pid); then
    echo "the dev server isn't running"
    exit 0
fi

if server_has_fifo "$pid"; then
    echo "sending 'stop' to the dev server (PID $pid)"
    echo stop >"$FIFO"
else
    echo "sending SIGTERM to the dev server (PID $pid)"
    kill -TERM "$pid"
fi

for i in $(seq 240); do
    kill -0 "$pid" 2>/dev/null || { echo "stopped"; rm -f "$SERVER_DIR/server.pid"; exit 0; }
    # still running after a minute, so the stop command was lost
    [ "$i" = 120 ] && kill -TERM "$pid" 2>/dev/null
    sleep 0.5
done
echo "the server didn't stop within 2 minutes, killing it" >&2
kill -KILL "$pid" 2>/dev/null || true
rm -f "$SERVER_DIR/server.pid"
