package app.hisn.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The ordered phone-setting checklist for one {@link DeviceFamily}.
 *
 * <p>Two kinds of step exist. A <b>measured</b> step ({@code BATTERY_UNRESTRICTED},
 * {@code BACKGROUND_ACTIVITY}) is a live Android reading: the app asks the platform, and the row
 * is done only when the platform says so. A <b>confirm</b> step ({@code AUTOSTART},
 * {@code KEEP_IN_RECENTS}) is a switch the maker does not expose, so the owner ticks it after
 * changing the OEM screen; Hisn stores that tick but never reports it as protection.</p>
 *
 * <p>A phone is "settled" when every applicable row is done. That is advice about keeping the
 * layers alive, not a claim of coverage: a force stop, a data wipe or a maker bug can still stop
 * the app, and {@code OemGuide} cannot change an Android setting by itself.</p>
 */
public final class OemGuide {
    private OemGuide() { }

    public enum Step {
        /** Android may still optimise Hisn; the app's battery screen is the repair. */
        BATTERY_UNRESTRICTED,
        /** Android's own app-info "Restricted" switch is on; the strongest background block. */
        BACKGROUND_ACTIVITY,
        /** Xiaomi, ColorOS, Vivo, EMUI and HiOS hide an autostart switch outside Settings. */
        AUTOSTART,
        /**
         * Xiaomi and vivo add their own "pop-up windows while running in the background"
         * permission. Without it Hisn's block explanation and its wait-before-opening screen
         * can silently fail to appear; Xiaomi answers it as an app op, vivo does not.
         */
        BACKGROUND_POPUPS,
        /** The maker kills apps that are not locked in the Recents screen. */
        KEEP_IN_RECENTS
    }

    public enum Status {
        /** The platform or the owner says this step is already done. */
        DONE,
        /** A live reading says the app is blocked; this is a real, fixable gap. */
        ACTION,
        /** The maker does not expose the switch; the owner has not confirmed it yet. */
        CONFIRM
    }

    /** One row of the checklist. */
    public static final class Item {
        public final Step step;
        public final Status status;

