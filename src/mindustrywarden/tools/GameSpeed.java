package mindustrywarden.tools;

import arc.Core;
import arc.util.Time;
import mindustry.Vars;

/**
 * Run the simulation faster, or slower.
 *
 * <p>Two different mechanisms, because one alone cannot do both ends.
 *
 * <p>Below 1x the frame step is simply scaled: a smaller step is always safe, and the
 * world moves in slow motion. Above 1x scaling the step is not an option, since the game
 * clamps its own at 3 and for good reason: movement and collision assume small steps, and
 * at a step of 8 a unit crosses a wall between two ticks instead of hitting it. So fast
 * forward runs the world several times per frame instead, each pass a normal tick with a
 * normal step. The simulation stays exactly as valid at 64x as at 1x, it simply happens
 * more often.
 *
 * <p>What it costs is the frame: 64x is sixty-four worlds simulated between two images,
 * so a large map will not hold sixty frames a second while it runs. That is a slideshow,
 * not a corruption, and it stops the moment the speed comes back down.
 */
public final class GameSpeed {
    /** The game's own delta ceiling, which is why anything above 1x runs extra ticks. */
    public static final float max = 3f;

    public static final float[] steps = {0.25f, 0.5f, 1f, 2f, 4f, 8f, 16f, 32f, 64f};

    private float multiplier = 1f;

    /**
     * Guards against re-entry.
     *
     * <p>The extra ticks are driven from a {@code Trigger.update} listener, and that
     * trigger is fired from inside {@code logic.update()}. Calling it from there without
     * a flag is unbounded recursion on the first frame above 1x.
     */
    private boolean inExtraTick;

    /**
     * Take over the game clock.
     *
     * <p>Installed once at startup rather than on the first change: at 1x the formula is
     * exactly the game's own, so nothing moves until asked.
     */
    public void install() {
        Time.setDeltaProvider(() -> {
            float raw = Math.min(Core.graphics.getDeltaTime() * 60f, max);
            return multiplier < 1f ? raw * multiplier : raw;
        });
    }

    /** Run the extra whole ticks for this frame. Call once per update. */
    public void update() {
        if (inExtraTick || multiplier <= 1f || !Vars.state.isPlaying()) {
            return;
        }

        int extra = Math.round(multiplier) - 1;
        inExtraTick = true;
        try {
            for (int i = 0; i < extra; i++) {
                Vars.logic.update();
            }
        } finally {
            inExtraTick = false;
        }
    }

    public float multiplier() {
        return multiplier;
    }

    public void multiplier(float value) {
        multiplier = Math.max(0.05f, Math.min(value, steps[steps.length - 1]));
    }

    public boolean heavy() {
        return multiplier >= 16f;
    }
}
