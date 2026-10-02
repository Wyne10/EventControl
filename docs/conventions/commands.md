# Commands

Commands use [CommandAPI](https://commandapi.jorel.dev/), registered from a single
`@Singleton` root class that is bound only when CommandAPI is installed (see
`CommandModule` in [Dependency Injection](dependency-injection.md)).

## Root command

`command/<Plugin>Command.kt` builds the tree in `init` and does nothing else:

```kotlin
@Singleton
class KitCommand @Inject constructor(private val plugin: PluginTemplate) {
    init { registerCommand() }

    private fun registerCommand() {
        CommandAPICommand("kit")
            .withSubcommand(ReloadCommand(plugin)())
            .withSubcommand(GiveCommand(…)())
            .register(plugin)
    }
}
```

Dependencies the subcommands need are injected into the root class and passed down as
constructor arguments. Subcommands are not injected individually.

## Subcommands

`command/SubCommands.kt` holds the shared base and the small leaves; larger subcommands get
their own file in `command/`. The base is three lines:

```kotlin
abstract class SubCommand(argument: String) {
    open val command = CommandAPICommand(argument)
    operator fun invoke() = command
}
```

Each leaf overrides `command`, chaining onto `super.command`, and is spliced in with the
`()` operator:

```kotlin
class ReloadCommand(plugin: PluginTemplate) : SubCommand("reload") {
    override val command: CommandAPICommand = super.command
        .withPermission("kit.reload")
        .executes(CommandExecutor { sender, _ ->
            plugin.reload()
            sender.placeholderComponent("success-plugin-reload").sendMessagePlayer(sender)
            PluginTemplate.logger.info("Plugin reloaded")
        })
}
```

Conventions:

- Every subcommand declares `.withPermission("<plugin>.<action>")`, and the same node is
  registered in the `bukkit { permissions { … } }` block of `build.gradle.kts` with a
  description and a `<plugin>.*` parent.
- `executes` for any sender, `executesPlayer` when the body needs a `Player`.
- All feedback goes through i18n keys ([Messages](internationalization.md)); admin actions
  additionally log one English line.
- Async work chains on `CompletableFuture` and returns to the main thread with
  `Schedulers.sync()` before touching the world.
- Shared guard clauses live as top-level functions in `SubCommands.kt` (`assertKitExists`,
  `assertOwner`): they send the error message themselves and return `Boolean`, so the
  executor reads as `if (!assertOwner(sender, kit)) return@…`.
- A subcommand that needs conversational input (prompting for a name in chat, say)
  implements `Listener` itself and registers in its own `init`.

## Custom arguments

Parsers shared between subcommands live in `command/CustomArguments.kt`, or at the bottom of
`SubCommands.kt` when there are only one or two. They are built with `CustomArgument` and
fail with a localized message:

```kotlin
fun timeSpanArgument(nodeName: String): Argument<TimeSpan> =
    CustomArgument(StringArgument(nodeName)) { info ->
        runCatching { Durations.getTimeSpan(info.input) }.getOrElse {
            throw CustomArgument.CustomArgumentException.fromBaseComponents(
                info.sender.placeholderComponent("error-duration").bungee())
        }
    }
```

Suggestions come from the live registry (loaded definition keys, loot pool keys) rather than
a hardcoded list.