        private Item(Step step, Status status) {
            this.step = step;
            this.status = status;
        }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Item)) return false;
            Item item = (Item) other;
            return step == item.step && status == item.status;
        }

        @Override public int hashCode() {
            return step.hashCode() * 31 + status.hashCode();
        }

        @Override public String toString() {
            return step + "=" + status;
        }
    }

    /** Informational rows that are not tasks but change what the layers cover. */
    public enum Note {
        /** A second space / dual-app profile has its own copy of Android and needs its own install. */
        SECOND_SPACE,
        /** A manual update crosses a maker install switch. */
        INSTALL_UPDATE
    }

    /**
     * Every applicable row, in the order the screen shows it.
     *
     * @param backgroundRestricted {@code TRUE}/{@code FALSE} from Android's app-info switch, or
     *                             {@code null} before Android 9 where the value cannot be read
     *                             (the row is then omitted rather than guessed).
     */
    public static List<Item> checklist(DeviceFamily family, boolean batteryUnrestricted,
            Boolean backgroundRestricted, boolean autostartConfirmed, boolean recentsConfirmed) {
        return checklist(family, batteryUnrestricted, backgroundRestricted, autostartConfirmed,
                recentsConfirmed, false, null, false);
    }

    /** Whether the maker adds its own background pop-up permission. */
    public static boolean hasPopupSwitch(DeviceFamily family) {
        return family == DeviceFamily.XIAOMI || family == DeviceFamily.VIVO;
    }

    /**
     * The checklist with the background pop-up row.
     *
     * @param popupsAllowed the maker's live answer, or {@code null} when it cannot be read
     *                      (vivo, or a Xiaomi build that does not answer); the owner's tick then
     *                      decides, like Autostart.
     */
    public static List<Item> checklist(DeviceFamily family, boolean batteryUnrestricted,
            Boolean backgroundRestricted, boolean autostartConfirmed, boolean recentsConfirmed,
            Boolean popupsAllowed, boolean popupsConfirmed) {
        return checklist(family, batteryUnrestricted, backgroundRestricted, autostartConfirmed,
                recentsConfirmed, true, popupsAllowed, popupsConfirmed);
    }

    private static List<Item> checklist(DeviceFamily family, boolean batteryUnrestricted,
            Boolean backgroundRestricted, boolean autostartConfirmed, boolean recentsConfirmed,
            boolean withPopups, Boolean popupsAllowed, boolean popupsConfirmed) {
        if (family == null) throw new IllegalArgumentException("Missing device family");
        List<Item> items = new ArrayList<>();
        items.add(new Item(Step.BATTERY_UNRESTRICTED,
                batteryUnrestricted ? Status.DONE : Status.ACTION));
        if (backgroundRestricted != null) {
            items.add(new Item(Step.BACKGROUND_ACTIVITY,
                    backgroundRestricted ? Status.ACTION : Status.DONE));
        }
        if (family.autostartScreen) {
            items.add(new Item(Step.AUTOSTART,
                    autostartConfirmed ? Status.DONE : Status.CONFIRM));
        }
        if (withPopups && hasPopupSwitch(family)) {
            Status status;
            if (popupsAllowed != null) status = popupsAllowed ? Status.DONE : Status.ACTION;
            else status = popupsConfirmed ? Status.DONE : Status.CONFIRM;
            items.add(new Item(Step.BACKGROUND_POPUPS, status));
        }
        if (family.recentsLock) {
            items.add(new Item(Step.KEEP_IN_RECENTS,
                    recentsConfirmed ? Status.DONE : Status.CONFIRM));
        }
        return Collections.unmodifiableList(items);
    }

    /** The first row that still needs the owner, or null when every applicable row is done. */
    public static Item next(DeviceFamily family, boolean batteryUnrestricted,
            Boolean backgroundRestricted, boolean autostartConfirmed, boolean recentsConfirmed) {
        for (Item item : checklist(family, batteryUnrestricted, backgroundRestricted,
                autostartConfirmed, recentsConfirmed)) {
            if (item.status != Status.DONE) return item;
        }
        return null;
    }

    /** How many rows of the full checklist, pop-ups included, still need the owner. */
    public static int open(DeviceFamily family, boolean batteryUnrestricted,
            Boolean backgroundRestricted, boolean autostartConfirmed, boolean recentsConfirmed,
            Boolean popupsAllowed, boolean popupsConfirmed) {
        int open = 0;
        for (Item item : checklist(family, batteryUnrestricted, backgroundRestricted,
                autostartConfirmed, recentsConfirmed, popupsAllowed, popupsConfirmed)) {
            if (item.status != Status.DONE) open++;
        }
        return open;
    }

    /** How many applicable rows still need the owner; 0 means settled advice, not proof of coverage. */
    public static int open(DeviceFamily family, boolean batteryUnrestricted,
            Boolean backgroundRestricted, boolean autostartConfirmed, boolean recentsConfirmed) {
        int open = 0;
        for (Item item : checklist(family, batteryUnrestricted, backgroundRestricted,
                autostartConfirmed, recentsConfirmed)) {
            if (item.status != Status.DONE) open++;
        }
        return open;
    }

    /**
     * Warnings that apply to the whole phone family. These are not checklist rows: a second
     * space cannot be repaired from this install, and an update switch matters only when the
     * owner installs one. The caller shows them as notes.
     */
    public static List<Note> notes(DeviceFamily family) {
        if (family == null) throw new IllegalArgumentException("Missing device family");
        List<Note> notes = new ArrayList<>();
        if (family.parallelSpace) notes.add(Note.SECOND_SPACE);
        if (family.sideloadGuard) notes.add(Note.INSTALL_UPDATE);
        return Collections.unmodifiableList(notes);
    }
}
