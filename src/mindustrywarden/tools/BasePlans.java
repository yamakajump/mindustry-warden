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
import mindustry.world.Tile;

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
     * <p>Only buildings of other teams, derelict included: derelict is where a griefed
     * base ends up once its team is wiped, and it blocks a rebuild exactly like an enemy
     * wall does. Your own buildings are left alone; a plan under one of them is a plan
     * you already rebuilt.
     */
    public int clearBlockers() {
        Team mine = Vars.player.team();
        int cleared = 0;

        for (BlockPlan plan : plans()) {
            int size = plan.block.size;
            int origin = -(size - 1) / 2;

            for (int dx = 0; dx < size; dx++) {
                for (int dy = 0; dy < size; dy++) {
                    Tile tile = Vars.world.tile(plan.x + origin + dx, plan.y + origin + dy);
                    if (tile != null && tile.build != null && tile.build.team != mine) {
                        tile.setNet(Blocks.air);
                        cleared++;
                    }
                }
            }
        }
        return cleared;
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
