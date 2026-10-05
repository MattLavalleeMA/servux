Servux-Paper
==============
An unofficial PaperMC port of [Servux](https://github.com/sakura-ryoko/servux)'s MiniHUD server-side support.

> **Unofficial fork.** This fork is maintained largely for personal use and is public so others can benefit.
> I make every effort to keep it current with upstream Servux, but it is **not endorsed or supported by
> Sakura Ryoko or masa**. Please don't report problems with this fork to the upstream Servux project.

Servux is a server-side mod that provides extra support/features for some client-side mods when playing on a server.
This fork adds a Paper plugin (in [`paper/`](paper/)) that serves the same MiniHUD channels from Paper (Bukkit) servers.
The upstream Fabric mod is still built from the repository root and is kept as close to upstream as possible.

**Servux itself is never needed on the clients or in single player**,
it's only needed/useful on the dedicated server side in multiplayer.

What the Paper plugin provides
==============================
* **`hud_data`**: spawn position, weather timers, world seed, TPS/mob-cap data loggers, and the recipe manager.
* **`structures`**: structure bounding boxes (Witch Huts, Ocean Monuments, Nether Fortresses, etc.).
* **`entity_data`**: entity and block entity NBT data.

The Litematica (`litematics`) and Tweakeroo (`tweaks`) channels, and upstream's gameplay tweaks
(e.g. stackable shulker boxes, rail/stair mirror fixes), are not ported.

Installing
==========
* Download `servux-paper-<minecraft>-<servux version>.jar` for your Minecraft version from
  [Releases](https://github.com/MattLavalleeMA/servux/releases) and put it in your server's `plugins/` folder.
* Requires Paper (Folia is not supported) and Java 25.
* Works with ViaVersion/ViaBackwards: if ViaVersion is installed, MiniHUD clients on older Minecraft
  versions are told the server matches their version (MiniHUD otherwise rejects a server on a different version).
* Settings are in `plugins/ServuxPaper/config.yml`. Seed sharing, weather status, and data loggers are off by default.
  Apply changes with `/servux reload`.
* Access is controlled with standard Bukkit permission nodes (`servux.hud_data`, `servux.structures`,
  `servux.entity_data`, ...), listed in [`plugin.yml`](paper/src/main/resources/plugin.yml).
  The seed and other players' inventories are op-only by default.

Branches
========
Branch names follow upstream's convention: the current Minecraft version is the default branch, older versions live under `LTS/`.

| Branch | Minecraft | Tracks upstream |
|---|---|---|
| `26.3-PaperMC` (default) | 26.3 | `26.3` |
| `LTS/26.2-PaperMC` | 26.2 | `LTS/26.2` |
| `LTS/26.1-PaperMC` | 26.1.2 | `LTS/26.1` |

Compiling
=========
* Clone the repository
* Open a command prompt/terminal to the repository directory
* Run `gradlew build` to build both the Fabric mod and the Paper plugin, or `gradlew :paper:build` for the Paper plugin only
* The Paper plugin jar will be in `paper/build/libs/`; the Fabric mod jar will be in `build/libs/`

See [`paper/ARCHITECTURE.md`](paper/ARCHITECTURE.md) and [`paper/IMPLEMENTATION.md`](paper/IMPLEMENTATION.md) for how the port is put together.

Credits & License
=================
Servux is created by masa and maintained by Sakura Ryoko. This fork is licensed under the same
GNU LGPL v3 as upstream; see [LICENSE.txt](LICENSE.txt).
