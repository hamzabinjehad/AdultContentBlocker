package app.hisn.core;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

public final class OemGuideTest {
    private static void fail(String message) {
        throw new AssertionError(message);
    }

    private static Set<OemGuide.Step> steps(List<OemGuide.Item> items) {
        Set<OemGuide.Step> steps = EnumSet.noneOf(OemGuide.Step.class);
        for (OemGuide.Item item : items) steps.add(item.step);
        return steps;
    }

    private static OemGuide.Status status(List<OemGuide.Item> items, OemGuide.Step step) {
        for (OemGuide.Item item : items) if (item.step == step) return item.status;
        return null;
    }

    private static void check(boolean value, String message) {
        if (!value) fail(message);
    }

    private static final OemGuide.Step POPUPS = OemGuide.Step.BACKGROUND_POPUPS;

    public static void main(String[] args) {
        int combinations = 0;
        for (DeviceFamily family : DeviceFamily.values()) {
            for (boolean battery : new boolean[]{false, true}) {
                for (Boolean background : new Boolean[]{null, Boolean.FALSE, Boolean.TRUE}) {
                    for (boolean autostart : new boolean[]{false, true}) {
                        for (boolean recents : new boolean[]{false, true}) {
                            List<OemGuide.Item> items = OemGuide.checklist(
                                    family, battery, background, autostart, recents);
                            String where = family.key + " battery=" + battery + " background="
                                    + background + " autostart=" + autostart + " recents=" + recents;
                            combinations++;

                            // The battery row is always first and always reflects the reading.
                            if (items.isEmpty()) fail(where + ": empty checklist");
                            if (items.get(0).step != OemGuide.Step.BATTERY_UNRESTRICTED) {
                                fail(where + ": battery is not the first row");
                            }
                            if (items.get(0).status != (battery ? OemGuide.Status.DONE
                                    : OemGuide.Status.ACTION)) {
                                fail(where + ": battery status does not follow the reading");
                            }
                            // The app-info switch has a row only where Android reports it.
                            if ((status(items, OemGuide.Step.BACKGROUND_ACTIVITY) != null)
                                    != (background != null)) {
                                fail(where + ": background row presence is wrong");
                            }
                            if (background != null && status(items, OemGuide.Step.BACKGROUND_ACTIVITY)
                                    != (background ? OemGuide.Status.ACTION : OemGuide.Status.DONE)) {
                                fail(where + ": background status does not follow the reading");
                            }
                            // OEM rows exist exactly for the families that need them.
                            if ((status(items, OemGuide.Step.AUTOSTART) != null)
                                    != family.autostartScreen) {
                                fail(where + ": autostart row presence is wrong");
                            }
                            if ((status(items, OemGuide.Step.KEEP_IN_RECENTS) != null)
                                    != family.recentsLock) {
                                fail(where + ": Recents row presence is wrong");
                            }
                            if (family.autostartScreen && status(items, OemGuide.Step.AUTOSTART)
                                    != (autostart ? OemGuide.Status.DONE
                                    : OemGuide.Status.CONFIRM)) {
                                fail(where + ": autostart confirmation is wrong");
                            }
                            if (family.recentsLock && status(items, OemGuide.Step.KEEP_IN_RECENTS)
                                    != (recents ? OemGuide.Status.DONE
                                    : OemGuide.Status.CONFIRM)) {
                                fail(where + ": Recents confirmation is wrong");
                            }
                            // A family without an OEM layer can never show an unmeasurable row.
                            if (!family.aggressiveBackgroundManager
                                    && steps(items).contains(OemGuide.Step.AUTOSTART)) {
                                fail(where + ": a plain family shows autostart");
                            }

                            int open = 0;
                            OemGuide.Item firstOpen = null;
                            for (OemGuide.Item item : items) {
                                if (item.status == OemGuide.Status.DONE) continue;
                                open++;
                                if (firstOpen == null) firstOpen = item;
                            }
                            int reported = OemGuide.open(family, battery, background, autostart, recents);
                            OemGuide.Item next = OemGuide.next(family, battery, background,
                                    autostart, recents);
                            if (reported != open) fail(where + ": open count " + reported
                                    + " but " + open + " rows");
                            if ((next == null) != (firstOpen == null)
                                    || (next != null && !next.equals(firstOpen))) {
                                fail(where + ": next is not the first open row");
                            }
                            boolean settled = battery && !Boolean.TRUE.equals(background);
                            boolean confirmed = (!family.autostartScreen || autostart)
                                    && (!family.recentsLock || recents);
                            if ((open == 0) != (settled && confirmed)) {
                                fail(where + ": settled state is wrong");
                            }
                        }
                    }
                }
            }
        }
        // 9 families x 2 battery x 3 background readings x 2 x 2 confirmations.
        if (combinations != 216) fail("Combinations: " + combinations);

        // The three named phones, row by row.
        List<OemGuide.Item> samsung = OemGuide.checklist(DeviceFamily.SAMSUNG, false,
                Boolean.TRUE, false, false);
        if (steps(samsung).size() != 3) fail("Samsung should show battery, background and Recents");
        if (OemGuide.next(DeviceFamily.SAMSUNG, false, Boolean.TRUE, false, false).step
                != OemGuide.Step.BATTERY_UNRESTRICTED) {
            fail("Samsung should offer battery first");
        }
        if (OemGuide.open(DeviceFamily.SAMSUNG, true, Boolean.FALSE, false, true) != 0) {
            fail("Samsung is not settled after its switches");
        }
        if (OemGuide.open(DeviceFamily.XIAOMI, true, Boolean.FALSE, false, false) != 2
                || OemGuide.next(DeviceFamily.XIAOMI, true, Boolean.FALSE, false, false).step
                != OemGuide.Step.AUTOSTART) {
            fail("Xiaomi should ask for autostart and Recents");
        }
        if (OemGuide.open(DeviceFamily.XIAOMI, true, Boolean.FALSE, true, true) != 0) {
            fail("Xiaomi is not settled after its confirmations");
        }
        if (OemGuide.open(DeviceFamily.NOTHING, true, Boolean.FALSE, false, false) != 0) {
            fail("Nothing OS should need only Android's own switches");
        }
        if (steps(OemGuide.checklist(DeviceFamily.NOTHING, true, null, true, true)).size() != 1) {
            fail("Nothing OS should show only the battery row before Android 9");
        }

        // Notes are warnings, not tasks: a second space and an install switch.
        Set<OemGuide.Note> samsungNotes = EnumSet.noneOf(OemGuide.Note.class);
        samsungNotes.addAll(OemGuide.notes(DeviceFamily.SAMSUNG));
        if (!samsungNotes.contains(OemGuide.Note.SECOND_SPACE)
                || !samsungNotes.contains(OemGuide.Note.INSTALL_UPDATE)) {
            fail("Samsung notes are incomplete");
        }
        if (!OemGuide.notes(DeviceFamily.XIAOMI).contains(OemGuide.Note.SECOND_SPACE)
                || !OemGuide.notes(DeviceFamily.XIAOMI).contains(OemGuide.Note.INSTALL_UPDATE)) {
            fail("Xiaomi notes are incomplete");
        }
        if (!OemGuide.notes(DeviceFamily.NOTHING).isEmpty()
                || !OemGuide.notes(DeviceFamily.STOCK).isEmpty()) {
            fail("Nothing OS or stock should carry no OEM note");
        }
        // Every mention of a family with an unmeasurable row also has notes or is plain.
        for (DeviceFamily family : DeviceFamily.values()) {
            if (family.parallelSpace && !OemGuide.notes(family).contains(OemGuide.Note.SECOND_SPACE)) {
                fail(family.key + ": second space has no note");
            }
            if (!family.parallelSpace && OemGuide.notes(family).contains(OemGuide.Note.SECOND_SPACE)) {
                fail(family.key + ": second-space note without a second space");
            }
        }

        boolean missingRejected = false;
        try {
            OemGuide.checklist(null, true, false, false, false);
        } catch (IllegalArgumentException expected) {
            missingRejected = true;
        }
        if (!missingRejected) fail("Missing family accepted");
        missingRejected = false;
        try {
            OemGuide.notes(null);
        } catch (IllegalArgumentException expected) {
            missingRejected = true;
        }
        if (!missingRejected) fail("Missing family accepted by notes");

                // Background pop-ups: Xiaomi and vivo only, live when the maker answers, a tick otherwise.
        check(OemGuide.hasPopupSwitch(DeviceFamily.XIAOMI) && OemGuide.hasPopupSwitch(DeviceFamily.VIVO)
                && !OemGuide.hasPopupSwitch(DeviceFamily.SAMSUNG) && !OemGuide.hasPopupSwitch(DeviceFamily.NOTHING)
                && !OemGuide.hasPopupSwitch(DeviceFamily.OPPO) && !OemGuide.hasPopupSwitch(DeviceFamily.STOCK),
                "pop-up switch families");
        check(status(OemGuide.checklist(DeviceFamily.XIAOMI, true, false, true, true, false, false), POPUPS)
                == OemGuide.Status.ACTION, "Xiaomi denied pop-ups are an action");
        check(status(OemGuide.checklist(DeviceFamily.XIAOMI, true, false, true, true, false, true), POPUPS)
                == OemGuide.Status.ACTION, "a tick never overrides a live denial");
        check(status(OemGuide.checklist(DeviceFamily.XIAOMI, true, false, true, true, true, false), POPUPS)
                == OemGuide.Status.DONE, "Xiaomi allowed pop-ups are done");
        check(status(OemGuide.checklist(DeviceFamily.VIVO, true, false, true, true, null, false), POPUPS)
                == OemGuide.Status.CONFIRM, "vivo cannot be read, so it asks for a tick");
        check(status(OemGuide.checklist(DeviceFamily.VIVO, true, false, true, true, null, true), POPUPS)
                == OemGuide.Status.DONE, "the tick settles vivo");
        check(!steps(OemGuide.checklist(DeviceFamily.SAMSUNG, true, false, false, true, false, false)).contains(POPUPS),
                "never shown on Samsung");
        check(!steps(OemGuide.checklist(DeviceFamily.XIAOMI, true, false, true, true)).contains(POPUPS),
                "the five-argument list is unchanged");
        java.util.List<OemGuide.Step> order = new java.util.ArrayList<>();
        for (OemGuide.Item item : OemGuide.checklist(DeviceFamily.XIAOMI, false, true, false, false, false, false)) {
            order.add(item.step);
        }
        check(order.equals(List.of(OemGuide.Step.BATTERY_UNRESTRICTED, OemGuide.Step.BACKGROUND_ACTIVITY,
                OemGuide.Step.AUTOSTART, POPUPS, OemGuide.Step.KEEP_IN_RECENTS)), "pop-ups come after Autostart");
        check(OemGuide.open(DeviceFamily.XIAOMI, true, false, true, true, false, false) == 1
                && OemGuide.open(DeviceFamily.XIAOMI, true, false, true, true, true, false) == 0,
                "the open count includes pop-ups");
        System.out.println("OemGuide tests passed (" + combinations + " phone states; Samsung, Xiaomi "
                + "and Nothing checked row by row)");
    }
}
