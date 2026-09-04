package mindustrywarden.tools;

import arc.Core;
import arc.Events;
import mindustry.Vars;
import mindustry.game.EventType.PlayerChatEvent;
import mindustry.gen.Call;

/**
 * Reads the chat in your language, and answers in theirs.
 *
 * <p>Mindustry is played in every language at once, and a server where half the chat is
 * Chinese and the other half French is a server where nobody coordinates. Two switches,
 * each doing one thing.
 *
 * <p>Incoming lines are translated and printed underneath the original, never in place of
 * it: a translation is a guess, and hiding what was actually said would take away the one
 * thing a reader can check.
 *
 * <p>Outgoing lines are sent a second time, translated. It works this way because the
 * game gives no way to catch a message on its way out, only to hear about it once gone,
 * so the choice is between a second line and nothing at all.
 */
public final class ChatTranslation {
    private static final String readSetting = "warden-chat-read";
    private static final String writeSetting = "warden-chat-write";
    private static final String intoSetting = "warden-chat-into";
    private static final String outSetting = "warden-chat-out";

    private final Translator translator = new Translator();

    /**
     * Guards against translating a translation we just sent ourselves.
     *
     * <p>Belt and braces with the id check above: two ways to spot our own line, because
     * the failure mode is a loop that floods a server's chat.
     */
    private String lastSent = "";

    public void install() {
        Events.on(PlayerChatEvent.class, event -> {
            if (event.player == null || event.message == null || event.message.isEmpty()) {
                return;
            }
            if (event.message.startsWith("/")) {
                return;
            }

            // By id, not by instance: on a server the player carried by the event is
            // not guaranteed to be the same object as the local one, and getting this
            // wrong means translating your own translation, forever.
            boolean mine = Vars.player != null && event.player.id == Vars.player.id;
            if (mine) {
                sendTranslation(event.message);
            } else {
                showTranslation(event.player.name, event.message);
            }
        });
    }

    private void showTranslation(String sender, String message) {
        if (!reading()) {
            return;
        }
        translator.translate(message, into(), translated ->
            Core.app.post(() -> Vars.ui.chatfrag.addMessage(
                "[lightgray]" + sender + "[white]: " + translated)));
    }

    private void sendTranslation(String message) {
        if (!writing() || message.equals(lastSent)) {
            return;
        }
        translator.translate(message, out(), translated -> Core.app.post(() -> {
            lastSent = translated;
            Call.sendChatMessage(translated);
        }));
    }

    public boolean reading() {
        return Core.settings.getBool(readSetting, false);
    }

    public void reading(boolean value) {
        Core.settings.put(readSetting, value);
    }

    public boolean writing() {
        return Core.settings.getBool(writeSetting, false);
    }

    public void writing(boolean value) {
        Core.settings.put(writeSetting, value);
    }

    /** The language incoming chat is turned into: yours. */
    public String into() {
        return Core.settings.getString(intoSetting, "fr");
    }

    public void into(String value) {
        Core.settings.put(intoSetting, value);
    }

    /** The language your own lines are repeated in: theirs. English by default. */
    public String out() {
        return Core.settings.getString(outSetting, "en");
    }

    public void out(String value) {
        Core.settings.put(outSetting, value);
    }
}
