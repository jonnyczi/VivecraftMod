Minecraft Java Edition @MINECRAFT@ in VR on the **Steam Frame**, with a **tabletop mode**: the world sits as a small
diorama on a table in your real room, like the Minecraft HoloLens demo. Walk around it, scroll it, and build and mine
with a laser from your hands, in survival and creative.

This is an unofficial, experimental build of [Vivecraft](https://github.com/Vivecraft/VivecraftMod), not made or
supported by the Vivecraft team, Valve, Mojang or Microsoft. You need to own Minecraft: Java Edition.

## Install

1. On the Steam Frame, switch to **Desktop Mode**.
2. Download **`Install Minecraft Tabletop.desktop`** from the assets below, with Firefox or Chromium.
3. Open your Downloads folder in Dolphin and double click the file. Choose **Execute** or **Continue** if asked.
4. A terminal shows the progress. Prism Launcher opens once: sign in with the Microsoft account that owns Minecraft,
   then close Prism Launcher.
5. Switch back to **Game Mode**. "Minecraft Tabletop" is in your library under Non-Steam.

The first start downloads Minecraft and Java, which takes a while, and may ask you to restart the game once for VR.

Instead of the .desktop file you can also run this in Konsole:

```
curl -fsSL https://github.com/@REPO@/releases/latest/download/install-steam-frame.sh | bash
```

Everything goes into `~/.local/share/minecraft-tabletop`, with its own Prism Launcher, so other Minecraft installs aren't
changed. It also downloads Prism Launcher, Fabric API and Sodium from their official sources.

## Playing

- Open the radial menu and choose **Switch to Tabletop** to enter or leave the tabletop mode.
- Point at blocks with your right hand, the laser shows what you'll break or place.
- Hold a **grip** to drag the world around inside the table, hold **both grips** to move, turn and resize the whole
  table.
- The left stick still walks your player, the world scrolls along when you get close to the edge.
- VR Settings has a **Tabletop** page for the size and the number of chunks shown.
- In multiplayer, tabletop reach needs `tabletopAllowed = true` in the server's `vivecraft-server-config.toml`.

## Update and uninstall

Run the installer again to update, your worlds, settings and login are kept.
To uninstall, delete `~/.local/share/minecraft-tabletop` and remove "Minecraft Tabletop" from your Steam library.

## Known issues

- On the Steam Frame controllers, A and clicking the left stick both jump and place blocks.
- Shader packs don't work in tabletop mode.

Source: the [`tabletop-openxr`](https://github.com/@REPO@/tree/tabletop-openxr) branch, Vivecraft is licensed under
LGPLv3.
