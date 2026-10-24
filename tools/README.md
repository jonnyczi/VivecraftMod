# Development tools

Helpers for developing the tabletop mode on NixOS and testing it on a Steam Frame. They aren't part of the mod.

- `nix-gradle.sh` runs gradle with JDK 25 from nixpkgs, `-C <dir>` runs it in another checkout.
- `run-client.sh` runs the dev client on its own Xvfb display with NullVR.
- `run-gametests.sh` runs the Fabric client gametests the same way, add `-PgametestSodium`, `-PgametestIris` or `-PgametestVulkan`
  to run them with Sodium, Iris (with Sodium) or the Vulkan backend.

## Multiplayer dev server (`server/`)

A dedicated Fabric server in `run/dev-server` (gitignored) for testing with the headset and extra players on this
laptop. It uses the Vivecraft jar of this checkout, build it first with `tools/nix-gradle.sh :fabric:assemble`, it ends
up in `build/libs/vivecraft-<version>-fabric.jar`.

- `server/setup-dev-server.sh` downloads the Fabric server launcher and Fabric API in the versions of
  `gradle.properties` and writes `server.properties` (`online-mode=false`, so offline laptop players can join next to
  the real account on the headset), `eula.txt` and a Vivecraft server config with `debug` and the join messages on.
  It is safe to run again, `run-dev-server.sh` runs it when the server is missing.
- `server/run-dev-server.sh [jar]` copies the newest built jar (or the given one) into `mods/` and starts the server
  in this terminal. It prints the address for the headset, `<hostname>.lan` (or the LAN IP). Type `op <name>` in the
  console for operator rights, `stop` to stop it.
- `server/run-dev-server.sh --background [jar]` starts it detached and waits until it's up, the console goes to
  `run/dev-server/console.log`. `server/dev-server-cmd.sh <command>` runs a console command, e.g. `op Steve`, and
  `server/stop-dev-server.sh` stops it, that one also works for a server in the foreground of another terminal.
- `server/run-player.sh [--vr] [--xvfb] <name>` starts another player on this laptop that joins the dev server right
  away, flat 2D by default, `--vr` uses NullVR in seated mode. Every name gets its own game dir in
  `run/dev-players/<name>`. It is the Loom dev client, but started without gradle and loaded with the same jar as the
  server, so rebuilding doesn't affect a running player. The client classpath is cached in `run/dev-players/.launch`
  by `server/client-launch.init.gradle`, `--refresh` updates it. `--xvfb` runs it on a hidden display without sound,
  `--server host:port` joins another server.

The laptop's firewall is off (`networking.firewall.enable = false` in the NixOS config). If it ever gets enabled, add
25565 to `networking.firewall.allowedTCPPorts`, or open it until the firewall restarts with
`sudo nixos-firewall-tool open tcp 25565`.

## Steam Frame release (`steam-frame/`)

An installer for other Steam Frame owners, published as a GitHub release of the fork, so they don't need this
repository. It is run in desktop mode on the Frame and installs everything into `~/.local/share/minecraft-tabletop`,
with its own Prism Launcher: Fabric, Fabric API and Sodium from their official sources, the headset jar from the
release, the Frame settings and a Steam shortcut. Prism opens once for the Microsoft login.

- Release versions follow upstream's with a suffix, like `26.2-1.3.15-tabletop1`. For a new release, commit
  `mod_version=<upstream version>-tabletop<n>` on the `tabletop-openxr` branch (only there, so `tabletop` keeps
  upstream's version for the pull requests).
- `steam-frame/make-release.sh` builds the release files in `build/steam-frame-release`: the jar from the committed
  `tabletop-openxr` branch, `install-steam-frame.sh` with all versions and checksums filled in, the
  `Install-Minecraft-Tabletop.desktop` file that players double click, a Modrinth modpack with the same mods and
  settings, the icon, `SHA256SUMS` and the release notes from `steam-frame/release-notes.md`.
- `steam-frame/defaults/` has the Steam Frame settings that the installer and the modpack start with.
- `steam-frame/install.sh` is the installer template. For testing it reads `INSTALL_DIR`, `SHORTCUT_NAME`, `ASSET_DIR`
  (local release files instead of the release page), `NO_SHORTCUT=1` and `NO_LOGIN=1`.
- Publish with `gh release create <version> --target <tabletop-openxr commit> --notes-file release-notes.md` and the
  other files, as a full release, so the `releases/latest` links of the installer work.

## Headset builds (`openxr/`)

The Steam Frame needs fayer3's OpenXR provider, which isn't merged yet. The `tabletop-openxr` branch is
`origin/openxr` with `tabletop` merged in, plus one commit with OpenXR only changes (passthrough blend mode, grip
bindings and input fixes).

- `openxr/build-headset-jar.sh` merges new `tabletop` commits into `tabletop-openxr` in the worktree
  `../VivecraftMod-openxr`, applies the uncommitted changes of this checkout on top and builds the jar.
  `openxr/resolve_conflicts.py` resolves the known conflicts with the OpenXR branch.
- `openxr/deploy-to-frame.sh [--launch]` installs that jar into the Prism instance on the headset over ssh, it never
  stops a running game. `openxr/frame-launch.sh` is the launcher it puts on the headset.
- `openxr/setup-stable-instance.sh` keeps a separate stable Prism instance on the headset, listed in the Steam library
  as "Minecraft Tabletop". Build it with `openxr/build-headset-jar.sh --committed`. The first run copies the dev
  instance with its worlds, later runs only replace the jar, so the stable worlds are never touched by dev builds.
