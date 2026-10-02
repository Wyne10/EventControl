# InfPoints

The server's centralized currency system. Any balance a plugin charges, pays out, or
displays is an InfPoints *point*, not a field in the plugin's own database.

Artifact: `org.bigcraft:InfPoints-api` (`libs.infPoints`), `compileOnly`.

## Points

A point is one configured currency, identified by a string key (`primary`, `xp`, `exp`, …)
and configured in InfPoints' own `config.yml` under `point:`. Its storage backend is chosen
there: `MEMORY`, `JSON`, `PDC`, `LEVEL`, `EXP` or `SQL`. Consumers care only about the key.

`Point` combines three views:

- `PointType` — balance operations by `UUID`: `get`, `add`, `subtract`, `set`, `transfer`.
- `PointView` — the same operations taking a `Player`, so the implementation can also notify
  them.
- `PointConfig` — display config (`name`, `pluralName`, `symbol`, `color`, `decimalFormat`)
  and the per-point pay/balance command config.

`subtract` and `transfer` return `false` when the balance is insufficient and change
nothing, which is the contract to build charging flows on.

## Using it

Store the currency key in config and resolve the `Point` lazily, so a reload or a renamed
currency is picked up:

```kotlin
@ConfigEntry(section = "kit")
val purchaseCurrency = "primary"

private val purchasePoint: Point
    get() = IPApi.getInstance().getPoint(purchaseCurrency)
        ?: throw IllegalArgumentException("Invalid currency $purchaseCurrency")
```

Charge first, act second, refund on failure — the pattern for any purchase that continues
into an asynchronous write:

```kotlin
if (!purchasePoint.subtract(player, amount)) return CompletableFuture.completedFuture(null)
return repository.create(...).handleAsync { result, throwable ->
    if (checkFailure(player, result, throwable)) { purchasePoint.add(player, amount); null }
    else …
}
```

`getFormat(uuid)` renders a balance with the point's configured decimal format; the display
name, symbol and colour are available through `getVisualConfig()` and are also exposed as
`%points_<key>_…%` placeholders.

## Events

`PointAddEvent`, `PointSubtractEvent` and `PointSetEvent` (all extending the cancellable
`PointEvent`) fire before a change. Handlers can call `setAmount` to change what is applied
or cancel to block it; `getNewBalance()` reports the outcome. The event is asynchronous when
fired off the main thread — check `isAsynchronous()` before touching the world.

## Soft dependency

If a plugin only *reflects* balances (snapshotting or displaying them), probe for the class
once in a small wrapper object, skip the feature when absent, and list InfPoints in
`softDepend`. If balances are load-bearing, put it in `depend`.
