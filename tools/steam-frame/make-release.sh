#!/usr/bin/env bash
# Builds the files for a release of the tabletop fork in build/steam-frame-release: the jar, the Steam Frame installer
# with all versions and checksums filled in, the .desktop file that runs it, a Modrinth modpack with the same mods and
# settings, the icon, SHA256SUMS and the release notes (the release description, not a file of the release).
# The version is minecraft_version-mod_version of the tabletop-openxr branch, like 26.2-1.3.15-tabletop1, it is also the
# release tag. For a new release, commit mod_version=<upstream version>-tabletop<n> on tabletop-openxr first.
# usage: tools/steam-frame/make-release.sh
# env: REPO (default jonnyczi/VivecraftMod), DIST (default build/steam-frame-release),
#      XR_WORKTREE (default ../VivecraftMod-openxr next to this checkout)
set -euo pipefail
TOOLS=$(cd "$(dirname "$0")" && pwd)
MAIN=$(git -C "$TOOLS" rev-parse --show-toplevel)
REPO=${REPO:-jonnyczi/VivecraftMod}
DIST=${DIST:-$MAIN/build/steam-frame-release}
XR_WORKTREE=${XR_WORKTREE:-$(dirname "$MAIN")/VivecraftMod-openxr}
UA="VivecraftMod tools/steam-frame (github.com/$REPO)"
JQ=$(nix build --no-link --print-out-paths 'nixpkgs#jq^bin' | sed -n 1p)/bin/jq
PYTHON=$(nix build --no-link --print-out-paths 'nixpkgs#python3' | sed -n 1p)/bin/python3

# value of a key in gradle.properties of the release branch
xr_prop() {
    sed -n "s/^$1=//p" "$XR_WORKTREE/gradle.properties" | tr -d '\r'
}

# url, sha512, sha1 and size of the primary file of a Modrinth version
modrinth() {
    curl -fsS -A "$UA" "https://api.modrinth.com/v2/project/$1/version/${2//+/%2B}" |
        "$JQ" -r '[.files[] | select(.primary)][0] |
            .url + " " + .hashes.sha512 + " " + .hashes.sha1 + " " + (.size | tostring)'
}

rm -rf "$DIST"
mkdir -p "$DIST"

echo "== jar"
"$MAIN/tools/openxr/build-headset-jar.sh" --committed >"$DIST/.build.log" 2>&1 ||
    { tail -20 "$DIST/.build.log"; exit 1; }
rm -f "$DIST/.build.log"
MINECRAFT=$(xr_prop minecraft_version)
MOD_VERSION=$(xr_prop mod_version)
case "$MOD_VERSION" in
*-tabletop[0-9]*) ;;
*)
    echo "mod_version of tabletop-openxr is $MOD_VERSION, commit a release version like 1.3.15-tabletop1 there" >&2
    exit 1
    ;;
