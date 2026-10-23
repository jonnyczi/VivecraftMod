#!/usr/bin/env bash
# Builds the headset jar: the tabletop-openxr branch (fayer3's OpenXR branch + tabletop + OpenXR only fixes),
# plus the uncommitted changes of this checkout. Works in a separate worktree, this checkout is never changed.
#  1. merges new tabletop commits into tabletop-openxr, resolving the known conflicts
#  2. applies the uncommitted changes and untracked files of this checkout on top, without committing them
#  3. builds, the jar ends up in $XR_WORKTREE/build/libs
# usage: tools/openxr/build-headset-jar.sh [--committed]
#   --committed skips step 2 and keeps a copy of the jar as build/stable/vivecraft-tabletop-stable.jar,
#   for the stable instance on the headset
# env: XR_WORKTREE (default ../VivecraftMod-openxr next to this checkout), XR_BRANCH (default tabletop-openxr),
#      SOURCE_BRANCH (default tabletop)
set -euo pipefail
COMMITTED=false
if [ "${1:-}" = "--committed" ]; then
    COMMITTED=true
fi
TOOLS=$(cd "$(dirname "$0")" && pwd)
MAIN=$(git -C "$TOOLS" rev-parse --show-toplevel)
XR_WORKTREE=${XR_WORKTREE:-$(dirname "$MAIN")/VivecraftMod-openxr}
XR_BRANCH=${XR_BRANCH:-tabletop-openxr}
SOURCE_BRANCH=${SOURCE_BRANCH:-tabletop}

if [ ! -d "$XR_WORKTREE" ]; then
    git -C "$MAIN" worktree add "$XR_WORKTREE" "$XR_BRANCH"
    cp "$MAIN/local.properties" "$XR_WORKTREE/" 2>/dev/null || true
fi
cd "$XR_WORKTREE"
[ "$(git rev-parse --abbrev-ref HEAD)" = "$XR_BRANCH" ] || { echo "$XR_WORKTREE is not on $XR_BRANCH"; exit 1; }

# undo the uncommitted changes of the last build, keeps ignored files like local.properties and the build cache
git reset -q --hard HEAD
git clean -fdq

# renamed classes and their packages on the openxr branch
fix_names() {
    local files
    files=$(grep -rl "org.vivecraft.client_vr.provider.ControllerType;\|org.vivecraft.client_vr.provider.openvr_lwjgl.VRInputAction;" \
        common/src fabric/src 2>/dev/null || true)
    [ -n "$files" ] || return 0
    echo "$files" | xargs sed -i \
        -e "s/org.vivecraft.client_vr.provider.ControllerType;/org.vivecraft.client_vr.provider.control.ControllerType;/" \
        -e "s/org.vivecraft.client_vr.provider.openvr_lwjgl.VRInputAction;/org.vivecraft.client_vr.provider.control.InputAction;/" \
        -e "s/\bVRInputAction action = /InputAction action = /"
}

resolve() {
    for f in $(git diff --name-only --diff-filter=U); do
        nix shell nixpkgs#python3 -c python3 "$TOOLS/resolve_conflicts.py" "$f"
        git add "$f"
    done
    if git diff --name-only --diff-filter=U | grep .; then
        echo "conflicts above need to be resolved by hand"
        exit 2
    fi
}

# 1. new tabletop commits
if ! git merge-base --is-ancestor "$SOURCE_BRANCH" HEAD; then
    git -c rerere.enabled=true -c rerere.autoUpdate=true merge --no-edit "$SOURCE_BRANCH" || true
    resolve
    fix_names
    git add -A
    git -c rerere.enabled=true commit -q --no-edit
    echo "merged $SOURCE_BRANCH into $XR_BRANCH"
fi

if [ "$COMMITTED" = false ]; then
    # 2. uncommitted changes, the openxr branch has no openvr_lwjgl/VRInputAction, its InputAction is changed separately
    out=$( (cd "$MAIN" && git diff HEAD) | git apply -3 \
        --exclude=common/src/main/java/org/vivecraft/client_vr/provider/openvr_lwjgl/VRInputAction.java 2>&1 ) || true
    echo "$out" | grep -q "^error:" && { echo "$out"; echo "applying the uncommitted changes failed"; exit 3; }
    (cd "$MAIN" && git ls-files --others --exclude-standard) | while read -r f; do
        mkdir -p "$(dirname "$f")"
        cp "$MAIN/$f" "$f"
    done
    resolve
    fix_names
fi

# 3. build
"$MAIN/tools/nix-gradle.sh" -C "$XR_WORKTREE" :fabric:build --console=plain | grep -E "error:|BUILD" -A3
ls -la "$XR_WORKTREE"/build/libs/*-fabric.jar
if [ "$COMMITTED" = true ]; then
    mkdir -p "$XR_WORKTREE/build/stable"
    cp "$XR_WORKTREE"/build/libs/*-fabric.jar "$XR_WORKTREE/build/stable/vivecraft-tabletop-stable.jar"
    ls -la "$XR_WORKTREE/build/stable/vivecraft-tabletop-stable.jar"
fi
