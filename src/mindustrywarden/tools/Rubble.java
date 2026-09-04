package mindustrywarden.tools;

import arc.struct.Seq;
import mindustry.Vars;
import mindustry.content.Blocks;
import mindustry.game.Team;
import mindustry.gen.Building;
import mindustry.gen.Fire;
import mindustry.gen.Groups;
import mindustry.world.Tile;

/**
 * The rubble a dead base leaves behind.
 *
 * <p>Destroying a team does not clear its buildings off the map. {@code destroyToDerelict}
 * hands them to the derelict team, so an eradicated enemy base stays standing, tile for
 * tile, under a different owner. On a sector fought over for a while that is thousands of
 * blocks: one save inspected here held 13049 of them against 2028 of the player's own.
 *
 * <p>Nothing else in Warden touches them. Capture skips derelict on purpose, since
 * derelict is not an enemy, and the recovery tools only clear what sits on a plan. So
 * this is the one tool for the rest, and it is deliberately separate: wiping thirteen
 * thousand blocks is not something to slip into another button unannounced.
 */
public final class Rubble {
    /** How many derelict buildings stand on the map right now. */
    public int count() {
        return standing(false).size;
    }

    /**
     * Remove every derelict building, and return how many went.
     *
     * <p>Collected first, then removed: taking a building out of the world while walking
     * it skips its neighbour.
     */
    public int clear() {
        Seq<Building> doomed = standing(true);
        for (Building building : doomed) {
            building.tile.setNet(Blocks.air);
        }
        return doomed.size;
    }

    /**
     * Put out every fire on the map, and return how many were burning.
     *
     * <p>The other thing a destroyed base leaves behind, and the one that undoes a
     * restore while it happens: a base put back into a burning sector loses blocks faster
     * than they are placed. Copied before removing, since removing walks the same group.
     */
    public int extinguish() {
        Seq<Fire> burning = new Seq<>();
        Groups.fire.each(burning::add);
        for (Fire fire : burning) {
            fire.remove();
        }
        return burning.size;
    }

    /**
     * Walk the map rather than {@code Groups.build}.
     *
     * <p>The group holds new members in a queue until it next updates, so it reports a
     * size that its own iteration does not yet cover: a block placed this tick counts as
     * one and iterates as none. Tiles have no such delay, and a pass over a 480x480 map
     * costs a few milliseconds, which is nothing for a button press.
     *
     * <p>Only the anchor tile of a building is taken, or a multi-tile block would be
     * counted once per tile it covers.
     */
    private Seq<Building> standing(boolean removableOnly) {
        Seq<Building> found = new Seq<>();
        for (Tile tile : Vars.world.tiles) {
            Building building = tile.build;
            if (building == null || building.tile != tile || building.team != Team.derelict) {
                continue;
            }
            if (removableOnly && building.block.privileged) {
                continue;
            }
            found.add(building);
        }
        return found;
    }
}
