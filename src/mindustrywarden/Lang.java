package mindustrywarden;

import arc.Core;
import arc.struct.ObjectMap;

/**
 * Every word the panel shows, in French and English.
 *
 * <p>Kept in one file rather than in the game's bundle system on purpose. A mod bundle
 * follows the game's language and cannot be changed from inside the mod, and the panel
 * needs a switch of its own: this is a tool used by one person on their own machine, who
 * may well run the game in French and read the tool in English, or the reverse.
 *
 * <p>The default follows the game, so nobody has to set anything. Keys are short and
 * flat, since there are eighty of them and no plural or gender to handle.
 */
public final class Lang {
    private static final String setting = "warden-language";

    private static final ObjectMap<String, String> en = new ObjectMap<>();
    private static final ObjectMap<String, String> fr = new ObjectMap<>();

    private static String language;

    static {
        put("title", "Warden", "Warden");

        put("tab.capture", "Capture", "Capture");
        put("tab.recover", "Repair", "Réparer");
        put("tab.backups", "Copies", "Copies");
        put("tab.units", "Units", "Unités");
        put("tab.supplies", "Stock", "Stock");
        put("tab.speed", "Speed", "Vitesse");
        put("tab.settings", "Settings", "Réglages");

        put("guard.nogame", "Load a game first.", "Charge une partie d'abord.");
        put("guard.client", "Warden only works in single player or when you host.",
            "Warden ne marche qu'en solo ou quand tu héberges.");

        put("capture.enemies", "enemies", "ennemis");
        put("capture.wave", "wave", "vague");
        put("capture.mode.attack", "attack", "attaque");
        put("capture.mode.waves", "waves", "vagues");
        put("capture.do", "Capture the sector", "Capturer le secteur");
        put("capture.state", "state", "état");
        put("capture.captured", "captured", "capturé");
        put("capture.open", "not yours", "pas à toi");
        put("capture.already", "This sector is already yours.",
            "Ce secteur est déjà à toi.");
        put("capture.hint",
            "Removes every enemy building and unit, then lets the game declare the win.",
            "Supprime tous les bâtiments et unités ennemis, puis laisse le jeu déclarer la victoire.");
        put("capture.done", "@ buildings and @ units removed.",
            "@ bâtiments et @ unités supprimés.");
        put("capture.nothing", "No enemy left here.", "Plus aucun ennemi ici.");
        put("capture.blocked", "Not captured: @", "Pas capturé : @");

        put("recover.plans", "blocks", "blocs");
        put("recover.rubble", "rubble", "vestiges");
        put("recover.fires", "fires", "feux");
        put("recover.all", "Restore everything", "Tout restaurer");
        put("recover.all.hint",
            "Clears the enemy, the rubble, the fires and your launch loadout, then puts "
                + "every remembered block back where it was.",
            "Supprime l'ennemi, les vestiges, les feux et ta base de départ, puis repose "
                + "chaque bloc mémorisé à sa place.");
        put("recover.all.confirm", "Restore the whole base?", "Restaurer toute la base ?");
        put("recover.all.done", "@ blocks restored, @ cleared.",
            "@ blocs restaurés, @ dégagés.");
        put("recover.rubble.do", "Clear rubble", "Nettoyer les vestiges");
        put("recover.rubble.hint",
            "A destroyed enemy base does not leave the map, it becomes derelict rubble.",
            "Une base ennemie détruite ne quitte pas la carte, elle devient des vestiges.");
        put("recover.clear.do", "Clear what covers", "Dégager par-dessus");
        put("recover.clear.hint",
            "A block cannot be rebuilt onto an occupied tile.",
            "Un bloc ne peut pas être reposé sur une case occupée.");
        put("recover.clear.own", "Mine too", "Les miens aussi");
        put("recover.clear.own.hint",
            "Includes your own blocks, such as the starter base. Cores are never removed.",
            "Inclut tes propres blocs, comme la base de départ. Les cores ne sont jamais supprimés.");
        put("recover.place.do", "Put the base back", "Reposer la base");
        put("recover.place.hint",
            "Writes the blocks straight into the world, with their configuration.",
            "Écrit les blocs directement dans le monde, avec leur configuration.");
        put("recover.queue.do", "Queue for builders", "Mettre en file");
        put("recover.queue.hint",
            "Costs resources and waits for a builder in range. Slow.",
            "Coûte des ressources et attend un constructeur à portée. Lent.");
        put("recover.export.do", "Export as schematics", "Exporter en schémas");
        put("recover.export.hint",
            "Saves the base as schematics, to rebuild it on another sector.",
            "Enregistre la base en schémas, pour la rebâtir sur un autre secteur.");
        put("recover.export.done", "@ schematics saved.", "@ schémas enregistrés.");
        put("recover.nothing", "Nothing to rebuild here.", "Rien à reconstruire ici.");
        put("recover.placed", "@ blocks put back.", "@ blocs reposés.");
        put("recover.cleared", "@ tiles cleared.", "@ cases dégagées.");
        put("recover.rubble.done", "@ rubble blocks removed.", "@ vestiges supprimés.");

        put("backups.auto", "Copy automatically", "Copier automatiquement");
        put("backups.every", "Every", "Toutes les");
        put("backups.minutes", "min", "min");
        put("backups.now", "Copy now", "Copier maintenant");
        put("backups.taken", "Copy taken.", "Copie faite.");
        put("backups.none", "No copies yet.", "Aucune copie pour l'instant.");
        put("backups.count", "@ copies", "@ copies");
        put("backups.restore", "Restore", "Restaurer");
        put("backups.confirm", "Go back to this copy? Everything since is lost.",
            "Revenir à cette copie ? Tout ce qui suit est perdu.");
        put("backups.justnow", "just now", "à l'instant");
        put("backups.ago", "@ min ago", "il y a @ min");
        put("backups.hint",
            "The only thing that brings a base back whole. Repair can only rebuild what "
                + "the game remembers being destroyed.",
            "La seule chose qui ramène une base entière. La réparation ne peut reposer que "
                + "ce que le jeu a mémorisé comme détruit.");
        put("backups.unavailable", "Only in a sector or a save you are playing.",
            "Seulement dans un secteur ou une sauvegarde en cours.");

        put("units.team", "Team", "Équipe");
        put("units.count", "Count", "Nombre");
        put("units.cap", "unit cap", "limite");
        put("units.alive", "alive", "en vie");
        put("units.fill", "Fill to the cap", "Remplir jusqu'à la limite");
        put("units.spawn", "Spawn", "Faire apparaître");
        put("units.spawned", "@ spawned.", "@ apparus.");
        put("units.capped", "@ of @ spawned, unit cap reached.",
            "@ sur @ apparus, limite d'unités atteinte.");

        put("supplies.amount", "Amount", "Quantité");
        put("supplies.max", "Max", "Max");
        put("supplies.give", "Give", "Donner");
        put("supplies.take", "Take", "Retirer");
        put("supplies.empty", "Empty", "Vider");
        put("supplies.all", "Fill everything", "Tout remplir");
        put("supplies.foreign", "Remove other planet's items", "Retirer les items d'ailleurs");
        put("supplies.otherplanets", "Show other planets", "Voir les autres planètes");
        put("supplies.nocore", "No core on this map.", "Aucun core sur cette carte.");
        put("supplies.pick", "Pick an item first.", "Choisis un item d'abord.");
        put("supplies.given", "@ kinds set to @.", "@ types réglés à @.");
        put("supplies.taken", "@ kinds emptied.", "@ types vidés.");
        put("supplies.research", "Unlock all research", "Débloquer la recherche");
        put("supplies.research.hint",
            "Stored on your profile, not in this save. No undo.",
            "Stocké sur ton profil, pas dans cette sauvegarde. Sans retour possible.");
        put("supplies.research.confirm", "Unlock the whole tech tree, permanently?",
            "Débloquer tout l'arbre technologique, définitivement ?");
        put("supplies.research.done", "@ entries unlocked.", "@ entrées débloquées.");

        put("speed.game", "Game speed", "Vitesse du jeu");
        put("speed.game.hint",
            "Everything moves: units, conveyors, waves. The number is a ceiling, the frame "
                + "rate decides the rest.",
            "Tout accélère : unités, convoyeurs, vagues. Le nombre est un plafond, le "
                + "framerate décide du reste.");
        put("speed.actual", "actual", "réelle");
        put("speed.you", "Your speed", "Ta vitesse");
        put("speed.you.hint", "Yours alone. Does not touch the game clock.",
            "La tienne seulement. Ne touche pas à l'horloge du jeu.");
        put("speed.team", "Whole team", "Toute l'équipe");
        put("speed.build", "Building", "Construction");
        put("speed.build.hint",
            "How fast your units place blocks. Does nothing while nothing is being built.",
            "Vitesse à laquelle tes unités posent les blocs. Sans effet si rien n'est en construction.");
        put("speed.mine", "Mining", "Minage");
        put("speed.mine.hint",
            "How fast your units pull ore out of the ground by hand.",
            "Vitesse à laquelle tes unités extraient le minerai à la main.");
        put("speed.invulnerable", "Nothing can destroy my blocks", "Rien ne peut détruire mes blocs");
        put("speed.invulnerable.hint",
            "Your blocks and units survive anything: fire, waves, a griefed reactor. It is "
                + "a team rule, so it is written into the save. Turn it off before you stop.",
            "Tes blocs et unités survivent à tout : le feu, les vagues, un réacteur saboté. "
                + "C'est une règle d'équipe, donc écrite dans la sauvegarde. Coupe-le avant d'arrêter.");

        put("settings.language", "Language", "Langue");
        put("settings.key", "Panel key", "Touche du panneau");
        put("settings.key.value", "Right shift", "Maj droite");
        put("settings.about",
            "Host and sandbox tools. Single player and hosted games only.",
            "Outils d'hôte et de bac à sable. Solo et parties hébergées seulement.");
    }

    private Lang() {
    }

    /** "fr" or "en", following the game on first run. */
    public static String language() {
        if (language == null) {
            String stored = Core.settings.getString(setting, "");
            language = stored.isEmpty()
                ? (Core.bundle.getLocale().getLanguage().equals("fr") ? "fr" : "en")
                : stored;
        }
        return language;
    }

    public static void language(String value) {
        language = value;
        Core.settings.put(setting, value);
    }

    public static String get(String key) {
        ObjectMap<String, String> table = language().equals("fr") ? fr : en;
        String found = table.get(key);
        return found == null ? key : found;
    }

    /** Replaces each {@code @} with the next argument, like the game's own bundles. */
    public static String get(String key, Object... args) {
        String text = get(key);
        for (Object argument : args) {
            text = text.replaceFirst("@", String.valueOf(argument));
        }
        return text;
    }

    private static void put(String key, String english, String french) {
        en.put(key, english);
        fr.put(key, french);
    }
}
