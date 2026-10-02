# AGENTS.md

Instructions for AI coding agents working in this repository. `CLAUDE.md` imports this file, so keep all agent guidance here.

## Project

`sprint` is a client-side [Fabric](https://fabricmc.net/) mod for Minecraft: Java Edition that gives the local player more control over sprinting.

The sibling mod [urntt/nojumpdelay](https://github.com/urntt/nojumpdelay) is the reference for the project setup, the configuration screen, its settings (defaults, reset rules, multiplayer modes, server list), and their style. Keep the two consistent where they solve the same problem.

## Project decisions

These decisions are settled. Do not deviate from them without the user's explicit approval.

### Minecraft and toolchain

- Development started on Minecraft 26.3 with Java 25. `gradle.properties` is the single source of truth for the mod, Minecraft, Fabric Loader, Loom, Fabric API, Mod Menu, and Java versions. `build.gradle`, `fabric.mod.json`, the mixin config, and the CI workflows read them from there; do not restate them elsewhere.
- Follow the latest official Fabric template ([FabricMC/fabric-example-mod](https://github.com/FabricMC/fabric-example-mod), also available from the [template generator](https://fabricmc.net/develop/template/)): the `net.fabricmc.fabric-loom` Gradle plugin, Mojang's official names with no `mappings` dependency, and `implementation` (not `modImplementation`) for dependencies. Do not use Yarn.
- Pin `loom_version` to a release version instead of the template's `-SNAPSHOT`, so builds are reproducible.
- Target only the latest stable (release) Minecraft version. Updates, fixes, and new features are always developed against it. Snapshots, pre-releases, and release candidates are not supported targets.
- Do not maintain older Minecraft versions and do not set up multi-version builds (no per-version branches, no Stonecutter or other preprocessors). When a new stable version is released, port the mod to it and drop the previous one.

### Identity

| Item | Value |
| --- | --- |
| Mod ID | `sprint` |
| Display name | `sprint` |
| Maven group | `com.urntt` |
| Base package | `com.urntt.sprint` |
| Version format | `<SemVer>+<Minecraft version>`, for example `1.0.0+26.3` |
| License | MIT |

The mod version itself follows [Semantic Versioning](https://semver.org/); the `+<Minecraft version>` suffix is build metadata naming the Minecraft version the build targets. Version numbers start at `1.0.0`.

### Distribution

- Releases are published only as GitHub Releases. Do not publish to Modrinth, CurseForge, or any other mod platform, and do not add publishing tooling for them.
- `README.md` must clearly warn that this is a movement modification: it may conflict with server anti-cheat systems, and using it in multiplayer may get the player set back, kicked, or banned. It must also state that the mod does not try to bypass any anti-cheat and is disabled in multiplayer until the player enables it on the configuration screen.
- The mod must never try to hide itself from or bypass server anti-cheat systems.

### Scope and behavior

- Client-only: `fabric.mod.json` declares `"environment": "client"`. There is no server-side component and no networking.
- The mod only changes when the local player starts and stops sprinting, through six features defined in the `Feature` enum, which is the single source of their ids (config keys and translation key suffixes) and initial states:
  - **Force sprint** (`force_sprint`, on by default): the sprint key counts as held whenever `LocalPlayer.aiStep` decides whether to start sprinting. It is a saved setting, not vanilla's in-memory toggle state, so it survives death, teleports, dimension changes, rejoining a world, and restarting the game. The input sent to the server keeps the real key state.
  - **Sprint when hungry** (`ignore_hunger`): the food level no longer limits sprinting (vanilla needs more than 6 food points).
  - **Keep sprinting at walls** (`keep_on_collision`): a head-on horizontal collision no longer stops run sprinting.
  - **Sprint in place** (`sprint_in_place`): no movement input no longer stops run sprinting, and starting to sprint without moving is allowed when the sprint key is held or forced.
  - **Omnidirectional sprint** (`omnidirectional`): movement input without a forward component no longer stops run sprinting, and starting to sprint in such a direction is allowed when the sprint key is held or forced.
  - **Sprint while sneaking** (`sprint_while_sneaking`): sneaking no longer prevents starting to sprint or cancels the forward double-tap. Crawling still prevents it.
  - Every feature other than force sprint is off by default.
- With omnidirectional sprint or sprint in place, the sprint-jump boost follows the movement input instead of the facing direction, and a jump without movement input gets no boost. Forward sprint jumps stay vanilla.
- Swim sprinting keeps its vanilla rules. Sprinting in place and in other directions only starts out of water, so it never turns into swimming.
- Only the local player (`LocalPlayer`) is affected. Every other entity, including other players and mobs simulated on the client, must stay vanilla.
- `SprintController` is the single owner of whether a feature is active: its toggle state is on and the current scene (singleplayer or a multiplayer server, determined on join) is allowed. `SprintRules` holds the movement rules the mixins share.
- Each feature has a toggle state and its own toggle key binding, unbound by default. Each toggle shows the feature's new state on the action bar and saves it to the configuration file, so it persists across game restarts.
- Defaults depend on the scene: each feature has a singleplayer default (worlds hosted by this client, including ones opened to LAN) and a server default (servers the multiplayer mode allows). Both equal the feature's initial state.
- Reset rules restore the scene's defaults for every feature when the player joins an allowed scene: "reset on world exit" for every world, and "reset on game exit" for the first allowed world after the game starts. Both are off by default. Resets happen on join so that they use the next scene's defaults and still work after a crash.
- The multiplayer mode is a hard limit: `DISABLED` (the default) rules out every server, `WHITELIST` allows only servers in the server list, and `BLACKLIST` allows every server except those in it. On a ruled-out server every feature stays off and the toggle keys only report that the mod is disabled there. Joining another player's LAN world or a Realm counts as multiplayer.
- Server list entries match the connected address by host (case-insensitive, after IDN conversion, and required to be a valid domain name or IP address) and by port only when the entry specifies one.
- The configuration screen is built from vanilla widgets and opens through Mod Menu or a separate "open settings" key binding, which is also unbound by default.

### Localization

- All user-facing text, including key binding names, the key binding category, action bar messages, and the configuration screen, uses translation keys. Never hard-code display strings.
- Provide translations for `en_us` and `zh_cn`, and keep both complete whenever a translation key is added or changed.

### Dependencies

- Required: Fabric Loader and Fabric API.
- Optional: Mod Menu, declared under `suggests` in `fabric.mod.json`. The mod must load and work normally without it, so Mod Menu classes may only be referenced from the Mod Menu entrypoint.
- Configuration is hand-written without a config library: a JSON file in the Fabric config directory, serialized with Gson (bundled with Minecraft). Any configuration screen uses vanilla widgets.
- Do not add other dependencies without the user's explicit approval.

### Implementation

- Language: Java only.
- Source sets: `src/main` holds only `fabric.mod.json` and the icon. All code and client resources live in `src/client`, and the client game tests live in `src/gametest`.
- Mixins: prefer the MixinExtras injectors bundled with Fabric Loader (for example `@ModifyExpressionValue` and `@WrapOperation`) over `@Redirect` and `@Overwrite`, to stay compatible with other mods and keep porting work small.

### Testing

- The client game tests in `src/gametest` cover the address matching, the configuration defaults, upgrades and reset rules (`SprintLogicGameTest`), every feature measured against vanilla, persistence of force sprint across respawn, teleport, dimension change and rejoining, the toggle keys, reset on world exit and settings screens in singleplayer (`SprintClientGameTest`), and each multiplayer mode on a local dedicated server (`SprintMultiplayerGameTest`). Keep them passing and extend them when behavior changes.
- The dedicated server needs `eula = true` in the `configureTests` block of `build.gradle`; it accepts the Minecraft EULA only for that local test server.
- After porting to a new Minecraft version, run the client game tests. A successful build does not prove that the mixins still have the intended effect.
- `README.md` describes how to run them, including on a headless machine.

### CI, releases, and changelog

- GitHub Actions (`.github/workflows/build.yml`) builds the project and runs the client game tests on every push and pull request.
- Maintain `CHANGELOG.md` following [Keep a Changelog](https://keepachangelog.com/). Record every user-visible change under `Unreleased` in the same change that introduces it.
- `.github/workflows/release.yml` builds the mod and publishes a GitHub Release for the project version, with the jar attached and the matching `CHANGELOG.md` section as release notes. It runs when a tag `v<version>` (for example `v1.0.0+26.3`) is pushed, or when started manually on a branch, in which case it creates that tag on the branch's latest commit. It fails if a pushed tag does not match the project version, if the changelog has no section for the version, or if the release already exists.
- Release only when the user asks. To release, set `mod_version` in `gradle.properties`, rename `Unreleased` in `CHANGELOG.md` to `[<version>] - <YYYY-MM-DD>` above a new empty `Unreleased` section, commit, and push. Then start the release workflow on `main`. Claude Code cloud sessions cannot push tags, so start the workflow through the GitHub Actions API instead.

## Engineering principles

- Fix root causes, not symptoms. Diagnose the underlying cause before implementing a permanent fix. If an immediate mitigation is necessary, treat it as temporary and follow through with a root-cause fix.
- Prefer configuration-driven design for values that are expected to vary by environment, deployment, or product requirements. Avoid unexplained or duplicated magic values, but do not introduce configuration where a well-named constant is the clearer source of truth.
- Preserve a single source of truth and clear ownership for data, state, configuration, business logic, and authoritative documentation. Avoid duplicating canonical information across multiple locations.
- Do not maintain parallel legacy and replacement implementations without an explicit migration and removal plan.

## Documentation

- Keep documentation aligned with the code. When a code change affects documented behavior, APIs, architecture, configuration, workflows, or usage, update the relevant documentation in the same change.
- Keep each document's responsibility clear. For example, use `README.md` for project overview and usage, and `VISION.md` for product direction, architectural principles, or long-term decisions.
- Always specify a language identifier for fenced code blocks in Markdown.

## Language

- Communicate with the user in Chinese, including explanations, progress updates, and user-facing planning.
- Use English for development artifacts, including source code, comments, docstrings, documentation, READMEs, Git branch names, commit messages, and other deliverables intended to live in the repository.

## Git

- Do not change or override the Git author or committer identity. When an identity must be configured for commits created during the task, use:
  - Name: `urntt`
  - Email: `urntts@gmail.com`
- Do all actions on the user's behalf. Do not rewrite existing commit authorship unless explicitly requested. Do not add `Co-Authored-By` trailers or session links to commit messages or pull request descriptions.
- Develop on `main` and push directly to it. Branches and pull requests are not required.
- Because changes land on `main` without review, make sure `./gradlew build` and the client game tests pass locally before pushing.
- If a branch is used, give it a category-based prefix that reflects the purpose of the change, such as `feat/`, `fix/`, `refactor/`, `docs/`, `test/`, or `chore/`.
- Follow the [Conventional Commits](https://www.conventionalcommits.org/) specification for commit messages.
