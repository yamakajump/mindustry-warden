package mindustrywarden;

import arc.Core;
import arc.files.Fi;
import arc.scene.style.TextureRegionDrawable;
import arc.scene.ui.layout.Scl;
import arc.scene.ui.layout.Table;
import arc.struct.ObjectSet;
import arc.struct.Seq;
import mindustry.Vars;
import mindustry.game.Team;
import mindustry.gen.Icon;
import mindustry.gen.Tex;
import mindustry.graphics.Pal;
import mindustry.type.Item;
import mindustry.type.UnitType;
import mindustry.ui.Styles;
import mindustry.ui.dialogs.BaseDialog;
import mindustrywarden.tools.BasePlans;
import mindustrywarden.tools.ChatTranslation;
import mindustrywarden.tools.GameSpeed;
import mindustrywarden.tools.Invulnerability;
import mindustrywarden.tools.Rubble;
import mindustrywarden.tools.SectorCapture;
import mindustrywarden.tools.Snapshots;
import mindustrywarden.tools.Supplies;
import mindustrywarden.tools.Translator;
import mindustrywarden.tools.UnitSpawner;
import mindustrywarden.tools.UnitTuning;

/**
 * The panel, and the only thing in the mod that knows a tool exists.
 *
 * <p>Laid out like the game's own settings dialog: a menu down the left, the section on
 * the right, sized against the screen rather than to a fixed width. The first version was
 * a 700 pixel column on a 2000 pixel screen, most of it paragraphs, and paragraphs are
 * what a panel writes when it has not decided what it is for.
 *
 * <p>So the numbers do the talking. Each section opens with cards holding one figure and
 * one word, explanations live in tooltips, and every section has exactly one button that
 * matters with the rest kept smaller underneath. Wording comes from {@link Lang}, French
 * or English.
 *
 * <p>Sections are rebuilt on each switch rather than kept and refreshed. Tool state
 * changes from outside the panel, a core can be destroyed while it is open, and a rebuild
 * is cheap next to reasoning about which widget went stale.
 */
public class WardenDialog extends BaseDialog {
    private enum Tab {
        capture("tab.capture", Icon.modeAttack),
        recover("tab.recover", Icon.hammer),
        backups("tab.backups", Icon.save),
        units("tab.units", Icon.units),
        supplies("tab.supplies", Icon.box),
        speed("tab.speed", Icon.waves),
        chat("tab.chat", Icon.chat),
        settings("tab.settings", Icon.settings);

        final String key;
        final TextureRegionDrawable icon;

        Tab(String key, TextureRegionDrawable icon) {
            this.key = key;
            this.icon = icon;
        }
    }

    private static final float menuWidthMax = 230f;
    private static final float cardHeight = 100f;

    /**
     * Everything below is measured against the window, not fixed.
     *
     * <p>The panel rebuilt itself on resize while keeping the widths it was written with,
     * so on a small window the rows ran off the right edge: a "Restaurer" button reading
     * "Restau", a row of cards wider than the space holding them. These are recomputed on
     * every rebuild from what the window actually allows.
     */
    private float menuWidth = menuWidthMax;
    private float contentWidth = 700f;
    private float cardWidth = 168f;
    private float mainButton = 430f;

    private final SectorCapture capture = new SectorCapture();
    private final BasePlans basePlans = new BasePlans();
    private final Rubble rubble = new Rubble();
    private final Snapshots snapshots;
    private final UnitSpawner spawner = new UnitSpawner();
    private final Supplies supplies = new Supplies();
    private final Invulnerability invulnerability = new Invulnerability();
    private final ChatTranslation chat;
    private final GameSpeed speed;
    private final UnitTuning tuning;

    private Tab tab = Tab.recover;
    private boolean clearOwn;
    private boolean allPlanets;
    private final ObjectSet<Item> selectedItems = new ObjectSet<>();
    private int amount = 1000;
    private UnitType unitType;
    private Team unitTeam;
    private int unitCount = 1;

    public WardenDialog(GameSpeed speed, UnitTuning tuning, Snapshots snapshots,
        ChatTranslation chat) {
        super(Lang.get("title"));
        this.speed = speed;
        this.tuning = tuning;
        this.snapshots = snapshots;
        this.chat = chat;
        addCloseButton();

        // Sized against the screen, so a window resize has to rebuild it.
        resized(this::rebuild);
    }

