package mindustrywarden.tools;

import arc.files.Fi;
import arc.struct.Seq;
import arc.util.Time;
import mindustry.Vars;
import mindustry.game.Saves.SaveSlot;
import mindustry.type.Sector;

/**
 * Copies of the sector, taken on a timer, kept in rotation.
 *
 * <p>This is the tool the rest of Warden exists to make unnecessary. Recovery works from
 * the plans the game keeps, and those only cover blocks destroyed by damage: anything
 * deconstructed, or destroyed while the rule was off, or lost to sector damage between
 * two visits, is gone from the save with nothing left to rebuild from. No tool can invent
 * it afterwards.
 *
 * <p>A copy taken beforehand can. The night this mod was written, what actually saved a
 * base was a manual export made hours earlier, holding 4948 buildings against the 2046
 * that survived. This does that on its own, every few minutes, for whatever sector is
 * being played.
 */
public final class Snapshots {
    /** How many copies to keep per sector. Twenty at five minutes is over an hour back. */
    private static final int keep = 20;

    public static final float[] intervals = {2f, 5f, 10f, 30f};

    private boolean automatic = true;
    private float intervalMinutes = 5f;
    private long last;

    /** Where copies live: beside the game's own saves, not among them. */
    public Fi directory() {
        Fi directory = Vars.dataDirectory.child("warden-snapshots");
        if (!directory.exists()) {
            directory.mkdirs();
        }
        return directory;
    }

    /**
     * One folder per sector, so a rotation on one map cannot push out the copies of
     * another. Custom games share a folder, since they have no sector to name.
     */
    public Fi folder() {
        Sector sector = Vars.state.rules.sector;
        String name = sector == null
            ? "custom"
            : sector.planet.name + "-" + sector.id;

        Fi folder = directory().child(name);
        if (!folder.exists()) {
            folder.mkdirs();
        }
        return folder;
    }

    public boolean available() {
        return Vars.state.isGame() && !Vars.net.client() && slot() != null;
    }

    /**
     * Take a copy now, and return it.
     *
     * <p>The game is asked to write its state out first: the file on disk is whatever the
     * last autosave left there, which on a long session is minutes behind the base a
     * player is looking at.
     */
    public Fi capture() {
        SaveSlot slot = slot();
        if (slot == null) {
            return null;
        }

        slot.save();
        Fi target = folder().child(Time.millis() + ".msav");
        slot.file.copyTo(target);
        last = Time.millis();
        prune();
        return target;
    }

    /** Newest first, which is the order a person reads a list of restore points in. */
    public Seq<Fi> list() {
        Seq<Fi> files = Seq.with(folder().list()).select(file -> file.extension().equals("msav"));
        files.sort((a, b) -> Long.compare(stamp(b), stamp(a)));
        return files;
    }

    /**
     * Put a copy back and reload the sector from it.
     *
     * <p>Written onto the game's own save file rather than loaded directly: the game
     * keeps writing to that path, so a copy loaded without replacing it would be
     * overwritten by the next autosave and the restore would quietly undo itself.
     */
    public void restore(Fi snapshot) {
        SaveSlot slot = slot();
        if (slot == null) {
            return;
        }
        snapshot.copyTo(slot.file);
        slot.load();
    }

    /** Age of a copy in minutes, for a list that means something to read. */
    public long minutesOld(Fi snapshot) {
        return Math.max(0, (Time.millis() - stamp(snapshot)) / 60_000);
    }

    /** Called every frame; takes a copy when one is due. */
    public void update() {
        if (!automatic || !available()) {
            return;
        }
        if (last != 0 && Time.millis() - last < (long) (intervalMinutes * 60_000)) {
            return;
        }
        capture();
    }

    public boolean automatic() {
        return automatic;
    }

    public void automatic(boolean value) {
        automatic = value;
    }

    public float interval() {
        return intervalMinutes;
    }

    public void interval(float minutes) {
        intervalMinutes = minutes;
    }

    private void prune() {
        Seq<Fi> files = list();
        for (int i = keep; i < files.size; i++) {
            files.get(i).delete();
        }
    }

    private static long stamp(Fi file) {
        try {
            return Long.parseLong(file.nameWithoutExtension());
        } catch (NumberFormatException ignored) {
            return file.lastModified();
        }
    }

    private static SaveSlot slot() {
        return Vars.control.saves.getCurrent();
    }
}
