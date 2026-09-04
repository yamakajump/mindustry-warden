package mindustrywarden.tools;

import mindustry.Vars;
import mindustry.ctype.UnlockableContent;
import mindustry.type.Item;
import mindustry.world.blocks.storage.CoreBlock.CoreBuild;

/**
 * Resources in the core, and the research tree opened.
 *
 * <p>The two live together because they answer the same question, "stop making me farm
 * for this", but they do not have the same reach: filling a core touches one game, while
 * unlocking research touches the campaign profile and outlives the session.
 */
public final class Supplies {
    /** Fill every item slot of the team core, and return how many kinds were topped up. */
    public int fillCore() {
        CoreBuild core = Vars.player.team().core();
        if (core == null) {
            return 0;
        }
        int filled = 0;
        for (Item item : Vars.content.items()) {
            if (core.items.get(item) < core.storageCapacity) {
                core.items.set(item, core.storageCapacity);
                filled++;
            }
        }
        return filled;
    }

    /** Whether there is a core to fill, which a freshly lost sector may not have. */
    public boolean hasCore() {
        return Vars.player.team().core() != null;
    }

    /**
     * Unlock every researchable item, block and unit, and return how many were still
     * locked.
     *
     * <p>This one is not scoped to the current game. Research is stored on the profile,
     * so it stays unlocked in every campaign afterwards and there is no undo. The panel
     * asks before calling it.
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
