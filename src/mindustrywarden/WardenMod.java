package mindustrywarden;

import arc.Core;
import arc.Events;
import arc.input.KeyCode;
import arc.util.Log;
import mindustry.Vars;
import mindustry.game.EventType.ClientLoadEvent;
import mindustry.game.EventType.Trigger;
import mindustry.mod.Mod;
import mindustrywarden.tools.GameSpeed;
import mindustrywarden.tools.Snapshots;
import mindustrywarden.tools.UnitTuning;

/**
 * Host and sandbox tools for Mindustry, behind one key.
 *
 * <p>Warden acts on the world this game owns: single player, or a game it hosts. It has
 * nothing to say on someone else's server, and {@link HostGuard} says so in the panel
 * rather than failing quietly halfway through a capture.
 */
public class WardenMod extends Mod {
    /** Installed at startup and kept, because it owns the game clock for the session. */
    private final GameSpeed speed = new GameSpeed();

    /** Kept for the same reason: movement speed has to be written every tick. */
    private final UnitTuning tuning = new UnitTuning();

    /** Copies the sector on a timer, which is the only real safety net here. */
    private final Snapshots snapshots = new Snapshots();

    /** Keeps one key press from being read once per simulation pass. */
    private final FrameGate keys = new FrameGate();

    private WardenDialog dialog;

    @Override
    public void init() {
        Events.on(ClientLoadEvent.class, event -> {
            speed.install();
            dialog = new WardenDialog(speed, tuning, snapshots);
            hookUpdate();
            Log.info("[warden] ready, press right shift in a game");

            if (WardenSelfTest.requested()) {
                new WardenSelfTest(speed, tuning).run();
            }
            if (WardenInspect.requested()) {
                new WardenInspect().run();
            }
            if (WardenRestore.requested()) {
                new WardenRestore().run();
            }
        });
    }

    /**
     * Right shift opens and closes the panel.
     *
     * <p>Right rather than left: the game binds left shift itself, for unit commands and
     * for the zoom modifier, and a panel that opens mid-command is a panel in the way.
     *
     * <p>Guarded against the chat and the console: both take typed input, and a key that
     * opens a dialog mid-sentence eats the sentence.
     */
    private void hookUpdate() {
        Events.run(Trigger.update, () -> {
            // Before the key handling and its early returns: both of these have to run on
            // every frame, whatever the panel and the chat are doing. The panel being
            // open suspends fast forward, so a game crawling at 64x answers the mouse
            // again as soon as you open the thing that turns it back down.
            speed.update(dialog.isShown());
            tuning.update();
            snapshots.update();

            // Once per frame, not once per pass. Fast forward runs the world several
            // times per frame and this listener with it, while keyTap stays true for the
            // whole frame: without the gate, one press toggled the panel once per pass
            // and the window only ever flashed.
            if (!keys.firstThisFrame()) {
                return;
            }
            if (Vars.state.isMenu() || Vars.ui.chatfrag.shown() || Vars.ui.consolefrag.shown()) {
                return;
            }
            if (!Core.input.keyTap(KeyCode.shiftRight)) {
                return;
            }
            if (dialog.isShown()) {
                dialog.hide();
            } else {
                dialog.open();
            }
        });
    }
}