esac
VERSION=$MINECRAFT-$MOD_VERSION
RELEASE_URL=https://github.com/$REPO/releases/download/$VERSION
JAR=vivecraft-$VERSION-fabric.jar
MRPACK=minecraft-tabletop-$VERSION.mrpack
ICON=minecraft-tabletop-icon.png
cp "$XR_WORKTREE/build/stable/vivecraft-tabletop-stable.jar" "$DIST/$JAR"
cp "$MAIN/common/src/main/resources/vivecraft_icon.png" "$DIST/$ICON"
JAR_VERSION=$("$PYTHON" -c 'import json, sys, zipfile
print(json.load(zipfile.ZipFile(sys.argv[1]).open("fabric.mod.json"))["version"])' "$DIST/$JAR")
[ "$JAR_VERSION" = "$VERSION" ] || { echo "the jar is version $JAR_VERSION, not $VERSION" >&2; exit 1; }
echo "$VERSION"

echo "== versions"
FABRIC_LOADER=$(curl -fsS -A "$UA" https://meta.fabricmc.net/v2/versions/loader |
    "$JQ" -r '[.[] | select(.stable)][0].version')
read -r FABRIC_API_URL FABRIC_API_SHA512 FABRIC_API_SHA1 FABRIC_API_SIZE \
    <<<"$(modrinth fabric-api "$(sed -n 's/^fabric_api_version=//p' "$XR_WORKTREE/gradle.properties" | tr -d '\r')")"
SODIUM_VERSION=$(sed -n 's/.*gametestRuntimeOnly "maven.modrinth:sodium:\(.*\)"/\1/p' "$MAIN/fabric/build.gradle" |
    head -1)
read -r SODIUM_URL SODIUM_SHA512 SODIUM_SHA1 SODIUM_SIZE <<<"$(modrinth sodium "$SODIUM_VERSION")"
read -r PRISM_URL PRISM_SHA256 <<<"$(curl -fsS -A "$UA" \
    https://api.github.com/repos/PrismLauncher/PrismLauncher/releases/latest |
    "$JQ" -r '.assets[] | select(.name == "PrismLauncher-Linux-aarch64.AppImage") |
        .browser_download_url + " " + (.digest | sub("^sha256:"; ""))')"
for v in FABRIC_LOADER FABRIC_API_URL FABRIC_API_SHA512 FABRIC_API_SHA1 FABRIC_API_SIZE SODIUM_URL SODIUM_SHA512 \
    SODIUM_SHA1 SODIUM_SIZE PRISM_URL PRISM_SHA256; do
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
# the presets are multi line, they replace their whole placeholder line
"$PYTHON" - "$DIST/install-steam-frame.sh" "$TOOLS/defaults" <<'PYEOF'
import sys
path, defaults = sys.argv[1], sys.argv[2]
source = open(path).read()
for placeholder, name in (('@VIVECRAFT_CONFIG@', 'vivecraft-client-config.json'), ('@OPTIONS@', 'options.txt')):
    assert source.count(placeholder + '\n') == 1, placeholder
    source = source.replace(placeholder + '\n', open(f'{defaults}/{name}').read())
open(path, 'w').write(source)
PYEOF
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

echo "== modpack"
# the same mods and settings as the installer, for launchers that import Modrinth modpacks
VERSION=$VERSION MINECRAFT=$MINECRAFT FABRIC_LOADER=$FABRIC_LOADER RELEASE_URL=$RELEASE_URL JAR=$JAR \
    FABRIC_API_URL=$FABRIC_API_URL FABRIC_API_SHA512=$FABRIC_API_SHA512 FABRIC_API_SHA1=$FABRIC_API_SHA1 \
    FABRIC_API_SIZE=$FABRIC_API_SIZE SODIUM_URL=$SODIUM_URL SODIUM_SHA512=$SODIUM_SHA512 SODIUM_SHA1=$SODIUM_SHA1 \
    SODIUM_SIZE=$SODIUM_SIZE "$PYTHON" - "$DIST" "$TOOLS/defaults" "$MRPACK" <<'PYEOF'
import hashlib, json, os, sys, zipfile
from urllib.parse import unquote
dist, defaults, mrpack = sys.argv[1:4]
env = os.environ

def mod(url, sha1, sha512, size, client, server):
    return {
        'path': 'mods/' + unquote(url.rsplit('/', 1)[1]),
        'hashes': {'sha1': sha1, 'sha512': sha512},
        'env': {'client': client, 'server': server},
        'downloads': [url],
        'fileSize': int(size),
    }

jar = open(f"{dist}/{env['JAR']}", 'rb').read()
index = {
    'formatVersion': 1,
    'game': 'minecraft',
    'versionId': env['VERSION'],
    'name': 'Minecraft Tabletop',
    'summary': "Minecraft with Vivecraft's tabletop mode, set up for the Steam Frame (unofficial)",
    'files': [
        mod(env['FABRIC_API_URL'], env['FABRIC_API_SHA1'], env['FABRIC_API_SHA512'], env['FABRIC_API_SIZE'],
            'required', 'required'),
        mod(env['SODIUM_URL'], env['SODIUM_SHA1'], env['SODIUM_SHA512'], env['SODIUM_SIZE'], 'required', 'unsupported'),
        mod(f"{env['RELEASE_URL']}/{env['JAR']}", hashlib.sha1(jar).hexdigest(), hashlib.sha512(jar).hexdigest(),
            len(jar), 'required', 'required'),
    ],
    'dependencies': {'minecraft': env['MINECRAFT'], 'fabric-loader': env['FABRIC_LOADER']},
}
with zipfile.ZipFile(f'{dist}/{mrpack}', 'w', zipfile.ZIP_DEFLATED) as pack:
    pack.writestr('modrinth.index.json', json.dumps(index, indent=2) + '\n')
    pack.write(f'{defaults}/vivecraft-client-config.json', 'overrides/config/vivecraft-client-config.json')
    pack.write(f'{defaults}/options.txt', 'overrides/options.txt')
PYEOF

echo "== notes and checksums"
sed -e "s|@RELEASE@|$VERSION|g" -e "s|@REPO@|$REPO|g" -e "s|@MINECRAFT@|$MINECRAFT|g" -e "s|@JAR@|$JAR|g" \
    -e "s|@MRPACK@|$MRPACK|g" -e "s|@FABRIC_LOADER@|$FABRIC_LOADER|g" \
    "$TOOLS/release-notes.md" >"$DIST/release-notes.md"
if grep -n '@[A-Z_0-9]*@' "$DIST/release-notes.md"; then
    echo "placeholders left in the release notes" >&2
    exit 1
fi
(cd "$DIST" && sha256sum -- "$JAR" "$MRPACK" install-steam-frame.sh "Install Minecraft Tabletop.desktop" "$ICON" \
    >SHA256SUMS)
echo "== $DIST"
ls -la "$DIST"
