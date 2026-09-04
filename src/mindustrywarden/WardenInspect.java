package mindustrywarden;

import arc.Core;
import arc.struct.ObjectIntMap;
import arc.util.Log;
import mindustry.Vars;
import mindustry.game.Team;
import mindustry.game.Teams.TeamData;
import mindustry.gen.Groups;
import mindustry.io.SaveIO;

/**
 * Prints what a save file actually contains, then quits.
 *
 * <p>Written because a conversation about "is my base back yet" cannot be settled by
 * looking at a screenshot: what matters is how many block plans the save still holds for
 * each team, and that number is not on screen anywhere. Reading the file with the game's
 * own loader answers it in one run, without touching the copy being played.
 *
 * <p>Point {@code WARDEN_INSPECT} at a copy of a {@code .msav}, never at the file a
 * running game is writing to.
 */
final class WardenInspect {
    static boolean requested() {
        return System.getenv("WARDEN_INSPECT") != null;
    }

    void run() {
        String path = System.getenv("WARDEN_INSPECT");
        Log.info("[inspect] reading @", path);

        try {
            SaveIO.load(Core.files.absolute(path));
        } catch (Throwable error) {
            Log.err("[inspect] could not read that save", error);
            Core.app.exit();
            return;
        }

        Log.info("[inspect] map @ x @, wave @, attack=@",
            Vars.world.width(), Vars.world.height(), Vars.state.wave, Vars.state.rules.attackMode);

        // Counted from the global building group rather than from the active teams:
        // derelict is never active, so a map covered in rubble reads as empty from the
        // team list. That is exactly how a wrong answer was given once already.
        ObjectIntMap<Team> buildings = new ObjectIntMap<>();
        Groups.build.each(building -> buildings.increment(building.team));

        for (Team team : Team.all) {
            int count = buildings.get(team, 0);
            TeamData data = Vars.state.teams.getOrNull(team);
            int plans = data == null ? 0 : data.plans.size;
            if (count > 0 || plans > 0) {
                Log.info("[inspect] team @: @ buildings, @ remembered plans",
                    team.name, count, plans);
            }
        }

        Core.app.exit();
    }
}
