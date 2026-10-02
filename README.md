# sprint

A simple client-side Fabric mod for Minecraft: Java Edition that gives the player more control over sprinting. Only your own player is affected.

## Multiplayer warning

**This mod is disabled in multiplayer by default. To use it in multiplayer, change the multiplayer mode on its configuration screen.**

This mod changes player movement. Servers that run anti-cheat systems may detect it, which can get your movement set back, get you kicked, or get you banned, and using it may break a server's rules. Some servers and game modes forbid exactly these changes. **The mod does not try to hide itself from or bypass any anti-cheat.** Check each server's rules before enabling it there. Use it in multiplayer at your own risk.

## Features

Each feature can be turned on and off on its own.

| Feature | Default | What it does |
| --- | --- | --- |
| Force Sprint | On | You sprint whenever vanilla would let you, as if the sprint key were always held. Unlike vanilla's "Toggle Sprint", it is a saved setting, so it stays on after death, teleports, dimension changes, rejoining a world, and restarting the game. |
| Sprint When Hungry | Off | You can sprint with 3 or fewer hunger shanks (6 or fewer food points), which vanilla does not allow. |
| Keep Sprinting at Walls | Off | Running into a wall no longer stops your sprint. |
| Sprint in Place | Off | Standing still no longer stops your sprint, and with the sprint key held or Force Sprint on you start sprinting without moving. Jumping in place does not push you forward. |
| Omnidirectional Sprint | Off | With the sprint key held or Force Sprint on, you sprint in every direction, not only forward. Sprint jumps push you in the direction you move. |
| Sprint While Sneaking | Off | You can start sprinting while sneaking, with the sprint key, Force Sprint, or by double-tapping forward. |

Everything else about sprinting stays vanilla. For example, blindness, using an item, or flying with an elytra still prevent sprinting, and swimming follows the vanilla rules.

## Installation

1. Install [Fabric Loader](https://fabricmc.net/use/) and [Fabric API](https://modrinth.com/mod/fabric-api).
2. Download the jar from this repository's [Releases](https://github.com/urntt/sprint/releases) page. Each release supports a single Minecraft version, shown after the `+` in its version number. For example, `1.0.0+26.3` is for Minecraft 26.3.
3. Put the jar into your `.minecraft/mods` folder.

[Mod Menu](https://modrinth.com/mod/modmenu) is optional. When installed, it opens the mod's configuration screen from its mod list.

## Usage

Force Sprint is on by default in singleplayer. On multiplayer servers the mod stays off until you allow it.

The mod adds these key bindings in **Options → Controls → Key Binds**, all unbound by default:

- One toggle key per feature, for example **Toggle Force Sprint**. Each turns its feature on or off and shows the new state on the action bar. On a server that the multiplayer settings rule out, it only shows that the mod is disabled there.
- **Open sprint Settings** opens the configuration screen. With Mod Menu installed, you can also open it from the mod list.

### Settings

All settings are saved to `config/sprint.json` as soon as you change them.

| Setting | Default | Meaning |
| --- | --- | --- |
| Current State | See [Features](#features) | The current state of each feature, the same ones the toggle keys switch. |
| Singleplayer Defaults | Same as the feature defaults | The state of each feature that a reset restores in singleplayer worlds, including worlds you open to LAN. |
| Server Defaults | Same as the feature defaults | The state of each feature that a reset restores on servers that the multiplayer mode allows. |
| Reset on World Exit | Off | Every world starts from its default states instead of keeping the last states. |
| Reset on Game Exit | Off | After restarting the game, the first world where the mod is allowed starts from its default states. |
| Multiplayer mode | Disabled | **Disabled**: never active on servers. **Whitelist**: active only on servers in the server list. **Blacklist**: active on all servers except those in the server list. |
| Server List | Empty | The addresses the whitelist and blacklist modes use. |

The multiplayer mode is a hard limit: on a server it rules out, every feature stays off whatever its current state is. Joining another player's LAN world or a Realm counts as multiplayer.

Server list entries are compared with the address you connect to, ignoring upper and lower case. An entry without a port, such as `mc.example.com`, matches the server on any port, while an entry with a port, such as `mc.example.com:25566`, matches only that port. The server list screen marks invalid addresses in red and does not save until they are fixed or removed.

## Development

Building requires the JDK version set by `java_version` in `gradle.properties`.

Build the mod:

```bash
./gradlew build
```

The jar is written to `build/libs/`.

Run the client game tests, which start Minecraft and check every feature against vanilla behavior, force sprint after respawning, teleporting, changing dimension and rejoining a world, the key bindings, the reset rules, the multiplayer modes on a local dedicated server, and the saved configuration:

```bash
./gradlew runClientGameTest
```

The game tests need a display. On a headless Linux machine, run them under Xvfb. Xvfb offers no sRGB-capable OpenGL visuals, so install Mesa's Vulkan driver (`mesa-vulkan-drivers` on Ubuntu) for the game to fall back to:

```bash
xvfb-run -a -s "-screen 0 1920x1080x24" ./gradlew runClientGameTest
```

Screenshots taken by the tests are saved to `build/run/clientGameTest/screenshots/`.

The multiplayer tests start a local dedicated server, so the test setup in `build.gradle` accepts the [Minecraft EULA](https://aka.ms/MinecraftEULA) for that test server.

## License

[MIT](LICENSE)
