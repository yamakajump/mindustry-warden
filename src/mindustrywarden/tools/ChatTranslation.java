package mindustrywarden.tools;

import arc.Core;
import arc.Events;
import arc.struct.ObjectIntMap;
import arc.struct.ObjectSet;
import arc.struct.Seq;
import arc.util.Log;
import mindustry.Vars;
import mindustry.game.EventType.PlayerChatEvent;
import mindustry.gen.Call;

/**
 * Reads the chat in your language, and answers in the room's.
 *
 * <p>Neither language is configured. Yours is the one the panel already speaks. The
 * room's is worked out from what is said in it: every line is passed through
 * {@link LanguageGuess}, the last twenty answers are kept, and the one that comes up most
 * is what the room speaks. Join a Russian server, type French, Russian goes out.
 *
 * <p>A count rather than the last line seen, because a room is not its most recent
 * sentence: one English "hi" in an otherwise Russian channel should not redirect
 * everything that follows.
 *
 * <p>Incoming lines are read from the chat itself rather than from the game's chat event,
 * because that event only fires for messages that arrive with a sender attached, and a
 * server that formats its own messages sends them without one. See {@link ChatWatcher}.
 * The translation then replaces the line in place, which is why it is read from there and
 * not merely listened to.
 */
public final class ChatTranslation {
    /**
     * What separates a line from its translation, and marks it as already done.
     *
     * <p>Plain text on purpose. A colour tag here would be handed to the translator on
     * any second pass and come back as a translated tag.
     */
    private static final String marker = " | ";

    private static final String enabledSetting = "warden-chat-on";
    private static final String forcedSetting = "warden-chat-forced";

    /** How many recent lines decide what the room speaks. */
    private static final int memory = 20;

    /** Below this many known lines, the room has not said enough to be called anything. */
    private static final int enough = 2;

    private final Translator translator = new Translator();
    private final LanguageGuess guess = new LanguageGuess();
    private final ChatWatcher watcher = new ChatWatcher();

    /** Languages of the last lines seen, newest last. */
    private final Seq<String> heard = new Seq<>();

    /** Lines already handled, so a rewritten line is not read as a new one. */
    private final ObjectSet<String> handled = new ObjectSet<>();

    /** Guards against translating a translation we just sent ourselves. */
    private String lastSent = "";

    public void install() {
        // Outgoing lines still come from the event: it is the only place that says a
        // message is ours, and ours is the only one worth sending twice.
        Events.on(PlayerChatEvent.class, event -> {
            if (event.player == null || event.message == null || event.message.isEmpty()) {
                return;
            }
            if (event.message.startsWith("/")) {
                return;
            }
            if (Vars.player != null && event.player.id == Vars.player.id) {
                sendTranslation(event.message);
            }
        });
    }

    /** Called once per frame: reads what the chat has shown since last time. */
    public void update() {
        if (!Vars.state.isGame()) {
            return;
        }

        watcher.poll((index, line) -> {
            // A line we already rewrote, whatever brought it back around. Without this
            // the marker itself gets translated, and "[lightgray]" comes back as
            // "[blanc]" in the middle of the chat.
            if (line.contains(marker)) {
                return;
            }

            String text = spoken(line);
            if (text == null || handled.contains(line)) {
                return;
            }

            String language = guess.of(text);
            remember(language);

            if (!enabled()) {
                return;
            }
            if (language == null) {
                Log.info("[warden] no language for: @", text);
                return;
            }
            if (language.equals(mine())) {
                return;
            }

            handled.add(line);
            Log.info("[warden] @ -> @: @", language, mine(), text);

            translator.translate(text, language, mine(), translated -> Core.app.post(() -> {
                Log.info("[warden] got: @", translated);
                String rewritten = line + marker + translated;
                if (watcher.replace(line, rewritten)) {
                    handled.add(rewritten);
                } else {
                    Log.info("[warden] line vanished before it could be rewritten");
                }
            }));
        });
    }

    /**
     * The part of a chat line someone actually said.
     *
     * <p>A line arrives formatted, name and colour tags included: {@code [coral][Yras][]:
     * привет}. Translating the whole thing would translate the name and mangle the tags,
     * so only what follows the last "]:" is taken. That also handles the tags servers
     * put in front, {@code <T>} and {@code <A>} and the like, since they sit before the
     * name and the name's bracket is still the last one.
     *
     * <p>A line without that shape is a server notice rather than speech, "has connected"
     * and such, and is left alone.
     */
    public static String spoken(String line) {
        int mark = line.lastIndexOf("]:");
        if (mark < 0 || mark + 2 >= line.length()) {
            return null;
        }
        // Colour tags stripped before anything is sent: a translator handed
        // "[lightgray]" translates it, and the word lands in the middle of the chat.
        String text = withoutTags(line.substring(mark + 2)).trim();
        return text.isEmpty() ? null : text;
    }

    /**
     * Drop every {@code [tag]} from a piece of text.
     *
     * <p>Written by hand rather than with a regular expression: the pattern for it is
     * four escaped brackets deep, and this reads as what it does.
     */
    private static String withoutTags(String text) {
        StringBuilder out = new StringBuilder(text.length());
        boolean inside = false;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '[') {
                inside = true;
            } else if (c == ']') {
                inside = false;
            } else if (!inside) {
                out.append(c);
            }
        }
        return out.toString();
    }

    private void remember(String language) {
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

    /** Whether the chat can be read at all, which the panel says rather than hides. */
    public boolean canRead() {
        return watcher.available();
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
        handled.clear();
    }
}
