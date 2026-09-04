package mindustrywarden;

import mindustry.Vars;

/**
 * The one place that decides whether Warden may act.
 *
 * <p>Every tool changes the world the game itself simulates: it deletes buildings, spawns
 * units, rewrites team rules. That is only ours to do on a world this game owns, which
 * means single player, or a game we host. Joined as a client on someone else's server, the
 * server is authoritative and none of this would apply anyway; what a client mod could
 * still reach there are the gaps the server validates poorly, which is griefing rather
 * than tooling.
 *
 * <p>The rule lives here rather than in each tool so that there is exactly one answer to
 * check, and one place to change if the answer ever needs to grow.
 */
public final class HostGuard {
    private HostGuard() {
    }

    /** True in single player and when hosting, false as a client and outside a game. */
    public static boolean allowed() {
        return Vars.state.isGame() && !Vars.net.client();
    }

    /** Why the tools are greyed out, in a sentence fit for the panel. */
    public static String refusal() {
        if (!Vars.state.isGame()) {
            return "Warden works on a running game. Load a save or a sector first.";
        }
        return "You are a client on someone else's server. Warden only acts on games you\n"
            + "host or play alone, because everything here rewrites the world the host\n"
            + "simulates.";
    }
}
