#!/usr/bin/env bash
# Sends a console command to the dev server started with --background and prints what it logged in response.
# usage: tools/server/dev-server-cmd.sh <command...>     e.g. tools/server/dev-server-cmd.sh op Steve
set -euo pipefail
source "$(dirname "$0")/lib.sh"

[ $# -gt 0 ] || { sed -n '2,3p' "$0" | sed 's/^# //'; exit 1; }
if ! pid=$(server_pid); then
    echo "the dev server isn't running" >&2
    exit 1
fi
if ! server_has_fifo "$pid"; then
    echo "the dev server runs in the foreground of a terminal, type the command there" >&2
    exit 1
fi

LOG=$SERVER_DIR/console.log
OFFSET=$(stat -c %s "$LOG")
echo "$*" >"$FIFO"
sleep 1
tail -c +$((OFFSET + 1)) "$LOG"
