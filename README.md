# Aero

A personal-use Minecraft Java Edition utility client, built as a Fabric mod
in Kotlin. Targets Minecraft 1.21.11.

## Setup (zero modding experience required)

1. **Install an IDE.** Download and install [IntelliJ IDEA Community
   Edition](https://www.jetbrains.com/idea/download/) (free). You don't need
   the paid Ultimate edition for this.
2. **Install a Java 21 JDK** if you don't already have one (IntelliJ can also
   download one for you when you open the project — see step 3).
3. **Open the project.** In IntelliJ: `File -> Open...` and select the
   `aero` folder (the one with `build.gradle` in it). IntelliJ will detect
   it as a Gradle project and start syncing automatically — this downloads
   Minecraft itself, its mappings (so code refers to real names like
   `Player` instead of Mojang's obfuscated names), Fabric Loader, Fabric
   API, and Fabric Language Kotlin. **This first sync needs internet access
   and can take several minutes to tens of minutes** depending on your
   connection — that's normal, just let it finish.
4. **Run the mod.** Once sync finishes, open the Gradle side panel
   (elephant icon) and run the `runClient` task, or from a terminal in the
   project folder run:
   ```
   ./gradlew runClient
   ```
   This launches a real (dev-environment) copy of Minecraft with Aero
   already installed. Press whatever key Sprint is bound to (default `R`)
   to toggle auto-sprint and confirm it works.

## Adding a new module

The promise is: **one new file + one registration line.** That's really
all it takes, because `Module`, `ModuleManager`, and `ConfigManager` are
all generic already:

1. Create a new file in `src/client/kotlin/dev/finn/aero/module/impl/`,
   e.g. `Fullbright.kt`, extending `Module` (see `Sprint.kt` as the
   reference example — it's a complete, working module).
2. Register it in `AeroClient.kt`:
   ```kotlin
   ModuleManager.register(Fullbright())
   ```

That's it. The new module automatically:
- appears in `ModuleManager.all()` / `byCategory()` for the GUI,
- gets `onTick()` called every client tick while enabled,
- gets `onRender()` called every frame while enabled,
- gets its keybind polled and toggled,
- gets its `enabled` flag and every `Setting` it registers saved to and
  loaded from `config/aero.json` automatically.

No changes to `ModuleManager`, `ConfigManager`, or the (future) ClickGUI
renderer are needed — the GUI and config code both iterate
`ModuleManager.all()` and `module.settings` generically, switching on the
sealed `Setting` subtype rather than hardcoding module names.

## Project layout

```
src/
  main/resources/fabric.mod.json   -- mod metadata, entrypoint declaration
  client/kotlin/dev/finn/aero/
    client/AeroClient.kt           -- mod entrypoint, registers modules, wires Fabric events
    module/Module.kt               -- base class (name, category, keybind, settings, lifecycle)
    module/ModuleManager.kt        -- registry + tick/keybind dispatch
    module/impl/Sprint.kt          -- reference module (auto-sprint)
    setting/Setting.kt             -- Bool/Slider/Mode/Keybind/Color settings
    config/ConfigManager.kt        -- JSON save/load via Gson
```

Aero is a client-only mod (no server-side code), so everything lives under
`src/client` rather than `src/main`.

## Known limitations / what's not built yet

- Modules implemented so far: Sprint, Speed, Fly, Scaffold, Jesus, Spider,
  NoFall, Freecam, Fullbright, KillAura, Criticals, AutoTotem,
  AutoAttributeSwap, AntiKnockback, Nuker, DeathWaypoint, HUD Info, and
  ESP (Player/Entity/Chest) plus X-Ray — all wired up end-to-end (GUI,
  config, keybinds).
- Not yet built: Aimbot/TriggerBot as a KillAura alternative, Nametags,
  AutoLoot/ChestStealer. Add them the same way as everything else — one
  file + one registration line in `AeroClient.kt`.
- The full ClickGUI (Meteor-style side-by-side category columns), a command
  palette (Alt+RightShift), and a settings screen are all built. The
  `Setting` sealed class stays generic so new setting types only need one
  new `when` branch in the renderer, not per-module GUI code.
