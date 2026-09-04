package mindustrywarden;

import arc.Core;
import arc.util.Log;
import mindustry.Vars;
import mindustry.io.SaveIO;
import mindustrywarden.tools.BasePlans;
import mindustrywarden.tools.Rubble;
import mindustrywarden.tools.SectorCapture;

/**
 * Runs the whole restore on a save file, without anyone playing it.
 *
 * <p>Same sequence as the panel's one button, driven from the command line: read a save,
 * clear the enemy, the rubble and the fires, put every remembered block back, write the
 * result out. It exists because the panel needs a player in front of it, and repairing a
 * base is exactly the kind of long, mechanical job worth doing while its owner is away.
 *
 * <p>Reads {@code WARDEN_RESTORE} and writes {@code WARDEN_RESTORE_OUT}. Never point the
 * output at a file a running game owns.
 */
final class WardenRestore {
    static boolean requested() {
        return System.getenv("WARDEN_RESTORE") != null;
    }

    void run() {
        String input = System.getenv("WARDEN_RESTORE");
        String output = System.getenv("WARDEN_RESTORE_OUT");
        Log.info("[restore] reading @", input);

        try {
            SaveIO.load(Core.files.absolute(input));
        } catch (Throwable error) {
            Log.err("[restore] could not read that save", error);
            Core.app.exit();
            return;
        }

        // Without a game to join, the local player has no team of its own, and every tool
        // here asks "which side am I on" before it does anything.
        Vars.player.team(Vars.state.rules.defaultTeam);
        Log.info("[restore] team @, @ plans to place",
            Vars.player.team().name, new BasePlans().plans().size);

        // A control run, to tell what the tools change from what the load-and-write cycle
        // changes on its own. Worth six lines: without it, every difference in the result
        // gets blamed on whichever tool ran last.
        if (System.getenv("WARDEN_RESTORE_DRY") != null) {
            Log.info("[restore] dry run, wave @, writing untouched", Vars.state.wave);
            SaveIO.save(Core.files.absolute(output));
            Core.app.exit();
            return;
        }

        // Without the wave jump: repairing a base is not capturing a sector, and the
        // counter also lives in the sector's own info, so putting it back afterwards does
        // not stick anyway.
        SectorCapture.Result removed = new SectorCapture().run(false);
        Rubble rubble = new Rubble();
        int rubbleGone = rubble.clear();
        int fires = rubble.extinguish();

        BasePlans plans = new BasePlans();
        int cleared = plans.clearBlockers(true);
        int placed = plans.placeAll();

        Log.info("[restore] enemy @ blocks, rubble @, fires @, covering @, placed @",
            removed.blocks, rubbleGone, fires, cleared, placed);
        Log.info("[restore] @ plans left", plans.plans().size);

        try {
            SaveIO.save(Core.files.absolute(output));
            Log.info("[restore] written to @", output);
        } catch (Throwable error) {
            Log.err("[restore] could not write the result", error);
        }

        Core.app.exit();
    }
}
