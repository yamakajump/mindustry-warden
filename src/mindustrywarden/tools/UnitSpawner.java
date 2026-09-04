package mindustrywarden.tools;

import arc.math.Mathf;
import arc.struct.Seq;
import mindustry.Vars;
import mindustry.game.Team;
import mindustry.type.UnitType;

/**
 * Put units on the map, of any type, for any team.
 *
 * <p>They land around the player rather than under the cursor: the panel is open while
 * spawning, so the cursor is on a button and not on the world.
 */
public final class UnitSpawner {
    /** How far around the player units are scattered, in world units. */
    private static final float spread = 24f;

    /** Every unit worth offering, in the order the game lists them. */
    public Seq<UnitType> types() {
        // Internal types are the game's own plumbing (block units, effects) and crash or
        // do nothing when spawned on their own.
        return Vars.content.units().select(type -> !type.internal);
    }

    /** Teams a player would plausibly spawn for: their own, and the ones on the map. */
    public Seq<Team> teams() {
        Seq<Team> teams = Seq.with(Vars.player.team());
        for (Team team : Team.baseTeams) {
            if (!teams.contains(team)) {
                teams.add(team);
            }
        }
        return teams;
    }

    /**
     * Spawn {@code count} units and return how many the game accepted.
     *
     * <p>The count can come back short: a team at its unit cap silently refuses the
     * spawn, and saying so is better than pretending ten arrived.
     */
    public int spawn(UnitType type, Team team, int count) {
        int spawned = 0;
        for (int i = 0; i < count; i++) {
            if (type.spawn(team,
                Vars.player.x + Mathf.range(spread),
                Vars.player.y + Mathf.range(spread)) != null) {
                spawned++;
            }
        }
        return spawned;
    }
}
