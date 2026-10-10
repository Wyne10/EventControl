---
description: >-
  Clone with submodules, build the shaded plugin jar, run a test server, and
  publish the API.
---

# Building from source

## What you need

Git, and a JDK to run Gradle with. Nothing else — the Gradle wrapper fetches Gradle, and the build provisions the JDKs it actually compiles and runs with: Java 16 for compilation, and a JetBrains Runtime 21 for the test server.

## Clone

`gradle/libs.versions.toml` is a symlink into the `libs` submodule, which holds the version catalog shared across these plugins. A clone without it fails while Gradle configures the build:

```bash
git clone --recurse-submodules https://github.com/Wyne10/EventControl.git
```

If you already cloned without `--recurse-submodules`, run `git submodule update --init`.

## Build

```bash
./gradlew shadowJar
```

The shaded plugin jar lands in `build/libs/EventControl-<version>.jar`, ready to drop into a server's `plugins/` folder. The build relocates Guice, Guava, Adventure, EnhancedLegacyText, the Kotlin standard library and WUtils under `me.wyne.eventcontrol.shadow`, so the plugin cannot collide with another plugin bundling the same libraries.

| Command                            | What it does                                                                           |
| ---------------------------------- | -------------------------------------------------------------------------------------- |
| `./gradlew shadowJar -Pdebug=true` | Builds without relocating anything, which keeps stack traces readable while debugging. |
| `./gradlew :api:build`             | Builds the API module on its own.                                                      |
| `./gradlew clean shadowJar`        | Rebuilds from scratch.                                                                 |

The project version lives in `gradle.properties` and names both the jar and the published API artifact.

## The two modules

| Module | Contents                                                                                                              |
| ------ | --------------------------------------------------------------------------------------------------------------------- |
| `api`  | Plain Java against the Bukkit API — the consumer types and the [extension SPI](extending-eventcontrol.md). Published. |
| root   | The plugin itself, in Kotlin. Shaded, and not published as a dependency.                                              |

Nothing in `api` may reference WUtils, Guice or Kotlin, because they are relocated into the plugin's shadow package and a relocated type in a published signature is unusable to anyone else. That is why the API speaks `java.time.Duration`, `CompletableFuture` and `ConfigurationSection` rather than library equivalents.

## Run a test server

```bash
./gradlew runServer
```

This downloads Paper 1.16.5 along with CommandAPI, PlaceholderAPI, ProtocolLib, ViaVersion and ViaBackwards, then starts a server in `run-1.16.5/` with the freshly built plugin installed. Add `-Pdebug=true` to run on 1.19.4 instead, with relocation off.

The shipped `queue/main.yml` and the two `demo` events are unpacked on the first start, so the server comes up with a working timeline. A queue tuned for watching rather than playing makes the lifecycle visible in a minute or two:

```yaml
interval: 40s
prepare-lead: 15s
lookahead: 3
```

{% hint style="info" %}
`logLevel: DEBUG` in `run-1.16.5/plugins/EventControl/config.yml` logs every slot transition. With the interval above, a full cycle — planned, source answered, preparing, prepared, running, finished — scrolls past in well under a minute, which is the fastest way to see whether a change broke the engine.

Delete `run-1.16.5/plugins/EventControl/queues.json` between runs if you want a fresh timeline; otherwise the restored one is resumed, and the source is not asked for slots that are already filled.
{% endhint %}

## Publish the API

The `api` module is the only thing published; the plugin jar is distributed as a jar, not as a dependency.

To try a release locally, into `~/.m2`:

```bash
./gradlew :api:publishToMavenLocal
```
