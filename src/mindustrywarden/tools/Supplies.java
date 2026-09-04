package mindustrywarden.tools;

import arc.struct.ObjectSet;
import arc.struct.Seq;
import mindustry.Vars;
import mindustry.ctype.UnlockableContent;
import mindustry.type.Item;
import mindustry.type.Planet;
import mindustry.world.blocks.storage.CoreBlock.CoreBuild;

/**
 * What goes into the core, and what the tech tree lets you build.
 *
 * <p>The two live together because they answer the same question, "stop making me farm
 * for this", but they do not have the same reach: filling a core touches one game, while
 * unlocking research touches the campaign profile and outlives the session.
 */
public final class Supplies {
    private Planet cachedPlanet;
    private ObjectSet<Item> cached;

    /**
     * Items that belong on the planet this game is played on.
     *
     * <p>The first version handed out {@code content.items()}, which is every item in the
     * game, so a Serpulo core came back full of beryllium and carbide: items no block
     * there can consume, sitting in the way. The game files each item under the tech tree
     * of its planet, and that is the honest test. An item filed nowhere, or under no
     * planet in particular, is offered everywhere rather than hidden on a guess.
     */
    public boolean belongsHere(Item item) {
        if (item.hidden) {
            return false;
        }
        Planet planet = Vars.state.rules.planet;
        if (planet == null) {
            return true;
        }

        boolean researchedSomewhere = item.techNode != null
            || (item.techNodes != null && !item.techNodes.isEmpty());

        // Researched on some planet, but not this one's tree: it is the other planet's.
        // Researched nowhere at all, like sand or scrap, so offered everywhere.
        return !researchedSomewhere || treeOf(planet).contains(item);
    }

    /**
     * Every item reachable in a planet's tech tree.
     *
     * <p>Asking an item which planet it belongs to does not work: {@code TechNode.planet}
     * is set on a tree's root and left null on the nodes below it, so beryllium answers
     * "nowhere" and passes for a Serpulo item. Reading the tree from the planet down
     * gives the same answer from the side where the data actually exists.
     *
     * <p>Cached per planet, since a tree is walked identically on every call and the
     * panel calls this once per item on every rebuild.
     */
    private ObjectSet<Item> treeOf(Planet planet) {
        if (planet == cachedPlanet && cached != null) {
            return cached;
        }

        ObjectSet<Item> items = new ObjectSet<>();
        if (planet.techTree != null) {
            planet.techTree.each(node -> {
                if (node.content instanceof Item item) {
                    items.add(item);
                }
            });
        }

        cachedPlanet = planet;
        cached = items;
        return items;
    }

    /** Items to offer: this planet's, or the whole game when asked for it. */
    public Seq<Item> items(boolean allPlanets) {
        return Vars.content.items().select(item ->
            !item.hidden && (allPlanets || belongsHere(item)));
    }

    public boolean hasCore() {
        return Vars.player.team().core() != null;
    }

    /** How much of one item a core holds, which is the only ceiling worth offering. */
    public int capacity() {
        CoreBuild core = Vars.player.team().core();
        return core == null ? 0 : core.storageCapacity;
    }

    /**
     * Put {@code amount} of each given item in the core, and return how many kinds moved.
     *
     * <p>Set rather than add: asking for a thousand of something twice should leave a
     * thousand, not two, and the core would clamp the second add anyway.
     */
    public int give(Iterable<Item> items, int amount) {
        CoreBuild core = Vars.player.team().core();
        if (core == null) {
            return 0;
        }
        int given = 0;
        for (Item item : items) {
            core.items.set(item, Math.min(amount, core.storageCapacity));
            given++;
        }
        return given;
    }

    /**
     * Take {@code amount} of each given item out of the core, and return how many kinds
     * lost something. An amount at or above what is held empties that item.
     */
    public int take(Iterable<Item> items, int amount) {
        CoreBuild core = Vars.player.team().core();
        if (core == null) {
            return 0;
        }
        int taken = 0;
        for (Item item : items) {
            int held = core.items.get(item);
            if (held > 0) {
                core.items.set(item, Math.max(0, held - amount));
                taken++;
            }
        }
        return taken;
    }

    /** Empty the core of everything that belongs to another planet, and count it. */
    public int removeForeign() {
        CoreBuild core = Vars.player.team().core();
        if (core == null) {
            return 0;
        }
        int removed = 0;
        for (Item item : Vars.content.items()) {
            if (!belongsHere(item) && core.items.get(item) > 0) {
                core.items.set(item, 0);
                removed++;
            }
        }
        return removed;
    }

    /**
     * Unlock every researchable item, block and unit, and return how many were locked.
     *
     * <p>Not scoped to the current game: research is stored on the profile, so it stays
     * unlocked in every campaign afterwards and there is no undo. The panel asks first.
     */
    public int unlockAll() {
        int[] unlocked = {0};
        Vars.content.each(content -> {
            if (content instanceof UnlockableContent item && !item.unlocked()) {
                item.unlock();
                unlocked[0]++;
            }
        });
        return unlocked[0];
    }
}
