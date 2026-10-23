#!/usr/bin/env bash
# Runs the Fabric dev client on its own Xvfb display, so the desktop is never touched.
# The client uses NullVR in seated mode by default, see fabric/runs/client/config.
# env: DISP (default :77), SIZE (default 1280x720x24), TASK (default :fabric:runClient)
set -euo pipefail
DISP=${DISP:-:77}
SIZE=${SIZE:-1280x720x24}
TASK=${TASK:-:fabric:runClient}
ROOT=$(git -C "$(dirname "$0")" rev-parse --show-toplevel)

# native libraries that LWJGL, GLFW and OpenAL load at runtime
LD=/run/opengl-driver/lib
for p in libx11 libxcursor libxrandr libxi libxxf86vm libxext libxrender libpulseaudio alsa-lib wayland \
    libxkbcommon vulkan-loader systemdLibs libGL; do
    LD="$LD:$(nix build --no-link --print-out-paths "nixpkgs#$p" | head -1)/lib"
done
export LD_LIBRARY_PATH=$LD

# isolated X server
mkdir -p "$ROOT/build"
if ! [ -e "/tmp/.X11-unix/X${DISP#:}" ]; then
    XVFB=$(nix build --no-link --print-out-paths nixpkgs#xvfb | head -1)
    "$XVFB/bin/Xvfb" "$DISP" -screen 0 "$SIZE" +extension GLX -nolisten tcp >"$ROOT/build/xvfb.log" 2>&1 &
    for _ in $(seq 50); do [ -e "/tmp/.X11-unix/X${DISP#:}" ] && break; sleep 0.1; done
fi
export DISPLAY=$DISP
unset WAYLAND_DISPLAY

# default dev client config: NullVR, seated, no first run screens
RUN=$ROOT/fabric/runs/client
mkdir -p "$RUN/config"
[ -f "$RUN/config/vivecraft-client-config.json" ] ||
    echo '{"stereoProviderPluginID":"NULLVR","vrEnabled":"true","seated":"true"}' >"$RUN/config/vivecraft-client-config.json"
[ -f "$RUN/options.txt" ] ||
    printf 'onboardAccessibility:false\nskipMultiplayerWarning:true\njoinedFirstServer:true\npauseOnLostFocus:false\n' >"$RUN/options.txt"

exec "$ROOT/tools/nix-gradle.sh" "$TASK" --console=plain "$@"
