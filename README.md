# Rise to Ruins — Archipelago Mod

Adds [Archipelago](https://archipelago.gg) multiworld randomizer support to Rise to Ruins. Goals become location checks, and buildings and spells are unlocked by receiving items from the multiworld.

Requires [RtRModLoader](https://github.com/mattthewaz/RtRModLoader) to run mod.
Requires [RtRAPWorld](https://github.com/mattthewaz/RtRAPWorld) to generate multiworld randomizers.

## !!Your existing saves!!

The mod redirects AP game profiles to a separate `ap_profiles/` directory, so your existing vanilla profiles should be completely unaffected. That said, the mod does patch core game systems, and bugs happen — **it's worth backing up your `profiles/` folder before playing with the mod installed.**

## Installation

1. Install RtRModLoader if you haven't already — see its README for instructions. 
2. Download `archipelago.jar` from the releases page and drop it into your `mods/` folder.
3. Launch the game through Steam.

## Setup

Each game profile has its own Archipelago connection. You'll need a separate profile for each multiworld slot you play.

### Creating a profile for a new seed

1. From the main menu, create a new game profile as normal.
2. Before starting a world, click the **Archipelago** button on the main menu. A settings dialog will appear.
3. Enter your connection details:
   - **Server** — the Archipelago server address, e.g. `archipelago.gg:38281`
   - **Slot Name** — your player name in the multiworld
   - **Password** — leave blank if the room has no password
4. Click **Save**.
5. Enter the world map. The mod will connect to the server automatically.

### Reconnecting / changing settings

Open the **AP Settings** button from the main menu at any time to update your connection settings. The new settings take effect the next time you enter the world map.

## Gameplay

### Locations

Completing goals in the goal web sends location checks to the server. Goals with multiple tiers must be completed once per tier — the goal resets after each tier and must be completed again. The goal description shows your current progress through its tiers, e.g. `[1/4]`.

### Items

Items received from the multiworld unlock content in your game:

- **Buildings** — unlocks the building in the build menu. Locked buildings are greyed out until received.
- **Spells** — unlocks the spell. Locked spells are greyed out and cannot be cast.
- **Ancient Relic** — grants a random perk.
- **Divine Spark** — grants 500 God XP.
- **Cache of Supplies** — drops a spread of resources near your camp/castle over time. (Or center of map if you don't have a camp/castle)

### Pure Essence (Hunt goal)

If the multiworld is configured with the Hunt victory condition, Pure Essence items are placed in the item pool. Collect enough of them to trigger the win condition.

## Save data

AP state (received items, completed tiers, item index) is saved separately from the game's own save in the game directory under:

```
ap_profiles/profile<N>/ap_config.json   ← connection settings
ap_profiles/profile<N>/ap_state.json    ← received items and check progress
```

This data is never rolled back when you load a save — if you received an item, you keep it.

## Building from source

Requires `Core.jar` from the game directory. The pom defaults to `../../RtR` relative to the project root, or override with:

```sh
mvn package -Drtr.home=/path/to/RtR
```

The output JAR is `target/Archipelago-0.1.jar`.