    public void open() {
        rebuild();
        show();
    }

    /** How many sections there are, for the screenshot pass. */
    int tabCount() {
        return Tab.values().length;
    }

    /** The section's own key, which doubles as a file name. */
    String tabName(int index) {
        return Tab.values()[index].name();
    }

    /** Open one section directly, which only the screenshot pass needs. */
    void openTab(int index) {
        tab = Tab.values()[index];
        rebuild();
        if (!isShown()) {
            show();
        }
    }

    private void rebuild() {
        cont.clear();

        if (!HostGuard.allowed()) {
            cont.table(Tex.pane, warning -> {
                warning.image(Icon.warning).color(Pal.remove).size(Vars.iconLarge).padRight(12f);
                warning.add(Vars.state.isGame()
                    ? Lang.get("guard.client")
                    : Lang.get("guard.nogame")).wrap().width(Math.min(420f, contentWidth)).color(Pal.lightishGray);
            }).pad(20f).row();
            return;
        }

        // Wide enough to lay a section out in rows, narrow enough that the rows reach
        // the far edge: a panel with an empty right third reads as a panel that was cut
        // off, which is what the first attempt at this looked like.
        float screen = Core.graphics.getWidth() / Scl.scl(1f);
        // Half the screen when there is room, nearly all of it when there is not.
        float width = Math.min(Math.max(screen * 0.52f, 560f), screen - 30f);
        float height = Math.min(Core.graphics.getHeight() / Scl.scl(1f) * 0.72f, 880f);

        menuWidth = Math.min(menuWidthMax, width * 0.3f);
        contentWidth = width - menuWidth - 30f;
        cardWidth = Math.min(168f, (contentWidth - 24f) / 3f);
        mainButton = Math.min(430f, contentWidth - 10f);

        cont.table(root -> {
            root.table(Tex.pane, menu -> {
                menu.top();
                for (Tab candidate : Tab.values()) {
                    menu.button(Lang.get(candidate.key), candidate.icon, Styles.flatTogglet,
                        Vars.iconMed, () -> {
                            tab = candidate;
                            rebuild();
                        }).checked(button -> tab == candidate)
                        .size(menuWidth - 20f, 58f).pad(3f).row();
                }
            }).width(menuWidth).growY().padRight(8f);

            root.pane(body -> {
                body.top().left();
                body.defaults().left();
                switch (tab) {
                    case capture -> buildCapture(body);
                    case recover -> buildRecover(body);
                    case backups -> buildBackups(body);
                    case units -> buildUnits(body);
                    case supplies -> buildSupplies(body);
                    case speed -> buildSpeed(body);
                    case chat -> buildChat(body);
                    case settings -> buildSettings(body);
                }
            }).scrollX(false).grow().pad(4f);
        }).width(width).maxHeight(height);
    }

    /**
     * One figure, one word. What a paragraph was trying to say.
     *
     * <p>A number gets the big type it deserves; a word does not, since "capturé" at that
     * size is wider than the card holding it and comes out as "captur". Both are centred
     * and wrapped to the card rather than trusted to fit.
     */
    private void card(Table table, Object value, String labelKey, boolean warn) {
        String text = String.valueOf(value);
        boolean numeric = text.matches("[0-9/]+");

        table.table(Tex.pane, card -> {
            card.add(text).style(Styles.outlineLabel).fontScale(numeric ? 1.7f : 1.0f)
                .color(warn ? Pal.remove : Pal.accent).width(cardWidth - 18f).wrap()
                .get().setAlignment(arc.util.Align.center);
            card.row();
            card.add(Lang.get(labelKey)).color(Pal.lightishGray);
        }).size(cardWidth, cardHeight).pad(4f);
    }

    /** How many items of this width fit one row of the section. */
    private int perRow(float itemWidth) {
        return Math.max(1, (int) (contentWidth / (itemWidth + 6f)));
    }

    /** The width one of {@code count} buttons may take on a row, capped at its natural size. */
    private float rowOf(int count, float natural) {
        return Math.min(natural, (contentWidth - count * 10f) / count);
    }

