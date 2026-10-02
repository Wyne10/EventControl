# The Shared Version Catalog

Every plugin in the family resolves its dependency versions from one `libs.versions.toml`
that lives in its own repository and is pulled into each plugin as a git submodule. Bumping
Paper, Guice or a WUtils module is one edit in one place, not fifteen.

## How it is wired

Two things are committed in each plugin repo, and they are all that connects it to the
catalog:

| Committed | What it is |
|---|---|
| `.gitmodules` + the `libs` gitlink | the submodule: `libs/` ⇒ `git@github.com:NeverMined-Entertainment/libs.git`, pinned to one commit |
| `gradle/libs.versions.toml` | a **symlink** to `../libs/libs.versions.toml` (git mode `120000`) |

Gradle picks up `gradle/libs.versions.toml` automatically and exposes it as the `libs`
accessor — there is no `versionCatalogs { }` block in `settings.gradle.kts`, and there
should not be one. The symlink is what makes the default location work while the real file
lives in the submodule.

## Cloning a plugin

```bash
git clone --recurse-submodules git@github.com:NeverMined-Entertainment/<Plugin>.git
```

If you already cloned without it:

```bash
git submodule update --init
```

Symptoms of a missing submodule: `libs/` is an empty directory, `gradle/libs.versions.toml`
is a dangling symlink, and Gradle fails at configuration time with an unresolved `libs`
accessor or "cannot find a version catalog". `git submodule update --init` fixes it; nothing
in the build needs changing.

## Adding the catalog to a repo that lacks it

```bash
git submodule add git@github.com:NeverMined-Entertainment/libs.git libs
ln -s ../libs/libs.versions.toml gradle/libs.versions.toml
git add .gitmodules libs gradle/libs.versions.toml
```

Check the symlink was committed as a symlink, not as a copy of the file:

```bash
git ls-files -s gradle/libs.versions.toml   # expect mode 120000
```

If the plugin lives beside the catalog on the same host, a relative submodule URL
(`../libs.git`) works too and follows whichever remote you cloned from — useful for repos
mirrored to more than one host.

### Windows, or anywhere symlinks are a problem

If git checks the symlink out as a plain text file (no developer mode, `core.symlinks=false`),
declare the catalog explicitly instead and delete the symlink:

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    versionCatalogs {
        create("libs") { from(files("libs/libs.versions.toml")) }
    }
    repositories { /* … */ }
}
```

Do this only if you have to — the symlink is the family default, and a repo carrying both
mechanisms is confusing.

## Using it

Aliases come from the TOML keys, camelCased:

```kotlin
compileOnly(libs.paperApi)          // [libraries] paperApi
implementation(libs.wutilsConfig)
alias(libs.plugins.shadow)          // [plugins] shadow
```

Never write a literal `group:artifact:version` in `build.gradle.kts`. If the dependency is
not in the catalog yet, add it there first — see below.

## Changing a version, or adding a dependency

The submodule is checked out on a **detached HEAD**, so committing inside `libs/` without
switching to a branch first leaves the work unreachable:

```bash
cd libs
git checkout master && git pull
$EDITOR libs.versions.toml
git commit -am "Bump wutils-common to 1.16.6" && git push
cd ..
git add libs && git commit -m "Update libs"   # moves this plugin's pointer
```

To pull someone else's catalog change into a plugin:

```bash
git submodule update --remote libs
git add libs && git commit -m "Update libs"
```

`git status` showing `M libs` means the checked-out submodule commit differs from the one
this repo pins. That is a real change: either commit the new pointer deliberately, or run
`git submodule update` to go back to the pinned commit. Do not commit it as a drive-by
alongside unrelated work — every plugin upgrades on its own schedule, and the pointer is how
that is recorded.

Keep the comment next to each version in the TOML pointing at where the version was checked
(the upstream releases page or Maven Central listing); several entries are deliberately
pinned to the last release supporting Java 16 or Minecraft 1.16.5.

## Private repositories

Catalog aliases only resolve if the repositories in `settings.gradle.kts` are reachable.
Company artifacts come from `mymavenrepo.com` (open) and a GitLab Maven registry that needs a
deploy token. Put it in `~/.gradle/gradle.properties` — never in the repo:

```properties
gitLabPrivateToken=<deploy token>
```

Without it, GitLab-hosted artifacts fail with a 401 while everything else resolves normally.
