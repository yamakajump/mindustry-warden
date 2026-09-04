package mindustrywarden;

import arc.Core;
import arc.util.Log;
import arc.util.Time;
import mindustry.Vars;
import mindustry.content.Blocks;
import mindustry.game.Gamemode;
import mindustry.game.Rules;
import mindustry.game.Team;
import mindustry.type.Item;
import arc.struct.Seq;
import mindustry.world.Tile;
import mindustrywarden.tools.BasePlans;
import mindustrywarden.tools.GameSpeed;
import mindustrywarden.tools.Rubble;
import mindustrywarden.tools.Snapshots;
import mindustrywarden.tools.SectorCapture;
import mindustrywarden.tools.Supplies;
import mindustrywarden.tools.UnitSpawner;
import mindustrywarden.tools.UnitTuning;

/**
 * Drives every tool in a throwaway world and prints what happened.
 *
 * <p>A client mod cannot be tested any other way. Its tools all reach into a running
 * game, so a unit test would be testing mocks, and the alternative is asking a human to
 * click five tabs after every change. That alternative shipped three broken versions in
 * one evening, which is what this exists to stop.
 *
 * <p>Runs only when {@code WARDEN_SELFTEST=1} is in the environment, so a player never
 * meets it. See the README for the command.
 */
final class WardenSelfTest {
    private final GameSpeed speed;
    private final UnitTuning tuning;
    private final mindustrywarden.tools.ChatTranslation chat;

    private int failures;

    WardenSelfTest(GameSpeed speed, UnitTuning tuning,
        mindustrywarden.tools.ChatTranslation chat) {
        this.speed = speed;
        this.tuning = tuning;
        this.chat = chat;
    }

    static boolean requested() {
        return "1".equals(System.getenv("WARDEN_SELFTEST"));
    }

    void run() {
        Log.info("[selftest] building a world");

        Vars.logic.reset();
        Vars.world.loadGenerator(120, 120, tiles ->
            tiles.each((x, y) -> tiles.set(x, y, new Tile(x, y, Blocks.stone, Blocks.air, Blocks.air))));

        Vars.state.rules = new Rules();
        Gamemode.survival.apply(Vars.state.rules);
        Vars.state.rules.waves = false;
        // Without a planet the item filter has nothing to filter against and lets
        // everything through, which would make the check below pass for the wrong reason.
        Vars.state.rules.planet = mindustry.content.Planets.serpulo;
        Vars.logic.play();

        // A core, because half the tools ask the player's team for one.
        Vars.world.tile(60, 60).setBlock(Blocks.coreShard, Team.sharded, 0);
        // And something hostile to capture, on the other side of the map.
        Vars.world.tile(20, 20).setBlock(Blocks.coreShard, Team.crux, 0);

        Core.app.post(this::exercise);
    }

