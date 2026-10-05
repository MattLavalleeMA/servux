# Servux Architecture Analysis

_Originally persisted from planning-session memory; the Fabric-side sections describe upstream Servux as of the branch this file lives on. See also [IMPLEMENTATION.md](IMPLEMENTATION.md) in this folder for the Fabric → Paper equivalency guide._

## Project Info
- **Mod**: Servux (Server-side companion for MiniHUD client mod)
- **Type**: Fabric mod (root project) + Paper plugin (`paper/` subproject)
- **Minecraft Version**: 26.3 (Java 25)
- **Mod Version**: 0.12.2 (from root `gradle.properties`; the Paper jar is versioned `<mc>-<mod_version>`, e.g. `servux-paper-26.3-0.12.2.jar`)
- **Paper dev bundle**: `26.3.build.152-beta` (`paper/gradle.properties`) — Paper had no stable 26.3 build at the time of the port
- **Main Package**: `fi.dy.masa.servux` (Paper plugin: `fi.dy.masa.servux.paper`)

### Branches (fork)
Mirrors upstream's convention: the current Minecraft version lives on `<version>-PaperMC`, previous versions on `LTS/<version>-PaperMC`.
- `26.3-PaperMC` — current; based on upstream `26.3`
- `LTS/26.2-PaperMC` — based on upstream `LTS/26.2`
- `LTS/26.1-PaperMC` — based on upstream `LTS/26.1`

## Architecture Overview

