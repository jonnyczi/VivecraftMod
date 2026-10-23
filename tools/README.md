# Development tools

Helpers for developing the tabletop mode on NixOS and testing it on a Steam Frame. They aren't part of the mod.

- `nix-gradle.sh` runs gradle with JDK 25 from nixpkgs, `-C <dir>` runs it in another checkout.
- `run-client.sh` runs the dev client on its own Xvfb display with NullVR.
- `run-gametests.sh` runs the Fabric client gametests the same way, add `-PgametestSodium` or `-PgametestVulkan`
  to run them with Sodium or the Vulkan backend.

## Headset builds (`openxr/`)

The Steam Frame needs fayer3's OpenXR provider, which isn't merged yet. The `tabletop-openxr` branch is
`origin/openxr` with `tabletop` merged in, plus one commit with OpenXR only changes (passthrough blend mode, grip
bindings and input fixes).

- `openxr/build-headset-jar.sh` merges new `tabletop` commits into `tabletop-openxr` in the worktree
  `../VivecraftMod-openxr`, applies the uncommitted changes of this checkout on top and builds the jar.
  `openxr/resolve_conflicts.py` resolves the known conflicts with the OpenXR branch.
- `openxr/deploy-to-frame.sh [--launch]` installs that jar into the Prism instance on the headset over ssh, it never
  stops a running game. `openxr/frame-launch.sh` is the launcher it puts on the headset.
