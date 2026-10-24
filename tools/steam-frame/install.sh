#!/usr/bin/env bash
# Installs Minecraft with Vivecraft's tabletop mode on a Steam Frame and adds it to the Steam library.
# Run it in desktop mode. Running it again updates everything and keeps the worlds, settings and login.
# Everything is in ~/.local/share/minecraft-tabletop, with its own Prism Launcher, so other Minecraft installs aren't
# touched. To uninstall, delete that folder and remove "Minecraft Tabletop" from the Steam library.
#
# The release values below are filled in by tools/steam-frame/make-release.sh.
# env for testing: INSTALL_DIR, SHORTCUT_NAME, ASSET_DIR (local release files instead of downloading them),
#                  NO_SHORTCUT=1, NO_LOGIN=1
set -euo pipefail

RELEASE="@RELEASE@"
RELEASE_URL="@RELEASE_URL@"
MINECRAFT="@MINECRAFT@"
FABRIC_LOADER="@FABRIC_LOADER@"
VIVECRAFT_JAR="@VIVECRAFT_JAR@"
VIVECRAFT_SHA256="@VIVECRAFT_SHA256@"
ICON="@ICON@"
ICON_SHA256="@ICON_SHA256@"
FABRIC_API_URL="@FABRIC_API_URL@"
FABRIC_API_SHA512="@FABRIC_API_SHA512@"
SODIUM_URL="@SODIUM_URL@"
SODIUM_SHA512="@SODIUM_SHA512@"
PRISM_URL="@PRISM_URL@"
PRISM_SHA256="@PRISM_SHA256@"

DIR=${INSTALL_DIR:-$HOME/.local/share/minecraft-tabletop}
NAME=${SHORTCUT_NAME:-Minecraft Tabletop}
PRISM=$DIR/PrismLauncher.AppImage
PRISM_DIR=$DIR/prism
INSTANCE=minecraft-tabletop
GAME=$PRISM_DIR/instances/$INSTANCE/minecraft

step() {
    printf '\n\033[1m==> %s\033[0m\n' "$*"
}

fail() {
    printf '\n\033[1;31mInstall failed: %s\033[0m\n' "$*" >&2
    exit 1
}

# downloads a file and checks it, unless it is already there
fetch() {
    local url=$1 dest=$2 algorithm=$3 sum=$4
    if [ -f "$dest" ] && echo "$sum  $dest" | "${algorithm}sum" -c --quiet 2>/dev/null; then
        return 0
    fi
    echo "downloading $(basename "$dest")"
    curl -fL --retry 3 --progress-bar -o "$dest.part" "$url" || fail "couldn't download $url"
    echo "$sum  $dest.part" | "${algorithm}sum" -c --quiet || fail "$(basename "$dest") is damaged, try again"
    mv "$dest.part" "$dest"
}

# a file of this release, from the release page or from ASSET_DIR when testing
fetch_release_file() {
    local name=$1 dest=$2 sum=$3
    if [ -n "${ASSET_DIR:-}" ]; then
        cp "$ASSET_DIR/$name" "$dest.part"
        echo "$sum  $dest.part" | sha256sum -c --quiet || fail "$name in $ASSET_DIR doesn't match"
        mv "$dest.part" "$dest"
    else
        fetch "$RELEASE_URL/$name" "$dest" sha256 "$sum"
    fi
}

# file name of a download url, Modrinth urls encode the + of the version
file_name() {
    basename "$1" | sed 's/%2B/+/g'
}

# replaces the jars of a mod in the mods folder
install_mod() {
    local pattern=$1 file=$2
    find "$GAME/mods" -maxdepth 1 -name "$pattern" ! -name "$(basename "$file")" -delete
    cp "$file" "$GAME/mods/"
}

echo "Minecraft Tabletop $RELEASE: Minecraft $MINECRAFT with Vivecraft's tabletop mode for the Steam Frame"
[ "$(uname -m)" = aarch64 ] || fail "this is for the Steam Frame, this computer is $(uname -m)"
command -v steamos-add-to-steam >/dev/null || [ -n "${NO_SHORTCUT:-}" ] || fail "this needs SteamOS"
mkdir -p "$DIR/downloads" "$PRISM_DIR/instances/$INSTANCE" "$GAME/mods" "$GAME/config"

step "Prism Launcher"
fetch "$PRISM_URL" "$PRISM" sha256 "$PRISM_SHA256"
chmod +x "$PRISM"
# settings that Prism otherwise asks for on the first start, it still asks for the Microsoft account
if [ ! -f "$PRISM_DIR/prismlauncher.cfg" ]; then
    cat >"$PRISM_DIR/prismlauncher.cfg" <<'EOF'
[General]
ConfigVersion=1.3
Language=en_US
UseSystemLocale=true
ApplicationTheme=system
IconTheme=pe_colored
AutomaticJavaDownload=true
AutomaticJavaSwitch=true
UserAskedAboutAutomaticJavaDownload=true
EOF
fi

