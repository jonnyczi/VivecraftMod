#!/usr/bin/env bash
# Runs the gradle wrapper with JDK 25 from nixpkgs. On NixOS the JDKs that the foojay toolchain resolver
# downloads don't run, so automatic JDK downloads are turned off and this JDK is used instead.
# usage: tools/nix-gradle.sh [-C <checkout>] <gradle arguments>
set -euo pipefail
DIR=$(git -C "$(dirname "$0")" rev-parse --show-toplevel)
if [ "${1:-}" = "-C" ]; then
    DIR=$2
    shift 2
fi
JDK=$(nix build --no-link --print-out-paths nixpkgs#jdk25 | head -1)
export JAVA_HOME=$(dirname "$(dirname "$(readlink -f "$JDK/bin/java")")")
cd "$DIR"
exec ./gradlew -Porg.gradle.java.installations.auto-download=false \
    -Porg.gradle.java.installations.fromEnv=JAVA_HOME "$@"
