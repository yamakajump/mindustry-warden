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

    /** Re-apply the movement multiplier, which the game clears on every tick. */
    public void update() {
        if (unitSpeed == 1f || !Vars.state.isPlaying()) {
            return;
        }

        if (wholeTeam) {
            for (Unit unit : Vars.player.team().data().units) {
                unit.speedMultiplier(unitSpeed);
            }
        } else if (!Vars.player.dead() && Vars.player.unit() != null) {
            Vars.player.unit().speedMultiplier(unitSpeed);
        }
    }

    public float unitSpeed() {
        return unitSpeed;
    }

    public void unitSpeed(float value) {
        unitSpeed = Math.max(1f, Math.min(value, steps[steps.length - 1]));
    }

    public boolean wholeTeam() {
        return wholeTeam;
    }

    /**
     * Switch between your own unit and every unit of your team.
     *
     * <p>Turning it off puts the team back to 1 by hand: the field is reset each tick
     * only for units something still writes to, and a unit left at 16x with nothing
     * writing to it keeps the last value written.
     */
    public void wholeTeam(boolean value) {
        if (wholeTeam && !value) {
            for (Unit unit : Vars.player.team().data().units) {
                unit.speedMultiplier(1f);
            }
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
