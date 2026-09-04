package mindustrywarden;

import arc.scene.ui.layout.Table;
import mindustry.Vars;
import mindustry.game.Team;
import mindustry.type.UnitType;
import mindustry.ui.Styles;
import mindustry.ui.dialogs.BaseDialog;
import mindustrywarden.tools.GameSpeed;
import mindustrywarden.tools.Invulnerability;
import mindustrywarden.tools.SectorCapture;
import mindustrywarden.tools.Supplies;
import mindustrywarden.tools.UnitSpawner;

/**
 * The panel, and the only thing in the mod that knows a tool exists.
 *
 * <p>Every tab is rebuilt on each display rather than kept and refreshed. A tool's state
 * can change from outside the panel, a core can be destroyed while it is open, and a
 * rebuild is cheap next to reasoning about which widget is stale.
 */
public class WardenDialog extends BaseDialog {
    private enum Tab {
        capture("Capture"),
        units("Units"),
        supplies("Supplies"),
        speed("Speed");

        final String title;

        Tab(String title) {
            this.title = title;
        }
    }

    private final SectorCapture capture = new SectorCapture();
    private final UnitSpawner spawner = new UnitSpawner();
    private final Supplies supplies = new Supplies();
    private final Invulnerability invulnerability = new Invulnerability();
    private final GameSpeed speed;

    private Tab tab = Tab.capture;
    private UnitType unitType;
    private Team unitTeam;
    private int unitCount = 1;

    public WardenDialog(GameSpeed speed) {
        super("Warden");
        this.speed = speed;
        addCloseButton();
    }

    public void open() {
        rebuild();
        show();
    }

    private void rebuild() {
        cont.clear();

        cont.table(tabs -> {
            for (Tab candidate : Tab.values()) {
                tabs.button(candidate.title, Styles.togglet, () -> {
                    tab = candidate;
                    rebuild();
                }).checked(button -> tab == candidate).size(130f, 46f).pad(2f);
            }
        }).row();

        if (!HostGuard.allowed()) {
            cont.add(HostGuard.refusal()).pad(20f).width(520f).wrap().row();
            return;
        }

        cont.table(body -> {
            body.defaults().pad(4f);
            switch (tab) {
                case capture -> buildCapture(body);
                case units -> buildUnits(body);
                case supplies -> buildSupplies(body);
                case speed -> buildSpeed(body);
            }
        }).pad(12f).width(560f);
    }

    private void buildCapture(Table body) {
        body.add("Removes every enemy building and unit from this map, then lets the game\n"
            + "declare the win on its own. On a campaign sector that captures it for good.").wrap()
            .width(520f).row();

        body.button("Capture this map", () -> {
            SectorCapture.Result result = capture.run();
            if (!result.anythingToDo) {
                Vars.ui.showInfo("Nothing to remove: no enemy building or unit is left here.");
                return;
            }
            hide();
            Vars.ui.showInfoFade(result.blocks + " buildings and " + result.units
                + " units removed. The win lands on the next tick.", 6f);
        }).size(240f, 52f).row();
    }

    private void buildUnits(Table body) {
        if (unitType == null) {
            unitType = spawner.types().first();
        }
        if (unitTeam == null) {
            unitTeam = Vars.player.team();
        }

        body.add("Unit").row();
        body.pane(list -> {
            for (UnitType type : spawner.types()) {
                list.button(type.localizedName, Styles.togglet, () -> {
                    unitType = type;
                    rebuild();
                }).checked(button -> unitType == type).width(250f).height(40f).row();
            }
        }).height(220f).width(280f).row();

        body.table(teams -> {
            teams.add("Team").padRight(8f);
            for (Team team : spawner.teams()) {
                teams.button(team.name, Styles.togglet, () -> {
                    unitTeam = team;
                    rebuild();
                }).checked(button -> unitTeam == team).width(96f).height(40f).pad(2f);
            }
        }).row();

        body.table(counts -> {
            counts.add("Count").padRight(8f);
            for (int count : new int[]{1, 5, 10, 25}) {
                int value = count;
                counts.button(String.valueOf(count), Styles.togglet, () -> {
                    unitCount = value;
                    rebuild();
                }).checked(button -> unitCount == value).width(64f).height(40f).pad(2f);
            }
        }).row();

        body.button("Spawn", () -> {
            int spawned = spawner.spawn(unitType, unitTeam, unitCount);
            if (spawned < unitCount) {
                Vars.ui.showInfoFade(spawned + " of " + unitCount
                    + " spawned. The team is at its unit cap.", 5f);
            } else {
                Vars.ui.showInfoFade(spawned + " " + unitType.localizedName + " spawned.", 3f);
            }
        }).size(240f, 52f).row();
    }

    private void buildSupplies(Table body) {
        body.button("Fill the core", () -> {
            if (!supplies.hasCore()) {
                Vars.ui.showInfo("Your team has no core on this map.");
                return;
            }
            int filled = supplies.fillCore();
            Vars.ui.showInfoFade(filled + " item kinds topped up to capacity.", 4f);
        }).size(240f, 52f).row();

        body.add("Unlocking research is stored on your profile, not in this save: it stays\n"
            + "unlocked in every campaign afterwards, and there is no undo.").wrap()
            .width(520f).padTop(12f).row();

        body.button("Unlock all research", () ->
            Vars.ui.showConfirm("Unlock everything in the tech tree, permanently?", () -> {
                int unlocked = supplies.unlockAll();
                Vars.ui.showInfoFade(unlocked + " entries unlocked.", 4f);
            })).size(240f, 52f).row();
    }

    private void buildSpeed(Table body) {
        body.add("Speed").row();
        body.table(steps -> {
            for (float step : GameSpeed.steps) {
                float value = step;
                steps.button(step + "x", Styles.togglet, () -> {
                    speed.multiplier(value);
                    rebuild();
                }).checked(button -> speed.multiplier() == value).width(72f).height(40f).pad(2f);
            }
        }).row();

        body.add("The game clamps its own step at 3x. Past that, units start crossing walls,\n"
            + "so there is no button for it.").wrap().width(520f).padBottom(12f).row();

        body.check("Invulnerable blocks and units", invulnerability.enabled(), on -> {
            invulnerability.toggle(on);
            Vars.ui.showInfoFade(on
                ? "Invulnerable. This is written into the save, so turn it off before you\n"
                  + "put the game away."
                : "Back to normal health.", 5f);
        }).row();
    }
}
