# Project Guidelines — DeerlingBot

## Overview

Paper/Bukkit Minecraft plugin targeting Java 17. It bridges QQ groups and Minecraft through the OneBot 11 WebSocket protocol. Keep comments and user-facing text in Chinese (Simplified).

## Architecture

- Entry point: [src/main/java/cn/lunadeer/mc/deerlingbot/DeerlingBot.java](../src/main/java/cn/lunadeer/mc/deerlingbot/DeerlingBot.java). `onEnable()` manually wires all managers and the startup order matters because the project uses eager singletons instead of DI.
- OneBot messages are received in `CoreConnector`, parsed into subclasses of `AbstractPost`, then dispatched as Bukkit events via `Bukkit.getPluginManager().callEvent()`.
- Reuse the existing subsystems instead of bypassing them: `CommandManager` for command discovery, `Scheduler` for async/delayed work, `DatabaseManager` plus table classes for persistence, and `WebDriverManager` for screenshot-based fancy commands.
- PlaceholderAPI is optional. Chrome/WebDriver resources are external runtime dependencies managed by the plugin.

## Code Style

- Prefer existing Java 17 idioms already used in the repo.
- Use `XLogger.info/warn/error/debug` with `{0}`, `{1}` placeholders. Do not use `System.out`.
- Use fastjson2 `JSONObject` and `JSONArray` for OneBot payload serialization.
- Keep outbound protocol helper naming consistent with the existing PascalCase static methods such as [src/main/java/cn/lunadeer/mc/deerlingbot/protocols/GroupOperation.java](../src/main/java/cn/lunadeer/mc/deerlingbot/protocols/GroupOperation.java).
- Follow the existing configuration pattern in [src/main/java/cn/lunadeer/mc/deerlingbot/configuration/Configuration.java](../src/main/java/cn/lunadeer/mc/deerlingbot/configuration/Configuration.java): `public static` fields, configuration annotations, and automatic camelCase-to-kebab-case YAML keys.

## Build and Test

- Preferred build commands: `./gradlew Clean&Build` or `./gradlew shadowJar`. On Windows use `gradlew.bat`.
- Build output is `build/libs/DeerlingBot-<version>-{lite|full}.jar`.
- `BuildFull=false` by default. Set it to `true` in `gradle.properties` when you need a fully shaded jar.
- Running the build updates [version.properties](../version.properties) based on the current git branch, so a normal build can dirty the worktree.
- There is no automated test suite in this repo.

## Conventions

- New bot commands should extend `BotCommand` in `commands/`. Use `@FancyCommand` for commands that depend on Chrome screenshot rendering.
- New OneBot events should extend `AbstractPost`, be parsed in `CoreConnector.onMessage()`, and be handled through the Bukkit event system.
- Always use [src/main/java/cn/lunadeer/mc/deerlingbot/utils/scheduler/Scheduler.java](../src/main/java/cn/lunadeer/mc/deerlingbot/utils/scheduler/Scheduler.java) instead of the Bukkit scheduler directly so Folia/Paper compatibility is preserved.
- The package names `utils/databse/` and `utils/databse/FIelds/` are established typos. Do not rename them casually.

## References

- OneBot 11 reference docs live under [onebot-11-ref/README.md](../onebot-11-ref/README.md).
- Linux Chrome dependency notes for fancy commands are in [常见缺少库与安装方式.md](../常见缺少库与安装方式.md).