    /**
     * How many buttons of at least {@code minWidth} fit a row.
     *
     * <p>Used instead of a fixed count so a narrow window wraps a row rather than
     * squeezing its buttons until "Donner" reads "Donn er" over two lines.
     */
    private int fit(float minWidth) {
        return Math.max(1, (int) (contentWidth / (minWidth + 10f)));
    }

    private void title(Table table, String key) {
        table.add(Lang.get(key)).color(Pal.accent).left().growX().padTop(4f).row();
        table.image().color(Pal.accent).height(3f).growX().padBottom(10f).row();
    }

    private void buildCapture(Table body) {
        title(body, "tab.capture");

        body.table(cards -> {
            cards.left();
            card(cards, Lang.get(Vars.state.rules.attackMode
                ? "capture.mode.attack" : "capture.mode.waves"), "tab.capture", false);
            card(cards, Vars.state.wave
                + (Vars.state.rules.winWave > 0 ? "/" + Vars.state.rules.winWave : ""),
                "capture.wave", false);
            card(cards, Vars.state.enemies, "capture.enemies", Vars.state.enemies > 0);
            // Three cards, not four: a fourth is one card too many for the narrowest
            // interface scale, and the sector's state is said in words below anyway.
        }).padBottom(12f).row();

        mindustry.type.Sector current = Vars.state.rules.sector;
        if (current != null && current.isCaptured()) {
            body.add(Lang.get("capture.already")).color(Pal.lightishGray).wrap().width(contentWidth).row();
        }

        body.button(Lang.get("capture.do"), Icon.modeAttack, Styles.defaultt, Vars.iconLarge, () -> {
            SectorCapture.Result result = capture.run();
            if (!result.anythingToDo) {
                Vars.ui.showInfo(Lang.get("capture.nothing"));
                return;
            }
            hide();
            Vars.ui.showInfoFade(Lang.get("capture.done", result.blocks, result.units), 5f);
            arc.util.Time.run(90f, () -> {
                String blocking = capture.blocking();
                if (blocking != null) {
                    Vars.ui.showInfoFade(Lang.get("capture.blocked", blocking), 7f);
                }
            });
        }).size(mainButton, 70f).tooltip(Lang.get("capture.hint")).row();
    }

    private void buildRecover(Table body) {
        title(body, "tab.recover");

        int plans = basePlans.plans().size;
        int rubbleCount = rubble.count();

        body.table(cards -> {
            cards.left();
            card(cards, plans, "recover.plans", plans > 0);
            card(cards, rubbleCount, "recover.rubble", rubbleCount > 0);
        }).padBottom(12f).row();

        body.button(Lang.get("recover.all"), Icon.refresh, Styles.defaultt, Vars.iconLarge, () ->
            Vars.ui.showConfirm(Lang.get("recover.all.confirm"), () -> {
                SectorCapture.Result removed = capture.run();
                int rubbleGone = rubble.clear();
                int fires = rubble.extinguish();
                int cleared = basePlans.clearBlockers(true);
                int placed = basePlans.placeAll();
                hide();
                Vars.ui.showInfoFade(Lang.get("recover.all.done", placed,
                    removed.blocks + rubbleGone + fires + cleared), 7f);
            })).size(mainButton, 70f).tooltip(Lang.get("recover.all.hint")).padBottom(14f).row();

        body.table(steps -> {
            steps.left();
            steps.defaults().size(rowOf(Math.min(2, fit(240f)), 265f), 56f).pad(4f);

            steps.button(Lang.get("recover.rubble.do"), Icon.trash, Styles.defaultt,
                Vars.iconMed, () -> {
                    int gone = rubble.clear();
                    rebuild();
                    Vars.ui.showInfoFade(Lang.get("recover.rubble.done", gone), 4f);
                }).tooltip(Lang.get("recover.rubble.hint"));

            steps.button(Lang.get("recover.clear.do"), Icon.eraser, Styles.defaultt,
                Vars.iconMed, () -> {
                    int cleared = basePlans.clearBlockers(clearOwn);
                    Vars.ui.showInfoFade(Lang.get("recover.cleared", cleared), 4f);
                }).tooltip(Lang.get("recover.clear.hint"));

            steps.row();

            steps.button(Lang.get("recover.place.do"), Icon.wrench, Styles.defaultt,
                Vars.iconMed, () -> {
                    int placed = basePlans.placeAll();
                    if (placed == 0) {
                        Vars.ui.showInfo(Lang.get("recover.nothing"));
                        return;
                    }
                    hide();
                    Vars.ui.showInfoFade(Lang.get("recover.placed", placed), 5f);
                }).tooltip(Lang.get("recover.place.hint"));
        }).row();

        body.check(Lang.get("recover.clear.own"), clearOwn, on -> clearOwn = on)
            .left().padLeft(6f).padBottom(10f)
            .tooltip(Lang.get("recover.clear.own.hint")).row();

        body.table(more -> {
            more.left();
            more.defaults().size(rowOf(Math.min(2, fit(240f)), 265f), 52f).pad(4f);

            more.button(Lang.get("recover.queue.do"), Icon.hammer, Styles.defaultt,
                Vars.iconMed, () -> {
                    int queued = basePlans.queueAll();
                    hide();
                    Vars.ui.showInfoFade(Lang.get("recover.placed", queued), 5f);
                }).tooltip(Lang.get("recover.queue.hint"));

            more.button(Lang.get("recover.export.do"), Icon.copy, Styles.defaultt,
                Vars.iconMed, () -> {
                    var saved = basePlans.export("Recovered base");
                    Vars.ui.showInfoFade(Lang.get("recover.export.done", saved.size), 5f);
                }).tooltip(Lang.get("recover.export.hint"));
        }).row();
    }

