# Warden

Host and sandbox tools for Mindustry, behind one key.

Warden acts on the world your game owns: single player, or a game you host. Press **right
shift**, and a panel gives you the four things a host actually needs and the game does not
offer. It borrows the game's own icons, styles and colours, so it looks like part of it
rather than a foreign window.

Built against Mindustry **v159.7**.

## What it does

| Tab | What it does |
|---|---|
| **Capture** | Removes every enemy building and unit, holds the wave timer back, and lets the game declare the win itself, which is what makes a campaign sector count as captured. A live panel shows the win condition and what is still standing in its way. |
| **Recover** | Brings a griefed base back. The game files every destroyed building of yours, with its configuration, and keeps the list in the save; this clears whatever was built on top of them, queues them all for rebuild, and can export them as schematics to rebuild elsewhere. |
| **Units** | Spawns any unit, for any team, in batches. |
| **Supplies** | Fills the core with every item, and unlocks the research tree. |
| **Speed** | Runs the simulation from 0.25x to 3x, and makes your blocks and units invulnerable. |

## What it does not do

Warden refuses to act when you are a client on someone else's server, and says so instead
of trying. That is not caution for its own sake: a remote server is authoritative, so the
only things a client mod could still reach there are the gaps that server validates
poorly. Reaching them is griefing, not tooling, and it is not what this is for.

Two behaviours worth knowing before you use them:

- **Unlocking research is stored on your profile**, not in the save. It stays unlocked in
  every campaign afterwards, and there is no undo. The panel asks first.
- **Invulnerability is written into the save**, because it is a team rule and rules travel
  with the save. Turn it off before you put the game away.

Speed stops at 3x because the game clamps its own frame step at 3. Above that, a unit
crosses a wall between two ticks instead of hitting it, so there is no button for it.

## Install

Drop `mindustry-warden.jar` into the game's `mods/` folder and restart:

| Platform | Folder |
|---|---|
| Windows (Steam) | `Steam/steamapps/common/Mindustry/saves/mods/` |
| Windows (standalone) | `%APPDATA%/Mindustry/mods/` |
| Linux | `~/.local/share/Mindustry/mods/` |
| macOS | `~/Library/Application Support/Mindustry/mods/` |

The mod is marked hidden, so it stays out of the multiplayer mod comparison: carrying it
will not get you refused from a server it cannot affect anyway.

## Build

Needs a JDK 17 and nothing else; Gradle pulls the game's classes itself.

```bash
./gradlew jar     # build/libs/mindustry-warden.jar
```

## Licence

MIT.
