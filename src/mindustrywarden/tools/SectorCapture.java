package mindustrywarden.tools;

import arc.struct.Seq;
import mindustry.Vars;
import mindustry.content.Blocks;
import mindustry.game.Team;
import mindustry.game.Teams.TeamData;
import mindustry.gen.Building;
import mindustry.gen.Groups;
import mindustry.gen.Unit;

/**
 * Hand the current map to your team, without playing the assault.
 *
 * <p>This deliberately does not declare a winner. It removes what stands in the way of
 * winning, and lets the game reach its own conclusion on the next tick, which is what
 * makes the capture count in the campaign and stay consistent for everyone connected.
 * Forcing a game over instead would leave enemy cores standing on a map marked as won.
 *
 * <p>The game's own condition, in {@code Logic.checkGameState}, is one of two:
 *
 * <pre>
 * waves    && enemies == 0 && wave >= winWave && !spawning
 * attack   && the wave team has no core left
 * </pre>
 *
 * <p>Both halves matter, because a procedurally generated sector is usually the first
 * kind and not the second: it has enemy spawn points rather than an enemy base, so
 * deleting every building leaves the win condition untouched and the waves still coming.
 */
public final class SectorCapture {
    /**
     * Breathing room, in ticks, pushed onto the wave timer before killing.
     *
     * <p>A wave landing between the kill and the game's next check would put an enemy
     * back on the board, and the capture would silently not fire. That is exactly what
     * happened on the first run: the base went, the wave arrived, nothing was captured.
     */
    private static final float grace = 60f * 60f;

    /** What a capture did, and what the game is still waiting for. */
    public static final class Result {
        public final int blocks;
        public final int units;
        public final boolean waveSkipped;
        public final boolean anythingToDo;

        Result(int blocks, int units, boolean waveSkipped) {
            this.blocks = blocks;
            this.units = units;
            this.waveSkipped = waveSkipped;
            this.anythingToDo = blocks > 0 || units > 0 || waveSkipped;
        }
    }

    public Result run() {
        Team mine = Vars.player.team();

        // The timer first: everything below is pointless if a wave spawns behind it.
        if (Vars.state.rules.waves) {
            Vars.state.wavetime = Math.max(Vars.state.wavetime, grace);
        }

        int blocks = 0;
        for (TeamData data : Vars.state.teams.active.copy()) {
            if (data.team == mine || data.team == Team.derelict) {
                continue;
            }
            // Copied before walking: removing a building takes it out of this very list.
            for (Building building : data.buildings.copy()) {
                // setNet rather than setBlock: on a hosted game this is the call that
                // reaches the other players, so their world matches ours.
                building.tile.setNet(Blocks.air);
                blocks++;
            }
        }

        // Units come from the global group rather than from each team's list. The team
        // lists are what the first version used, and they missed what the spawners had
        // just produced: an enemy the game still counted, and one enemy is enough to
        // hold the capture back forever.
        Seq<Unit> doomed = new Seq<>();
        Groups.unit.each(unit -> {
            if (unit.team() != mine && unit.team() != Team.derelict) {
                doomed.add(unit);
            }
        });
        for (Unit unit : doomed) {
            unit.kill();
        }

        boolean waveSkipped = false;
        // A wave sector is won at its winning wave, so the counter is the other half of
        // the condition. Sectors without one are won by emptying the map alone.
        if (Vars.state.rules.winWave > 0 && Vars.state.wave < Vars.state.rules.winWave) {
            Vars.state.wave = Vars.state.rules.winWave;
            waveSkipped = true;
        }

        return new Result(blocks, doomed.size, waveSkipped);
    }

    /**
     * What the game is still waiting for, or null when nothing is.
     *
     * <p>Called a moment after a capture: a run that removed everything and still did not
     * capture has a reason, and showing it beats a player clicking the same button again.
     */
    public String blocking() {
        if (Vars.state.gameOver || !Vars.state.isCampaign()) {
            return null;
        }
        if (Vars.state.enemies > 0) {
            return Vars.state.enemies + " enemy units are still alive.";
        }
        if (Vars.state.rules.attackMode && Vars.state.rules.waveTeam.isAlive()) {
            return "The enemy team still holds a core somewhere on this map.";
        }
        if (!Vars.state.rules.attackMode && Vars.state.rules.winWave <= 0) {
            return "This sector has no winning wave and no enemy core, so the game has no\n"
                + "capture condition to satisfy. Nothing here can capture it.";
        }
        return null;
    }
}