    private void buildBackups(Table body) {
        title(body, "tab.backups");

        if (!snapshots.available()) {
            body.add(Lang.get("backups.unavailable")).color(Pal.lightishGray).wrap().width(contentWidth);
            return;
        }

        Seq<Fi> copies = snapshots.list();

        body.table(cards -> {
            cards.left();
            card(cards, copies.size, "backups.count", false);
            card(cards, (int) snapshots.interval(), "backups.minutes", false);
        }).padBottom(12f).row();

        body.button(Lang.get("backups.now"), Icon.save, Styles.defaultt, Vars.iconLarge, () -> {
            snapshots.capture();
            rebuild();
            Vars.ui.showInfoFade(Lang.get("backups.taken"), 3f);
        }).size(mainButton, 70f).tooltip(Lang.get("backups.hint")).padBottom(12f).row();

        body.table(row -> {
            row.left();
            row.check(Lang.get("backups.auto"), snapshots.automatic(), on -> {
                snapshots.automatic(on);
                rebuild();
            }).padRight(20f);

            row.add(Lang.get("backups.every")).color(Pal.lightishGray).padRight(8f);
            for (float minutes : Snapshots.intervals) {
                float value = minutes;
                row.button(String.valueOf((int) minutes), Styles.togglet, () -> {
                    snapshots.interval(value);
                    rebuild();
                }).checked(button -> snapshots.interval() == value).size(58f, 44f).pad(2f);
            }
        }).padBottom(12f).row();

        for (Fi copy : copies) {
            long age = snapshots.minutesOld(copy);
            body.table(Tex.pane, row -> {
                row.add(age == 0 ? Lang.get("backups.justnow") : Lang.get("backups.ago", age))
                    .growX().left();
                row.add(copy.length() / 1024 + " kB").color(Pal.lightishGray).width(100f).left();
                row.button(Lang.get("backups.restore"), Icon.refresh, Styles.defaultt,
                    Vars.iconSmall, () ->
                        Vars.ui.showConfirm(Lang.get("backups.confirm"), () -> {
                            hide();
                            snapshots.restore(copy);
                        })).size(Math.min(180f, contentWidth * 0.3f), 46f);
            }).width(contentWidth - 8f).pad(3f).row();
        }
    }

