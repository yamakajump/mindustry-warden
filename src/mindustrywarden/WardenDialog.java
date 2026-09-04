package mindustrywarden;

import arc.scene.style.TextureRegionDrawable;
import arc.scene.ui.layout.Table;
import mindustry.Vars;
import mindustry.game.Team;
import mindustry.gen.Icon;
import mindustry.gen.Tex;
import mindustry.graphics.Pal;
import mindustry.type.UnitType;
import mindustry.ui.Styles;
import mindustry.ui.dialogs.BaseDialog;
import mindustrywarden.tools.BasePlans;
import mindustrywarden.tools.GameSpeed;
import mindustrywarden.tools.Invulnerability;
import mindustrywarden.tools.SectorCapture;
import mindustrywarden.tools.Supplies;
import mindustrywarden.tools.UnitSpawner;

/**
 * The panel, and the only thing in the mod that knows a tool exists.
 *
 * <p>It borrows the game's own furniture rather than inventing any: {@link Icon} and
 * {@link Tex} drawables, {@link Styles} button styles, {@link Pal} colours, and the
 * accent-underlined section headers the game uses in its database and settings dialogs. A
 * mod panel that invents its own look reads as a foreign object bolted onto the game, and
 * the game already answers every question this panel had to ask.
 *
 * <p>Tabs are rebuilt on each switch rather than kept and refreshed. Tool state changes
 * from outside the panel, a core can be destroyed while it is open, and a rebuild is cheap
 * next to reasoning about which widget went stale. What must stay live inside a tab uses a
 * label bound to a provider, so it follows the game without a rebuild.
 */
public class WardenDialog extends BaseDialog {
    private enum Tab {
        capture("Capture", Icon.modeAttack),
        recover("Recover", Icon.hammer),
        units("Units", Icon.units),
        supplies("Supplies", Icon.box),
        speed("Speed", Icon.waves);

        final String title;
        final TextureRegionDrawable icon;

        Tab(String title, TextureRegionDrawable icon) {
            this.title = title;
            this.icon = icon;
        }
    }

    private static final float panelWidth = 620f;
    private static final float unitIcon = 48f;
    private static final int unitsPerRow = 9;

    private final SectorCapture capture = new SectorCapture();
    private final BasePlans basePlans = new BasePlans();
    private final UnitSpawner spawner = new UnitSpawner();
    private final Supplies supplies = new Supplies();
    private final Invulnerability invulnerability = new Invulnerability();
    private final GameSpeed speed;

    private Tab tab = Tab.capture;
    /** Off by default: clearing your own blocks is the rarer, more destructive intent. */
    private boolean clearOwn;
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
        cont.top();

        cont.table(tabs -> {
            for (Tab candidate : Tab.values()) {
                tabs.button(candidate.title, candidate.icon, Styles.flatTogglet, Vars.iconMed, () -> {
                    tab = candidate;
                    rebuild();
                // Five tabs across the panel width, so the bar cannot outgrow the body
                // below it and leave the dialog wider than its own content.
                }).checked(button -> tab == candidate).size(120f, 52f).pad(2f);
            }
        }).padBottom(8f).row();

        if (!HostGuard.allowed()) {
            cont.table(Tex.pane, warning -> {
                warning.image(Icon.warning).color(Pal.remove).size(Vars.iconLarge).padRight(10f);
                warning.add(HostGuard.refusal()).wrap().width(panelWidth - 90f).color(Pal.lightishGray);
            }).width(panelWidth).pad(10f).row();
            return;
        }