    private void exercise() {
        check("a game is running", Vars.state.isGame());
        check("the guard allows single player", HostGuard.allowed());

        Supplies supplies = new Supplies();
        check("the team has a core", supplies.hasCore());
        check("copper belongs on this planet", supplies.belongsHere(mindustry.content.Items.copper));
        check("beryllium does not", !supplies.belongsHere(mindustry.content.Items.beryllium));

        int kinds = supplies.give(supplies.items(false), 500);
        check("items were given (" + kinds + " kinds)", kinds > 0);
        int foreign = 0;
        for (Item item : Vars.content.items()) {
            if (!supplies.belongsHere(item) && Vars.player.team().core().items.get(item) > 0) {
                foreign++;
            }
        }
        check("nothing from another planet was given", foreign == 0);

        UnitSpawner spawner = new UnitSpawner();
        int spawned = spawner.spawn(mindustry.content.UnitTypes.poly, Team.sharded, 3);
        check("3 units spawned (" + spawned + ")", spawned == 3);

        // A tick, because the team's unit list is rebuilt by updateTeamStats during the
        // game's own update and is still empty the instant after a spawn.
        Vars.logic.update();
        check("the team sees them (" + Vars.player.team().data().units.size + ")",
            Vars.player.team().data().units.size >= 3);

        // Movement speed, the one that silently did nothing before. The player has to be
        // in a unit for this: the tool accelerates who you are playing, and a test that
        // measures some other unit measures nothing.
        var unit = mindustry.gen.Groups.unit.find(candidate -> candidate.team() == Team.sharded);
        check("a unit to accelerate", unit != null);
        if (unit != null) {
            Vars.player.unit(unit);
            float base = unit.speed();

            tuning.unitSpeed(4f);
            tuning.update();
            Vars.logic.update();
            check("your own unit speeds up (" + unit.speed() + " vs " + base + ")",
                unit.speed() > base * 1.5f);

            tuning.unitSpeed(1f);
            Vars.logic.update();
            check("and it comes back down (" + unit.speed() + ")", unit.speed() <= base * 1.1f);

            // The other path: every unit of the team rather than only the player's.
            tuning.unitSpeed(4f);
            tuning.wholeTeam(true);
            tuning.update();
            Vars.logic.update();
            var other = mindustry.gen.Groups.unit.find(candidate ->
                candidate.team() == Team.sharded && candidate != unit);
            check("the whole team speeds up", other == null || other.speed() > base * 1.5f);
            tuning.wholeTeam(false);
            tuning.unitSpeed(1f);
            Vars.logic.update();
        }

        // Build and mine rates, which a player reported as doing nothing: the check is
        // that the game's own accessor reflects what the panel wrote.
        tuning.buildSpeed(4f);
        tuning.mineSpeed(8f);
        check("build speed reaches the game (" + Vars.state.rules.buildSpeed(Team.sharded) + ")",
            Vars.state.rules.buildSpeed(Team.sharded) == 4f);
        check("mine speed reaches the game (" + Vars.state.rules.unitMineSpeed(Team.sharded) + ")",
            Vars.state.rules.unitMineSpeed(Team.sharded) == 8f);
        check("and the panel reads them back", tuning.buildSpeed() == 4f && tuning.mineSpeed() == 8f);
        tuning.buildSpeed(1f);
        tuning.mineSpeed(1f);

        // Fast forward, and above all that it comes back.
        speed.multiplier(64f);
        long start = System.nanoTime();
        speed.update(false);
        long spent = (System.nanoTime() - start) / 1_000_000;
        check("64x stayed within its budget (" + spent + " ms)", spent < 60);
        check("it reports what it managed (" + speed.achieved() + "x)", speed.achieved() >= 1f);
        speed.update(true);
        check("the open panel suspends it", speed.achieved() == 1f);
        speed.multiplier(1f);

        // The panel key, which fast forward read once per simulation pass and turned into
        // a window flashing open and shut.
        FrameGate gate = new FrameGate();
        check("a key is read once per frame", gate.firstThisFrame() && !gate.firstThisFrame());

        BasePlans plans = new BasePlans();
        check("no plans in a fresh world", plans.plans().isEmpty());
        check("clearing nothing is harmless", plans.clearBlockers(false) >= 0);

        SectorCapture capture = new SectorCapture();
        SectorCapture.Result result = capture.run();
        check("the enemy core was removed (" + result.blocks + " blocks)", result.blocks > 0);
        check("the player core survived", Vars.player.team().core() != null);

        // Rubble, which nothing else touches: an eradicated base does not leave the map,
        // it changes hands to derelict and stays standing.
        Vars.world.tile(40, 40).setBlock(Blocks.titaniumWall, Team.derelict, 0);
        // A tick, because an entity group holds new members in a queue until it updates.
        Vars.logic.update();
        Rubble rubble = new Rubble();
        check("rubble is counted (" + rubble.count() + ")", rubble.count() > 0);
        check("rubble is removed", rubble.clear() > 0 && rubble.count() == 0);
        check("putting out fires is harmless with none", rubble.extinguish() == 0);

        // The whole restore sequence, in the order the single button runs it. Mostly a
        // guard against a class going missing from one of the four, which is what a
        // hot-swapped jar did to a live game.
        Vars.world.tile(30, 30).setBlock(Blocks.copperWall, Team.crux, 0);
        capture.run();
        rubble.clear();
        plans.clearBlockers(true);
        plans.placeAll();
        check("the restore sequence runs end to end", Vars.player.team().core() != null);

        // Snapshots, which need a save slot to copy: the whole point is a file on disk
        // taken before the damage, so a test without one proves nothing.
        Snapshots snapshots = new Snapshots();
        Vars.control.saves.addSave("warden-selftest");
        check("a slot to copy from", snapshots.available());
        check("a copy is taken", snapshots.capture() != null);
        check("and it is listed", !snapshots.list().isEmpty());
        check("its age reads as recent", snapshots.minutesOld(snapshots.list().first()) == 0);

        // Language detection, which decides what the room speaks and therefore what
        // every outgoing line is translated into. Samples taken from a real server's chat.
        var guess = new mindustrywarden.tools.LanguageGuess();
        check("russian (" + guess.of("они все в нулевые корды летят") + ")",
            "ru".equals(guess.of("они все в нулевые корды летят")));
        check("ukrainian (" + guess.of("це не працює правильно їх") + ")",
            "uk".equals(guess.of("це не працює правильно їх")));
        check("chinese (" + guess.of("我翻译一下有点听不懂") + ")",
            "zh-CN".equals(guess.of("我翻译一下有点听不懂")));
        check("japanese (" + guess.of("これはテストですね") + ")",
            "ja".equals(guess.of("これはテストですね")));
        check("french (" + guess.of("je vais faire la base avec toi") + ")",
            "fr".equals(guess.of("je vais faire la base avec toi")));
        check("english (" + guess.of("i try to creat the big exporting base") + ")",
            "en".equals(guess.of("i try to creat the big exporting base")));
        check("noise is not a language", guess.of("gg") == null && guess.of(":)") == null);
        check("the table is not empty", guess.knownLanguages() > 10);

        // The translator, which is the one tool here that depends on something outside
        // the machine. A failure prints rather than fails the run: no network is a fair
        // reason for this check not to answer, and it must not block the rest.
        var translator = new mindustrywarden.tools.Translator();
        translator.translate("hello there", "en", "fr",
            translated -> Log.info("[selftest] ok   english to french: @", translated));
        // The case that actually matters: a real line from a Russian server.
        translator.translate("они все в нулевые корды летят", "ru", "fr",
            translated -> Log.info("[selftest] ok   russian to french: @", translated));

        // The chat, end to end: a real line from a Russian server, put in the way a
        // server puts it, then read, translated and rewritten in place. This is the path
        // that quietly did nothing when it was built on the game's chat event, which
        // servers formatting their own messages never fire.
        var spoken = mindustrywarden.tools.ChatTranslation.spoken("[coral][Yras][]: привет всем");
        check("plain line (" + spoken + ")", "привет всем".equals(spoken));
        var tagged = mindustrywarden.tools.ChatTranslation.spoken("<T> [Yras]: надо всеь");
        check("tagged line (" + tagged + ")", "надо всеь".equals(tagged));
        var admin = mindustrywarden.tools.ChatTranslation.spoken("<A> [Kefir 225 Sector]: Да");
        check("admin line (" + admin + ")", "Да".equals(admin));
        check("a notice is not speech",
            mindustrywarden.tools.ChatTranslation.spoken("yamakajump has connected.") == null);

        // The watcher, in one frame. It cannot be checked across frames here: the chat
        // fragment empties itself whenever the network is idle
        // (ChatFragment: "if(!net.active() && messages.size > 0) clearMessages()"), so in
        // single player a line does not survive to the next frame. On a server, which is
        // the only place this feature runs, it does.
        check("the chat can be read", chat.canRead());
        chat.enabled(true);

        var watcher = new mindustrywarden.tools.ChatWatcher();
        watcher.poll((index, line) -> { });

        String line = "<T> [Yras]: они все в нулевые корды летят";
        Vars.ui.chatfrag.addMessage(line);

        Seq<String> caught = new Seq<>();
        watcher.poll((index, shown) -> caught.add(shown));
        check("the watcher saw the line (" + caught.size + ")", caught.contains(line));

        check("and can rewrite it in place",
            watcher.replace(line, line + " [lightgray]| ils volent tous vers zéro"));
        check("the rewrite is what the chat now holds",
            watcher.snapshot().contains(shown -> shown.contains("[lightgray]|")));

        Time.run(200f, () -> {
            Log.info(failures == 0
                ? "[selftest] PASS"
                : "[selftest] FAIL, " + failures + " checks failed");
            Core.app.exit();
        });
    }

    private void check(String what, boolean ok) {
        Log.info((ok ? "[selftest] ok   " : "[selftest] FAIL ") + what);
        if (!ok) {
            failures++;
        }
    }
}
