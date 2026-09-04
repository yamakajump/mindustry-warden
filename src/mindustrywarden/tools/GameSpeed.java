package mindustrywarden.tools;

import arc.Core;
import arc.util.Time;
import mindustry.Vars;

/**
 * Run the simulation faster, or slower.
 *
 * <p>Below 1x the frame step is simply scaled: a smaller step is always safe. Above 1x
 * the step cannot be scaled, since the game clamps its own at 3 and for good reason,
 * movement and collision assume small steps. So fast forward runs the world several times
 * per frame instead, each pass a normal tick.
 *
 * <p>Which is where the first version went wrong: it ran every extra tick it was asked
 * for, whatever that cost. At 64x on a real base that is a frame every second or two, and
 * the panel needed to turn it back down is behind that frame rate. A speed setting you
 * cannot undo is not a setting, it is a trap.
 *
 * <p>So the extra ticks now run against a time budget. The multiplier is a ceiling rather
 * than a promise: the world runs as many extra ticks as fit in a few milliseconds, and
 * the frame is drawn regardless. {@link #achieved()} reports what actually happened, so
 * the panel can say 12x when 64x was asked for instead of pretending.
 */
public final class GameSpeed {
    /** The game's own delta ceiling, which is why anything above 1x runs extra ticks. */
    public static final float max = 3f;

    public static final float[] steps = {0.25f, 0.5f, 1f, 2f, 4f, 8f, 16f, 32f, 64f};

    /**
     * How long a frame may spend on extra ticks, in nanoseconds.
     *
     * <p>Twelve milliseconds leaves the rest of a 60 Hz frame to the game itself, so the
     * interface stays responsive at any multiplier. Slower than the number on the button,
     * and usable, beats exact and frozen.
     */
    private static final long budget = 12_000_000L;

    private float multiplier = 1f;
    private float achieved = 1f;

    /**
     * Guards against re-entry.
     *
     * <p>The extra ticks are driven from a {@code Trigger.update} listener, and the game
     * fires that trigger from inside {@code logic.update()}. Calling it from there
     * without a flag is unbounded recursion on the first frame above 1x.
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

    /**
     * Run this frame's extra ticks. Call once per update.
     *
     * @param suspended true while the panel is open, so that a game slowed to a crawl by
     *                  its own speed setting is responsive again the moment you go to
     *                  change it
     */
    public void update(boolean suspended) {
        if (inExtraTick || multiplier <= 1f || !Vars.state.isPlaying()) {
            achieved = multiplier;
            return;
        }
        if (suspended) {
            achieved = 1f;
            return;
        }

        int extra = Math.round(multiplier) - 1;
        long deadline = System.nanoTime() + budget;
        int ran = 0;

        inExtraTick = true;
        try {
            while (ran < extra && System.nanoTime() < deadline) {
                Vars.logic.update();
                ran++;
            }
        } finally {
            inExtraTick = false;
        }

        achieved = ran + 1f;
    }

    /** What the last frame actually managed, which the budget can hold below the ask. */
    public float achieved() {
        return achieved;
    }

    public boolean throttled() {
        return multiplier > 1f && achieved < multiplier - 0.5f;
    }

    public float multiplier() {
        return multiplier;
    }

    public void multiplier(float value) {
        multiplier = Math.max(0.05f, Math.min(value, steps[steps.length - 1]));
    }
}
