package app.hisn.prototype.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberSwipeToDismissBoxState
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.hisn.prototype.AppIcon
import app.hisn.prototype.HisnState
import app.hisn.prototype.InstalledApp
import app.hisn.prototype.Usage
import app.hisn.prototype.appLabel
import app.hisn.prototype.installedApps
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext


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
    var input by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    val snackbar = LocalHisnSnackbar.current
    val haptics = rememberHisnHaptics()
    val focusManager = LocalFocusManager.current

    val removeDomain: (String) -> Unit = { domain ->
        state.removeDomain(domain)
        haptics.tick()
        snackbar(
            HisnMessage(
                text = "Removed $domain",
                actionLabel = "Undo",
                onAction = { state.addDomain(domain) }
            )
        )
    }

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
                    onValueChange = {
                        input = it
                        if (error != null) error = null
                    },
                    label = { Text("Add a domain") },
                    placeholder = { Text("example.com") },
                    singleLine = true,
                    isError = error != null,
                    supportingText = error?.let {
                        { Text(it) }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        val domain = input.trim().lowercase()
                        val problem = when {
                            domain.isBlank() -> "Enter a domain first"
                            !domain.contains(".") -> "That does not look like a domain"
                            state.domains.contains(domain) -> "$domain is already blocked"
                            else -> null
                        }
                        if (problem != null) {
                            error = problem
                            haptics.reject()
                        } else {
                            state.addDomain(domain)
                            focusManager.clearFocus()
                            input = ""
                            error = null
                            haptics.confirm()
                            snackbar(
                                HisnMessage(
                                    text = "Added $domain",
                                    actionLabel = "Undo",
                                    onAction = { state.removeDomain(domain) }
                                )
                            )
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
                    title = when (state.domains.size) {
                        0 -> "No domains yet"
                        1 -> "1 blocked domain"
                        else -> "${state.domains.size} blocked domains"
                    },
                    supporting = if (state.domains.isEmpty()) {
                        "Domains are matched on this phone. Settings and dialers are excluded."
                    } else {
                        "Swipe a row to remove it. Domains are matched on this phone; settings and dialers are excluded."
                    }
                )
            }

            if (state.domains.isEmpty()) {
                item {
                    AnimatedVisibility(
                        visible = true,
                        enter = fadeIn(tween(240)) + expandVertically(
                            animationSpec = HisnMotion.spatial(),
                            expandFrom = Alignment.Top
                        )
                    ) {
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
                }
            } else {
                items(state.domains.toList(), key = { it }) { domain ->
                    SwipeToDelete(
                        modifier = Modifier.animateItem(),
                        onDelete = { removeDomain(domain) }
                    ) {
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
                                    IconButton(onClick = { removeDomain(domain) }) {
                                        Icon(Icons.Rounded.Delete, contentDescription = "Remove $domain")
                                    }
                                },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                            )
                        }
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
    var input by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    val snackbar = LocalHisnSnackbar.current
    val haptics = rememberHisnHaptics()
    val focusManager = LocalFocusManager.current

    val removeWord: (String) -> Unit = { word ->
        state.removeWord(word)
        haptics.tick()
        snackbar(
            HisnMessage(
                text = "Removed \"$word\"",
                actionLabel = "Undo",
                onAction = { state.addWord(word) }
            )
        )
    }

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
                    onValueChange = {
                        input = it
                        if (error != null) error = null
                    },
                    label = { Text("Add a word or phrase") },
                    placeholder = { Text("literal match") },
                    singleLine = true,
                    isError = error != null,
                    supportingText = error?.let {
                        { Text(it) }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        val word = input.trim()
                        val problem = when {
                            word.isBlank() -> "Enter a word first"
                            word.length > 128 -> "Keep each rule under 128 characters"
                            state.words.contains(word) -> "Already saved"
                            else -> null
                        }
                        if (problem != null) {
                            error = problem
                            haptics.reject()
                        } else {
                            state.addWord(word)
                            focusManager.clearFocus()
                            input = ""
                            error = null
                            haptics.confirm()
                            snackbar(
                                HisnMessage(
                                    text = "Added \"$word\"",
                                    actionLabel = "Undo",
                                    onAction = { state.removeWord(word) }
                                )
                            )
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
                    title = when (state.words.size) {
                        0 -> "No word rules yet"
                        1 -> "1 word rule"
                        else -> "${state.words.size} word rules"
                    },
                    supporting = if (state.words.isEmpty()) {
                        "Literal matches, saved on this phone. The built-in adult-tag layer is separate."
                    } else {
                        "Swipe a row to remove it. Literal matches, saved on this phone."
                    }
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
                items(state.words.toList(), key = { it }) { word ->
                    SwipeToDelete(
                        modifier = Modifier.animateItem(),
                        onDelete = { removeWord(word) }
                    ) {
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
                                    IconButton(onClick = { removeWord(word) }) {
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
}

/**
 * Swipe-to-remove with a real background, not a hidden gesture. The item leaves the lazy list, the
 * snackbar offers the undo, and [Modifier.animateItem] closes the gap with the M3 placement spring.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeToDelete(
    modifier: Modifier = Modifier,
    onDelete: () -> Unit,
    content: @Composable () -> Unit
) {
    val shape = MaterialTheme.shapes.large
    val dismissState = rememberSwipeToDismissBoxState()
    SwipeToDismissBox(
        state = dismissState,
        modifier = modifier.clip(shape),
        backgroundContent = {
            val fromStart = dismissState.dismissDirection == SwipeToDismissBoxValue.StartToEnd
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(horizontal = 22.dp),
                contentAlignment = if (fromStart) Alignment.CenterStart else Alignment.CenterEnd
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Remove",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        },
        enableDismissFromStartToEnd = true,
        enableDismissFromEndToStart = true,
        // The row leaves only after the swipe settles; animateItem then closes the gap and the
        // snackbar can offer the undo for a few seconds.
        onDismiss = { onDelete() }
    ) {
        content()
    }
}

/** Placeholder rows while PackageManager is read; no spinner, the list keeps its final shape. */
@Composable
private fun AppListSkeleton() {
    HisnCard {
        repeat(6) { index ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SkeletonCircle(40.dp)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    SkeletonBlock(
                        Modifier
                            .fillMaxWidth(if (index % 2 == 0) 0.52f else 0.66f)
                            .height(14.dp)
                    )
                    Spacer(Modifier.height(7.dp))
                    SkeletonBlock(
                        Modifier
                            .fillMaxWidth(if (index % 2 == 0) 0.78f else 0.58f)
                            .height(10.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                SkeletonBlock(
                    Modifier
                        .width(52.dp)
                        .height(32.dp),
                    corner = 16.dp
                )
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
                    trailingIcon = if (query.isNotEmpty()) {
                        {
                            IconButton(onClick = { query = "" }) {
                                Icon(Icons.Rounded.Close, contentDescription = "Clear search")
                            }
                        }
                    } else {
                        null
                    },
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
                item { AppListSkeleton() }
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
    val haptics = rememberHisnHaptics()
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
            onValueChange = {
                haptics.toggle(it)
                onBlockedChange(it)
            }
        )
    )
}

@Composable
fun ScheduleScreen(
    state: HisnState,
    onBack: () -> Unit
) {
    val labels = listOf("M", "T", "W", "T", "F", "S", "S")
    val haptics = rememberHisnHaptics()
    val snackbar = LocalHisnSnackbar.current

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
                                haptics.tick()
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
                        onClick = { snackbar(HisnMessage("Time picker comes with the port")) }
                    )
                    InsetDivider()
                    SettingRow(
                        icon = Icons.Rounded.Schedule,
                        title = "End time",
                        supporting = "06:00",
                        onClick = { snackbar(HisnMessage("Time picker comes with the port")) }
                    )
                }
            }
            item {
                Button(
                    onClick = {
                        haptics.confirm()
                        val days = state.scheduleDays.size
                        snackbar(
                            HisnMessage(
                                "Schedule saved for $days ${if (days == 1) "day" else "days"}"
                            )
                        )
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
    onAddApps: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var editing by remember { mutableStateOf<String?>(null) }
    var usageAccess by remember { mutableStateOf(Usage.hasAccess(context)) }
    val limited = state.appLimits.keys.toList()
    val haptics = rememberHisnHaptics()
    val snackbar = LocalHisnSnackbar.current

    // Coming back from Android's Usage access screen must refresh the grant, not keep a stale "no".
    LaunchedEffect(Unit) { usageAccess = Usage.hasAccess(context) }

    editing?.let { packageName ->
        MinutesDialog(
            appLabel = appLabel(context, packageName),
            initial = state.appLimit(packageName) ?: HisnState.DEFAULT_MINUTES,
            onDismiss = { editing = null },
            onConfirm = { minutes ->
                val previous = state.appLimit(packageName)
                state.setAppLimit(packageName, minutes)
                editing = null
                haptics.confirm()
                snackbar(
                    HisnMessage(
                        text = "${appLabel(context, packageName)} limited to $minutes min",
                        actionLabel = "Undo",
                        onAction = {
                            if (previous == null) {
                                state.removeAppLimit(packageName)
                            } else {
                                state.setAppLimit(packageName, previous)
                            }
                        }
                    )
                )
            }
        )
    }

    DetailScaffold("Daily limit", onBack) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                PageIntro(
                    "Give each app its own daily limit. Hisn reads Android's own screen-time record for " +
                        "today — nothing leaves the phone. Closing an app at its limit is the production " +
                        "accessibility layer's job; this screen saves the limit."
                )
            }
            item {
                HisnCard {
                    SwitchRow(
                        icon = Icons.Rounded.Timer,
                        title = "Use a daily limit",
                        supporting = if (state.limit.on) {
                            if (limited.isEmpty()) "On · No apps selected yet" else "On · ${limited.size} apps limited"
                        } else {
                            "Off · Saved apps are not limited"
                        },
                        checked = state.limit.on,
                        onCheckedChange = state.limit::set
                    )
                }
            }

            item {
                SectionHeader(
                    title = if (limited.isEmpty()) "No apps limited yet" else "${limited.size} apps limited",
                    supporting = "Each limit counts from local midnight and is set per app."
                )
            }
            item {
                HisnCard {
                    if (limited.isEmpty()) {
                        ListItem(
                            headlineContent = { Text("Add an app to start", style = MaterialTheme.typography.titleMedium) },
                            supportingContent = {
                                Text(
                                    "The limit only applies to the apps you add here.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            leadingContent = { Icon(Icons.Rounded.Timer, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                        )
                    } else {
                        limited.forEachIndexed { index, packageName ->
                            val minutes = state.appLimit(packageName) ?: HisnState.DEFAULT_MINUTES
                            LimitRow(
                                packageName = packageName,
                                minutes = minutes,
                                usedMs = if (usageAccess) {
                                    Usage.usedTodayMs(context, packageName)
                                } else {
                                    null
                                },
                                onClick = {
                                    haptics.tick()
                                    editing = packageName
                                },
                                onRemove = {
                                    val previous = state.appLimit(packageName) ?: HisnState.DEFAULT_MINUTES
                                    state.removeAppLimit(packageName)
                                    haptics.tick()
                                    snackbar(
                                        HisnMessage(
                                            text = "Limit removed",
                                            actionLabel = "Undo",
                                            onAction = { state.setAppLimit(packageName, previous) }
                                        )
                                    )
                                }
                            )
                            if (index < limited.lastIndex) InsetDivider()
                        }
                    }
                }
            }

            item {
                Button(
                    onClick = {
                        haptics.tick()
                        onAddApps()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Add apps")
                }
            }

            if (!usageAccess) {
                item {
                    SectionHeader(
                        title = "Usage access",
                        supporting = "Android needs one special-access grant to report today's screen time."
                    )
                }
                item {
                    HisnCard {
                        SettingRow(
                            icon = Icons.Rounded.Schedule,
                            title = "Grant usage access",
                            supporting = "Needed to show how long each app ran today",
                            onClick = { Usage.openSettings(context) }
                        )
                    }
                }
            }

            item {
                ReassuranceRow(
                    icon = Icons.Rounded.Lock,
                    text = "Limits stay on this phone. Hisn keeps no list of what you opened."
                )
            }
        }
    }
}

@Composable
private fun LimitRow(
    packageName: String,
    minutes: Int,
    usedMs: Long?,
    onClick: () -> Unit,
    onRemove: () -> Unit
) {
    val context = LocalContext.current
    val label = appLabel(context, packageName)
    val limitMs = minutes * 60_000L
    val used = usedMs ?: 0L
    ListItem(
        headlineContent = {
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        supportingContent = {
            Column {
                Text(
                    text = if (usedMs == null) {
                        "$minutes min a day"
                    } else {
                        "$minutes min a day · ${Usage.formatDuration(usedMs)} used today"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (usedMs != null) {
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { (used.toFloat() / limitMs.toFloat()).coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp),
                        color = if (used >= limitMs) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                        trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                    )
                }
            }
        },
        leadingContent = { AppIcon(packageName, 40.dp) },
        trailingContent = {
            IconButton(onClick = onRemove) {
                Icon(
                    Icons.Rounded.Delete,
                    contentDescription = "Remove the limit for $label"
                )
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(role = Role.Button, onClick = onClick)
    )
}

/** Pick installed apps to limit; each new app starts at the default and is edited on the main screen. */
@Composable
fun LimitAppsScreen(
    state: HisnState,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var query by rememberSaveable { mutableStateOf("") }
    var apps by remember { mutableStateOf<List<InstalledApp>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    val haptics = rememberHisnHaptics()
    val snackbar = LocalHisnSnackbar.current

    LaunchedEffect(Unit) {
        apps = withContext(Dispatchers.IO) { installedApps(context) }
        loading = false
    }

    val limited = state.appLimits.keys.toList()
    val visible = remember(apps, query, limited) {
        val needle = query.trim().lowercase()
        apps
            .sortedWith(
                compareByDescending<InstalledApp> { limited.contains(it.packageName) }
                    .thenBy { it.label.lowercase() }
            )
            .filter {
                needle.isEmpty() ||
                    it.label.lowercase().contains(needle) ||
                    it.packageName.lowercase().contains(needle)
            }
    }

    DetailScaffold("Add apps", onBack) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                PageIntro(
                    "Tap a row to add the app to the daily limit. A new app starts at " +
                        "${HisnState.DEFAULT_MINUTES} minutes; open Daily limit to change it."
                )
            }
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Search installed apps") },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    trailingIcon = if (query.isNotEmpty()) {
                        {
                            IconButton(onClick = { query = "" }) {
                                Icon(Icons.Rounded.Close, contentDescription = "Clear search")
                            }
                        }
                    } else {
                        null
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                SectionHeader(
                    title = "${limited.size} ${if (limited.size == 1) "app" else "apps"} limited",
                    supporting = when {
                        loading -> "Reading this phone's apps…"
                        apps.isEmpty() -> "No launchable apps were returned by this phone."
                        else -> "${apps.size} launchable apps on this phone."
                    }
                )
            }

            if (loading) {
                item { AppListSkeleton() }
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
                            val minutes = state.appLimit(app.packageName)
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
                                        text = if (minutes == null) {
                                            "${app.packageName} · No limit"
                                        } else {
                                            "${app.packageName} · $minutes min a day"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                leadingContent = { AppIcon(app.packageName, 40.dp) },
                                trailingContent = { Checkbox(checked = minutes != null, onCheckedChange = null) },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                modifier = Modifier.toggleable(
                                    value = minutes != null,
                                    role = Role.Checkbox,
                                    onValueChange = { include ->
                                        haptics.toggle(include)
                                        if (include) {
                                            state.setAppLimit(app.packageName, HisnState.DEFAULT_MINUTES)
                                            if (!state.limit.on) state.limit.set(true)
                                            snackbar(
                                                HisnMessage(
                                                    text = "${app.label} · ${HisnState.DEFAULT_MINUTES} min a day",
                                                    actionLabel = "Undo",
                                                    onAction = { state.removeAppLimit(app.packageName) }
                                                )
                                            )
                                        } else {
                                            val previous = state.appLimit(app.packageName)
                                            state.removeAppLimit(app.packageName)
                                            snackbar(
                                                HisnMessage(
                                                    text = "${app.label} removed from the limit",
                                                    actionLabel = "Undo",
                                                    onAction = {
                                                        if (previous != null) {
                                                            state.setAppLimit(app.packageName, previous)
                                                        }
                                                    }
                                                )
                                            )
                                        }
                                    }
                                )
                            )
                            if (index < visible.lastIndex) InsetDivider()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MinutesDialog(
    appLabel: String,
    initial: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    var minutes by rememberSaveable { mutableIntStateOf(initial) }
    val haptics = rememberHisnHaptics()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Daily limit for $appLabel") },
        text = {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedIconButton(
                        onClick = {
                            val next = (minutes - 5).coerceAtLeast(HisnState.MIN_MINUTES)
                            if (next == minutes) haptics.reject() else {
                                haptics.tick()
                                minutes = next
                            }
                        }
                    ) {
                        Icon(Icons.Rounded.Remove, contentDescription = "Five minutes less")
                    }
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AnimatedCount(
                            value = minutes,
                            style = MaterialTheme.typography.headlineSmall
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "min",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    OutlinedIconButton(
                        onClick = {
                            val next = (minutes + 5).coerceAtMost(HisnState.MAX_MINUTES)
                            if (next == minutes) haptics.reject() else {
                                haptics.tick()
                                minutes = next
                            }
                        }
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = "Five minutes more")
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(15, 30, 60, 120).forEach { preset ->
                        FilterChip(
                            selected = minutes == preset,
                            onClick = {
                                haptics.tick()
                                minutes = preset
                            },
                            label = { Text("$preset") }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(minutes) }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun LockScreen(onBack: () -> Unit) {
    var hours by rememberSaveable { mutableIntStateOf(7) }
    val haptics = rememberHisnHaptics()
    val snackbar = LocalHisnSnackbar.current

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
                            onClick = {
                                haptics.tick()
                                hours = option
                            },
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
                    onClick = {
                        haptics.confirm()
                        snackbar(HisnMessage("Protection lock started for $hours hours (prototype)"))
                    },
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
    val snackbar = LocalHisnSnackbar.current

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
                        onClick = { snackbar(HisnMessage("Android Settings opens here in the port")) }
                    )
                    InsetDivider()
                    SettingRow(
                        icon = Icons.Rounded.Notifications,
                        title = "Notifications",
                        supporting = "Off · layer alerts and watchdog",
                        trailing = { ValueTrailing("Off") },
                        onClick = { snackbar(HisnMessage("Android Settings opens here in the port")) }
                    )
                    InsetDivider()
                    SettingRow(
                        icon = Icons.Rounded.Lock,
                        title = "VPN",
                        supporting = "On · DNS filtering",
                        trailing = { ValueTrailing("On") },
                        onClick = { snackbar(HisnMessage("Android VPN settings open here in the port")) }
                    )
                }
            }
            item {
                Button(
                    onClick = { snackbar(HisnMessage("This opens Android Settings in the port")) },
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
    val snackbar = LocalHisnSnackbar.current
    val haptics = rememberHisnHaptics()

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
                    onClick = {
                        haptics.confirm()
                        snackbar(HisnMessage("Route check passed · prototype"))
                    },
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
    var empty by remember { mutableStateOf(false) }
    val haptics = rememberHisnHaptics()
    val terms = listOf("porn", "xxx", "adult", "nude", "nsfw")
    val status = LocalHisnStatusColors.current

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
                    placeholder = { Text("type to check") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                Button(
                    onClick = {
                        val lower = input.trim().lowercase()
                        val hit = terms.firstOrNull { lower.contains(it) }
                        empty = lower.isBlank()
                        blocked = !empty && hit != null
                        result = when {
                            empty -> "Type something first — there is nothing to check yet."
                            hit != null -> "Would block — matched \"$hit\"."
                            else -> "Would allow — no reviewed term matched."
                        }
                        when {
                            empty -> haptics.reject()
                            blocked -> haptics.tick()
                            else -> haptics.confirm()
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Rounded.Search, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Test")
                }
            }
            item {
                AnimatedVisibility(
                    visible = result != null,
                    enter = fadeIn(tween(220)) + expandVertically(
                        animationSpec = HisnMotion.spatial(),
                        expandFrom = Alignment.Top
                    ),
                    exit = fadeOut(tween(100)) + shrinkVertically(
                        animationSpec = HisnMotion.spatial(),
                        shrinkTowards = Alignment.Top
                    )
                ) {
                    val message = result ?: ""
                    HisnCard(
                        containerColor = when {
                            empty -> MaterialTheme.colorScheme.surfaceContainerHigh
                            blocked -> MaterialTheme.colorScheme.errorContainer
                            else -> status.successContainer
                        }
                    ) {
                        ListItem(
                            headlineContent = {
                                Text(
                                    text = when {
                                        empty -> "Nothing to test"
                                        blocked -> "Blocked"
                                        else -> "Allowed"
                                    },
                                    style = MaterialTheme.typography.titleMedium
                                )
                            },
                            supportingContent = { Text(message, style = MaterialTheme.typography.bodyMedium) },
                            leadingContent = {
                                IconBadge(
                                    icon = when {
                                        empty -> Icons.Rounded.Search
                                        blocked -> Icons.Rounded.Warning
                                        else -> Icons.Rounded.CheckCircle
                                    },
                                    container = when {
                                        empty -> MaterialTheme.colorScheme.surfaceContainerLowest
                                        blocked -> MaterialTheme.colorScheme.errorContainer
                                        else -> status.successContainer
                                    },
                                    content = when {
                                        empty -> MaterialTheme.colorScheme.onSurfaceVariant
                                        blocked -> MaterialTheme.colorScheme.error
                                        else -> status.success
                                    }
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
    onBack: () -> Unit,
    onNavigate: (String) -> Unit
) {
    when {
        detail == "domains" -> DomainsScreen(state, onBack)
        detail == "words" -> WordsScreen(state, onBack)
        detail == "apps" -> AppsScreen(state, onBack)
        detail == "schedule" -> ScheduleScreen(state, onBack)
        detail == "limit" -> LimitScreen(
            state = state,
            onAddApps = { onNavigate("limit:apps") },
            onBack = onBack
        )
        detail == "limit:apps" -> LimitAppsScreen(state, onBack = { onNavigate("limit") })
        detail == "lock" -> LockScreen(onBack)
        detail == "permissions" -> PermissionsScreen(onBack)
        detail == "diagnostics" -> DiagnosticsScreen(onBack)
        detail == "wordtester" -> WordTesterScreen(onBack)
        detail.startsWith("info:") -> InfoScreen(detail.removePrefix("info:"), onBack)
        else -> InfoScreen("about", onBack)
    }
}