    private void buildUnits(Table body) {
        title(body, "tab.units");

        Team mine = Vars.player.team();
        body.table(cards -> {
            cards.left();
            card(cards, spawner.alive(mine), "units.alive", false);
            card(cards, spawner.cap(mine), "units.cap", false);
        }).padBottom(10f).row();

        if (unitType == null) {
            unitType = spawner.types().first();
        }
        if (unitTeam == null) {
            unitTeam = Vars.player.team();
        }

        body.table(selected -> {
            selected.image(new TextureRegionDrawable(unitType.fullIcon)).size(Vars.iconXLarge).pad(6f);
            selected.add(unitType.localizedName).color(Pal.accent).padLeft(8f);
        }).padBottom(8f).row();

        body.table(grid -> {
            grid.left();
            int index = 0;
            for (UnitType type : spawner.types()) {
                grid.button(new TextureRegionDrawable(type.uiIcon), Styles.clearNoneTogglei,
                    Vars.iconLarge, () -> {
                        unitType = type;
                        rebuild();
                    }).size(56f).checked(button -> unitType == type).tooltip(type.localizedName);

                if (++index % perRow(56f) == 0) {
                    grid.row();
                }
            }
        }).padBottom(12f).row();

        body.table(row -> {
            row.left();
            row.add(Lang.get("units.team")).color(Pal.lightishGray).padRight(10f);
            for (Team team : spawner.teams()) {
                row.button(Tex.whiteui, Styles.clearNoneTogglei, 30f, () -> {
                    unitTeam = team;
                    rebuild();
                }).size(48f).pad(2f).checked(button -> unitTeam == team).tooltip(team.name)
                    .with(button -> button.getStyle().imageUpColor = team.color);
            }

        }).padBottom(4f).row();

        body.table(row -> {
            row.left();
            row.add(Lang.get("units.count")).color(Pal.lightishGray).padRight(10f);
            for (int count : new int[]{1, 5, 10, 25}) {
                int value = count;
                row.button(String.valueOf(count), Styles.togglet, () -> {
                    unitCount = value;
                    rebuild();
                }).checked(button -> unitCount == value).size(60f, 44f).pad(2f);
            }
        }).padBottom(12f).row();

        body.table(actions -> {
            actions.left();
            actions.button(Lang.get("units.spawn"), Icon.add, Styles.defaultt, Vars.iconLarge, () -> {
                int spawned = spawner.spawn(unitType, unitTeam, unitCount);
                rebuild();
                Vars.ui.showInfoFade(spawned < unitCount
                    ? Lang.get("units.capped", spawned, unitCount)
                    : Lang.get("units.spawned", spawned + " " + unitType.localizedName), 4f);
            }).size(mainButton * 0.6f, 70f).pad(3f);

            // "As many as the game will take", which is what a cap is for.
            actions.button(Lang.get("units.fill"), Icon.units, Styles.defaultt, Vars.iconMed, () -> {
                int room = Math.max(0, spawner.cap(unitTeam) - spawner.alive(unitTeam));
                int spawned = spawner.spawn(unitType, unitTeam, room);
                rebuild();
                Vars.ui.showInfoFade(
                    Lang.get("units.spawned", spawned + " " + unitType.localizedName), 4f);
            }).size(mainButton * 0.62f, 70f).pad(3f);
        }).row();
    }

