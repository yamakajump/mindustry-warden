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

    private WardenDialog dialog;

    @Override
    public void init() {
        Events.on(ClientLoadEvent.class, event -> {
            speed.install();
            dialog = new WardenDialog(speed);
            bindKey();
            Log.info("[warden] ready, press right shift in a game");
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
    private void bindKey() {
        Events.run(Trigger.update, () -> {
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
