package mindustrywarden;

import arc.Core;

/**
 * Lets something happen once per drawn frame, however often it is asked.
 *
 * <p>Warden hangs its work on {@code Trigger.update}, which the game fires once per
 * {@code logic.update()}. That is once per frame until fast forward runs the world
 * several times in the same frame, and then the listener runs once per pass.
 *
 * <p>For most work that is harmless. For a key it is not: {@code keyTap} stays true for
 * the whole frame, so at 64x a single press of the panel key was read 64 times and
 * toggled the panel 64 times. What that looks like is a window flashing open and shut,
 * with the game apparently refusing to open it.
 *
 * <p>The frame id is the only honest boundary here, since it does not move between passes
 * the way a tick counter does.
 */
final class FrameGate {
    private long last = -1;

    /** True the first time it is called in a given frame, false for every later call. */
    boolean firstThisFrame() {
        long frame = Core.graphics.getFrameId();
        if (frame == last) {
            return false;
        }
        last = frame;
        return true;
    }
}
