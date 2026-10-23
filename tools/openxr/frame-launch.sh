#!/bin/sh
# Runs on the Steam Frame: starts the Prism instance in the headset gaming session, without a terminal.
# Installed to ~/vivecraft-tools/launch.sh by deploy-to-frame.sh.
INSTANCE=${1:-vivecraft-tabletop-26.2}
APPIMAGE=${PRISM_APPIMAGE:-$HOME/Desktop/PrismLauncher-Linux-aarch64.AppImage}
export DISPLAY=:0 XDG_RUNTIME_DIR=/run/user/$(id -u) DBUS_SESSION_BUS_ADDRESS=unix:path=/run/user/$(id -u)/bus
exec "$APPIMAGE" --launch "$INSTANCE" >"$HOME/vivecraft-tools/prism.log" 2>&1 </dev/null
