package mindustrywarden.tools;

import arc.Core;
import arc.Events;
import arc.struct.ObjectIntMap;
import arc.struct.Seq;
import mindustry.Vars;
import mindustry.game.EventType.PlayerChatEvent;
import mindustry.gen.Call;

/**
 * Reads the chat in your language, and answers in the room's.
 *
 * <p>Neither language is configured. Yours is the one the panel already speaks. The
 * room's is worked out from what has been said in it: every incoming line is passed
 * through {@link LanguageGuess}, the last twenty answers are kept, and the one that comes
 * up most is what the room speaks. Joining a Russian server and typing French sends
 * Russian, and none of that took a setting.
 *
 * <p>A count rather than the last line seen, because a room is not its most recent
 * sentence: one English "hi" in an otherwise Russian channel should not redirect
 * everything that follows.
 *
 * <p>Incoming lines are printed underneath the original, never in place of it: a
 * translation is a guess and a reader should be able to check it. Outgoing lines are sent
 * a second time, translated, because the game announces a message after it has gone
 * rather than before, so a second line is the only thing on offer.
 */
public final class ChatTranslation {
    private static final String enabledSetting = "warden-chat-on";
    private static final String forcedSetting = "warden-chat-forced";

    /** How many recent lines decide what the room speaks. */
    private static final int memory = 20;

    /** Below this many known lines, the room has not said enough to be called anything. */
    private static final int enough = 2;

    private final Translator translator = new Translator();
    private final LanguageGuess guess = new LanguageGuess();

    /** Languages of the last lines by other people, newest last. */
    private final Seq<String> heard = new Seq<>();

    /** Guards against translating a translation we just sent ourselves. */
    private String lastSent = "";

    public void install() {
        Events.on(PlayerChatEvent.class, event -> {
            if (event.player == null || event.message == null || event.message.isEmpty()) {
                return;
            }
            if (event.message.startsWith("/")) {
                return;
            }

            // By id, not by instance: on a server the event does not have to carry the
            // same object as the local player, and mistaking your own line for someone
            // else's means translating your own translation, on a loop.
            boolean mine = Vars.player != null && event.player.id == Vars.player.id;
            if (mine) {
                sendTranslation(event.message);
            } else {
                remember(event.message);
                showTranslation(event.player.name, event.message);
            }
        });
    }

    /** Keep track of what language this room speaks, one line at a time. */
    private void remember(String message) {
        String language = guess.of(message);
        if (language == null || language.equals(mine())) {
            return;
        }

        heard.add(language);
        if (heard.size > memory) {
            heard.remove(0);
        }
    }

    /**
     * What the room speaks, or null when it has not said enough to tell.
     *
     * <p>Yours never counts: the point is the language you would otherwise not be
     * understood in.
     */
    public String roomLanguage() {
        String forced = Core.settings.getString(forcedSetting, "");
        if (!forced.isEmpty()) {
            return forced;
        }
        if (heard.size < enough) {
            return null;
        }

        ObjectIntMap<String> counts = new ObjectIntMap<>();
        for (String language : heard) {
            counts.increment(language);
        }

        String best = null;
        int bestCount = 0;
        for (String language : counts.keys()) {
            int count = counts.get(language, 0);
            if (count > bestCount) {
                bestCount = count;
                best = language;
            }
        }
        return best;
    }

    /** How many of the remembered lines are in the room's language, for the panel. */
    public int agreeing() {
        String room = roomLanguage();
        if (room == null) {
            return 0;
        }
        int count = 0;
        for (String language : heard) {
            if (room.equals(language)) {
                count++;
            }
        }
        return count;
    }

    public int heardCount() {
        return heard.size;
    }

    private void showTranslation(String sender, String message) {
        if (!enabled()) {
            return;
        }
        String language = guess.of(message);
        if (language != null && language.equals(mine())) {
            return;
        }

        translator.translate(message, language, mine(), translated ->
            Core.app.post(() -> Vars.ui.chatfrag.addMessage(
                "[lightgray]" + sender + "[white]: " + translated)));
    }

    private void sendTranslation(String message) {
        if (!enabled() || message.equals(lastSent)) {
            return;
        }
        String room = roomLanguage();
        if (room == null || room.equals(mine())) {
            return;
        }

        translator.translate(message, mine(), room, translated -> Core.app.post(() -> {
            lastSent = translated;
            Call.sendChatMessage(translated);
        }));
    }

    /** Your language, which is the one the panel is already in. */
    public String mine() {
        return mindustrywarden.Lang.language();
    }

    public boolean enabled() {
        return Core.settings.getBool(enabledSetting, false);
    }

    public void enabled(boolean value) {
        Core.settings.put(enabledSetting, value);
    }

    /** An override, for the times the room is harder to read than usual. */
    public String forced() {
        return Core.settings.getString(forcedSetting, "");
    }

    public void forced(String value) {
        Core.settings.put(forcedSetting, value);
    }

    /** Drop what was heard, for when the panel is used to change rooms. */
    public void forget() {
        heard.clear();
    }
}
