# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html). Each version carries the targeted Minecraft version as build metadata, for example `1.0.0+26.3`.

## [Unreleased]

## [1.0.0+26.3] - 2026-10-02

### Added

- Force Sprint, on by default: sprint whenever vanilla allows it, as if the sprint key were always held. It is a saved setting, so it stays on after death, teleports, dimension changes, rejoining a world, and restarting the game.
- Sprint When Hungry: allow sprinting with 3 or fewer hunger shanks.
- Keep Sprinting at Walls: running into a wall no longer stops the sprint.
- Sprint in Place: stay sprinting while standing still, and start sprinting without moving. Jumping in place does not push the player forward.
- Omnidirectional Sprint: sprint in every direction, with sprint jumps following the movement direction.
- Sprint While Sneaking: allow starting to sprint while sneaking, including by double-tapping forward.
- Add a toggle key binding for each feature and an "Open sprint Settings" key binding, all unbound by default. Toggling shows the feature's new state on the action bar.
- Add a configuration screen built from vanilla widgets, available through the key binding and through Mod Menu, which is optional. It has the current state of each feature, per-feature defaults for singleplayer worlds and allowed servers, and options to reset to the defaults on world exit or game exit.
- Add multiplayer modes (disabled, whitelist, blacklist) and a server list screen. The mod is disabled on multiplayer servers by default.
- Save all settings to `config/sprint.json`.
- Add English and Simplified Chinese translations.
