# shellcheck shell=bash
# Shared settings and helpers for the dev server scripts in tools/server, sourced by them, not run directly.
ROOT=$(git -C "$(dirname "${BASH_SOURCE[0]}")" rev-parse --show-toplevel)
SERVER_DIR=$ROOT/run/dev-server
PLAYERS_DIR=$ROOT/run/dev-players
FIFO=$SERVER_DIR/console.fifo

# value of a key in gradle.properties
gradle_prop() {
    sed -n "s/^$1=//p" "$ROOT/gradle.properties" | tr -d '\r'
}

# path of a nix package output, built or fetched on first use
nix_pkg() {
    nix build --no-link --print-out-paths "nixpkgs#$1" | head -1
}

java25() {
    echo "$(nix_pkg jdk25)/bin/java"
}

server_port() {
    local port
    port=$(sed -n 's/^server-port=//p' "$SERVER_DIR/server.properties" 2>/dev/null | tr -d '\r')
    echo "${port:-25565}"
}

# prints the PID of the running dev server, fails if it isn't running
server_pid() {
    local pid
    pid=$(cat "$SERVER_DIR/server.pid" 2>/dev/null) || return 1
    [ -n "$pid" ] && kill -0 "$pid" 2>/dev/null || return 1
    # make sure the PID wasn't reused by something else
    [ "$(readlink "/proc/$pid/cwd")" = "$SERVER_DIR" ] || return 1
    tr '\0' ' ' <"/proc/$pid/cmdline" | grep -q 'fabric-server-launch.jar' || return 1
    echo "$pid"
}

# true if the running server reads its console from the fifo, which is the case for --background
server_has_fifo() {
    [ "$(readlink "/proc/$1/fd/0")" = "$FIFO" ]
}

# newest Vivecraft fabric jar built from this checkout, skipping sources and dev jars
newest_vivecraft_jar() {
    ls -t "$ROOT"/build/libs/*-fabric.jar "$ROOT"/fabric/build/libs/*.jar 2>/dev/null |
        grep -v -E -- '-(sources|dev|api)(-[a-z]+)?\.jar$' | head -1 || true
}

# puts a Vivecraft jar into a mods dir in place of any other Vivecraft jar, unless it's already there
install_vivecraft_jar() {
    local old
    old=$(ls "$2"/vivecraft*.jar 2>/dev/null | head -1 || true)
    if [ -n "$old" ] && [ "$(basename "$old")" = "$(basename "$1")" ] && cmp -s "$1" "$old"; then
        return 0
    fi
    rm -f "$2"/vivecraft*.jar
    cp "$1" "$2/"
}

# the Vivecraft jar the server uses, so the laptop players can use exactly the same one
server_vivecraft_jar() {
    ls "$SERVER_DIR"/mods/vivecraft*.jar 2>/dev/null | head -1 || true
}

lan_ip() {
    ip -4 route get 1.1.1.1 2>/dev/null | sed -n 's/.* src \([0-9.]*\).*/\1/p'
}

# how to reach this laptop from the headset
print_address() {
    local port ip host suffix=""
    port=$(server_port)
    ip=$(lan_ip)
    host=$(hostname)
    [ "$port" = 25565 ] || suffix=":$port"
    echo "Server address for the headset (Multiplayer > Add Server):"
    if [ -n "$ip" ] && getent ahostsv4 "$host.lan" 2>/dev/null | grep -q "^$ip "; then
        echo "    $host.lan$suffix    (or $ip:$port)"
    else
        echo "    ${ip:-<no LAN address found>}:$port"
    fi
    echo "From this laptop: localhost:$port"
}
