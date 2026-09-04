package mindustrywarden.tools;

/**
 * Works out what language a line of chat is in, without asking anyone.
 *
 * <p>Two reasons this is local rather than a request. It has to run on every incoming
 * message to keep a count of what the room speaks, and one request per message would
 * exhaust a free translation endpoint within an evening. And the endpoint that would
 * answer it refuses "auto" anyway, so the source language has to come from somewhere.
 *
 * <p>Most of the work is done by the alphabet: Cyrillic, Han, Kana, Hangul, Arabic, Greek,
 * Hebrew and Thai each belong to few enough languages to be decided on sight. What is left
 * is the Latin alphabet, where the alphabet says nothing and the small words say
 * everything: a line holding "les", "des" and "pour" is French whatever it is about.
 *
 * <p>It answers null rather than guessing when a line is too short or holds nothing
 * recognisable. "gg", "ok" and ":)" are in no language, and counting them would drown the
 * signal in a room where half the chat is exactly that.
 */
public final class LanguageGuess {
    /** Below this, a line is a reaction rather than a sentence. */
    private static final int shortest = 5;

    /** Function words, the cheapest tell there is for the Latin alphabet. */
    private static final String[][] markers = {
        {"fr", "le", "la", "les", "des", "une", "est", "pas", "pour", "que", "qui", "avec",
            "sur", "je", "tu", "il", "on", "nous", "vous", "mais", "dans", "plus", "moi",
            "toi", "oui", "non", "merci", "salut", "bien", "faire", "fait", "peux", "veux"},
        {"en", "the", "is", "are", "you", "and", "to", "of", "in", "it", "that", "this",
            "have", "with", "for", "not", "can", "will", "your", "we", "they", "what",
            "how", "please", "thanks", "yes", "just", "there", "here", "need", "want",
            "well", "done", "good", "nice", "bro", "man", "guys", "help", "base", "make",
            "build", "come", "wait", "stop", "sorry", "hello", "why", "who", "where"},
        {"es", "el", "la", "los", "las", "que", "no", "es", "en", "un", "una", "por",
            "con", "para", "como", "pero", "hola", "gracias", "si", "muy", "esta", "tiene"},
        {"de", "der", "die", "das", "und", "ist", "nicht", "ein", "eine", "zu", "mit",
            "für", "ich", "du", "wir", "auf", "auch", "aber", "hallo", "danke", "kann"},
        {"pt", "os", "as", "que", "não", "uma", "por", "com", "para", "como", "mas",
            "obrigado", "sim", "você", "está", "muito", "tem", "isso", "aqui"},
        {"it", "il", "lo", "gli", "che", "non", "una", "per", "con", "sono", "come",
            "anche", "grazie", "ciao", "questo", "molto", "adesso"},
    };

    /**
     * The language of {@code text}, or null when it cannot be told.
     *
     * @return a two letter code, except Chinese which needs its region to translate
     */
    public String of(String text) {
        if (text == null) {
            return null;
        }

        String clean = text.trim();
        if (clean.isEmpty()) {
            return null;
        }

        // The alphabet answers whatever the length: "хз" is two letters and unmistakably
        // Russian. The length rule exists for the Latin alphabet alone, where "ok" and
        // "gg" belong to no language and would otherwise be counted as English.
        String script = byScript(clean);
        if (script != null) {
            return script;
        }

        return clean.length() < shortest ? null : byWords(clean);
    }

    /**
     * The alphabet, where it decides on its own.
     *
     * <p>Ukrainian is separated from Russian by four letters it alone uses, which is worth
     * the two lines: telling a Ukrainian player their own language is Russian is not a
     * neutral mistake.
     */
    private static String byScript(String text) {
        int cyrillic = 0;
        int han = 0;
        int kana = 0;
        int hangul = 0;
        int arabic = 0;
        int greek = 0;
        int hebrew = 0;
        int thai = 0;
        int letters = 0;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (!Character.isLetter(c)) {
                continue;
            }
            letters++;

            Character.UnicodeBlock block = Character.UnicodeBlock.of(c);
            if (block == Character.UnicodeBlock.CYRILLIC) {
                cyrillic++;
            } else if (block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS) {
                han++;
            } else if (block == Character.UnicodeBlock.HIRAGANA
                || block == Character.UnicodeBlock.KATAKANA) {
                kana++;
            } else if (block == Character.UnicodeBlock.HANGUL_SYLLABLES
                || block == Character.UnicodeBlock.HANGUL_JAMO) {
                hangul++;
            } else if (block == Character.UnicodeBlock.ARABIC) {
                arabic++;
            } else if (block == Character.UnicodeBlock.GREEK) {
                greek++;
            } else if (block == Character.UnicodeBlock.HEBREW) {
                hebrew++;
            } else if (block == Character.UnicodeBlock.THAI) {
                thai++;
            }
        }

        if (letters == 0) {
            return null;
        }

        // Kana before Han: Japanese mixes both, Chinese uses Han alone.
        if (kana * 4 > letters) {
            return "ja";
        }
        if (hangul * 2 > letters) {
            return "ko";
        }
        if (han * 2 > letters) {
            return "zh-CN";
        }
        if (cyrillic * 2 > letters) {
            return text.matches(".*[іїєґІЇЄҐ].*") ? "uk" : "ru";
        }
        if (arabic * 2 > letters) {
            return "ar";
        }
        if (greek * 2 > letters) {
            return "el";
        }
        if (hebrew * 2 > letters) {
            return "he";
        }
        if (thai * 2 > letters) {
            return "th";
        }
        return null;
    }

    /** The small words, for everything written in the Latin alphabet. */
    private static String byWords(String text) {
        String[] words = text.toLowerCase().split("[^\\p{L}]+");
        if (words.length == 0) {
            return null;
        }

        String best = null;
        int bestScore = 0;

        for (String[] language : markers) {
            int score = 0;
            for (String word : words) {
                for (int i = 1; i < language.length; i++) {
                    if (language[i].equals(word)) {
                        score++;
                        break;
                    }
                }
            }
            if (score > bestScore) {
                bestScore = score;
                best = language[0];
            }
        }

        // One function word in a long line is a coincidence; in a short one it is the
        // whole sentence. Asking for two past a handful of words keeps "no" from making
        // a line Spanish.
        int needed = words.length > 6 ? 2 : 1;
        return bestScore >= needed ? best : null;
    }

    /** How many languages the table can name, for a test that it was not left empty. */
    public int knownLanguages() {
        return markers.length + 8;
    }
}
