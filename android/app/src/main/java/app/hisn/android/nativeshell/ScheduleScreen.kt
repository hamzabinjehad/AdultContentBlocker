package app.hisn.android.nativeshell

import android.app.TimePickerDialog
import android.content.Context
import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.os.ConfigurationCompat
import app.hisn.android.R
import app.hisn.core.DailyAppSchedule
import java.text.DateFormatSymbols
import java.util.Calendar
import java.util.Locale

private val ScheduleSaver = listSaver<DailyAppSchedule, Any>(
    save = { listOf(it.enabled, it.startMinute, it.endMinute, ArrayList(it.apps), it.allApps, it.days) },
    restore = {
        DailyAppSchedule(
            it[0] as Boolean, it[1] as Int, it[2] as Int,
            (it[3] as List<*>).filterIsInstance<String>().toSet(), it[4] as Boolean, it[5] as Int
        )
    }
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ScheduleScreen(state: HisnState, onBack: () -> Unit) {
    val context = LocalContext.current
    val resources = androidx.compose.ui.platform.LocalResources.current
    val haptics = rememberHisnHaptics()
    val snackbar = LocalHisnSnackbar.current
    val locale = ConfigurationCompat.getLocales(LocalConfiguration.current)[0] ?: Locale.ENGLISH
    val weekdays = DateFormatSymbols.getInstance(locale)
    val locked = state.lockRemainingMillis > 0
    var original by rememberSaveable(stateSaver = ScheduleSaver) { mutableStateOf(state.appSchedule) }
    var enabled by rememberSaveable { mutableStateOf(original.enabled) }
    var start by rememberSaveable { mutableIntStateOf(original.startMinute) }
    var end by rememberSaveable { mutableIntStateOf(original.endMinute) }
    var days by rememberSaveable { mutableIntStateOf(original.days) }
    var keepList by rememberSaveable { mutableStateOf(original.allApps) }
    var blocked by rememberSaveable { mutableStateOf(ArrayList(if (original.allApps) emptySet() else original.apps)) }
    var kept by rememberSaveable { mutableStateOf(ArrayList(if (original.allApps) original.apps else emptySet())) }
    var pickingStart by rememberSaveable { mutableStateOf<Boolean?>(null) }
    var error by rememberSaveable { mutableStateOf<Int?>(null) }
    val selected = if (keepList) kept else blocked
    val changed = enabled != original.enabled || start != original.startMinute || end != original.endMinute ||
        days != original.days || keepList != original.allApps || selected.toSet() != original.apps

    pickingStart?.let { selectingStart ->
        DisposableEffect(selectingStart, locked) {
            if (locked) {
                pickingStart = null
                onDispose { }
            } else {
                val current = if (selectingStart) start else end
                val dialog = TimePickerDialog(context, { _, hour, minute ->
                    if (selectingStart) start = hour * 60 + minute else end = hour * 60 + minute
                    error = null
                }, current / 60, current % 60, DateFormat.is24HourFormat(context))
                dialog.setTitle(if (selectingStart) R.string.schedule_window_start else R.string.schedule_window_end)
                dialog.setOnDismissListener { pickingStart = null }
                dialog.show()
                onDispose {
                    dialog.setOnDismissListener(null)
                    dialog.dismiss()
                }
            }
        }
    }

    DetailScaffold(title = stringResource(if (unrotEdition) R.string.shield_row_quiet else R.string.schedule_edit), onBack = onBack) { padding ->
        LazyColumn(
            Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                if (locked) {
                    Text(stringResource(R.string.rules_locked), color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Text(stringResource(R.string.schedule_editor_intro), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item {
                HisnCard {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.schedule_enable)) },
                        trailingContent = {
                            HisnSwitch(enabled, enabled = !locked)
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.toggleable(value = enabled, enabled = !locked, role = Role.Switch) {
                            enabled = it
                            error = null
                        }
                    )
                    InsetDivider()
                    SettingRow(
                        icon = Icons.Rounded.Schedule,
                        title = stringResource(R.string.schedule_window_start),
                        supporting = scheduleTime(context, start),
                        onClick = if (locked) null else { { pickingStart = true } }
                    )
                    InsetDivider()
                    SettingRow(
                        icon = Icons.Rounded.Schedule,
                        title = stringResource(R.string.schedule_window_end),
                        supporting = scheduleTime(context, end),
                        onClick = if (locked) null else { { pickingStart = false } }
                    )
                }
            }
            // Unrot: the same clock as setup, so the time range reads at a glance. The rows above
            // stay for exact minutes and for anyone who prefers a picker.
            if (unrotEdition && !locked && enabled && start != end) {
                item {
                    androidx.compose.foundation.layout.Box(
                        Modifier.fillMaxWidth(),
                        contentAlignment = androidx.compose.ui.Alignment.Center
                    ) {
                        QuietDial(start, end, onChange = { s, e -> start = s; end = e; error = null })
                    }
                }
            }
            item {
                SectionHeader(
                    stringResource(R.string.schedule_days_title),
                    supporting = if (end < start) stringResource(R.string.schedule_days_help) else null
                )
            }
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    repeat(7) { index ->
                        val bit = 1 shl index
                        val weekday = (index + 1) % 7 + 1
                        FilterChip(
                            selected = days and bit != 0,
                            enabled = !locked,
                            onClick = { days = days xor bit; error = null },
                            label = { Text(weekdays.shortWeekdays[weekday]) },
                            colors = if (unrotEdition) androidx.compose.material3.FilterChipDefaults.filterChipColors(
                                selectedContainerColor = chosenFill(),
                                selectedLabelColor = if (LocalDarkTheme.current) androidx.compose.ui.graphics.Color(0xFFF2C94F)
                                    else androidx.compose.ui.graphics.Color(0xFF8A5E0C),
                                selectedLeadingIconColor = if (LocalDarkTheme.current) androidx.compose.ui.graphics.Color(0xFFF2C94F)
                                    else androidx.compose.ui.graphics.Color(0xFF8A5E0C)
                            ) else androidx.compose.material3.FilterChipDefaults.filterChipColors(),
                            border = if (unrotEdition) androidx.compose.material3.FilterChipDefaults.filterChipBorder(
                                enabled = !locked, selected = days and bit != 0,
                                selectedBorderColor = if (LocalDarkTheme.current) androidx.compose.ui.graphics.Color(0xFFF2C94F)
                                    else androidx.compose.ui.graphics.Color(0xFFB07A12),
                                selectedBorderWidth = 1.5.dp
                            ) else androidx.compose.material3.FilterChipDefaults.filterChipBorder(enabled = !locked, selected = days and bit != 0),
                            leadingIcon = if (days and bit != 0) ({
                                Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                            }) else null,
                            modifier = Modifier.semantics {
                                contentDescription = weekdays.weekdays[weekday]
                            }
                        )
                    }
                }
            }
            item {
                HisnCard {
                    Column(Modifier.selectableGroup()) {
                        listOf(false to R.string.schedule_mode_list, true to R.string.schedule_mode_all)
                            .forEach { (keep, label) ->
                                val checked = keepList == keep
                                ListItem(
                                    headlineContent = { Text(stringResource(label)) },
                                    leadingContent = { RadioButton(checked, onClick = null, enabled = !locked) },
                                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                    modifier = Modifier
                                        .heightIn(min = 48.dp)
                                        .selectable(checked, enabled = !locked, role = Role.RadioButton) {
                                            keepList = keep
                                            error = null
                                        }
                                )
                            }
                        InsetDivider()
                        SettingRow(
                            icon = Icons.Rounded.Apps,
                            title = stringResource(if (keepList) R.string.schedule_keep_apps else R.string.schedule_choose_apps),
                            supporting = pluralStringResource(
                                if (keepList) R.plurals.kept_apps else R.plurals.selected_apps,
                                selected.size, selected.size
                            ),
                            onClick = if (locked) null else {
                                {
                                    val choosingKeepList = keepList
                                    state.pickScheduleApps(choosingKeepList, selected.toSet()) { apps ->
                                        if (choosingKeepList) kept = ArrayList(apps) else blocked = ArrayList(apps)
                                        error = null
                                    }
                                }
                            }
                        )
                    }
                }
            }
            if (keepList) item {
                Text(stringResource(R.string.schedule_all_apps_help), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            error?.let { reason ->
                item {
                    Text(
                        stringResource(reason), color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                    )
                }
            }
            item {
                Button(
                    onClick = save@{
                        error = when {
                            start == end -> R.string.schedule_invalid_time
                            days == 0 -> R.string.schedule_days_error
                            enabled && !keepList && selected.isEmpty() -> R.string.schedule_choose_apps_error
                            else -> null
                        }
                        if (error != null) {
                            haptics.reject()
                            return@save
                        }
                        val edited = DailyAppSchedule(enabled, start, end, selected.toSet(), keepList, days)
                        if (state.saveSchedule(original, edited)) {
                            original = edited
                            haptics.confirm()
                            snackbar(HisnMessage(resources.getString(R.string.schedule_saved)))
                        } else {
                            haptics.reject()
                        }
                    },
                    enabled = !locked && changed,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                ) { Text(stringResource(R.string.schedule_save)) }
            }
        }
    }
}

private fun scheduleTime(context: Context, minute: Int): String {
    val clock = Calendar.getInstance().apply {
        clear()
        set(2000, Calendar.JANUARY, 1, minute / 60, minute % 60)
    }
    return DateFormat.getTimeFormat(context).format(clock.time)
}
