package app.hisn.prototype.ui

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.Accessibility
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.hisn.prototype.AppIcon
import app.hisn.prototype.HisnState
import app.hisn.prototype.InstalledApp
import app.hisn.prototype.installedApps
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private fun toast(context: Context, message: String) {
    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DetailScaffold(
    title: String,
    onBack: () -> Unit,
    content: @Composable (PaddingValues) -> Unit
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        content(innerPadding)
    }
}

@Composable
fun DomainsScreen(
    state: HisnState,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var input by rememberSaveable { mutableStateOf("") }

    DetailScaffold("Blocked domains", onBack) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    label = { Text("Add a domain") },
                    placeholder = { Text("example.com") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        val domain = input.trim().lowercase()
                        when {
                            domain.isBlank() -> toast(context, "Enter a domain first")
                            !domain.contains(".") -> toast(context, "That does not look like a domain")
                            state.domains.contains(domain) -> toast(context, "$domain is already blocked")
                            else -> {
                                state.addDomain(domain)
                                input = ""
                                toast(context, "Added $domain")
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Add domain")
                }
            }

            item {
                SectionHeader(
                    title = if (state.domains.isEmpty()) {
                        "No domains yet"
                    } else {
                        "${state.domains.size} blocked domains"
                    },
                    supporting = "Domains are matched on this phone. Settings and dialers are excluded."
                )
            }

            if (state.domains.isEmpty()) {
                item {
                    HisnCard {
                        ListItem(
                            headlineContent = { Text("Nothing blocked yet", style = MaterialTheme.typography.titleMedium) },
                            supportingContent = {
                                Text(
                                    "Add a domain above. This list is the one place domains are added.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            leadingContent = { Icon(Icons.Rounded.Language, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                        )
                    }
                }
            } else {
                items(state.domains.toList()) { domain ->
                    HisnCard {
                        ListItem(
                            headlineContent = { Text(domain, style = MaterialTheme.typography.titleMedium) },
                            supportingContent = {
                                Text(
                                    "Blocked on this phone",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            leadingContent = { Icon(Icons.Rounded.Language, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                            trailingContent = {
                                IconButton(onClick = {
                                    state.removeDomain(domain)
                                    toast(context, "Removed $domain")
                                }) {
                                    Icon(Icons.Rounded.Delete, contentDescription = "Remove $domain")
                                }
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                        )
                    }
                }
            }

            item {
                ReassuranceRow(
                    icon = Icons.Rounded.Lock,
                    text = "Saved on this phone. Nothing is uploaded."
                )
            }
        }
    }
}

@Composable
fun WordsScreen(
    state: HisnState,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var input by rememberSaveable { mutableStateOf("") }

    DetailScaffold("Blocked words", onBack) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    label = { Text("Add a word or phrase") },
                    placeholder = { Text("literal match") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        val word = input.trim()
                        when {
                            word.isBlank() -> toast(context, "Enter a word first")
                            word.length > 128 -> toast(context, "Keep each rule under 128 characters")
                            state.words.contains(word) -> toast(context, "Already saved")
                            else -> {
                                state.addWord(word)
                                input = ""
                                toast(context, "Added \"$word\"")
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Add word rule")
                }
            }

            item {
                SectionHeader(
                    title = if (state.words.isEmpty()) "No word rules yet" else "${state.words.size} word rules",
                    supporting = "Literal matches, saved on this phone. The built-in adult-tag layer is a separate layer."
                )
            }

            if (state.words.isEmpty()) {
                item {
                    HisnCard {
                        ListItem(
                            headlineContent = { Text("No word rules", style = MaterialTheme.typography.titleMedium) },
                            supportingContent = {
                                Text(
                                    "Try the word tester from Settings to see how a rule behaves.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            leadingContent = { Icon(Icons.Rounded.TextFields, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                        )
                    }
                }
            } else {
                items(state.words.toList()) { word ->
                    HisnCard {
                        ListItem(
                            headlineContent = { Text(word, style = MaterialTheme.typography.titleMedium) },
                            supportingContent = {
                                Text(
                                    "Literal match",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            leadingContent = { Icon(Icons.Rounded.TextFields, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                            trailingContent = {
                                IconButton(onClick = {
                                    state.removeWord(word)
                                    toast(context, "Removed \"$word\"")
                                }) {
                                    Icon(Icons.Rounded.Delete, contentDescription = "Remove $word")
                                }
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Apps to block: the real apps installed on this phone, read from PackageManager.
 *
 * The old screen shipped a fixed list of seven names, so the picker could never match the device it
 * ran on. Rows now carry the app's own icon and package name, and the choice is saved by package.
 */
@Composable
fun AppsScreen(
    state: HisnState,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var query by rememberSaveable { mutableStateOf("") }
    var apps by remember { mutableStateOf<List<InstalledApp>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        apps = withContext(Dispatchers.IO) { installedApps(context) }
        loading = false
    }

    val blocked = state.blockedApps.toList()
    val visible = remember(apps, query, blocked) {
        val needle = query.trim().lowercase()
        apps
            .sortedWith(
                compareByDescending<InstalledApp> { blocked.contains(it.packageName) }
                    .thenBy { it.label.lowercase() }
            )
            .filter {
                needle.isEmpty() ||
                    it.label.lowercase().contains(needle) ||
                    it.packageName.lowercase().contains(needle)
            }
    }

    DetailScaffold("Apps to block", onBack) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                PageIntro(
                    "Pick from the apps installed on this phone. A selected app is blocked entirely — " +
                        "not just filtered. The choice is saved on this phone."
                )
            }
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Search installed apps") },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                SectionHeader(
                    title = if (blocked.isEmpty()) {
                        "No apps blocked"
                    } else {
                        "${blocked.size} ${if (blocked.size == 1) "app" else "apps"} blocked"
                    },
                    supporting = when {
                        loading -> "Reading this phone's apps…"
                        apps.isEmpty() -> "No launchable apps were returned by this phone."
                        else -> "${apps.size} launchable apps on this phone. Toggle a row to block it."
                    }
                )
            }

            if (loading) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
            } else if (visible.isEmpty()) {
                item {
                    HisnCard {
                        ListItem(
                            headlineContent = { Text("No app matches", style = MaterialTheme.typography.titleMedium) },
                            supportingContent = {
                                Text(
                                    if (query.isBlank()) {
                                        "This phone returned no launchable apps."
                                    } else {
                                        "Nothing installed matches \"$query\"."
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            leadingContent = { Icon(Icons.Rounded.Apps, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                        )
                    }
                }
            } else {
                item {
                    HisnCard {
                        visible.forEachIndexed { index, app ->
                            InstalledAppRow(
                                app = app,
                                blocked = blocked.contains(app.packageName),
                                onBlockedChange = { state.setAppBlocked(app.packageName, it) }
                            )
                            if (index < visible.lastIndex) InsetDivider()
                        }
                    }
                }
            }

            item {
                ReassuranceRow(
                    icon = Icons.Rounded.Lock,
                    text = "${blocked.size} apps selected. The list stays on this phone."
                )
            }
        }
    }
}

@Composable
private fun InstalledAppRow(
    app: InstalledApp,
    blocked: Boolean,
    onBlockedChange: (Boolean) -> Unit
) {
    ListItem(
        headlineContent = {
            Text(
                text = app.label,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        supportingContent = {
            Text(
                text = if (blocked) "${app.packageName} · Blocked entirely" else "${app.packageName} · Allowed",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        leadingContent = { AppIcon(app.packageName, 40.dp) },
        trailingContent = { Switch(checked = blocked, onCheckedChange = null) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.toggleable(
            value = blocked,
            role = Role.Switch,
            onValueChange = onBlockedChange
        )
    )
}

@Composable
fun ScheduleScreen(
    state: HisnState,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val labels = listOf("M", "T", "W", "T", "F", "S", "S")

    DetailScaffold("Schedule", onBack) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { PageIntro("Close selected apps during a daily window. Overnight windows belong to the day they start on.") }
            item {
                HisnCard {
                    SwitchRow(
                        icon = Icons.Rounded.Schedule,
                        title = "Use a schedule",
                        supporting = "Applies on the days you choose",
                        checked = state.schedule.on,
                        onCheckedChange = state.schedule::set
                    )
                }
            }
            item { SectionHeader("Days") }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(labels.size) { index ->
                        FilterChip(
                            selected = state.scheduleDays.contains(index),
                            onClick = {
                                val days = state.scheduleDays
                                state.updateScheduleDays(
                                    if (days.contains(index)) days - index else days + index
                                )
                            },
                            label = { Text(labels[index]) }
                        )
                    }
                }
            }
            item {
                HisnCard {
                    SettingRow(
                        icon = Icons.Rounded.Schedule,
                        title = "Start time",
                        supporting = "22:00",
                        onClick = { toast(context, "Time picker comes with the port") }
                    )
                    InsetDivider()
                    SettingRow(
                        icon = Icons.Rounded.Schedule,
                        title = "End time",
                        supporting = "06:00",
                        onClick = { toast(context, "Time picker comes with the port") }
                    )
                }
            }
            item {
                Button(
                    onClick = {
                        val days = state.scheduleDays.size
                        toast(context, "Schedule saved for $days days")
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Rounded.CheckCircle, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Save schedule")
                }
            }
        }
    }
}

@Composable
fun LimitScreen(
    state: HisnState,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val presets = listOf(15, 30, 60, 120)

    DetailScaffold("Daily limit", onBack) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { PageIntro("Limit how long the selected apps can be used each day. The counter resets at midnight.") }
            item {
                HisnCard {
                    SwitchRow(
                        icon = Icons.Rounded.Timer,
                        title = "Use a daily limit",
                        supporting = "Only while accessibility is connected",
                        checked = state.limit.on,
                        onCheckedChange = state.limit::set
                    )
                }
            }
            item { SectionHeader("Minutes per day") }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(presets.size) { index ->
                        val preset = presets[index]
                        FilterChip(
                            selected = state.limitMinutes == preset,
                            onClick = { state.setLimit(state.limit.on, preset) },
                            label = { Text("$preset min") }
                        )
                    }
                }
            }
            item {
                Button(
                    onClick = {
                        toast(context, "Daily limit saved: ${state.limitMinutes} minutes")
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Save limit")
                }
            }
        }
    }
}

@Composable
fun LockScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var hours by rememberSaveable { mutableIntStateOf(7) }

    DetailScaffold("Protection lock", onBack) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                PageIntro("Make saved rules read-only for a set time. Waiting is the only release inside Hisn; a guardian-signed policy may add restrictions.")
            }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(listOf(1, 7, 48)) { option ->
                        FilterChip(
                            selected = hours == option,
                            onClick = { hours = option },
                            label = { Text(if (option == 1) "1 hour" else "$option hours") }
                        )
                    }
                }
            }
            item {
                HisnCard {
                    ListItem(
                        headlineContent = { Text("What the lock changes", style = MaterialTheme.typography.titleMedium) },
                        supportingContent = {
                            Text(
                                "Rules become read-only inside Hisn. Android settings, data wipe and uninstall are separate unless you use the direct build or Device Owner mode.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        leadingContent = { Icon(Icons.Rounded.Lock, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                }
            }
            item {
                Button(
                    onClick = { toast(context, "Protection lock started for $hours hours (prototype)") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text("Start a $hours-hour lock")
                }
            }
        }
    }
}

@Composable
fun PermissionsScreen(onBack: () -> Unit) {
    val context = LocalContext.current

    DetailScaffold("Permissions", onBack) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { PageIntro("Hisn needs these Android permissions for the layers you choose. It does not request an account or a contact list.") }
            item {
                HisnCard {
                    SettingRow(
                        icon = Icons.Rounded.Accessibility,
                        title = "Accessibility",
                        supporting = "On · powers text and image checks",
                        trailing = { ValueTrailing("On") },
                        onClick = { toast(context, "Android Settings opens here in the port") }
                    )
                    InsetDivider()
                    SettingRow(
                        icon = Icons.Rounded.Notifications,
                        title = "Notifications",
                        supporting = "Off · layer alerts and watchdog",
                        trailing = { ValueTrailing("Off") },
                        onClick = { toast(context, "Android Settings opens here in the port") }
                    )
                    InsetDivider()
                    SettingRow(
                        icon = Icons.Rounded.Lock,
                        title = "VPN",
                        supporting = "On · DNS filtering",
                        trailing = { ValueTrailing("On") },
                        onClick = { toast(context, "Android VPN settings open here in the port") }
                    )
                }
            }
            item {
                Button(
                    onClick = { toast(context, "This opens Android Settings in the port") },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Open Android settings")
                }
            }
        }
    }
}

@Composable
fun DiagnosticsScreen(onBack: () -> Unit) {
    val context = LocalContext.current

    DetailScaffold("Session diagnostics", onBack) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { PageIntro("Counters for this session. Nothing is uploaded and no browsing history is kept.") }
            item {
                HisnCard {
                    MetricRow("DNS queries", "1,284")
                    InsetDivider(start = 16.dp)
                    MetricRow("Blocked", "37")
                    InsetDivider(start = 16.dp)
                    MetricRow("Passed", "1,247")
                    InsetDivider(start = 16.dp)
                    MetricRow("Errors", "0")
                }
            }
            item {
                HisnCard {
                    MetricRow("Last route check", "2 minutes ago")
                    InsetDivider(start = 16.dp)
                    MetricRow("Protection lock", "Not active")
                }
            }
            item {
                Button(
                    onClick = { toast(context, "Route check passed · prototype") },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Rounded.BarChart, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Run a network check")
                }
            }
        }
    }
}

@Composable
private fun MetricRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
fun WordTesterScreen(onBack: () -> Unit) {
    var input by rememberSaveable { mutableStateOf("") }
    var result by remember { mutableStateOf<String?>(null) }
    var blocked by remember { mutableStateOf(false) }
    val terms = listOf("porn", "xxx", "adult", "nude", "nsfw")

    DetailScaffold("Try word rules", onBack) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { PageIntro("Type a word or phrase to see how the built-in adult-tag layer would treat it. Nothing is saved or sent.") }
            item {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    label = { Text("Word or phrase") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                Button(
                    onClick = {
                        val lower = input.trim().lowercase()
                        val hit = terms.firstOrNull { lower.contains(it) }
                        blocked = hit != null
                        result = when {
                            lower.isBlank() -> "Type something first."
                            hit != null -> "Would block — matched \"$hit\"."
                            else -> "Would allow — no reviewed term matched."
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Test")
                }
            }
            item {
                result?.let { message ->
                    HisnCard(
                        containerColor = if (blocked) {
                            MaterialTheme.colorScheme.errorContainer
                        } else {
                            LocalHisnStatusColors.current.successContainer
                        }
                    ) {
                        ListItem(
                            headlineContent = { Text(if (blocked) "Blocked" else "Allowed", style = MaterialTheme.typography.titleMedium) },
                            supportingContent = { Text(message, style = MaterialTheme.typography.bodyMedium) },
                            leadingContent = {
                                Icon(
                                    imageVector = if (blocked) Icons.Rounded.Warning else Icons.Rounded.CheckCircle,
                                    contentDescription = null,
                                    tint = if (blocked) MaterialTheme.colorScheme.error else LocalHisnStatusColors.current.success
                                )
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun InfoScreen(key: String, onBack: () -> Unit) {
    val (title, body) = infoFor(key)
    DetailScaffold(title, onBack) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Text(
                text = body,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(24.dp))
            ReassuranceRow(
                icon = Icons.Rounded.Lock,
                text = "Hisn keeps matching on this phone."
            )
        }
    }
}

private fun infoFor(key: String): Pair<String, String> = when (key) {
    "uninstall" -> "Uninstall protection" to
        "On the direct build, Hisn can close the uninstall and disable screens while a protection lock is active. The Play build omits that guard. Android settings and a data wipe remain outside Hisn's control unless the phone is managed separately."
    "keep" -> "Keep protection running" to
        "Android may pause background services. The watchdog checks every 15 minutes and warns when a layer stops, but it cannot restart the layer by itself. Open Hisn after a reboot or a permission change."
    "guardian" -> "Guardian pairing" to
        "Pairing with a guardian laptop lets a signed policy add restrictions. It cannot remove the lock early, and Hisn does not need an account for it."
    "how" -> "How Hisn works" to
        "Hisn combines a DNS filter, whole-app restrictions and on-device text, tag and image checks. Matching happens on the phone. There is no account and no browsing log. The platform lock (Screen Time, Device Owner, MDM) is separate from Hisn's own rules."
    "bypass" -> "What can bypass Hisn" to
        "DNS filtering covers supported domain lookups, not direct IP connections, cached answers, or apps that run their own encrypted DNS. Text, tag and image checks only cover the apps you select and may act after content appears. Android can stop services or revoke permissions. The protection lock freezes rules inside Hisn, not Android settings or uninstall."
    "privacy" -> "Privacy" to
        "Matching happens on-device. Prompts, frames and page text are not uploaded. Hisn keeps no browsing history. The list updater receives the signed list only. A DNS tunnel still sends allowed lookups to the configured resolver, as the DNS screen explains."
    "language" -> "Language" to
        "The prototype carries Arabic copy for the Protection screen only, so the RTL pass can be reviewed without changing the device locale. The production app already has values-ar strings for every screen; the port will use them."
    "gap" -> "Background check" to
        "The watchdog found a layer that stopped. Open the layer from Protection, re-arm it, then run a protection check to confirm the route."
    "about" -> "About Hisn" to
        "Hisn 1.6.0 · local-first adult-content protection for Android. Protection layers, checks and the domain, word and installed-app lists really save on this phone; the DNS tunnel, accessibility guard and image model still live in the production app, so this build does not filter traffic. No account, no cloud, no browsing log."
    else -> "About Hisn" to
        "Hisn keeps matching on this phone. This is a UI prototype: settings and lists persist, enforcement does not."
}

@Composable
fun DetailHost(
    detail: String,
    state: HisnState,
    onBack: () -> Unit
) {
    when {
        detail == "domains" -> DomainsScreen(state, onBack)
        detail == "words" -> WordsScreen(state, onBack)
        detail == "apps" -> AppsScreen(state, onBack)
        detail == "schedule" -> ScheduleScreen(state, onBack)
        detail == "limit" -> LimitScreen(state, onBack)
        detail == "lock" -> LockScreen(onBack)
        detail == "permissions" -> PermissionsScreen(onBack)
        detail == "diagnostics" -> DiagnosticsScreen(onBack)
        detail == "wordtester" -> WordTesterScreen(onBack)
        detail.startsWith("info:") -> InfoScreen(detail.removePrefix("info:"), onBack)
        else -> InfoScreen("about", onBack)
    }
}
