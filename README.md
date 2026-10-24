> [!NOTE]
> **This is an unofficial fork** that adds a **tabletop mode** to Vivecraft: the world sits as a small diorama on a
> table in your real room, seen through the headset's passthrough, like the Minecraft HoloLens demo. It is made for the
> **Valve Steam Frame** and is not made or supported by the Vivecraft team.
>
> **Steam Frame:** download `Install Minecraft Tabletop.desktop` from the
> [latest release](https://github.com/jonnyczi/VivecraftMod/releases/latest) in desktop mode and double click it, the
> release page explains the details. The release also has the jar and a Modrinth modpack for installing it yourself.
> The code is on the [`tabletop`](https://github.com/jonnyczi/VivecraftMod/tree/tabletop) branch, the headset builds
> come from [`tabletop-openxr`](https://github.com/jonnyczi/VivecraftMod/tree/tabletop-openxr), which adds the
> unmerged OpenXR provider.

# Vivecraft Mod (of Vivecraft fabric)
A port of [Vivecraft](https://www.vivecraft.org) to Fabric/Quilt and back to Forge/Neoforge.\
This is an official continuation of the project, using mixin's instead of patches.\
This means we could use a multiloader setup to develop on Fabric, Forge, Neoforge and Quilt at the same time.\
It also removed Optifine as a dependency, so mod compatibility *should* be better as well.

Curseforge: [![DOWNLOAD](https://cf.way2muchnoise.eu/title/667903.svg)](https://www.curseforge.com/minecraft/mc-mods/vivecraft)\
Modrinth: [![Modrinth](https://img.shields.io/modrinth/dt/vivecraft)](https://modrinth.com/mod/vivecraft)

Since this version no longer ships with Optifine, we recommend you use Sodium (and the rest of the family) on Fabric/Quilt/Neoforge (and the unofficial ports on Forge) to maximize performance. For shaders you can use Iris on Fabric/Quilt/Neoforge (and the unofficial ports on Forge).
If you want, you can still use Optifine though. There is built in compatibility for it.

## Mod compatibility
Mod compatibility is important to us. If you find any issues, don't be afraid to open an issue. Be sure to check if it isn't already known though, so we don't have duplicates.

## Current status
- [x] Loaded in game
- [x] Moving in game
- [x] Playable (with some bugs and missing features)
- [x] Fully playable
- [x] Sodium support
- [X] Iris support
- [X] In game VR switching
- [X] API, details [here](https://github.com/Vivecraft/VivecraftMod/wiki/Mod-API)
- [ ] OpenXR support

[Vivecraft Discord server](https://discord.gg/2x3QCk8qa9)\
[Development Discord server](https://discord.gg/jYyyv7zhSW)
