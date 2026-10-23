#!/usr/bin/env bash
# Runs the Fabric client gametests on an Xvfb display, screenshots end up in fabric/build/run/clientGameTest/screenshots.
# Extra gradle arguments are passed on, -PgametestSodium adds sodium, -PgametestVulkan uses the vulkan backend.
exec env TASK=:fabric:runClientGameTest "$(dirname "$0")/run-client.sh" "$@"
