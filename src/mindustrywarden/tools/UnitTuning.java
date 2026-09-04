package mindustrywarden.tools;

import mindustry.Vars;
import mindustry.game.Rules.TeamRule;
import mindustry.gen.Call;
import mindustry.gen.Unit;

/**
 * How fast your side moves, builds and mines.
 *
 * <p>Movement and the rest do not travel the same way, which is why they are set
 * differently here.
 *
 * <p>Movement speed is not a rule. It is {@code Unit.speedMultiplier}, a transient field
 * the game resets to 1 every tick before re-applying whatever status effects are on the
 * unit. So it cannot be set once: it is written every frame, from the mod's update hook,
 * and it stops on its own the moment the multiplier goes back to 1.
 *
 * <p>Building and mining speed are team rules. They are set once, and pushed to the other
 * players with {@code setRules} so that a hosted game agrees with itself. Being rules,
 * they are also written into the save.
 */
public final class UnitTuning {
    public static final float[] steps = {1f, 2f, 4f, 8f, 16f};

    private float unitSpeed = 1f;
    private boolean wholeTeam;

    /** Re-apply the movement speed, which lasts one tick and has to be renewed. */
    public void update() {
        if (unitSpeed == 1f || !Vars.state.isPlaying()) {
            return;
        }

        if (wholeTeam) {
            for (Unit unit : Vars.player.team().data().units) {
                accelerate(unit);
            }
        } else if (!Vars.player.dead() && Vars.player.unit() != null) {
            accelerate(Vars.player.unit());
        }
    }

    /**
     * Speed one unit up for this tick.
     *
     * <p>Not by writing {@code speedMultiplier}, which was the first attempt and did
     * nothing: the game fires {@code Trigger.update} at the very top of its own update
     * and clears every multiplier back to 1 further down the same frame, so anything
     * written from a listener is erased before it is read. {@code statusSpeed} goes
     * through a dynamic status effect instead, which is re-applied during that same
     * clearing pass, and it takes an absolute speed in tiles per second rather than a
     * factor.
     */
    private void accelerate(Unit unit) {
        unit.statusSpeed(unit.type.speed * 60f / Vars.tilesize * unitSpeed);
    }

    public float unitSpeed() {
        return unitSpeed;
    }

    /**
     * Set the movement multiplier, and hand back what was sped up when it returns to 1.
     *
     * <p>The dynamic status this goes through is applied with an infinite duration, so it
     * does not wear off on its own: simply stopping would leave every unit touched at
     * whatever multiplier was last written, for the rest of the game. This was a real
     * bug, and it is why coming back down is an explicit step rather than an absence.
     */
    public void unitSpeed(float value) {
        float previous = unitSpeed;
        unitSpeed = Math.max(1f, Math.min(value, steps[steps.length - 1]));

        if (previous != 1f && unitSpeed == 1f) {
            restore();
        }
    }

    /** Write a multiplier of 1 back onto everything this could have touched. */
    private void restore() {
        if (!Vars.state.isGame()) {
            return;
        }
        for (Unit unit : Vars.player.team().data().units) {
            unit.statusSpeed(unit.type.speed * 60f / Vars.tilesize);
        }
        if (!Vars.player.dead() && Vars.player.unit() != null) {
            Unit unit = Vars.player.unit();
            unit.statusSpeed(unit.type.speed * 60f / Vars.tilesize);
        }
    }

    public boolean wholeTeam() {
        return wholeTeam;
    }

    /**
     * Switch between your own unit and every unit of your team.
     *
     * <p>Narrowing the scope hands the rest of the team back its own speed, for the same
     * reason the multiplier does: nothing expires by itself here.
     */
    public void wholeTeam(boolean value) {
        if (wholeTeam && !value) {
            restore();
        }
        wholeTeam = value;
    }

    public float buildSpeed() {
        return rule().buildSpeedMultiplier;
    }

    public void buildSpeed(float value) {
        rule().buildSpeedMultiplier = value;
        push();
    }

    public float mineSpeed() {
        return rule().unitMineSpeedMultiplier;
    }

    public void mineSpeed(float value) {
        rule().unitMineSpeedMultiplier = value;
        push();
    }

    private static TeamRule rule() {
        return Vars.state.rules.teams.get(Vars.player.team());
    }

    private static void push() {
        if (Vars.net.server()) {
            Call.setRules(Vars.state.rules);
        }
    }
}
