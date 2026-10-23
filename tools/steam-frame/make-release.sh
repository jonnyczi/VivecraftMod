#!/usr/bin/env bash
# Builds the files for a Steam Frame release of the tabletop mode, for a GitHub release of the fork:
# the headset jar, install.sh with all versions and checksums filled in, the installer .desktop file and the icon.
# usage: tools/steam-frame/make-release.sh <version>      e.g. 0.1.0, the release tag is tabletop-v<version>
# env: REPO (default jonnyczi/VivecraftMod), DIST (default build/steam-frame-release)
set -euo pipefail
VERSION=${1:?usage: make-release.sh <version>}
TOOLS=$(cd "$(dirname "$0")" && pwd)
MAIN=$(git -C "$TOOLS" rev-parse --show-toplevel)
REPO=${REPO:-jonnyczi/VivecraftMod}
DIST=${DIST:-$MAIN/build/steam-frame-release}
TAG=tabletop-v$VERSION
RELEASE_URL=https://github.com/$REPO/releases/download/$TAG
UA="VivecraftMod tools/steam-frame (github.com/$REPO)"
JQ=$(nix build --no-link --print-out-paths 'nixpkgs#jq^bin' | sed -n 1p)/bin/jq

prop() {
    sed -n "s/^$1=//p" "$MAIN/gradle.properties" | tr -d '\r'
}

# url and sha512 of the primary file of a Modrinth version
modrinth() {
    curl -fsS -A "$UA" "https://api.modrinth.com/v2/project/$1/version/${2//+/%2B}" |
        "$JQ" -r '[.files[] | select(.primary)][0] | .url + " " + .hashes.sha512'
}

rm -rf "$DIST"
mkdir -p "$DIST"

echo "== headset jar"
"$MAIN/tools/openxr/build-headset-jar.sh" --committed >"$DIST/.build.log" 2>&1 ||
    { tail -20 "$DIST/.build.log"; exit 1; }
MINECRAFT=$(prop minecraft_version)
JAR=vivecraft-$MINECRAFT-tabletop-$VERSION.jar
XR_WORKTREE=${XR_WORKTREE:-$(dirname "$MAIN")/VivecraftMod-openxr}
cp "$XR_WORKTREE/build/stable/vivecraft-tabletop-stable.jar" "$DIST/$JAR"
ICON=minecraft-tabletop-icon.png
cp "$MAIN/common/src/main/resources/vivecraft_icon.png" "$DIST/$ICON"

echo "== versions"
FABRIC_LOADER=$(curl -fsS -A "$UA" https://meta.fabricmc.net/v2/versions/loader |
    "$JQ" -r '[.[] | select(.stable)][0].version')
read -r FABRIC_API_URL FABRIC_API_SHA512 <<<"$(modrinth fabric-api "$(prop fabric_api_version)")"
SODIUM_VERSION=$(sed -n 's/.*gametestRuntimeOnly "maven.modrinth:sodium:\(.*\)"/\1/p' "$MAIN/fabric/build.gradle" |
    head -1)
read -r SODIUM_URL SODIUM_SHA512 <<<"$(modrinth sodium "$SODIUM_VERSION")"
read -r PRISM_URL PRISM_SHA256 <<<"$(curl -fsS -A "$UA" \
    https://api.github.com/repos/PrismLauncher/PrismLauncher/releases/latest |
    "$JQ" -r '.assets[] | select(.name == "PrismLauncher-Linux-aarch64.AppImage") |
        .browser_download_url + " " + (.digest | sub("^sha256:"; ""))')"
for v in FABRIC_LOADER FABRIC_API_URL FABRIC_API_SHA512 SODIUM_URL SODIUM_SHA512 PRISM_URL PRISM_SHA256; do
    [ -n "${!v}" ] && [ "${!v}" != null ] || { echo "couldn't find $v" >&2; exit 1; }
    echo "$v=${!v}"
done

echo "== installer"
sed -e "s|@RELEASE@|$VERSION|" -e "s|@RELEASE_URL@|$RELEASE_URL|" -e "s|@MINECRAFT@|$MINECRAFT|" \
    -e "s|@FABRIC_LOADER@|$FABRIC_LOADER|" -e "s|@VIVECRAFT_JAR@|$JAR|" \
    -e "s|@VIVECRAFT_SHA256@|$(sha256sum "$DIST/$JAR" | cut -d' ' -f1)|" -e "s|@ICON@|$ICON|" \
    -e "s|@ICON_SHA256@|$(sha256sum "$DIST/$ICON" | cut -d' ' -f1)|" \
    -e "s|@FABRIC_API_URL@|$FABRIC_API_URL|" -e "s|@FABRIC_API_SHA512@|$FABRIC_API_SHA512|" \
    -e "s|@SODIUM_URL@|$SODIUM_URL|" -e "s|@SODIUM_SHA512@|$SODIUM_SHA512|" \
    -e "s|@PRISM_URL@|$PRISM_URL|" -e "s|@PRISM_SHA256@|$PRISM_SHA256|" \
    "$TOOLS/install.sh" >"$DIST/install-steam-frame.sh"
if grep -n '@[A-Z_0-9]*@' "$DIST/install-steam-frame.sh"; then
    echo "placeholders left in the installer" >&2
    exit 1
fi
chmod +x "$DIST/install-steam-frame.sh"

# double clicked in desktop mode, always runs the installer of the latest release
cat >"$DIST/Install Minecraft Tabletop.desktop" <<EOF
[Desktop Entry]
Type=Application
Name=Install Minecraft Tabletop
Comment=Installs or updates Minecraft with Vivecraft's tabletop mode
Exec=konsole --hold -e bash -c "curl -fsSL https://github.com/$REPO/releases/latest/download/install-steam-frame.sh | bash"
Icon=applications-games
Terminal=false
EOF

sed -e "s|@RELEASE@|$VERSION|g" -e "s|@REPO@|$REPO|g" -e "s|@MINECRAFT@|$MINECRAFT|g" \
    "$TOOLS/release-notes.md" >"$DIST/release-notes.md"
rm -f "$DIST/.build.log"
echo "== $DIST"
ls -la "$DIST"
