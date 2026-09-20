# Dedicated Server Support

Shortcut Terminal is dist-safe: it can be installed on a **dedicated server (DEDICATED_SERVER)**
as well as on a client. This document explains the rules and what was fixed.

## What was broken

NeoForge's runtime dist checker refuses to load `net.minecraft.client.*` classes on a
`DEDICATED_SERVER`. Any class that ends up loaded by the server (mod constructor, registry,
network payload registration, command executors, ...) and references a client-only Minecraft
class will abort mod loading with:

```
java.lang.RuntimeException: Attempted to load class net/minecraft/client/Minecraft for invalid dist DEDICATED_SERVER
```

## Fix (this repository, main branch)

| Area | Before | After |
|------|--------|-------|
| `pkg/PkgManager#getGameDir` | called `Minecraft.getInstance().gameDirectory` | uses `FMLPaths.GAMEDIR` (works on both dists) |
| `network/TriggerSyncPayload` | client logic + `Minecraft` inside payload class | delegates to `client.ClientPayloadHandler` |
| `network/ServerSyncDataPayload` | client logic + `Minecraft` inside payload class | delegates to `client.ClientPayloadHandler` |
| `network/ScreenshotPayload` | client logic + `Minecraft`/`CameraType` inside payload class | delegates to `client.ClientPayloadHandler` |
| `network/ModPackets` | referenced `gui.TerminalScreen` | delegates to `client.ClientPayloadHandler` |
| `dummy/PlayerMacroManager` | lived in a common package, referenced `Minecraft` | moved to `client.PlayerMacroManager` |
| mod constructor | no dist logging | logs and skips client-only systems when `Dist != CLIENT` |

### Rules to keep it working

1. `net.minecraft.client.*` may only be referenced from `unsa.st.com.client.*` or `unsa.st.com.gui.*`.
2. Common code must never *execute* client-only branches on the server: guard with
   `FMLEnvironment.dist == Dist.CLIENT`, `level.isClientSide()`, or a `playToClient` handler.
3. Clientbound payload handlers should only forward raw data to a class in the `client` package.
4. Client-only event subscribers must use `@EventBusSubscriber(value = Dist.CLIENT)`.

## Building

```bash
./gradlew build            # jar lands in build/libs/
./gradlew runServer        # dedicated server smoke test (recommended)
```

CI (`.github/workflows/build.yml`, job `Build Mod`) publishes a `ShortcutTerminal-Mod` artifact
for every push to `main`; download the latest successful run to get a server-safe jar.

## Notes

- The `1.0.1.Beta` release predates this fix (it still contains `pkg.PackageManager`, which is
  gone in `main`). Please use a build from `main` / latest CI artifact.
- On a server the terminal inventories, file system (`UserFileSystem`, real disk IO), `/proc`
  kernel data, package manager and assembly bench all work. Item tooltips and the terminal GUI
  are client-side by nature and simply render on the client.
