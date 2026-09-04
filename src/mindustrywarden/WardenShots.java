package mindustrywarden;

import arc.Core;
import arc.util.Log;
import arc.util.ScreenUtils;
import arc.util.Time;
import mindustry.Vars;
import mindustry.content.Blocks;
import mindustry.game.Gamemode;
import mindustry.game.Rules;
import mindustry.game.Team;

/**
 * Opens every section of the panel and photographs it.
 *
 * <p>The self-test proves the tools work; nothing proved the panel was worth looking at,
 * and a screenshot round trip through the person who asked for the redesign is a slow way
 * to find out that a column is too narrow. This builds a throwaway world, walks the
 * sections and writes a PNG of each, so the layout can be judged before anyone installs
 * it.
 *
 * <p>Runs on {@code WARDEN_SHOT=<folder>}.
 */
final class WardenShots {
    private final WardenDialog dialog;

    WardenShots(WardenDialog dialog) {
        this.dialog = dialog;
    }

    static boolean requested() {
        return System.getenv("WARDEN_SHOT") != null;
    }

    void run() {
        // Without a world the host guard refuses, which is the same state as being a
        // guest on someone's server: the cheapest way to photograph what a guest sees.
        if (System.getenv("WARDEN_SHOT_LOCKED") != null) {
            Log.info("[shots] locked, no world");
            Core.app.post(() -> shoot(0));
            return;
        }

        Log.info("[shots] building a world");

        Vars.logic.reset();
        Vars.world.loadGenerator(120, 120, tiles ->
            tiles.each((x, y) -> tiles.set(x, y,
                new mindustry.world.Tile(x, y, Blocks.stone, Blocks.air, Blocks.air))));

        Vars.state.rules = new Rules();
        Gamemode.survival.apply(Vars.state.rules);
        Vars.state.rules.waves = false;
        Vars.state.rules.planet = mindustry.content.Planets.serpulo;
        Vars.logic.play();

        Vars.world.tile(60, 60).setBlock(Blocks.coreShard, Team.sharded, 0);
        Vars.world.tile(20, 20).setBlock(Blocks.coreShard, Team.crux, 0);
        // Some rubble and a plan, so the cards have numbers rather than zeroes.
        Vars.world.tile(40, 40).setBlock(Blocks.titaniumWall, Team.derelict, 0);

        // A small window is where a fixed width shows itself, so the pass can ask for
        // one: WARDEN_SHOT_SIZE=1000x700.
        String size = System.getenv("WARDEN_SHOT_SIZE");
        if (size != null && size.contains("x")) {
            String[] parts = size.split("x");
            Core.graphics.setWindowSize(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
            Log.info("[shots] window @", size);
        }

        Core.app.post(() -> shoot(0));
    }

    /**
     * One section per pass, a few frames apart.
     *
     * <p>Not in a loop: the panel is drawn by the game's own frame, so a loop would
     * photograph the same image seven times.
     */
    private void shoot(int index) {
        if (index >= dialog.tabCount()) {
            Log.info("[shots] done");
            Core.app.exit();
            return;
        }

        dialog.openTab(index);

        Time.run(12f, () -> {
            String name = dialog.tabName(index);
            ScreenUtils.saveScreenshot(
                Core.files.absolute(System.getenv("WARDEN_SHOT")).child(index + "-" + name + ".png"));
            Log.info("[shots] @", name);
            shoot(index + 1);
        });
    }
}
