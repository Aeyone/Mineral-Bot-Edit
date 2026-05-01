# Repository Guidelines

## Project Structure & Module Organization
This repository is a multi-module Gradle project rooted at `settings.gradle`. Core modules are:
- `bot-api`: public interfaces and shared contracts.
- `bot-shared-library`: common implementations and control abstractions.
- `bot-base-client`: modified Minecraft client, LWJGL bridge, and bundled `src/main/mc-client` / `src/main/mc-resources`.
- `bot-intelligence`: AI goals and pathfinding.
- `bot-bukkit`, `bot-standalone`, `bot-mineral`: runtime entry points and packaging targets.

Most Kotlin lives in `src/main/kotlin`. `bot-intelligence` keeps Kotlin files under `src/main/java`; preserve the existing source root instead of relocating files. Build outputs go under each module’s `build/` directory and should not be edited.

## Build, Test, and Development Commands
- `./gradlew build`: compile all modules and run the full Gradle verification lifecycle used by CI.
- `./gradlew :bot-intelligence:build`: build one module while iterating on AI logic.
- `./gradlew :bot-bukkit:shadowJar`: produce the shaded Bukkit plugin artifact.
- `./gradlew :bot-base-client:runMinecraftClient`: launch the embedded client locally.
- `./gradlew publish`: publish artifacts to the private Maven repo; requires credentials.

Use JDK 21 in CI-compatible environments. Most modules target Java 17; `bot-mineral` uses Java 21.

## Coding Style & Naming Conventions
Follow existing Kotlin/Gradle style: 4-space indentation, UTF-8, and concise comments only where logic is non-obvious. Keep package names lowercase under `gg.mineral.bot...`, use `PascalCase` for types, `camelCase` for members, and suffix AI goal classes with `Goal` (for example `MeleeCombatGoal`). No formatter or lint task is configured here, so match surrounding code closely.

## Testing Guidelines
There is no substantial committed test suite yet. For changes, run at least the affected module build and any relevant smoke path (for example `:bot-bukkit:shadowJar` for plugin changes). New tests should live under `src/test/kotlin` or `src/test/java` and mirror the production package layout.

## Commit & Pull Request Guidelines
Keep commit subjects short and imperative, matching recent history such as `Edit base goal` or dependency bumps. PRs should include:
- a brief summary of the behavioral change,
- affected modules,
- linked issue or context,
- verification steps or logs,
- screenshots only when UI/client rendering behavior changed.

## Security & Configuration Tips
Publishing and some dependency resolution use private credentials via `USERNAME` and `TOKEN` or Gradle properties `gpr.user` / `gpr.token`. Do not hardcode secrets or commit generated jars.
