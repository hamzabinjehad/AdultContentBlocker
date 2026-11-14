package app.hisn.android.nativeshell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.hisn.android.R
import app.hisn.core.DeviceFamily
import app.hisn.core.OemGuide

/**
 * The maker-specific setup screen: what this phone does to background apps, and the one place
 * to repair it.
 *
 * <p>The battery and background rows are live Android readings. The autostart and Recents rows
 * are the opposite: the maker does not expose them, so the owner ticks them after changing the
 * OEM screen, and the tick is stored here as advice, not as protection. The screen says so in
 * [R.string.phone_limits] and never reports a layer as running because a row is ticked.</p>
 */
@Composable
fun PhoneSetupScreen(state: HisnState, onBack: () -> Unit) {
    val phone = state.phone
    val items = OemGuide.checklist(
        phone.family,
        phone.batteryUnrestricted,
        phone.backgroundRestricted,
        phone.autostartConfirmed,
        phone.recentsConfirmed,
        phone.popupsAllowed,
        phone.popupsConfirmed
    )
    val open = items.count { it.status != OemGuide.Status.DONE }

    DetailScaffold(stringResource(R.string.phone_title), onBack) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { PhoneHeader(phone) }

            item {
                SectionHeader(
                    title = stringResource(R.string.phone_checks),
                    supporting = if (open == 0) {
                        stringResource(R.string.phone_ready)
                    } else {
                        stringResource(R.string.phone_state_check, open)
                    }
                )
            }
            item {
                HisnCard {
                    items.forEachIndexed { index, item ->
                        PhoneStepRow(item, state)
                        if (index < items.lastIndex) InsetDivider()
                    }
                }
            }
            if (open == 0) {
                item {
                    Text(
                        text = stringResource(R.string.phone_ready_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }
            }

            item { SectionHeader(stringResource(R.string.phone_notes)) }
            item {
                HisnCard {
                    PhoneNoteRows(phone, state)
                }
            }

            item {
                ReassuranceRow(
                    icon = Icons.Rounded.Lock,
                    text = stringResource(R.string.phone_limits)
                )
            }
        }
    }
}