        cont.table(body -> {
            body.top();
            body.defaults().left();
            switch (tab) {
                case capture -> buildCapture(body);
                case recover -> buildRecover(body);
                case units -> buildUnits(body);
                case supplies -> buildSupplies(body);
                case speed -> buildSpeed(body);
            }
        }).width(panelWidth).pad(4f);
    }

    /** The game's own section header: accent label over an accent rule. */
    private static void header(Table table, String text) {
        table.add(text).color(Pal.accent).left().growX().row();
        table.image().color(Pal.accent).height(3f).growX().padBottom(8f).row();
    }

    private static void note(Table table, String text) {
        table.add(text).wrap().width(panelWidth - 24f).color(Pal.lightishGray).padBottom(8f).row();
    }

    private void buildCapture(Table body) {
        header(body, "Capture this map");

        // Live, because the first version left a player staring at a panel that said the
        // capture had worked while the game was still waiting on one surviving unit.
        body.table(Tex.pane, status -> {
            status.defaults().left().growX().pad(2f);
            status.label(() -> Vars.state.rules.attackMode
                ? "Mode: attack, won when the enemy has no core left"
                : "Mode: waves, won at the winning wave with no enemy alive").row();
            status.label(() -> "Wave " + Vars.state.wave
                + (Vars.state.rules.winWave > 0 ? " of " + Vars.state.rules.winWave : "")).row();
            status.label(() -> Vars.state.enemies + " enemy units alive")
                .update(label -> label.setColor(Vars.state.enemies > 0 ? Pal.remove : Pal.heal)).row();
        }).width(panelWidth - 24f).pad(6f).row();

        note(body, "Removes every enemy building and unit, holds the wave timer back, and\n"
            + "lets the game declare the capture itself on the next tick.");

        body.button("Capture", Icon.modeAttack, Styles.defaultt, Vars.iconMed, () -> {
            SectorCapture.Result result = capture.run();
            if (!result.anythingToDo) {
                Vars.ui.showInfo("Nothing to remove: no enemy building or unit is left here.");
                return;
            }
            hide();
            Vars.ui.showInfoFade(result.blocks + " buildings and " + result.units
                + " units removed.", 5f);

            // A moment later, because the game's check runs on the next tick and the
            // answer to "why is it not captured" is worth more than silence.
            arc.util.Time.run(90f, () -> {
                String blocking = capture.blocking();
                if (blocking != null) {
                    Vars.ui.showInfoFade("Not captured yet: " + blocking, 7f);
                }
            });
        }).size(240f, 54f).padTop(4f).row();
    }

    private void buildRecover(Table body) {
        header(body, "Recover your destroyed base");

        body.table(Tex.pane, status -> {
            status.defaults().left().growX().pad(2f);
            status.label(() -> basePlans.plans().size + " destroyed blocks remembered")
                .update(label -> label.setColor(basePlans.plans().isEmpty() ? Pal.lightishGray : Pal.accent))
                .row();
        }).width(panelWidth - 24f).pad(6f).row();

        note(body, "The game files every building of yours that dies, with its position and\n"
            + "its configuration, and keeps the list in the save. Clear what was built on\n"
            + "top of them first: a plan under an occupied tile queues fine and then never\n"
            + "builds. The starter base the game drops when you launch counts as covering,\n"
            + "which is what the second option is for. Cores are never removed.");

        body.button("1. Clear what covers them", Icon.eraser, Styles.defaultt, Vars.iconMed, () -> {
            int cleared = basePlans.clearBlockers(clearOwn);
            Vars.ui.showInfoFade(cleared == 0
                ? "Nothing was standing on your plans."
                : cleared + " tiles cleared of what was built over your base.", 5f);
        }).size(320f, 54f).row();

        body.check("Clear my own blocks too, such as the launch loadout", clearOwn, on -> clearOwn = on)
            .left().padLeft(10f).padBottom(16f).row();

        body.button("2. Queue everything for rebuild", Icon.hammer, Styles.defaultt, Vars.iconMed, () -> {
            int queued = basePlans.queueAll();
            if (queued == 0) {
                Vars.ui.showInfo("Nothing to rebuild: the game remembers no destroyed block\n"
                    + "for your team on this map.");
                return;
            }
            hide();
            Vars.ui.showInfoFade(queued + " blocks queued. Your builders and any support\n"
                + "unit on the map will work through them.", 6f);
        }).size(320f, 54f).padBottom(16f).row();

        header(body, "Take it somewhere else");

        note(body, "Saves the same plans as schematics, cut into pieces the game will accept,\n"
            + "so the base can be rebuilt on another sector entirely. Logic processor links\n"
            + "keep their old absolute positions and will need redoing.");

        body.button("Export to schematics", Icon.copy, Styles.defaultt, Vars.iconMed, () -> {
            var saved = basePlans.export("Recovered base");
            Vars.ui.showInfoFade(saved.isEmpty()
                ? "Nothing to export."
                : saved.size + " schematics saved, look for \"Recovered base\".", 6f);
        }).size(320f, 54f).row();
    }

    private void buildUnits(Table body) {
        if (unitType == null) {
            unitType = spawner.types().first();
        }
        if (unitTeam == null) {
            unitTeam = Vars.player.team();
        }

        header(body, "Spawn units");

        body.table(Tex.pane, selected -> {
            selected.image(new TextureRegionDrawable(unitType.fullIcon)).size(Vars.iconXLarge).pad(6f);
            selected.add(unitType.localizedName).color(Pal.accent).padLeft(6f).growX().left();
        }).width(panelWidth - 24f).pad(6f).row();

        body.pane(grid -> {
            grid.left();
            int index = 0;
            for (UnitType type : spawner.types()) {
                grid.button(new TextureRegionDrawable(type.uiIcon), Styles.clearNoneTogglei,
                    Vars.iconLarge, () -> {
                        unitType = type;
                        rebuild();
                    }).size(unitIcon).checked(button -> unitType == type).tooltip(type.localizedName);

                if (++index % unitsPerRow == 0) {
                    grid.row();
                }
            }
        }).height(230f).width(panelWidth - 24f).scrollX(false).padBottom(8f).row();

        body.table(teams -> {
            teams.add("Team").color(Pal.lightishGray).padRight(10f);
            for (Team team : spawner.teams()) {
                teams.button(Tex.whiteui, Styles.clearNoneTogglei, 28f, () -> {
                    unitTeam = team;
                    rebuild();
                }).size(46f).pad(2f)
                    .checked(button -> unitTeam == team)
                    .tooltip(team.name)
                    .with(button -> button.getStyle().imageUpColor = team.color);
            }
        }).padBottom(4f).row();

        body.table(counts -> {
            counts.add("Count").color(Pal.lightishGray).padRight(10f);
            for (int count : new int[]{1, 5, 10, 25}) {
                int value = count;
                counts.button(String.valueOf(count), Styles.togglet, () -> {
                    unitCount = value;
                    rebuild();
                }).checked(button -> unitCount == value).size(64f, 44f).pad(2f);
            }
        }).padBottom(8f).row();

        body.button("Spawn", Icon.add, Styles.defaultt, Vars.iconMed, () -> {
            int spawned = spawner.spawn(unitType, unitTeam, unitCount);
            Vars.ui.showInfoFade(spawned < unitCount
                ? spawned + " of " + unitCount + " spawned, the team is at its unit cap."
                : spawned + " " + unitType.localizedName + " spawned.", 4f);
        }).size(240f, 54f).row();
    }

    private void buildSupplies(Table body) {
        header(body, "Resources");

        body.button("Fill the core", Icon.box, Styles.defaultt, Vars.iconMed, () -> {
            if (!supplies.hasCore()) {
                Vars.ui.showInfo("Your team has no core on this map.");
                return;
            }
            Vars.ui.showInfoFade(supplies.fillCore() + " item kinds topped up to capacity.", 4f);
        }).size(280f, 54f).padBottom(16f).row();

        header(body, "Research");

        note(body, "Unlocking is stored on your profile, not in this save: it stays unlocked\n"
            + "in every campaign afterwards, and there is no undo.");

        body.button("Unlock all research", Icon.tree, Styles.defaultt, Vars.iconMed, () ->
            Vars.ui.showConfirm("Unlock everything in the tech tree, permanently?", () ->
                Vars.ui.showInfoFade(supplies.unlockAll() + " entries unlocked.", 4f)))
            .size(280f, 54f).row();
    }

    private void buildSpeed(Table body) {
        header(body, "Game speed");

        body.table(steps -> {
            for (float step : GameSpeed.steps) {
                float value = step;
                steps.button(label(step), Styles.togglet, () -> {
                    speed.multiplier(value);
                    rebuild();
                }).checked(button -> speed.multiplier() == value).size(76f, 46f).pad(2f);
            }
        }).padBottom(6f).row();

        note(body, "The game clamps its own step at 3x. Past that units start crossing walls\n"
            + "between ticks, so there is no button for it.");

        header(body, "Invulnerability");

        note(body, "A team rule, so it travels with the save. Turn it off before you put the\n"
            + "game away.");

        body.check("Blocks and units of your team", invulnerability.enabled(), on -> {
            invulnerability.toggle(on);
            Vars.ui.showInfoFade(on ? "Invulnerable." : "Back to normal health.", 3f);
        }).left().row();
    }

    /** "1x" rather than "1.0x", which is what the game would print. */
    private static String label(float step) {
        return (step == Math.rint(step) ? String.valueOf((int) step) : String.valueOf(step)) + "x";
    }
}
