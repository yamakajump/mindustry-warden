package mindustrywarden.tools;

import arc.Core;
import arc.util.Time;

/**
 * Run the simulation faster, or slower.
 *
 * <p>Mindustry clamps its own frame delta at 3 and that ceiling is kept here rather than
 * worked around. The clamp exists because movement and collision assume small steps: at a
 * delta of 8 a unit crosses a wall between two ticks instead of hitting it. So the offer
 * stops at 3x, which is honest, instead of a 10x button that quietly does nothing above 3
 * and corrupts what it does reach.
 *
 * <p>Speeding up is a host-side change: on a hosted game the server simulates for
 * everyone, so guests see the world move faster while their own interpolation still runs
 * at their clock. It works, it just looks slightly rough on their side.
 */
public final class GameSpeed {
    /** The game's own delta ceiling. Above this the simulation stops being trustworthy. */
    public static final float max = 3f;

    /** Speeds worth a button. Below 1 the clock is simply scaled, which is always safe. */
    public static final float[] steps = {0.25f, 0.5f, 1f, 2f, 3f};

    private float multiplier = 1f;

    /**
     * Take over the game clock.
     *
     * <p>Installed once at startup rather than on the first change: at 1x the formula is
     * exactly the game's own, so nothing moves until asked, and there is no provider to
     * swap in and out at the moment a player is watching the speed change.
     */
    public void install() {
        Time.setDeltaProvider(() -> Math.min(Core.graphics.getDeltaTime() * 60f * multiplier, max));
    }

    public float multiplier() {
        return multiplier;
    }

    public void multiplier(float value) {
        multiplier = Math.max(0.05f, Math.min(value, max));
    }

    public boolean modified() {
        return multiplier != 1f;
    }
}