@Composable
private fun PhoneHeader(phone: PhoneState) {
    val status = LocalHisnStatusColors.current
    HisnCard(containerColor = status.cardMuted) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = phone.name.ifEmpty { "Android" },
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                text = phone.android,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = familyLabel(phone.family),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = familyBody(phone.family),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PhoneStepRow(item: OemGuide.Item, state: HisnState) {
    val status = LocalHisnStatusColors.current
    val phone = state.phone
    val key = when (item.step) {
        OemGuide.Step.BATTERY_UNRESTRICTED -> "battery"
        OemGuide.Step.BACKGROUND_ACTIVITY -> "appinfo"
        OemGuide.Step.AUTOSTART -> "autostart"
        OemGuide.Step.BACKGROUND_POPUPS -> "popups"
        OemGuide.Step.KEEP_IN_RECENTS -> "recents"
    }
    val title = stringResource(
        when (item.step) {
            OemGuide.Step.BATTERY_UNRESTRICTED -> R.string.phone_battery_title
            OemGuide.Step.BACKGROUND_ACTIVITY -> R.string.phone_background_title
            OemGuide.Step.AUTOSTART -> R.string.phone_autostart_title
            OemGuide.Step.BACKGROUND_POPUPS -> R.string.phone_popups_title
            OemGuide.Step.KEEP_IN_RECENTS -> R.string.phone_recents_title
        }
    )
    val supporting = stringResource(
        when (item.step) {
            OemGuide.Step.BATTERY_UNRESTRICTED ->
                if (phone.batteryUnrestricted) R.string.phone_battery_on else R.string.phone_battery_off
            OemGuide.Step.BACKGROUND_ACTIVITY ->
                if (phone.backgroundRestricted == true) R.string.phone_background_off
                else R.string.phone_background_on
            OemGuide.Step.AUTOSTART -> R.string.phone_autostart_body
            OemGuide.Step.BACKGROUND_POPUPS -> when (phone.popupsAllowed) {
                true -> R.string.phone_popups_on
                false -> R.string.phone_popups_off
                null -> R.string.phone_popups_body
            }
            OemGuide.Step.KEEP_IN_RECENTS -> R.string.phone_recents_body
        }
    )
    val icon = when (item.step) {
        OemGuide.Step.BATTERY_UNRESTRICTED -> Icons.Rounded.BatteryChargingFull
        OemGuide.Step.BACKGROUND_ACTIVITY -> Icons.Rounded.Security
        OemGuide.Step.AUTOSTART -> Icons.Rounded.PlayCircle
        OemGuide.Step.BACKGROUND_POPUPS -> Icons.Rounded.Layers
        OemGuide.Step.KEEP_IN_RECENTS -> Icons.Rounded.Lock
    }
    // Autostart is the one confirm row with a hidden OEM screen behind it; Recents has no screen
    // to open, so its row is only the explanation and the tick.
    val opensSettings = item.step != OemGuide.Step.KEEP_IN_RECENTS
    // Pop-ups are a live row on Xiaomi builds that answer, and a tick where the maker does not.
    val confirmable = item.step == OemGuide.Step.AUTOSTART
        || item.step == OemGuide.Step.KEEP_IN_RECENTS
        || (item.step == OemGuide.Step.BACKGROUND_POPUPS && phone.popupsAllowed == null)
    val done = item.status == OemGuide.Status.DONE
    val confirmLabel = if (confirmable) {
        stringResource(R.string.phone_confirm_label, title)
    } else {
        ""
    }

    // A confirm row keeps its touch actions side by side: "Open" and the tick. Recents has no
    // screen to open, so it shows only the tick. Two targets are easier for TalkBack than a tick
    // nested inside a clickable row.
    SettingRow(
        icon = icon,
        title = title,
        supporting = supporting,
        onClick = if (opensSettings && !confirmable) {
            { state.openPhoneSetting(key) }
        } else {
            null
        },
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (confirmable && opensSettings) {
                    TextButton(
                        onClick = { state.openPhoneSetting(key) },
                        modifier = Modifier.heightIn(min = 48.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(stringResource(R.string.phone_open))
                    }
                }
                if (confirmable) {
                    Checkbox(
                        checked = done,
                        onCheckedChange = { state.confirmPhoneStep(key, it) },
                        modifier = Modifier.semantics { contentDescription = confirmLabel }
                    )
                } else {
                    Text(
                        text = stringResource(if (done) R.string.perm_on else R.string.perm_off),
                        style = MaterialTheme.typography.labelLarge,
                        color = if (done) status.success else MaterialTheme.colorScheme.error
                    )
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    )
}

@Composable
private fun PhoneNoteRows(phone: PhoneState, state: HisnState) {
    val notes = OemGuide.notes(phone.family)
    // The DNS tunnel is the layer Android usually keeps alive across a restart; this is the one
    // Android setting that helps on every maker, so it is always shown.
    SettingRow(
        icon = Icons.Rounded.Wifi,
        title = stringResource(R.string.phone_vpn_title),
        supporting = stringResource(R.string.phone_vpn_body),
        onClick = { state.openPhoneSetting("vpn") }
    )
    if (notes.contains(OemGuide.Note.SECOND_SPACE)) {
        InsetDivider()
        SettingRow(
            icon = Icons.Rounded.Lock,
            title = stringResource(R.string.phone_second_space_title),
            supporting = stringResource(R.string.phone_second_space),
            trailing = null
        )
    }
    if (notes.contains(OemGuide.Note.INSTALL_UPDATE)) {
        InsetDivider()
        SettingRow(
            icon = Icons.Rounded.PlayCircle,
            title = stringResource(R.string.phone_install_title),
            supporting = stringResource(
                when (phone.family) {
                    DeviceFamily.SAMSUNG -> R.string.phone_install_samsung
                    DeviceFamily.XIAOMI -> R.string.phone_install_xiaomi
                    else -> R.string.phone_install_other
                }
            ),
            trailing = null
        )
    }
}

/** The family in the app's language; the saved key stays the untranslated device family. */
@Composable
private fun familyLabel(family: DeviceFamily): String = stringResource(
    when (family) {
        DeviceFamily.SAMSUNG -> R.string.phone_family_samsung
        DeviceFamily.XIAOMI -> R.string.phone_family_xiaomi
        DeviceFamily.NOTHING -> R.string.phone_family_nothing
        DeviceFamily.OPPO -> R.string.phone_family_oppo
        DeviceFamily.VIVO -> R.string.phone_family_vivo
        DeviceFamily.HUAWEI -> R.string.phone_family_huawei
        DeviceFamily.TRANSSION -> R.string.phone_family_transsion
        DeviceFamily.STOCK -> R.string.phone_family_stock
        DeviceFamily.OTHER -> R.string.phone_family_other
    }
)

/** One sentence on what this family does to background apps. */
@Composable
private fun familyBody(family: DeviceFamily): String = stringResource(
    when (family) {
        DeviceFamily.SAMSUNG -> R.string.phone_samsung_body
        DeviceFamily.XIAOMI -> R.string.phone_xiaomi_body
        DeviceFamily.NOTHING -> R.string.phone_nothing_body
        DeviceFamily.OPPO -> R.string.phone_oppo_body
        DeviceFamily.VIVO -> R.string.phone_vivo_body
        DeviceFamily.HUAWEI -> R.string.phone_huawei_body
        DeviceFamily.TRANSSION -> R.string.phone_transsion_body
        DeviceFamily.STOCK -> R.string.phone_stock_body
        DeviceFamily.OTHER -> R.string.phone_other_body
    }
)