    private void buildSupplies(Table body) {
        title(body, "tab.supplies");

        if (!supplies.hasCore()) {
            body.add(Lang.get("supplies.nocore")).color(Pal.lightishGray);
            return;
        }

        body.table(items -> {
            items.left();
            int index = 0;
            for (Item item : supplies.items(allPlanets)) {
                items.button(new TextureRegionDrawable(item.uiIcon), Styles.clearNoneTogglei,
                    Vars.iconLarge, () -> {
                        if (!selectedItems.add(item)) {
                            selectedItems.remove(item);
                        }
                        rebuild();
                    }).size(56f).checked(button -> selectedItems.contains(item))
                    .tooltip(item.localizedName);

                if (++index % perRow(56f) == 0) {
                    items.row();
                }
            }
        }).padBottom(8f).row();

        body.table(row -> {
            row.left();
            row.add(Lang.get("supplies.amount")).color(Pal.lightishGray).padRight(10f);
            for (int option : new int[]{100, 1000, 10000, supplies.capacity()}) {
                int value = option;
                row.button(value == supplies.capacity() ? Lang.get("supplies.max")
                        : (value >= 1000 ? (value / 1000) + "k" : String.valueOf(value)),
                    Styles.togglet, () -> {
                        amount = value;
                        rebuild();
                    }).checked(button -> amount == value).size(70f, 44f).pad(2f);
            }

        }).padBottom(4f).row();

        body.check(Lang.get("supplies.otherplanets"), allPlanets, on -> {
            allPlanets = on;
            rebuild();
        }).left().padBottom(12f).row();

        body.table(actions -> {
            actions.left();
            int perLine = Math.min(3, fit(170f));
            actions.defaults().size(rowOf(perLine, 190f), 62f).pad(4f);
            int[] placed = {0};
            Runnable wrap = () -> {
                if (++placed[0] % perLine == 0) {
                    actions.row();
                }
            };

            actions.button(Lang.get("supplies.give"), Icon.add, Styles.defaultt, Vars.iconMed, () -> {
                if (selectedItems.isEmpty()) {
                    Vars.ui.showInfo(Lang.get("supplies.pick"));
                    return;
                }
                Vars.ui.showInfoFade(
                    Lang.get("supplies.given", supplies.give(selectedItems, amount), amount), 4f);
            });
            wrap.run();

            actions.button(Lang.get("supplies.take"), Icon.download, Styles.defaultt,
                Vars.iconMed, () -> {
                    if (selectedItems.isEmpty()) {
                        Vars.ui.showInfo(Lang.get("supplies.pick"));
                        return;
                    }
                    Vars.ui.showInfoFade(
                        Lang.get("supplies.taken", supplies.take(selectedItems, amount)), 4f);
                });
            wrap.run();

            actions.button(Lang.get("supplies.empty"), Icon.trash, Styles.defaultt,
                Vars.iconMed, () -> {
                    if (selectedItems.isEmpty()) {
                        Vars.ui.showInfo(Lang.get("supplies.pick"));
                        return;
                    }
                    Vars.ui.showInfoFade(Lang.get("supplies.taken",
                        supplies.take(selectedItems, Integer.MAX_VALUE)), 4f);
                });
            wrap.run();
        }).padBottom(10f).row();

        body.table(more -> {
            more.left();
            int perLine = Math.min(2, fit(240f));
            more.defaults().size(rowOf(perLine, 280f), 52f).pad(3f);

            more.button(Lang.get("supplies.all"), Icon.box, Styles.defaultt, Vars.iconMed, () -> {
                var local = supplies.items(false);
                supplies.give(local, supplies.capacity());
                Vars.ui.showInfoFade(Lang.get("supplies.given", local.size,
                    supplies.capacity()), 4f);
            });

            more.button(Lang.get("supplies.foreign"), Icon.trash, Styles.defaultt,
                Vars.iconMed, () ->
                    Vars.ui.showInfoFade(Lang.get("supplies.taken", supplies.removeForeign()), 4f))
                .with(button -> {
                    if (perLine == 1) {
                        more.row();
                    }
                });
        }).padBottom(10f).row();

        body.button(Lang.get("supplies.research"), Icon.tree, Styles.defaultt, Vars.iconMed, () ->
            Vars.ui.showConfirm(Lang.get("supplies.research.confirm"), () ->
                Vars.ui.showInfoFade(Lang.get("supplies.research.done", supplies.unlockAll()), 4f)))
            .size(rowOf(2, 280f), 52f).tooltip(Lang.get("supplies.research.hint")).row();
    }

