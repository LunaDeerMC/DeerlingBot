# Project Guidelines — DeerlingBot

## Overview

Paper/Bukkit Minecraft plugin (Java 17) bridging QQ groups and Minecraft via OneBot 11 WebSocket protocol. Comments and user-facing strings are in **Chinese (Simplified)**.

## Architecture

- **Entry point**: [DeerlingBot.java](src/main/java/cn/lunadeer/mc/deerlingbot/DeerlingBot.java) — `JavaPlugin`, manually wires all singletons in `onEnable()`
- **Singleton pattern**: Managers use `instance = this;` in constructor + `static getInstance()` — no DI framework
- **Event bridge**: OneBot WebSocket messages are parsed into **Bukkit Events** (`AbstractPost` extends `Event`), dispatched via `Bukkit.getPluginManager().callEvent()`, and handled with `@EventHandler` listeners
- **Managers** (`managers/`): `CoreConnector` (WebSocket), `CommandManager` (reflexive command loading), `MessageManager` (bidirectional group↔server chat), `BindManager` (QQ↔player binding), `TemplateFactory` (HTML templates), `WebDriverManager` (headless Chrome screenshots)
- **Protocols** (`protocols/`): OneBot 11 segments (`TextSegment`, `ImageSegment`, `MentionSegment`, `ReplySegment`), events (`GroupMessage`, `PrivateMessage`, notice types), and outbound operations (`GroupOperation`, `PrivateOperation`)
- **Utils** (`utils/`): Custom SQL builder in `utils/databse/` (note: intentional typo in package name), reflection-based YAML config in `utils/configuration/`, Folia/Spigot-aware scheduler in `utils/scheduler/`, Bukkit command framework in `utils/command/`

## Code Style

- **Java 17 features**: records, pattern matching for `instanceof`, switch expressions, `String.formatted()`, `List.of()`
- **Static operations**: PascalCase for outbound protocol methods (`SendGroupMessage`, `SetGroupCard`) — see [GroupOperation.java](src/main/java/cn/lunadeer/mc/deerlingbot/protocols/GroupOperation.java)
- **Logging**: Use `XLogger.info/warn/error/debug` with `{0}`, `{1}` placeholder formatting — never `System.out`
- **JSON**: fastjson2 (`com.alibaba.fastjson2.JSONObject/JSONArray`) for all OneBot serialization
- **Config**: Static fields + annotations (`@Comments`, `@HandleManually`, `@PostProcess`) on `ConfigurationPart` classes. Keys auto-convert camelCase→kebab-case — see [Configuration.java](src/main/java/cn/lunadeer/mc/deerlingbot/configuration/Configuration.java)
- **Database**: Fluent SQL builder (`Select.select(...).from(...).where(...).execute()`), supports SQLite/MySQL/PostgreSQL via `DatabaseManager`. Schema defined programmatically in table classes — see [WhitelistTable.java](src/main/java/cn/lunadeer/mc/deerlingbot/tables/WhitelistTable.java)

## Build and Test

```sh
# Build (shadow JAR) — outputs to build/libs/
./gradlew shadowJar

# Lite build (default, BuildFull=false): libraries as compileOnly, downloaded by server
# Full build: set BuildFull=true in gradle.properties to shadow all deps

# Clean + build
./gradlew clean shadowJar
```

- Output: `DeerlingBot-<version>-{full|lite}.jar`
- Versioning: auto from git branch — `master` → beta, `dev/*` → alpha.N (see [version.properties](version.properties))
- No test suite currently exists

## Project Conventions

- **Bot commands**: Extend `BotCommand`, placed in `commands/` package — auto-discovered via reflection at startup. Use `@FancyCommand` annotation for Chrome-dependent commands
- **New OneBot events**: Extend `AbstractPost` (for Bukkit Event integration), parse in `CoreConnector.onMessage()`, add corresponding handler with `@EventHandler`
- **New config fields**: Add `public static` fields to `Configuration` or `MessageText` inner classes. Use `@Comments` for YAML doc comments
- **Platform compat**: Always use `Scheduler` from `utils/scheduler/` for async/delayed tasks — never Bukkit scheduler directly (Folia support)
- **Package typos**: `utils/databse/` and `utils/databse/FIelds/` — these are established names, do not rename without a migration plan

## Integration Points

- **OneBot 11**: WebSocket connection to QQ bot backend (NapCat/go-cqhttp/etc.) — configured via `Configuration.oneBotWebSocket`
- **PlaceholderAPI**: Optional integration for message formatting and template rendering
- **Selenium/Chrome**: Headless browser at `plugins/DeerlingBot/libs/chrome/chrome` for HTML→image rendering
- **External resources**: Templates and libs downloaded from `LunaDeerMC/DeerlingBot_resources` GitHub releases
