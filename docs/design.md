# Warden, design

Written 2026-09-04, before the first line of code.

## Why it exists

A sector was lost to a griefed reactor, and the game deletes a lost sector's save the
instant it announces the loss: the `.msav`, its rotating backup and its preview go
together, before a player can pause. Recapturing an eradication sector by hand is hours of
assault for a map that was already beaten once.

The tools that would have made this a five-minute problem do not exist for v8.
[super-cheat](https://github.com/abomb4/super-cheat) and
[hackustry](https://github.com/QmelZ/hackustry) are both stuck on v6 and abandoned;
[Foo's client](https://github.com/mindustry-antigrief/mindustry-client) is a whole custom
client rather than a mod, and aims at anti-grief rather than hosting. So: a small Java mod
against 159.7.

## Scope

Warden acts on the world the game owns, which is single player and hosted games. It does
not act as a client on someone else's server.

That line is drawn on a technical fact, not a mood. A remote server is authoritative over
the world, so a client mod cannot change it through supported means at all; what remains
are the gaps the server validates poorly, chiefly movement. Exploiting those is griefing,
and building it would be answering the incident that started this project by reproducing
it.

## Shape

```
WardenMod        entry point, owns the game clock, binds F9
WardenDialog     the panel, one tab per tool, rebuilt on every display
HostGuard        the single answer to "may Warden act right now"
tools/           one class per tool, no knowledge of the UI or of each other
```

The guard lives in one file rather than being repeated in four, so there is one place to
read and one place to change if the answer ever grows a case.

The tools do not share an interface. Their signatures genuinely differ, and an interface
wide enough to hold "capture this map" and "spawn 25 of this unit for that team" would
carry nothing.

## The tools

**Capture.** Removes enemy buildings and units, then stops. The game's own check declares
the win on the next tick: an attack sector is won when no enemy core remains, a survival
sector when the winning wave is reached with no enemies alive, so the survival case also
advances the wave counter. Going through the game's check is what makes the campaign
record the capture and what keeps a hosted game consistent. Forcing a game over instead
would leave enemy cores standing on a map marked as won.

Buildings go through `setNet`, which is the call that reaches other players.

**Units.** Any type except the internal ones, any base team, in batches, scattered around
the player. Reports what the game accepted rather than what was asked: a team at its unit
cap silently refuses spawns.

**Supplies.** Fills the core, and unlocks the tech tree behind a confirmation because
unlocking is stored on the profile and outlives the save.

**Speed and invulnerability.** Speed scales the frame delta and stops at 3x, the game's
own clamp, because collision assumes small steps. Invulnerability is a team rule
multiplier rather than an edit of every building: one number to put back, and rules
replicate to clients through `setRules`.

## Testing

The logic here is almost entirely calls into the running game, so unit tests would test
mocks rather than the game and prove nothing. The real test is the game itself: load a
throwaway map, exercise each tab, confirm the result on screen.

The capture is validated on a worthless map before it is ever pointed at a campaign
sector. It deletes buildings, and there is no undo for pointing it at the wrong world.

## Deliberately left out

Anything aimed at servers we do not host. Icons in the unit picker, which the first
version lists by name. A configurable key, which F9 covers until someone says otherwise.
