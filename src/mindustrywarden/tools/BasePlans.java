package mindustrywarden.tools;

import arc.struct.Seq;
import arc.struct.StringMap;
import mindustry.Vars;
import mindustry.content.Blocks;
import mindustry.entities.units.BuildPlan;
import mindustry.game.Schematic;
import mindustry.game.Schematic.Stile;
import mindustry.game.Team;
import mindustry.game.Teams.BlockPlan;
import mindustry.gen.Building;
import mindustry.gen.Call;
import mindustry.world.Tile;
import mindustry.world.blocks.storage.CoreBlock.CoreBuild;

/**
 * Your destroyed base, which the game still remembers.
 *
 * <p>When a building of yours dies, the game files a {@code BlockPlan} holding its
 * position, rotation, block and configuration, and writes the whole list into the save
 * ({@code SaveVersion.writeTeamBlocks}). A griefed base is therefore not gone: it is a
 * list of plans sitting under whatever now stands on top of it.
 *
 * <p>The game already offers one way to use them, holding B over an area, and it queues
 * every plan it touches without checking whether the ground is free. That is the catch
 * this class exists for: a plan buried under an enemy block queues fine and then never
 * builds, because a builder cannot place onto an occupied tile. So the work is in three
 * steps, and they are separate on purpose: clear what covers the plans, queue them, and,
 * for the ones you would rather rebuild somewhere else entirely, take them out as
 * schematics.
 */
public final class BasePlans {
    /** Live view of the plans, in the game's own order. */
    public Seq<BlockPlan> plans() {
        Seq<BlockPlan> all = new Seq<>();
        for (BlockPlan plan : Vars.state.teams.get(Vars.player.team()).plans) {
            if (plan.block != null) {
                all.add(plan);
            }
        }
        return all;
    }

    /**
     * Delete every building that stands where a plan wants to go, and return how many.
     *
     * <p>Buildings of other teams always go, derelict included: derelict is where a
     * griefed base ends up once its team is wiped, and it blocks a rebuild exactly like
     * an enemy wall does.
     *
     * <p>{@code includeOwn} adds your own buildings, which is what the sector loadout is:
     * the starter base the game drops when you launch, sitting on ground your old base
     * used to hold. Two things are never removed even then. A core, because losing the
     * last one is an instant defeat rather than a cleanup. And privileged blocks, which
     * belong to the map rather than to a player.
     *
     * <p>Removal goes through {@code setNet}, which takes the building out without a
     * {@code BlockDestroyEvent}. That matters here: the event is what files a plan
     * ({@code Logic} listens for it), so clearing does not quietly add every cleared
     * block to the very list being rebuilt.
     */
    public int clearBlockers(boolean includeOwn) {
        Team mine = Vars.player.team();
        int cleared = 0;

        for (BlockPlan plan : plans()) {
            int size = plan.block.size;
            int origin = -(size - 1) / 2;

            for (int dx = 0; dx < size; dx++) {
                for (int dy = 0; dy < size; dy++) {
                    Tile tile = Vars.world.tile(plan.x + origin + dx, plan.y + origin + dy);
                    if (tile == null || tile.build == null) {
                        continue;
                    }
                    if (removable(tile.build, mine, includeOwn)) {
                        tile.setNet(Blocks.air);
                        cleared++;
                    }
                }
            }
        }
        return cleared;
    }

    private static boolean removable(Building building, Team mine, boolean includeOwn) {
        if (building.block.privileged) {
            return false;
        }
        if (building.team != mine) {
            return true;
        }
        return includeOwn && !(building instanceof CoreBuild);
    }

