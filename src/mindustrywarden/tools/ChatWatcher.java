package mindustrywarden.tools;

import arc.func.Cons2;
import arc.struct.Seq;
import arc.util.Log;
import mindustry.Vars;

import java.lang.reflect.Field;

/**
 * Watches what the chat actually shows, and can rewrite it.
 *
 * <p>Listening to {@code PlayerChatEvent} was the obvious way and it does not work. The
 * game has two ways of receiving a line: one carries a sender and fires the event, the
 * other carries only the finished text and fires nothing at all, printing it straight to
 * the chat. Servers that format their own messages, which is most servers running plugins,
 * use the second. On such a server the event never arrives and a translator built on it
 * sees an empty room.
 *
 * <p>So this reads the fragment's own list instead. It is private, hence the reflection,
 * and that is the price of catching every line rather than the ones the game happens to
 * announce. The list is also what makes rewriting possible: a translation can replace the
 * line it belongs to instead of piling up underneath it.
 *
 * <p>Newest first: {@code addMessage} inserts at zero.
 */
public final class ChatWatcher {
    private Seq<String> messages;
    private boolean unavailable;
    private int seen;

    /**
     * Hand every newly shown line to {@code onLine}, with its index.
     *
     * <p>Called once per frame. Several lines can land in one frame on a busy server, so
     * it walks everything that appeared rather than looking only at the top.
     */
    public void poll(Cons2<Integer, String> onLine) {
        Seq<String> list = list();
        if (list == null) {
            return;
        }

        if (list.size < seen) {
            // The chat was cleared, on a map change or a reconnect.
            seen = list.size;
            return;
        }

        int fresh = list.size - seen;
        seen = list.size;

        // Oldest of the new lines first, so they are handled in the order they were said.
        for (int i = fresh - 1; i >= 0; i--) {
            onLine.get(i, list.get(i));
        }
    }

    /**
     * Replace a line, finding it by its text rather than by the index it had.
     *
     * <p>A translation comes back long after the line was shown, and by then other people
     * have spoken and every index has moved. The text is what stays the same.
     */
    public boolean replace(String original, String replacement) {
        Seq<String> list = list();
        if (list == null) {
            return false;
        }

        int index = list.indexOf(original, false);
        if (index < 0) {
            return false;
        }

        list.set(index, replacement);
        return true;
    }

    /** A copy of what the chat currently shows, newest first. For the tests. */
    public Seq<String> snapshot() {
        Seq<String> list = list();
        return list == null ? new Seq<>() : list.copy();
    }

    /** True while the chat is readable, which is everything this can do without. */
    public boolean available() {
        return list() != null;
    }

    @SuppressWarnings("unchecked")
    private Seq<String> list() {
        if (messages != null || unavailable) {
            return messages;
        }
        if (Vars.ui == null || Vars.ui.chatfrag == null) {
            return null;
        }

        try {
            Field field = Vars.ui.chatfrag.getClass().getDeclaredField("messages");
            field.setAccessible(true);
            messages = (Seq<String>) field.get(Vars.ui.chatfrag);
        } catch (Throwable denied) {
            // A game that hides its chat is a game without this feature, not a crash.
            unavailable = true;
            Log.info("[warden] chat not readable, translation of incoming lines is off");
        }
        return messages;
    }
}
