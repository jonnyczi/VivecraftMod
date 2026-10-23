#!/usr/bin/env bash
# Creates the dedicated Fabric server in run/dev-server for multiplayer tests, see tools/README.md.
# Safe to run again: it only downloads what is missing or outdated, and never changes the world or a setting that is
# already in server.properties.
set -euo pipefail
source "$(dirname "$0")/lib.sh"

MC=$(gradle_prop minecraft_version)
LOADER=$(gradle_prop fabric_loader_version)
FABRIC_API=$(gradle_prop fabric_api_version)
JQ=$(nix_pkg 'jq^bin')/bin/jq
UA="VivecraftMod tools/server (github.com/Vivecraft/VivecraftMod)"

mkdir -p "$SERVER_DIR/mods" "$SERVER_DIR/config"
cd "$SERVER_DIR"

# Fabric server launcher. On the first start it downloads the vanilla server and the libraries next to it.
INSTALLER=$(curl -fsS -A "$UA" https://meta.fabricmc.net/v2/versions/installer |
    "$JQ" -r '[.[] | select(.stable)][0].version')
WANT="minecraft=$MC loader=$LOADER installer=$INSTALLER"
if ! [ -f fabric-server-launch.jar ] || [ "$(cat .launcher-version 2>/dev/null)" != "$WANT" ]; then
    echo "downloading the Fabric server launcher ($WANT)"
    curl -fsS -A "$UA" -o fabric-server-launch.jar.part \
        "https://meta.fabricmc.net/v2/versions/loader/$MC/$LOADER/$INSTALLER/server/jar"
    mv fabric-server-launch.jar.part fabric-server-launch.jar
    # the launcher keeps an existing server.jar, so drop it when the Minecraft version changed
    grep -q "^minecraft=$MC " .launcher-version 2>/dev/null || rm -f server.jar
    echo "$WANT" >.launcher-version
fi

# Fabric API from Modrinth, checked against its sha512
FAPI_JAR=mods/fabric-api-$FABRIC_API.jar
if ! [ -f "$FAPI_JAR" ]; then
    echo "downloading Fabric API $FABRIC_API"
    VERSION_JSON=$(curl -fsS -A "$UA" "https://api.modrinth.com/v2/project/fabric-api/version/${FABRIC_API//+/%2B}")
    URL=$("$JQ" -r '[.files[] | select(.primary)][0].url' <<<"$VERSION_JSON")
    SHA=$("$JQ" -r '[.files[] | select(.primary)][0].hashes.sha512' <<<"$VERSION_JSON")
    curl -fsS -A "$UA" -o "$FAPI_JAR.part" "$URL"
    echo "$SHA  $FAPI_JAR.part" | sha512sum -c --quiet
    rm -f mods/fabric-api-*.jar
    mv "$FAPI_JAR.part" "$FAPI_JAR"
fi

# accepts the Minecraft EULA (https://aka.ms/MinecraftEULA) for this local dev server
[ -f eula.txt ] || printf '# accepted by tools/server/setup-dev-server.sh for a local dev server\neula=true\n' >eula.txt

# dev defaults, only for keys that aren't in server.properties yet, so later changes are kept.
# online-mode=false lets the offline laptop players join next to a real account on the headset.
touch server.properties
while IFS= read -r line; do
    grep -q "^${line%%=*}=" server.properties || echo "$line" >>server.properties
done <<EOF
online-mode=false
enforce-secure-profile=false
difficulty=normal
gamemode=survival
spawn-protection=0
server-port=25565
motd=Vivecraft dev server ($(git -C "$ROOT" rev-parse --abbrev-ref HEAD))
EOF

# Vivecraft server config: debug logs the version handshake of every Vivecraft client, the join messages say
# who joined in VR, seated or non-VR, and tabletop reach is off by default on dedicated servers.
# The server fills in all other settings on the first start.
[ -f config/vivecraft-server-config.toml ] ||
    printf '[general]\n\tdebug = true\n\n[messages]\n\tenabled = true\n\n[vrChanges]\n\ttabletopAllowed = true\n' \
        >config/vivecraft-server-config.toml

echo "dev server ready in $SERVER_DIR"
echo "start it with tools/server/run-dev-server.sh, then type 'op <name>' in its console to make a player operator"
