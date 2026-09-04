package mindustrywarden.tools;

import arc.Core;
import arc.struct.ObjectIntMap;
import arc.struct.ObjectSet;
import arc.struct.Seq;
import arc.scene.ui.TextField;
import arc.util.Log;
import mindustry.Vars;
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
    private static final String replaceSetting = "warden-chat-replace";

    /** How many recent lines decide what the room speaks. */
    private static final int memory = 20;

    /**
     * How many known lines it takes to name a room.
     *
     * <p>One. Waiting for two meant the first thing said after joining went out
     * untranslated, which is the moment it is most needed.
     */
    private static final int enough = 1;

    private final Translator translator = new Translator();
    private final LanguageGuess guess = new LanguageGuess();
    private final ChatWatcher watcher = new ChatWatcher();

    /** Languages of the last lines seen, newest last. */
    private final Seq<String> heard = new Seq<>();

    /** Lines already handled, so a rewritten line is not read as a new one. */
    private final ObjectSet<String> handled = new ObjectSet<>();

    /** Guards against translating a translation we just sent ourselves. */
    private String lastSent = "";

    /**
     * The last line of ours that was translated.
     *
     * <p>A second guard against sending twice. The first version had two ways of noticing
     * an outgoing line, the chat event and the chat itself, and on a server where both
     * work every message went out in duplicate.
     */
    private String lastTranslated = "";

    /** The chat's input box, borrowed once. */
    private TextField chatfield;

    /** Called once per frame: intercepts what is about to be sent, reads what arrived. */
    public void update() {
        if (!Vars.state.isGame()) {
            return;
        }

        interceptOutgoing();

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

            // Ours, spotted by the name in front of it. The chat event would say so more
            // cleanly, but it does not fire on a server that formats its own messages,
            // which is the same reason incoming lines are read from here.
            if (isMine(line)) {
                handled.add(line);
                sendTranslation(text);
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
     * Take a message out of the chat box before the game sends it, and send its
     * translation instead.
     *
     * <p>Only possible because of the order of things. The game reads the box, empties
     * it, and refuses to send an empty message, all inside its own update, which happens
     * after this one. Emptying the box here therefore means the game sends nothing, and
     * what goes out is whatever this sends in its place.
     *
     * <p>If the translation cannot be had, the original is sent unchanged. Swallowing a
     * message and then failing would leave the player having said nothing at all.
     */
    private void interceptOutgoing() {
        if (!enabled() || !replacing() || Vars.ui == null || !Vars.ui.chatfrag.shown()) {
            return;
        }
        if (!Core.input.keyTap(mindustry.input.Binding.chat)) {
            return;
        }

        TextField field = field();
        if (field == null) {
            return;
        }

        String text = field.getText().trim();
        if (text.isEmpty() || text.startsWith("/")) {
            return;
        }

        String room = roomLanguage();
        if (room == null || room.equals(mine())) {
            return;
        }

        String language = guess.of(text);
        if (language != null && !language.equals(mine())) {
            return;
        }

        field.setText("");
        Log.info("[warden] holding back, @ -> @: @", mine(), room, text);

        translator.translate(text, mine(), room,
            translated -> Core.app.post(() -> {
                Log.info("[warden] sending instead: @", translated);
                lastSent = translated;
                lastTranslated = translated;
                Call.sendChatMessage(translated);
            }),
            () -> Core.app.post(() -> {
                Log.info("[warden] no translation, sending the original");
                lastTranslated = text;
                Call.sendChatMessage(text);
            }));
    }

    /** The chat's own input box, which is private and has to be borrowed. */
    private TextField field() {
        if (chatfield != null) {
            return chatfield;
        }
        try {
            java.lang.reflect.Field found =
                Vars.ui.chatfrag.getClass().getDeclaredField("chatfield");
            found.setAccessible(true);
            chatfield = (TextField) found.get(Vars.ui.chatfrag);
        } catch (Throwable denied) {
            Log.info("[warden] chat box not reachable, messages will be sent as typed");
        }
        return chatfield;
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

    /**
     * Whether a chat line is one of ours.
     *
     * <p>By the name in front of the "]:", with colour tags removed from both sides: a
     * player name carries its own colours and the chat adds more around it.
     */
    private static boolean isMine(String line) {
        if (Vars.player == null) {
            return false;
        }
        int mark = line.lastIndexOf("]:");
        if (mark < 0) {
            return false;
        }

        String header = withoutTags(line.substring(0, mark));
        String me = withoutTags(Vars.player.name).trim();
        return !me.isEmpty() && header.contains(me);
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
        if (!enabled()) {
            return;
        }
        if (message.equals(lastSent) || message.equals(lastTranslated)) {
            // Either our own translation coming back around, or the same line reaching
            // here twice. Neither is something new to send.
            return;
        }
        lastTranslated = message;

        String room = roomLanguage();
        if (room == null) {
            Log.info("[warden] not sending a translation: the room has said nothing yet");
            return;
        }
        if (room.equals(mine())) {
            return;
        }

        // Only lines in our own language go out translated. This is what stops the loop:
        // a translation we sent comes back as one of our messages, already in the room's
        // language, and would otherwise be sent again, and again. Comparing against the
        // last text sent is not enough on its own, since a server free to add a prefix
        // makes the returning line different from the one that left.
        String language = guess.of(message);
        if (language != null && !language.equals(mine())) {
            return;
        }

        Log.info("[warden] mine, @ -> @: @", mine(), room, message);
        translator.translate(message, mine(), room, translated -> Core.app.post(() -> {
            Log.info("[warden] sending: @", translated);
            lastSent = translated;
            Call.sendChatMessage(translated);
        }));
    }

    /** Your language, which is the one the panel is already in. */
    public String mine() {
        return mindustrywarden.Lang.language();
    }

    /** Whether your message is replaced by its translation rather than followed by it. */
    public boolean replacing() {
        return Core.settings.getBool(replaceSetting, false);
    }

    public void replacing(boolean value) {
        Core.settings.put(replaceSetting, value);
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