step "Minecraft $MINECRAFT with Fabric"
if [ ! -f "$PRISM_DIR/instances/$INSTANCE/instance.cfg" ]; then
    cat >"$PRISM_DIR/instances/$INSTANCE/instance.cfg" <<EOF
[General]
ConfigVersion=1.3
InstanceType=OneSix
name=$NAME
AutomaticJava=true
OverrideMiscellaneous=true
CloseAfterLaunch=false
QuitAfterGameStop=true
EOF
fi
# always written, so updates can change the versions
cat >"$PRISM_DIR/instances/$INSTANCE/mmc-pack.json" <<EOF
{
    "components": [
        {
            "important": true,
            "uid": "net.minecraft",
            "version": "$MINECRAFT"
        },
        {
            "dependencyOnly": true,
            "uid": "net.fabricmc.intermediary",
            "version": "$MINECRAFT"
        },
        {
            "uid": "net.fabricmc.fabric-loader",
            "version": "$FABRIC_LOADER"
        }
    ],
    "formatVersion": 1
}
EOF

step "Mods"
fetch "$FABRIC_API_URL" "$DIR/downloads/$(file_name "$FABRIC_API_URL")" sha512 "$FABRIC_API_SHA512"
install_mod "fabric-api-*.jar" "$DIR/downloads/$(file_name "$FABRIC_API_URL")"
fetch "$SODIUM_URL" "$DIR/downloads/$(file_name "$SODIUM_URL")" sha512 "$SODIUM_SHA512"
install_mod "sodium-*.jar" "$DIR/downloads/$(file_name "$SODIUM_URL")"
fetch_release_file "$VIVECRAFT_JAR" "$DIR/downloads/$VIVECRAFT_JAR" "$VIVECRAFT_SHA256"
install_mod "vivecraft-*.jar" "$DIR/downloads/$VIVECRAFT_JAR"

# settings for the Steam Frame, only on the first install, so changes in the game are kept
if [ ! -f "$GAME/config/vivecraft-client-config.json" ]; then
    # OpenXR, which has passthrough, 80% resolution, which the Frame can render at full frame rate, and the tabletop
    # toggle in the radial menu, which gets two more buttons for it, so the default items stay
    cat >"$GAME/config/vivecraft-client-config.json" <<'EOF'
{
  "stereoProviderPluginID": "OPENXR",
  "vrEnabled": "true",
  "renderScaleFactor": "0.8",
  "vrRadialButtons": "10",
  "RADIAL_8": "vivecraft.key.toggleTabletop"
}
EOF
fi
if [ ! -f "$GAME/options.txt" ]; then
    # the Vulkan backend works best with OpenXR on the Frame
    printf '%s\n' 'preferredGraphicsBackend:"vulkan"' 'renderDistance:8' 'onboardAccessibility:false' >"$GAME/options.txt"
fi

step "Steam library"
fetch_release_file "$ICON" "$DIR/icon.png" "$ICON_SHA256"
cat >"$DIR/launch.sh" <<EOF
#!/bin/sh
# started by the "$NAME" Steam shortcut, Prism starts the game and quits with it, so Steam sees the game end
exec "$PRISM" --dir "$PRISM_DIR" --launch "$INSTANCE" >"$DIR/launch.log" 2>&1 </dev/null
EOF
chmod +x "$DIR/launch.sh"
cat >"$DIR/$INSTANCE.desktop" <<EOF
[Desktop Entry]
Type=Application
Name=$NAME
Comment=Minecraft with Vivecraft's tabletop mode
Exec=$DIR/launch.sh
Icon=$DIR/icon.png
Terminal=false
EOF
chmod +x "$DIR/$INSTANCE.desktop"
if [ -n "${NO_SHORTCUT:-}" ]; then
    echo "skipped"
elif [ -f "$DIR/.in-steam" ]; then
    echo "\"$NAME\" is already in the Steam library"
else
    steamos-add-to-steam "$DIR/$INSTANCE.desktop" || fail "couldn't add the shortcut, is Steam running?"
    touch "$DIR/.in-steam"
    echo "added \"$NAME\" to the Steam library"
fi

step "Microsoft account"
has_account() {
    [ -f "$PRISM_DIR/accounts.json" ] && grep -q '"ownsMinecraft": *true' "$PRISM_DIR/accounts.json"
}
if has_account; then
    echo "already signed in"
elif [ -n "${NO_LOGIN:-}" ]; then
    echo "skipped"
else
    message="Prism Launcher opens now. Sign in with the Microsoft account that owns Minecraft: Java Edition, then close Prism Launcher to finish the install."
    echo "$message"
    if command -v zenity >/dev/null; then
        zenity --info --title "$NAME" --text "$message" --width 400 2>/dev/null || true
    fi
    "$PRISM" --dir "$PRISM_DIR" >"$DIR/login.log" 2>&1 || true
    has_account || fail "no Minecraft account was added, run the installer again to sign in"
    echo "signed in"
fi

step "Done"
echo "Switch to Game Mode and start \"$NAME\" from the Steam library (Non-Steam)."
echo "The first start downloads Minecraft and Java, and may ask to restart the game once for VR."
echo "Run this installer again to update."