    private void buildSpeed(Table body) {
        title(body, "speed.game");

        body.table(cards -> {
            cards.left();
            card(cards, label(speed.achieved()), "speed.actual", speed.throttled());
            card(cards, label(tuning.unitSpeed()), "speed.you", false);
        }).padBottom(10f).row();

        body.table(steps -> {
            steps.left();
            int index = 0;
            for (float step : GameSpeed.steps) {
                float value = step;
                steps.button(label(step), Styles.togglet, () -> {
                    speed.multiplier(value);
                    rebuild();
                }).checked(button -> speed.multiplier() == value).size(86f, 46f).pad(2f);

                // Nine speeds do not fit one row of the panel, and the row that overflows
                // is the one holding 64x, which is the reason anyone opens this section.
                if (++index % perRow(90f) == 0) {
                    steps.row();
                }
            }
        }).padBottom(4f).row();

        body.add(Lang.get("speed.game.hint")).color(Pal.lightishGray).wrap().width(contentWidth)
            .padBottom(14f).row();

        title(body, "speed.you");

        body.table(steps -> {
            steps.left();
            for (float step : UnitTuning.steps) {
                float value = step;
                steps.button(label(step), Styles.togglet, () -> {
                    tuning.unitSpeed(value);
                    rebuild();
                }).checked(button -> tuning.unitSpeed() == value).size(86f, 46f).pad(2f);
            }
            steps.check(Lang.get("speed.team"), tuning.wholeTeam(), tuning::wholeTeam).padLeft(20f);
        }).padBottom(14f).row();

        body.table(rates -> {
            rates.left();
            rates.add(Lang.get("speed.build")).color(Pal.lightishGray)
                .width(Math.min(160f, contentWidth * 0.3f))
                .tooltip(Lang.get("speed.build.hint"));
            for (float step : UnitTuning.steps) {
                float value = step;
                rates.button(label(step), Styles.togglet, () -> {
                    tuning.buildSpeed(value);
                    rebuild();
                }).checked(button -> tuning.buildSpeed() == value)
                    .size(Math.min(68f, (contentWidth * 0.7f - 20f) / 5f), 44f).pad(2f);
            }
        }).row();

        body.table(rates -> {
            rates.left();
            rates.add(Lang.get("speed.mine")).color(Pal.lightishGray)
                .width(Math.min(160f, contentWidth * 0.3f))
                .tooltip(Lang.get("speed.mine.hint"));
            for (float step : UnitTuning.steps) {
                float value = step;
                rates.button(label(step), Styles.togglet, () -> {
                    tuning.mineSpeed(value);
                    rebuild();
                }).checked(button -> tuning.mineSpeed() == value)
                    .size(Math.min(68f, (contentWidth * 0.7f - 20f) / 5f), 44f).pad(2f);
            }
        }).padBottom(14f).row();

        body.check(Lang.get("speed.invulnerable"), invulnerability.enabled(),
            invulnerability::toggle).left().tooltip(Lang.get("speed.invulnerable.hint")).row();
    }

    private void buildChat(Table body) {
        title(body, "tab.chat");

        body.check(Lang.get("chat.read"), chat.reading(), chat::reading)
            .left().tooltip(Lang.get("chat.read.hint")).padBottom(6f).row();

        body.table(row -> {
            row.left();
            row.add(Lang.get("chat.into")).color(Pal.lightishGray)
                .width(Math.min(160f, contentWidth * 0.3f));
            languages(row, chat.into(), chat::into);
        }).padBottom(14f).row();

        body.check(Lang.get("chat.write"), chat.writing(), chat::writing)
            .left().tooltip(Lang.get("chat.write.hint")).padBottom(6f).row();

        body.table(row -> {
            row.left();
            row.add(Lang.get("chat.out")).color(Pal.lightishGray)
                .width(Math.min(160f, contentWidth * 0.3f));
            languages(row, chat.out(), chat::out);
        }).padBottom(14f).row();

        body.add(Lang.get("chat.privacy")).color(Pal.lightishGray).wrap()
            .width(contentWidth).row();
    }

    /** The language buttons, shared by both directions of the chat. */
    private void languages(Table table, String current, arc.func.Cons<String> pick) {
        int index = 0;
        for (String code : Translator.languages) {
            // The button shows "zh", the request still asks for "zh-CN": the full code
            // does not fit and comes out broken across two lines.
            table.button(code.length() > 2 ? code.substring(0, 2) : code, Styles.togglet, () -> {
                pick.get(code);
                rebuild();
            }).checked(button -> current.equals(code)).size(78f, 44f).pad(2f);

            if (++index % Math.max(1, fit(82f) - 2) == 0) {
                table.row();
                table.add("").width(Math.min(160f, contentWidth * 0.3f));
            }
        }
    }

    private void buildSettings(Table body) {
        title(body, "settings.language");

        body.table(languages -> {
            languages.left();
            for (String code : new String[]{"fr", "en"}) {
                languages.button(code.equals("fr") ? "Français" : "English", Styles.togglet, () -> {
                    Lang.language(code);
                    rebuild();
                }).checked(button -> Lang.language().equals(code)).size(180f, 52f).pad(3f);
            }
        }).padBottom(16f).row();

        title(body, "settings.key");

        body.add(Lang.get("settings.key.value")).color(Pal.lightishGray).padBottom(16f).row();

        body.add(Lang.get("settings.about")).color(Pal.lightishGray).wrap().width(contentWidth).row();
    }

    /** "1x" rather than "1.0x", which is what the game would print. */
    private static String label(float step) {
        return (step == Math.rint(step) ? String.valueOf((int) step) : String.valueOf(step)) + "x";
    }
}
