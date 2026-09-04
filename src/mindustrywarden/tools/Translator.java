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
        translate(text, null, target, done);
    }

    /**
     * Translate {@code text} into {@code target}, then hand the result to {@code done}.
     *
     * <p>Two services, in order. Google first, because it detects the source itself and
     * reads chat shorthand better. MyMemory second, because Google answers a script with
     * a "Sorry" page rather than a translation and may one day answer the game with one
     * too. The fallback needs to be told the source language, which is why {@code source}
     * is carried this far: it is the guess the room count was built from anyway.
     *
     * <p>Never called back on failure. A line that cannot be translated is shown as it
     * came, which is what would have happened without the mod.
     */
    public void translate(String text, String source, String target, Cons<String> done) {
        String encoded = URLEncoder.encode(text, StandardCharsets.UTF_8);

        Http.get(endpoint + target + "&q=" + encoded, response -> {
            String translated = parse(response.getResultAsString());
            if (usable(translated, text)) {
                done.get(translated);
            } else {
                fallback(encoded, source, target, text, done);
            }
        }, error -> fallback(encoded, source, target, text, done));
    }

    /** MyMemory, which answers plain JSON and does not mind being called by a program. */
    private void fallback(String encoded, String source, String target, String original,
        Cons<String> done) {

        String from = source == null || source.isEmpty() ? "en" : source;
        String url = "https://api.mymemory.translated.net/get?q=" + encoded
            + "&langpair=" + from + "|" + target;

        Http.get(url, response -> {
            try {
                String translated = Jval.read(response.getResultAsString())
                    .get("responseData").get("translatedText").asString();
                if (usable(translated, original)) {
                    done.get(translated);
                }
            } catch (Throwable malformed) {
                Log.debug("[warden] unreadable fallback reply");
            }
        }, error -> Log.debug("[warden] translation failed: @", error.getMessage()));
    }

    /** A translation worth showing: present, and not the line we already have. */
    private static boolean usable(String translated, String original) {
        return translated != null && !translated.isEmpty()
            && !translated.equalsIgnoreCase(original)
            && !translated.contains("INVALID SOURCE LANGUAGE");
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
