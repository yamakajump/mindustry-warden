package mindustrywarden.tools;

import mindustry.Vars;
import mindustry.content.Blocks;
import mindustry.game.Team;
import mindustry.game.Teams.TeamData;
import mindustry.gen.Building;
import mindustry.gen.Unit;

/**
 * Hand the current map to your team, without playing the assault.
 *
 * <p>This deliberately does not declare a winner. It removes what stands in the way of
 * winning, and lets the game reach its own conclusion on the next tick: an attack sector
 * is won when no enemy core is left, a survival sector when the winning wave is reached
 * with no enemies alive. Going through the game's own check is what makes the capture
 * count in the campaign, save the sector, and stay consistent for everyone connected.
 * Forcing a game over instead would leave enemy cores standing on a map marked as ours.
 */
public final class SectorCapture {
    /** What a capture actually removed, so the panel can say it rather than guess. */
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
        int blocks = 0;
        int units = 0;

        // Copied before walking: removing a building takes it out of the very list being
        // iterated, and killing a unit does the same to the other one.
        for (TeamData data : Vars.state.teams.active.copy()) {
            if (data.team == mine || data.team == Team.derelict) {
                continue;
            }

            for (Building building : data.buildings.copy()) {
                // setNet rather than setBlock: on a hosted game this is the call that
                // reaches the other players, so their world matches ours.
                building.tile.setNet(Blocks.air);
                blocks++;
            }

            for (Unit unit : data.units.copy()) {
                unit.kill();
                units++;
            }
        }

        boolean waveSkipped = false;
        // A survival sector has no enemy core to remove: it is won by surviving to a
        // given wave, so the wave counter is the thing standing in the way.
        if (!Vars.state.rules.attackMode
            && Vars.state.rules.winWave > 0
            && Vars.state.wave < Vars.state.rules.winWave) {
            Vars.state.wave = Vars.state.rules.winWave;
            waveSkipped = true;
        }

        return new Result(blocks, units, waveSkipped);
    }
}