### Top-Level Packages
1. **commands/** - Command registration and handling
2. **dataproviders/** - Server data gathering/exposure (HUD, structures, entities, litematics, tweaks)
3. **event/** - Event handlers (ServerHandler, PlayerHandler, ServerInitHandler)
4. **interfaces/** - Interface definitions for handlers and managers
5. **loggers/** - Data logging functionality
6. **mixin/** - Bytecode injection hooks into Minecraft
7. **network/** - Custom packet payload system
8. **scheduler/** - Task scheduling
9. **schematic/** - Schematic handling (litematics format)
10. **settings/** - Configuration/settings system
11. **util/** - Utility classes

## Network Protocol (Core)

### Packet Channels (5 total)
All use Fabric Networking API v1 with custom payload types:

1. **HUD Metadata** (`servux:hud_metadata`)
   - Channel ID: `servux:hud_metadata`
   - Protocol Version: 3
   - Handler: `ServuxHudHandler<ServuxHudPacket.Payload>`
   - Data: Spawn position, weather, recipe manager, seed, data loggers
   - Payload Type: `CustomPacketPayload.Type<ServuxHudPacket.Payload>`

2. **Structures** (`servux:structures`)
   - Channel ID: `servux:structures`
   - Protocol Version: 3
   - Handler: `ServuxStructuresHandler<ServuxStructuresPacket.Payload>`
   - Data: Structure bounding boxes (Witch Huts, Ocean Monuments, etc.)
   - Uses timeout-based chunk tracking

3. **Entities** (`servux:entity_data`)
   - Channel ID: `servux:entity_data`
   - Protocol Version: 2
   - Handler: `ServuxEntitiesHandler<ServuxEntitiesPacket.Payload>`
   - Data: Entity NBT data, block entity data, player inventory

4. **Litematics** (`servux:litematics`)
   - Channel ID: `servux:litematics`
   - Protocol Version: 2
   - Handler: `ServuxLitematicaHandler<ServuxLitematicaPacket.Payload>`
   - Data: Schematic paste operations and validation

5. **Tweaks** (`servux:tweaks`)
   - Channel ID: `servux:tweaks`
   - Protocol Version: 2
   - Handler: `ServuxTweaksHandler<ServuxTweaksPacket.Payload>`
   - Data: Stackable shulker box settings and fixes

### Network API Architecture

- **IPluginServerPlayHandler<T>**: Interface extending `ServerPlayNetworking.PlayPayloadHandler<T>`
  - Registers payloads via `PayloadTypeRegistry.serverboundPlay()` and `PayloadTypeRegistry.clientboundPlay()`
  - Registers receivers via `ServerPlayNetworking.registerGlobalReceiver()`
  - Handles both C2S and S2C packet directions

- **ServerPlayHandler<T>**: Singleton that manages multiple handlers
  - Uses `ArrayListMultimap<Identifier, IPluginServerPlayHandler<T>>`
  - Coordinates registration/unregistration of all packet handlers

- **IServerPayloadData**: Common interface for packet encoding/decoding
  - `getVersion()`: Protocol version
  - `getPacketType()`: Packet type enum value
  - `getTotalSize()`: Byte allocation
  - `isEmpty()`: Check if packet has data
  - `fromPacket(FriendlyByteBuf)`: Decode from buffer
  - `toPacket(FriendlyByteBuf)`: Encode to buffer
  - `clear()`: Reset/cleanup

- **PacketSplitter**: Handles large NBT payloads that exceed MTU
  - Splits large data across multiple packets

## Data Providers

All extend `DataProviderBase` and implement `IDataProvider`:

1. **HudDataProvider**
   - Sends metadata about server (Servux version, MC version)
   - Spawn position (dimension + coordinates)
   - World seed (permission-gated)
   - Weather state tracking
   - Recipe manager data
   - Data logger framework
   - Tick-based updates (configurable interval)

2. **StructureDataProvider**
   - Exposes structure bounding boxes from world
   - Uses `ChunkPos` and `StructureStart` from Minecraft's structure system
   - Supports blacklist/whitelist filtering
   - Chunk loading detection via mixin
   - Timeout-based caching (600-1200 ticks)

3. **EntitiesDataProvider**
   - Entity NBT data queries
   - Block entity (tile entity) data
   - Player inventory data (ender items, main inventory)
   - Permission-based access control
   - Entity fixing (Allay gathering fix)

4. **LitematicsDataProvider**
   - Schematic paste operations
   - `SchematicPlacement` management
   - `SchematicBufferManager` for transmitting schematics
   - Block rotation/mirror fixes for rail and chest blocks
   - Layer-based pasting

5. **TweaksDataProvider**
   - Stackable shulker boxes (size 1-99)
   - Configuration management
   - Item stack data manipulation

6. **ServuxConfigProvider**
   - Global mod configuration
   - Debug mode setting

## Mixins (26 total)

Registered in `src/main/resources/mixins.servux.json` (package `fi.dy.masa.servux.mixin`). Names below reflect the 26.3 Mojang-mapped naming; older docs used Yarn-style names (e.g. `MixinPlayerManager`, `MixinServerWorld`).

### Block Mixins (5)
- **MixinBlock_updateSuppression**: `Block.popResource()` hook for update suppression
- **MixinChestBlock**: Fixes mirror placement of double chests (Litematics feature)
- **MixinHopperBlockEntity**: Stackable shulker box handling in hopper fullness/merge checks
- **MixinRailBlocks**: Fixes 180° rotation of straight rails (RailBlock, DetectorRailBlock, PoweredRailBlock)
- **MixinStairBlock**: Fixes mirror placement of stairs

### Entity Mixins (3)
- **MixinAllay**: Forces Allay to gather items (wraps the gamerule check in `wantsToPickUp()`)
- **MixinItemEntity**: Wraps the gamerule check in `hurtServer()`
- **MixinMob**: Allay gathering fix (wraps the gamerule check in `aiStep()`)

### Item Mixin (1)
- **IMixinItemInstance**: `ItemInstance.getMaxStackSize()` override for stackable shulker boxes

### Easy Place Mixins (2) — `mixin/easy_place/` (moved from `item/` and `network/` in 26.3)
- **MixinBlockItem_EasyPlace**: `BlockItem.getPlacementState()` hook for Litematica easy-place protocol
- **MixinServerGamePacketListenerImpl_easyPlace**: Removes hit position check for placement
  - Hooks: `ServerGamePacketListenerImpl.handleUseItemOn()`
  - Modifies: `Vec3.subtract()` invocation → returns ZERO

### NBT Mixins (2)
- **IMixinTagValueInput**: `@Accessor` into `TagValueInput` (NBT read view)
- **IMixinTagValueOutput**: `@Accessor` into `TagValueOutput` (NBT write view)

### Network Mixin (1)
- **MixinServerGamePacketListenerImpl_queryNbt**: Overrides NBT query permissions
  - Hooks: `ServerGamePacketListenerImpl.handleBlockEntityTagQuery()` and `handleEntityTagQuery()`
  - Replaces: `PermissionSet.hasPermission()` with Servux's own permission system

### Debug Mixin (1)
- **MixinSharedConstants**: DEBUG_ENABLED control

### Server Mixins (6)
- **IMixinServerTickRateManager**: Accessor for tick-rate state
- **MixinCommands**: Registers `/servux` commands
  - Hooks: `Commands.<init>()` after whitelist registration
  - Injects commands into brigadier dispatcher

- **MixinMain**: Captures the registry holder and root directory during `Main.main()`
- **MixinDedicatedServer**: Server init event (`DedicatedServer.<init>`)
- **MixinMinecraftServer**: Core server event hooks
  - `tickServer()`: Data provider tick updates
  - `prepareLevels()`: Spawn position capture
  - `runServer()`: Server starting/started events
  - `reloadResources()`: Resource reload pre/post events
  - `stopServer()`: Server stopping/stopped events

- **MixinPlayerList**: Player event hooks
  - `canPlayerLogin()`: Client connect event
  - `placeNewPlayer()`: Player join event
  - `respawn()`: Player respawn event
  - `op()`/`deop()`: Operator status change events
  - `remove()`: Player leave event

### World Mixins (5)
- **IMixinLevelTicks**: Accessor for world tick scheduling
- **MixinChunkMap**: Chunk load detection
  - Hooks: `ChunkMap.markChunkPendingToSend()` → calls `StructureDataProvider.onStartedWatchingChunk()`

- **MixinServerLevel**: Server world updates
  - `setRespawnData()`: Spawn position tracking
  - `advanceWeatherCycle()`: Weather state tracking

- **MixinLevel_updateSuppression**: Update suppression at the `Level` level
- **MixinLevelChunk_updateSuppression**: Update suppression in `LevelChunk.setBlockState()`

## Fabric API Dependencies

From `build.gradle` (`fabric_api_version` = 0.161.0+26.3; all modules are jar-in-jar `include`d):
- **fabric-api-base**
  - Base Fabric API module

- **fabric-networking-api-v1**
  - `PayloadTypeRegistry`: Register custom packet payloads (C2S and S2C)
  - `ServerPlayNetworking`: Register global receivers for packets
  - `ServerPlayNetworking.Context`: Context passed to packet handlers

- **fabric-permission-api-v1** (new in 26.3; replaces the Lucko `fabric-permissions-api` dependency, which is now commented out)
  - `PermissionsUtil.check()`/`require()` call `Entity#checkPermission(Identifier, PermissionLevel)`, falling back to the vanilla permission level
  - Nodes are sanitized to Identifier-safe characters and use `servux:<provider>` form, e.g. `servux:hud_data.data_provider`, `servux:hud_data.weather` (previously `servux.provider.hud_data`)
  - The Paper plugin does **not** follow this rename; it keeps its own Bukkit nodes (`servux.hud_data`, etc.) declared in `paper/src/main/resources/plugin.yml`

## Access Widener

Located in `src/main/resources/servux.accesswidener`:
```
mutable field net/minecraft/SharedConstants DEBUG_ENABLED Z
accessible field net/minecraft/world/level/NaturalSpawner MAGIC_NUMBER I
```
Provides access to otherwise private/final fields.

## Event System

### Server Lifecycle Events (via MixinMinecraftServer)
1. `onServerStarting()` - Before server initialization
2. `onServerStarted()` - After server initialization complete
3. `onServerResourceReloadPre()` - Before resource reload
4. `onServerResourceReloadPost()` - After resource reload
5. `onServerStopping()` - Before server stop
6. `onServerStopped()` - After server stop

### Player Events (via MixinPlayerList)
1. `onClientConnect()` - Client attempts connection
2. `onPlayerJoin()` - Player joins world
3. `onPlayerRespawn()` - Player respawns
4. `onPlayerOp()` - Player becomes operator
5. `onPlayerDeOp()` - Player loses operator status
6. `onPlayerLeave()` - Player disconnects

### World Events (via MixinServerLevel)
1. `HudDataProvider.setSpawnPos()` - Spawn position changes
2. `HudDataProvider.tickWeather()` - Weather ticks (every game tick)

### Chunk Events (via MixinChunkMap)
1. `onStartedWatchingChunk()` - Player begins seeing chunk
   - Used by StructureDataProvider to track structure visibility

## PaperMC Port Feasibility (MiniHUD protocol channels only)

**Scope**: Only `hud_data`, `structures`, and `entity_data` channels are relevant to a MiniHUD-focused port — these are the channels MiniHUD actually consumes per FEATURES.md. `litematics` and `tweaks` channels serve Litematica/Tweakeroo respectively and are out of scope.

**Verdict**: Feasible, no Fabric mixins required for this scope. Servux's MiniHUD-facing features are additive (new custom payload channels + data reads), not vanilla-behavior patches. MiniHUD client needs **zero changes**: custom payload/plugin-message channels are a transport-agnostic vanilla protocol mechanism — the same underlying packet is used by Fabric's `ServerPlayNetworking`, Bukkit's `Messenger`, and PacketEvents' `WrapperPlayServerPluginMessage`. Compatibility depends entirely on replicating Servux's exact `CompoundTag` NBT layout and `PacketSplitter` chunking scheme on the Paper side, not on which transport library sends it.

**Toolchain decisions**:
- NMS access: `paperweight-userdev` — Paper's official, Mojang-license-compliant Gradle toolchain, providing a Mojang-mapped dev environment with build-time remapping (analogous to Fabric Loom, but for direct NMS calls only — no bytecode-injection/Mixin equivalent).
- Packet interception: **none needed in the shipped implementation.** The original plan assumed **PacketEvents** for the chunk-watch trigger and the NBT-query permission override; the former turned out to be covered by Paper's public `PlayerChunkLoadEvent`, and the latter was sidestepped (see `entity_data` below). The three custom channels use Bukkit's `Messenger` directly. The PacketEvents dependency remains commented out in `paper/build.gradle`. ProtocolLib is not used.
- Target environment: standard Paper (not Folia) — no regionized-threading complications for tick-based data providers or main-thread NMS access.
- Current target: MC 26.3 via dev bundle `26.3.build.152-beta` (no stable Paper 26.3 build yet — move to a `-stable` build when one is published). 26.2 is maintained on `LTS/26.2-PaperMC` (`26.2.build.84-stable`). Originally prototyped against 26.1.2 (Paper Build 72).
- 26.3 port note: `Recipe.CODEC` is now a holder codec, so the recipe manager dump uses `Recipe.DIRECT_CODEC` (same change as upstream Fabric).

**Per-channel feasibility**:
- `hud_data` — High, implemented (see [IMPLEMENTATION.md](IMPLEMENTATION.md), "`hud_data` channel"). Spawn position, seed, weather, TPS all available via public Bukkit/Paper API. Weather's exact tick-countdown integers (not just booleans) are confirmed available via `World#getWeatherDuration()`/`getThunderDuration()`/`getClearWeatherDuration()` — no NMS dip needed. Recipe manager dump uses NMS (`ServerLevel#recipeAccess()`) + `Recipe.DIRECT_CODEC`; mixed-type lists are homogenized before sending because MaLiLib's `DataOps` doesn't unwrap vanilla's `{"": value}` list wrappers.
- `structures` — Medium-High, confirmed and implemented (see [IMPLEMENTATION.md](IMPLEMENTATION.md), "`structures` channel"). Structure bounding boxes need NMS access via `paperweight-userdev` (no clean Bukkit API exists for full per-chunk structure piece boxes — `org.bukkit.generator.structure.Structure` only supports nearest-structure search). The chunk-watch trigger (replacing `MixinChunkMap`) turned out **not** to need PacketEvents interception as originally assumed — Paper's public `io.papermc.paper.event.packet.PlayerChunkLoadEvent` covers it directly.
- `entity_data` — Medium-High, confirmed and implemented (see [IMPLEMENTATION.md](IMPLEMENTATION.md), "`entity_data` channel"). Entity/block-entity NBT dumps via NMS `Entity#saveWithoutId`/`BlockEntity#saveWithFullMetadata`; player inventory via `CraftItemStack.asNMSCopy`. The vanilla `NbtQuery` packet permission override (replacing `MixinServerGamePacketListenerImpl_queryNbt`) would need packet interception of `ServerboundEntityTagQuery`/`ServerboundBlockEntityTagQuery` — not implemented; sidestepped by routing all MiniHUD entity-data needs through Servux's own custom channel instead of vanilla's NbtQuery packets.

**Known technical wrinkle — NBT "View" abstraction** (resolved): Fabric's `IMixinTagValueInput`/`IMixinTagValueOutput` (Mixin `@Accessor`s) show that recent MC versions wrap NBT access behind `ValueInput`/`ValueOutput`-style Views rather than exposing raw `CompoundTag` at save/load call sites. Paper has no Mixin `@Accessor` equivalent, so `paper/.../util/NbtViewHelper.java` reads the private `TagValueOutput#output` field via plain Java reflection instead. Affects `entity_data` NBT dumps; re-verify the field name when bumping Minecraft versions.

**Non-protocol port aspects** (not required for MiniHUD wire compatibility, but relevant to a full plugin):
- Configuration: Servux uses its own JSON config (`servux.json`); a Paper plugin would use YAML config + Bukkit permission nodes (LuckPerms integrates natively with Bukkit/Paper, arguably simpler than on Fabric).
- Command registration: the `/servux` command → implemented via Paper's Brigadier-based command registration (`LifecycleEvents.COMMANDS`).
- Event registration: server/player lifecycle events (`ServerHandler`, `PlayerHandler`) → standard Bukkit event listeners (`PlayerJoinEvent`, etc.) instead of mixins; some fine-grained events (e.g. resource-reload pre/post) may need Paper-specific events or polling.

**Excluded from scope**: gameplay-behavior mixins tied to litematics/tweaks (rail rotation, chest mirror, stairs, Allay gathering fix) — these modify vanilla behavior rather than transmit protocol data, and would be low feasibility on Paper anyway (no mixin equivalent for direct AI/behavior overrides).

**Items left unverified at planning time** (since addressed by the implementation and the MiniHUD 0.40.7 protocol sync — kept for history): protocol version handshake semantics (`PROTOCOL_VERSION` per channel + `MAX_FAILURES=4` auto-unregister-after-failures logic) and exact MiniHUD client behavior on a version mismatch; weather exact timer fields; recipe manager NBT round-trip shape; the NBT View-unwrapping reflection approach described above.

## Multi-platform port architecture
**Repo structure before the port**: `settings.gradle` had no `include` statements (single-project build) — `build.gradle` applies `net.fabricmc.fabric-loom` + `maven-publish` directly to the root project. The root project is still the Fabric mod build.

**Chosen pattern**: a Gradle subproject `paper/` alongside the existing root Fabric build — NOT a symmetric `common/fabric/paper` monorepo restructure (that would require moving all existing Fabric source files, which is disruptive and makes upstream merges much harder). Same general pattern used by real multi-platform projects like LuckPerms and ViaVersion (common+bukkit+fabric+... subprojects in one repo), simplified here to "existing root project + one new subproject" since only Paper is being added, no shared common module for now.

**Diff footprint versus upstream**: `settings.gradle` gains one `include 'paper'` line; everything else Paper-specific is the `paper/` folder (own `build.gradle` using `io.papermc.paperweight.userdev`, own `plugin.yml`, own `src/main/java/fi/dy/masa/servux/paper/...` tree). Outside `paper/`, the fork also carries a `README.md` rewrite, a `.gitignore` entry, and a small weather fix in the Fabric `HudDataProvider` (always send a non-negative `SetClear` during clear weather). Keeping this footprint small is what keeps merges from upstream conflict-light. Caveat: Gradle's default `build` task aggregates subprojects, so a bare `./gradlew build` also builds `paper/`. No shared `common` module (cross-subproject source-sharing between Fabric Loom and paperweight-userdev adds complexity not needed so far).

See [IMPLEMENTATION.md](IMPLEMENTATION.md) for the per-channel Fabric → Paper equivalency guide.
