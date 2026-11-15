package app.hisn.android;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.provider.Settings;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import app.hisn.core.ProtectionLock;

/**
 * Uninstall protection: the keep-installed and install guards on the direct build, the
 * device-owner restrictions on a managed phone, and the provisioning command otherwise. Moved
 * out of the classic screen so the native shell opens it directly.
 */
final class UninstallDialog {
    private UninstallDialog() { }

    /** Shows the dialog; [onChanged] runs after any saved change so the caller can refresh. */
    static void show(Activity activity, Runnable onChanged) {
        PolicyStore settings;
        try { settings = PolicyStore.get(activity); }
        catch (RuntimeException unavailable) {
            Toast.makeText(activity, R.string.policy_unavailable, Toast.LENGTH_LONG).show();
            return;
        }
        UninstallProtectionController.Status status = UninstallProtectionController.read(activity);
        boolean commitment = status.remainingMillis > 0;
        boolean screenGuard = BuildConfig.SELF_PROTECTION && !status.deviceOwner;

        LinearLayout form = new LinearLayout(activity);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(Ui.dp(activity, 20), Ui.dp(activity, 4), Ui.dp(activity, 20), Ui.dp(activity, 12));
        // The current state comes first, then the switches; the explanation follows them.
        // The dialog's title already says "Uninstall protection"; the state line drops that prefix.
        String state = longStatus(activity, settings, status);
        int prefix = state.indexOf(" \u00b7 ");
        form.addView(Ui.body(activity, prefix >= 0 ? state.substring(prefix + 3) : state));
        TextView explanation = Ui.caption(activity, activity.getString(status.deviceOwner
                ? R.string.uninstall_dialog_body_owner
                : screenGuard ? R.string.self_protection_dialog_body
                : R.string.uninstall_dialog_body_unmanaged));

        Switch keepSwitch = null;
        if (status.deviceOwner || screenGuard) {
            keepSwitch = Ui.toggle(activity, activity.getString(screenGuard
                            ? R.string.self_protection_keep : R.string.uninstall_keep),
                    activity.getString(screenGuard ? (commitment ? R.string.self_protection_commitment
                                    : R.string.uninstall_keep_hint)
                            : commitment ? R.string.uninstall_commitment_auto
                            : R.string.uninstall_keep_hint));
            keepSwitch.setChecked(settings.uninstallLock());
            // Enabling is a tightening and stays available during a protection lock. Disabling
            // is refused until the lock ends; the guard is already on for the lock regardless.
            keepSwitch.setEnabled(!(commitment && keepSwitch.isChecked()));
            form.addView(keepSwitch, Ui.margins(activity, 8, 0));
            if (screenGuard && commitment && !settings.uninstallLock()) {
                form.addView(Ui.caption(activity, activity.getString(R.string.self_protection_lock_auto)),
                        Ui.margins(activity, 2, 0));
            }
        }
        Switch installSwitch = null;
        if (screenGuard) {
            installSwitch = Ui.toggle(activity, activity.getString(R.string.self_protection_install),
                    activity.getString(commitment && settings.installLock()
                            ? R.string.self_protection_commitment
                            : R.string.self_protection_install_hint));
            installSwitch.setChecked(settings.installLock());
            installSwitch.setEnabled(!(commitment && installSwitch.isChecked()));
            form.addView(installSwitch, Ui.margins(activity, 8, 0));
        }
        Switch lockdownSwitch = null;
        Switch strictSwitch = null;
        Switch suspendSwitch = null;
        if (status.deviceOwner) {
            lockdownSwitch = Ui.toggle(activity, activity.getString(R.string.managed_lockdown_switch));
            lockdownSwitch.setChecked(settings.managedLockdown());
            lockdownSwitch.setEnabled(!(settings.managedLockdown() && commitment));
            form.addView(lockdownSwitch, Ui.margins(activity, 8, 0));
            form.addView(Ui.caption(activity, activity.getString(R.string.managed_lockdown_hint)),
                    Ui.margins(activity, 4, 0));
            if (Build.VERSION.SDK_INT >= 29) {
                strictSwitch = Ui.toggle(activity, activity.getString(R.string.managed_strict_switch),
                        activity.getString(R.string.managed_strict_hint));
                strictSwitch.setChecked(settings.managedStrict());
                strictSwitch.setEnabled(!(settings.managedStrict() && commitment));
                form.addView(strictSwitch, Ui.margins(activity, 12, 0));
            }
            if (Build.VERSION.SDK_INT >= 24) {
                suspendSwitch = Ui.toggle(activity, activity.getString(R.string.managed_suspend_switch),
                        activity.getString(R.string.managed_suspend_hint));
                suspendSwitch.setChecked(settings.suspendBlocked());
                suspendSwitch.setEnabled(!(settings.suspendBlocked() && commitment));
                form.addView(suspendSwitch, Ui.margins(activity, 8, 0));
            } else {
                // Package suspension arrived with Android 7.0; do not offer a switch that cannot act.
                form.addView(Ui.caption(activity, activity.getString(R.string.managed_api_7_hint)),
                        Ui.margins(activity, 8, 0));
            }
        }
        form.addView(explanation, Ui.margins(activity, 14, 0));
        if (!status.deviceOwner && !screenGuard) {
            form.addView(Ui.caption(activity, activity.getString(R.string.uninstall_command_label)),
                    Ui.margins(activity, 10, 0));
            form.addView(Ui.commandBlock(activity,
                    UninstallProtectionController.provisioningCommand(activity)), Ui.margins(activity, 6, 0));
        }
        ScrollView scroll = new ScrollView(activity);
        scroll.addView(form, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        final Switch keep = keepSwitch;
        final Switch install = installSwitch;
        final Switch lockdown = lockdownSwitch;
        final Switch strict = strictSwitch;
        final Switch suspend = suspendSwitch;
        AlertDialog dialog = new AlertDialog.Builder(activity)
                // "Managed" describes device-owner mode; the direct build's guard is simply this.
                .setTitle(screenGuard ? R.string.self_protection_title : R.string.uninstall_title)
                .setView(scroll).create();
        form.addView(Ui.divider(activity), Ui.margins(activity, 14, 2));
        Button primary;
        if (status.deviceOwner || screenGuard) {
            boolean turningOn = !settings.uninstallLock();
            primary = Ui.button(activity, turningOn ? R.string.uninstall_turn_on : R.string.uninstall_apply,
                    Ui.ButtonKind.PRIMARY);
            primary.setOnClickListener(view -> {
                apply(activity, settings, turningOn || (keep != null && keep.isChecked()),
                        install != null ? install.isChecked() : settings.installLock(),
                        status.deviceOwner && lockdown != null
                                ? lockdown.isChecked() : settings.managedLockdown(),
                        status.deviceOwner && strict != null
                                ? strict.isChecked() : settings.managedStrict(),
                        status.deviceOwner && suspend != null
                                ? suspend.isChecked() : settings.suspendBlocked());
                dialog.dismiss();
                onChanged.run();
            });
        } else {
            primary = Ui.button(activity, R.string.uninstall_copy_command, Ui.ButtonKind.PRIMARY);
            primary.setOnClickListener(view -> {
                copy(activity, UninstallProtectionController.provisioningCommand(activity));
                dialog.dismiss();
            });
        }
        form.addView(primary, Ui.buttonParams(activity));
        if (status.deviceOwner || screenGuard) {
            if (status.mayRelease()) {
                Button release = Ui.button(activity, R.string.uninstall_release, Ui.ButtonKind.GHOST);
                release.setOnClickListener(view -> {
                    dialog.dismiss();
                    confirmRelease(activity, onChanged);
                });
                form.addView(release, Ui.buttonParams(activity));
            } else if (screenGuard && !status.adminActive) {
                // The admin adds a deactivation screen in front of uninstall, which the guard also leaves.
                form.addView(adminButton(activity, dialog), Ui.buttonParams(activity));
            }
        } else if (!status.adminActive) {
            form.addView(adminButton(activity, dialog), Ui.buttonParams(activity));
        }
        Button cancel = Ui.button(activity, android.R.string.cancel, Ui.ButtonKind.QUIET);
        cancel.setOnClickListener(view -> dialog.dismiss());
        form.addView(cancel, Ui.buttonParams(activity));
        dialog.show();
    }

    /** The one-line state shown in the shell's settings row. */
    static String summary(Context context) {
        PolicyStore settings;
        try { settings = PolicyStore.get(context); }
        catch (RuntimeException unavailable) { return context.getString(R.string.policy_unavailable); }
        // The row already carries the title; a long state keeps its first sentence only.
        String status = longStatus(context, settings, UninstallProtectionController.read(context));
        int prefix = status.indexOf(" · ");
        if (prefix >= 0) status = status.substring(prefix + 3);
        int sentence = status.indexOf(". ");
        return status.length() > 60 && sentence > 0 ? status.substring(0, sentence + 1) : status;
    }

    private static Button adminButton(Activity activity, AlertDialog dialog) {
        Button admin = Ui.button(activity, R.string.admin_setup, Ui.ButtonKind.GHOST);
        admin.setOnClickListener(view -> {
            dialog.dismiss();
            try { activity.startActivity(UninstallProtectionController.adminIntent(activity)); }
            catch (RuntimeException unavailable) {
                Toast.makeText(activity, R.string.perm_settings_unavailable, Toast.LENGTH_LONG).show();
            }
        });
        return admin;
    }

    private static String longStatus(Context context, PolicyStore settings,
            UninstallProtectionController.Status status) {
        if (BuildConfig.SELF_PROTECTION && !status.deviceOwner) {
            boolean committed = ProtectionLock.locked(status.remainingMillis);
            boolean connected = TextGuardService.connected;
            if (committed && !connected) return context.getString(R.string.self_protection_state_lock_inactive);
            if (committed) return context.getString(settings.installLock()
                            ? R.string.self_protection_state_lock_on_installs
                            : R.string.self_protection_state_lock_on,
                    Durations.remaining(context, status.remainingMillis));
            if (!status.keepInstalled) return context.getString(settings.installLock()
                    ? R.string.self_protection_state_installs_only : R.string.self_protection_state_off);
            if (connected) return context.getString(settings.installLock()
                    ? R.string.self_protection_state_on_installs : R.string.self_protection_state_on);
            return context.getString(R.string.self_protection_state_inactive);
        }
        switch (status.state()) {
            case ADMIN_ONLY: return context.getString(R.string.uninstall_state_admin);
            case READY: return context.getString(R.string.uninstall_state_ready);
            case PROTECTED:
                return status.remainingMillis > 0
                        ? context.getString(R.string.uninstall_state_protected_commitment,
                        Durations.remaining(context, status.remainingMillis))
                        : context.getString(R.string.uninstall_state_protected);
            case OUT_OF_SYNC: return context.getString(R.string.uninstall_state_sync);
            case UNAVAILABLE:
            default:
                return context.getString(status.error != null ? R.string.uninstall_state_error
                        : R.string.uninstall_state_unavailable);
        }
    }

    private static void apply(Activity activity, PolicyStore settings, boolean keepInstalled,
            boolean installLock, boolean managedLockdown, boolean managedStrict, boolean suspendBlocked) {
        try {
            // All choices are saved in one write, so the guard switches, the uninstall block
            // and the restrictions can never describe different states.
            settings.updateManagedChoice(keepInstalled, installLock, managedLockdown, managedStrict,
                    suspendBlocked);
        } catch (IllegalStateException locked) {
            Toast.makeText(activity, R.string.uninstall_locked, Toast.LENGTH_LONG).show();
            return;
        } catch (RuntimeException unavailable) {
            Toast.makeText(activity, R.string.policy_unavailable, Toast.LENGTH_LONG).show();
            return;
        }
        UninstallProtectionController.Result result = UninstallProtectionController.sync(activity);
        long remaining = 0;
        try { remaining = settings.remainingMillis(); }
        catch (RuntimeException unavailable) { /* The status refresh reports it. */ }
        boolean bootArmed = ProtectionLock.bootShieldArmed(BuildConfig.SELF_PROTECTION,
                keepInstalled, remaining);
        BootShieldService.mirror(activity, bootArmed);
        if (bootArmed && !Settings.canDrawOverlays(activity)) {
            // The boot shield needs this; without it the first moments after a restart stay open.
            Toast.makeText(activity, R.string.boot_shield_permission, Toast.LENGTH_LONG).show();
            try {
                activity.startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        android.net.Uri.parse("package:" + activity.getPackageName())));
            } catch (RuntimeException unavailable) { /* Settings lacks the page; the guard still works. */ }
        } else {
            Toast.makeText(activity, result.ok ? R.string.uninstall_saved : R.string.uninstall_failed,
                    Toast.LENGTH_LONG).show();
        }
        try { LayerWatchdog.syncSchedule(activity); }
        catch (RuntimeException unavailable) { /* The next open retries. */ }
        if (bootArmed && Build.VERSION.SDK_INT >= 33
                && activity.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            // The watchdog's only signal for a closed app is a notification.
            activity.requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 102);
        }
    }

    private static void confirmRelease(Activity activity, Runnable onChanged) {
        new AlertDialog.Builder(activity).setTitle(R.string.uninstall_release)
                .setMessage(R.string.uninstall_release_confirm)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.uninstall_release, (dialog, which) -> {
                    String error = UninstallProtectionController.releaseManagedMode(activity);
                    int message = error == null ? R.string.uninstall_released
                            : "locked".equals(error) ? R.string.uninstall_locked
                            : R.string.uninstall_failed;
                    Toast.makeText(activity, message, Toast.LENGTH_LONG).show();
                    onChanged.run();
                }).show();
    }

    private static void copy(Activity activity, String text) {
        ClipboardManager clipboard = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard == null) {
            Toast.makeText(activity, R.string.uninstall_failed, Toast.LENGTH_LONG).show();
            return;
        }
        clipboard.setPrimaryClip(ClipData.newPlainText("Hisn setup", text));
        Toast.makeText(activity, R.string.uninstall_copied, Toast.LENGTH_LONG).show();
    }
}
