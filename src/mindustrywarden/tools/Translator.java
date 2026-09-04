package mindustrywarden.tools;

import arc.func.Cons;
import arc.util.Http;
import arc.util.Log;
import arc.util.serialization.Jval;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Turns one line of chat into another language.
 *
 * <p>Goes through Google's unauthenticated translate endpoint, the one a browser uses,
 * because it needs no key and no account: a tool that asks a player to register somewhere
 * before their teammate can be understood is a tool nobody switches on. The cost is that
 * it is not a published API and may change without notice, which is why a failure here is
 * silent rather than an error in the middle of a game.
 *
 * <p>What it sends is what it is given, one chat line at a time, to Google. Anyone using
 * it should know that: the panel says so before the switch.
 */
public final class Translator {
    private static final String endpoint =
        "https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&dt=t&tl=";

    /** The languages worth offering in a game chat, by frequency of appearance in one. */
    public static final String[] languages = {"fr", "en", "zh-CN", "ru", "es", "de", "pt", "uk"};

    /**
     * Translate {@code text} into {@code target}, then hand the result to {@code done}.
     *
     * <p>Never called back on failure: a chat line that cannot be translated is a chat
     * line shown as it came, which is what would have happened anyway.
     */
    public void translate(String text, String target, Cons<String> done) {
        String url = endpoint + target + "&q=" + URLEncoder.encode(text, StandardCharsets.UTF_8);

        Http.get(url, response -> {
            String translated = parse(response.getResultAsString());
            if (translated != null && !translated.isEmpty() && !translated.equalsIgnoreCase(text)) {
                done.get(translated);
            }
        }, error -> Log.debug("[warden] translation failed: @", error.getMessage()));
    }

    /**
     * Pull the text out of the endpoint's reply.
     *
     * <p>The reply is nested arrays rather than named fields, and a long line comes back
     * split into several segments that have to be joined: {@code [[["Hello ","Bonjour
     * ",...],["there","là",...]],...]}.
     */
    private static String parse(String json) {
        try {
            Jval.JsonArray segments = Jval.read(json).asArray().get(0).asArray();
            StringBuilder text = new StringBuilder();
            for (Jval segment : segments) {
                String piece = segment.asArray().get(0).asString();
                if (piece != null) {
                    text.append(piece);
                }
            }
            return text.toString().trim();
        } catch (Throwable malformed) {
            Log.debug("[warden] unreadable translation reply");
            return null;
        }
    }
}