    /**
     * Put every plan back on the map at once, and return how many blocks landed.
     *
     * <p>Queueing is the honest way and it is slow for a reason: a build plan waits on
     * the resources in your core and on a builder being within range of it, so a base
     * spread over a sector comes back in pieces over several minutes, or not at all.
     * This does not queue anything. It writes the blocks straight into the world, with
     * their configuration, and takes each plan off the list as it goes.
     *
     * <p>Blocks go in through {@code setNet} and configurations through
     * {@code Call.tileConfig}, so a hosted game sends both to the other players instead
     * of growing a base only the host can see.
     */
    public int placeAll() {
        Team mine = Vars.player.team();
        var pending = Vars.state.teams.get(mine).plans;
        int placed = 0;

        for (BlockPlan plan : plans()) {
            Tile tile = Vars.world.tile(plan.x, plan.y);
            if (tile == null) {
                continue;
            }

            tile.setNet(plan.block, mine, plan.rotation);

            if (plan.config != null && tile.build != null) {
                Call.tileConfig(Vars.player, tile.build, plan.config);
            }

            // The game only drops a plan when a builder finishes it, and nothing here is
            // a builder. Left in, the list would keep offering blocks already standing.
            pending.remove(other -> other.x == plan.x && other.y == plan.y);
            placed++;
        }
        return placed;
    }

    /** Queue every plan for rebuilding, which is holding B over the whole map at once. */
    public int queueAll() {
        Seq<BlockPlan> all = plans();
        for (BlockPlan plan : all) {
            Vars.player.unit().addBuild(
                new BuildPlan(plan.x, plan.y, plan.rotation, plan.block, plan.config));
        }
        return all.size;
    }

    /**
     * Save the plans as schematics, and return them.
     *
     * <p>Cut into tiles of the game's maximum schematic size, because a base is nearly
     * always larger than one schematic may be, and a single oversized schematic would be
     * refused at paste time rather than at save time. The pieces are numbered by grid
     * position so they can be laid back down next to each other.
     *
     * <p>Known limit: a block configured with an absolute position, a logic processor and
     * its links above all, keeps that absolute position here. Inside a schematic those
     * are meant to be relative, so a processor will come back with its links pointing at
     * where the old base stood. Everything simpler than that, contents of sorters,
     * unloaders and gates, comes back correct.
     */
    public Seq<Schematic> export(String name) {
        Seq<BlockPlan> all = plans();
        Seq<Schematic> saved = new Seq<>();
        if (all.isEmpty()) {
            return saved;
        }

        int size = Math.max(8, Vars.maxSchematicSize);
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        for (BlockPlan plan : all) {
            minX = Math.min(minX, plan.x);
            minY = Math.min(minY, plan.y);
        }

        // Grouped by which tile of the grid each plan falls into, so that one pass over
        // the plans produces every piece.
        var grouped = new arc.struct.ObjectMap<Long, Seq<BlockPlan>>();
        for (BlockPlan plan : all) {
            long key = (long) ((plan.x - minX) / size) << 32 | ((plan.y - minY) / size);
            grouped.get(key, Seq::new).add(plan);
        }

        int piece = 0;
        for (var entry : grouped) {
            Seq<BlockPlan> group = entry.value;
            int pieceMinX = Integer.MAX_VALUE;
            int pieceMinY = Integer.MAX_VALUE;
            int pieceMaxX = Integer.MIN_VALUE;
            int pieceMaxY = Integer.MIN_VALUE;

            for (BlockPlan plan : group) {
                pieceMinX = Math.min(pieceMinX, plan.x);
                pieceMinY = Math.min(pieceMinY, plan.y);
                pieceMaxX = Math.max(pieceMaxX, plan.x);
                pieceMaxY = Math.max(pieceMaxY, plan.y);
            }

            Seq<Stile> tiles = new Seq<>();
            for (BlockPlan plan : group) {
                tiles.add(new Stile(plan.block, plan.x - pieceMinX, plan.y - pieceMinY,
                    plan.config, (byte) plan.rotation));
            }

            piece++;
            Schematic schematic = new Schematic(tiles,
                StringMap.of("name", name + " " + piece, "description",
                    "Recovered by Warden from " + group.size + " destroyed blocks"),
                pieceMaxX - pieceMinX + 1, pieceMaxY - pieceMinY + 1);

            Vars.schematics.add(schematic);
            saved.add(schematic);
        }

        return saved;
    }
}
