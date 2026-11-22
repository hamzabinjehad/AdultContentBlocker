package app.hisn.android.nativeshell

import app.hisn.android.Edition
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
import androidx.compose.foundation.layout.size
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
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.Security
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
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.semantics.contentDescription
import app.hisn.android.DnsVpnService
import app.hisn.android.R
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext


@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DetailScaffold(
    title: String,
    onBack: () -> Unit,
    actions: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {},
    content: @Composable (PaddingValues) -> Unit
) {
    Scaffold(
        modifier = if (unrotEdition) Modifier.unrotBackdrop() else Modifier,
        containerColor = if (unrotEdition) Color.Transparent else MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
        topBar = {
            TopAppBar(
                // Unrot titles every page in its serif, like the tabs; Hisn keeps Material's.
                title = { Text(title, style = if (unrotEdition) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = tr("Back")
                        )
                    }
                },
                actions = actions,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = if (unrotEdition) Color.Transparent else MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        content(innerPadding)
    }
}

/** Sites people often add to stop scrolling; none is on the adult list. */
private val UnrotSiteSuggestions = listOf("reddit.com", "x.com", "9gag.com", "twitch.tv", "kick.com", "tiktok.com")

@Composable
fun DomainsScreen(
    state: HisnState,
    onBack: () -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    var input by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    val snackbar = LocalHisnSnackbar.current
    val haptics = rememberHisnHaptics()
    val focusManager = LocalFocusManager.current

    val removeDomain: (String) -> Unit = remove@{ domain ->
        if (!state.removeDomain(domain)) return@remove
        haptics.tick()
        snackbar(
            HisnMessage(
                text = String.format(tr("Removed %1\$s"), domain),
                actionLabel = tr("Undo"),
                onAction = { state.addDomain(domain) }
            )
        )
    }

    DetailScaffold(if (unrotEdition) stringResource(R.string.unrot_title_sites) else tr("Blocked domains"), onBack) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                val submit: () -> Unit = submit@{
                    val domain = runCatching { app.hisn.core.SiteCheckInput.ruleHost(input) }.getOrNull()
                    val problem = when {
                        input.isBlank() -> tr("Enter a domain first")
                        domain == null -> tr("That does not look like a domain")
                        state.domains.contains(domain) -> String.format(tr("%1\$s is already blocked"), domain)
                        else -> null
                    }
                    if (problem != null) {
                        error = problem
                        haptics.reject()
                    } else if (domain != null) {
                        if (!state.addDomain(domain)) return@submit
                        focusManager.clearFocus()
                        input = ""
                        error = null
                        haptics.confirm()
                        snackbar(
                            HisnMessage(
                                text = String.format(tr("Added %1\$s"), domain),
                                actionLabel = tr("Undo"),
                                onAction = { state.removeDomain(domain) }
                            )
                        )
                    }
                }
                OutlinedTextField(
                    value = input,
                    onValueChange = {
                        input = it
                        if (error != null) error = null
                    },
                    label = if (unrotEdition) null else ({ Text(tr("Add a domain")) }),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Uri,
                        autoCorrectEnabled = false,
                        imeAction = androidx.compose.ui.text.input.ImeAction.Done
                    ),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { submit() }),
                    placeholder = { Text(if (unrotEdition) tr("Add a domain") else "example.com") },
                    singleLine = true,
                    isError = error != null,
                    supportingText = error?.let {
                        { Text(it) }
                    },
                    // Unrot: one pill with the add button inside it, like a search field.
                    shape = if (unrotEdition) RoundedCornerShape(28.dp) else OutlinedTextFieldDefaults.shape,
                    colors = if (unrotEdition) OutlinedTextFieldDefaults.colors(
                        unfocusedContainerColor = LocalHisnStatusColors.current.card,
                        focusedContainerColor = LocalHisnStatusColors.current.card,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                    ) else OutlinedTextFieldDefaults.colors(),
                    trailingIcon = if (unrotEdition) {
                        {
                            Button(onClick = submit, enabled = input.isNotBlank(),
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                modifier = Modifier.padding(end = 6.dp).height(44.dp)
                                    .semantics { contentDescription = tr("Add domain") }) {
                                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(stringResource(R.string.add_short))
                            }
                        }
                    } else null,
                    modifier = Modifier.fillMaxWidth()
                )
                if (!unrotEdition) {
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = submit,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(tr("Add domain"))
                    }
                }
            }

            // Unrot: the time-sink sites people add most, one tap each, only those not yet listed.
            if (unrotEdition) {
                val suggestions = UnrotSiteSuggestions.filter { it !in state.domains }
                if (suggestions.isNotEmpty()) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(stringResource(R.string.domains_suggested), style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            androidx.compose.foundation.layout.FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                suggestions.forEach { site ->
                                    androidx.compose.material3.AssistChip(
                                        onClick = {
                                            if (state.addDomain(site)) {
                                                haptics.confirm()
                                                snackbar(HisnMessage(
                                                    text = String.format(tr("Added %1\$s"), site),
                                                    actionLabel = tr("Undo"),
                                                    onAction = { state.removeDomain(site) }
                                                ))
                                            }
                                        },
                                        label = { Text(site) },
                                        leadingIcon = { Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp)) }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (state.domains.size > 8) {
                item {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                        label = { Text(stringResource(R.string.domains_search, state.domains.size)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Unrot's empty card already says "nothing yet"; one message is enough.
            if (!(unrotEdition && state.domains.isEmpty())) item {
                SectionHeader(
                    title = when (state.domains.size) {
                        0 -> tr("No domains yet")
                        1 -> tr("1 blocked domain")
                        else -> String.format(tr("%1\$d blocked domains"), state.domains.size)
                    },
                    supporting = stringResource(R.string.domain_rule_scope)
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
                        Column {
                        if (unrotEdition) Box(Modifier.fillMaxWidth().padding(vertical = 6.dp), contentAlignment = Alignment.Center) { EmptyArt(Icons.Rounded.Security, TileBlush) }
                        HisnCard {
                            ListItem(
                                headlineContent = { Text(tr("Nothing blocked yet"), style = MaterialTheme.typography.titleMedium) },
                                supportingContent = {
                                    Text(
                                        tr("Add a domain above. This list is the one place domains are added."),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                leadingContent = if (unrotEdition) null else ({ Icon(Icons.Rounded.Language, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }),
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                            )
                        }
                        }
                    }
                }
            } else {
                items(state.domains.toList().filter { query.isBlank() || it.contains(query.trim(), ignoreCase = true) }, key = { it }) { domain ->
                    SwipeToDelete(
                        modifier = Modifier.animateItem(),
                        onDelete = { removeDomain(domain) }
                    ) {
                        HisnCard {
                            ListItem(
                                headlineContent = { Text(domain, style = MaterialTheme.typography.titleMedium) },
                                supportingContent = {
                                    Text(
                                        tr("Blocked on this phone"),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                leadingContent = { Icon(Icons.Rounded.Language, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                                trailingContent = {
                                    IconButton(onClick = { removeDomain(domain) }) {
                                        Icon(Icons.Rounded.Delete, contentDescription = String.format(tr("Remove %1\$s"), domain))
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
                    text = tr("Saved on this phone. Nothing is uploaded.")
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

    val removeWord: (String) -> Unit = remove@{ word ->
        if (!state.removeWord(word)) return@remove
        haptics.tick()
        snackbar(
            HisnMessage(
                text = String.format(tr("Removed “%1\$s”"), word),
                actionLabel = tr("Undo"),
                onAction = { state.addWord(word) }
            )
        )
    }

    DetailScaffold(if (unrotEdition) stringResource(R.string.unrot_title_words) else tr("Blocked words"), onBack) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                val submit: () -> Unit = submit@{
                    val raw = input.trim()
                    val word = runCatching { app.hisn.core.WordMatcher.canonicalRule(raw) }.getOrNull()
                    val problem = when {
                        raw.isBlank() -> tr("Enter a word first")
                        raw.codePointCount(0, raw.length) > 128 -> tr("Keep each rule under 128 characters")
                        word == null -> tr("Enter a word first")
                        state.words.contains(word) -> tr("Already saved")
                        else -> null
                    }
                    if (problem != null) {
                        error = problem
                        haptics.reject()
                    } else if (word != null) {
                        if (!state.addWord(word)) return@submit
                        focusManager.clearFocus()
                        input = ""
                        error = null
                        haptics.confirm()
                        snackbar(
                            HisnMessage(
                                text = String.format(tr("Added “%1\$s”"), word),
                                actionLabel = tr("Undo"),
                                onAction = { state.removeWord(word) }
                            )
                        )
                    }
                }
                OutlinedTextField(
                    value = input,
                    onValueChange = {
                        input = it
                        if (error != null) error = null
                    },
                    label = if (unrotEdition) null else ({ Text(tr("Add a word or phrase")) }),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        imeAction = androidx.compose.ui.text.input.ImeAction.Done
                    ),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { submit() }),
                    placeholder = { Text(if (unrotEdition) tr("Add a word or phrase") else tr("literal match")) },
                    singleLine = true,
                    isError = error != null,
                    supportingText = error?.let {
                        { Text(it) }
                    },
                    // Unrot: one pill with the add button inside it, like a search field.
                    shape = if (unrotEdition) RoundedCornerShape(28.dp) else OutlinedTextFieldDefaults.shape,
                    colors = if (unrotEdition) OutlinedTextFieldDefaults.colors(
                        unfocusedContainerColor = LocalHisnStatusColors.current.card,
                        focusedContainerColor = LocalHisnStatusColors.current.card,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                    ) else OutlinedTextFieldDefaults.colors(),
                    trailingIcon = if (unrotEdition) {
                        {
                            Button(onClick = submit, enabled = input.isNotBlank(),
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                modifier = Modifier.padding(end = 6.dp).height(44.dp)
                                    .semantics { contentDescription = tr("Add word rule") }) {
                                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(stringResource(R.string.add_short))
                            }
                        }
                    } else null,
                    modifier = Modifier.fillMaxWidth()
                )
                if (!unrotEdition) {
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = submit,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(tr("Add word rule"))
                    }
                }
            }

            // Unrot's empty card already says "nothing yet"; one message is enough.
            if (!(unrotEdition && state.words.isEmpty())) item {
                SectionHeader(
                    title = when (state.words.size) {
                        0 -> tr("No word rules yet")
                        1 -> tr("1 word rule")
                        else -> String.format(tr("%1\$d word rules"), state.words.size)
                    },
                    supporting = if (state.words.isEmpty()) {
                        tr("Literal matches, saved on this phone. The built-in adult-tag layer is separate.")
                    } else {
                        tr("Swipe a row to remove it. Literal matches, saved on this phone.")
                    }
                )
            }

            if (state.words.isEmpty()) {
                item { if (unrotEdition) Box(Modifier.fillMaxWidth().padding(vertical = 6.dp), contentAlignment = Alignment.Center) { EmptyArt(Icons.Rounded.TextFields, TileBlush) } }
                item {
                    HisnCard {
                        ListItem(
                            headlineContent = { Text(tr("No word rules"), style = MaterialTheme.typography.titleMedium) },
                            supportingContent = {
                                Text(
                                    if (unrotEdition) stringResource(R.string.unrot_words_try) else tr("Try the word tester from Settings to see how a rule behaves."),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            leadingContent = if (unrotEdition) null else ({ Icon(Icons.Rounded.TextFields, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }),
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
                                        tr("Literal match"),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                leadingContent = { Icon(Icons.Rounded.TextFields, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                                trailingContent = {
                                    IconButton(onClick = { removeWord(word) }) {
                                        Icon(Icons.Rounded.Delete, contentDescription = String.format(tr("Remove %1\$s"), word))
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
internal fun AppListSkeleton() {
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

    var showAll by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(showAll) {
        loading = true
        apps = withContext(Dispatchers.IO) { installedApps(context, showAll) }
        loading = false
    }

    // Today's screen time, read once from Android's own record; empty without usage access.
    var usage by remember { mutableStateOf<Map<String, Long>>(emptyMap()) }
    val usageAccess = state.permissions["usage"] == true
    LaunchedEffect(usageAccess) {
        usage = if (usageAccess) withContext(Dispatchers.IO) { Usage.usedTodayAll(context) } else emptyMap()
    }

    val blocked = state.blockedApps.toList()
    // The apps used most today, so the likely choices are one tap away instead of a search.
    // A row stays put after it is switched on; it only leaves when the screen is reopened.
    val suggested = remember(apps, usage) {
        apps.filter { (usage[it.packageName] ?: 0L) >= 60_000L && !blocked.contains(it.packageName) }
            .sortedByDescending { usage[it.packageName] ?: 0L }
            .take(5)
    }
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

    DetailScaffold(tr("Apps to block"), onBack) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                PageIntro(
                    tr(
                        "Pick from the apps installed on this phone. A selected app is blocked entirely — " +
                            "not just filtered. The choice is saved on this phone."
                    )
                )
            }
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text(tr("Search installed apps")) },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    trailingIcon = if (query.isNotEmpty()) {
                        {
                            IconButton(onClick = { query = "" }) {
                                Icon(Icons.Rounded.Close, contentDescription = tr("Clear search"))
                            }
                        }
                    } else {
                        null
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item { ShowAllAppsRow(showAll) { showAll = it } }
            item {
                SectionHeader(
                    title = if (blocked.isEmpty()) {
                        tr("No apps blocked")
                    } else {
                        if (blocked.size == 1) tr("1 app blocked") else String.format(tr("%1\$d apps blocked"), blocked.size)
                    },
                    supporting = when {
                        loading -> tr("Reading this phone's apps…")
                        apps.isEmpty() -> tr("No launchable apps were returned by this phone.")
                        showAll -> String.format(tr("%1\$d apps on this phone. Toggle a row to block it."), apps.size)
                        else -> String.format(tr("%1\$d launchable apps on this phone. Toggle a row to block it."), apps.size)
                    }
                )
            }

            if (!loading && query.isBlank() && suggested.isNotEmpty()) {
                item {
                    SectionHeader(
                        title = tr("Most used today"),
                        supporting = tr("From Android's screen-time record on this phone.")
                    )
                }
                item {
                    HisnCard {
                        suggested.forEachIndexed { index, app ->
                            InstalledAppRow(
                                app = app,
                                blocked = blocked.contains(app.packageName),
                                onBlockedChange = { state.setAppBlocked(app.packageName, it) },
                                detail = String.format(
                                    tr("%1\$s today"),
                                    Usage.formatDuration(usage[app.packageName] ?: 0L)
                                )
                            )
                            if (index < suggested.lastIndex) InsetDivider()
                        }
                    }
                }
                item { SectionHeader(tr("All apps")) }
            } else if (!loading && query.isBlank() && !usageAccess && apps.isNotEmpty()) {
                item {
                    HisnCard {
                        SettingRow(
                            icon = Icons.Rounded.BarChart,
                            title = tr("Show most-used apps first"),
                            supporting = tr("Needs Android's usage access. The record stays on this phone."),
                            onClick = { state.openPermission("usage") }
                        )
                    }
                }
            }

            if (loading) {
                item { AppListSkeleton() }
            } else if (visible.isEmpty()) {
                item {
                    HisnCard {
                        ListItem(
                            headlineContent = { Text(tr("No app matches"), style = MaterialTheme.typography.titleMedium) },
                            supportingContent = {
                                Text(
                                    if (query.isBlank()) {
                                        tr("This phone returned no launchable apps.")
                                    } else {
                                        String.format(tr("Nothing installed matches “%1\$s”."), query)
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
                    text = String.format(tr("%1\$d apps selected. The list stays on this phone."), blocked.size)
                )
            }
        }
    }
}

@Composable
private fun InstalledAppRow(
    app: InstalledApp,
    blocked: Boolean,
    onBlockedChange: (Boolean) -> Unit,
    detail: String = app.packageName
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
                text = String.format(tr(if (blocked) "%1\$s · Blocked entirely" else "%1\$s · Allowed"), detail),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        leadingContent = { AppIcon(app.packageName, 40.dp) },
        trailingContent = { HisnSwitch(blocked) },
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
            onConfirm = confirm@{ minutes ->
                val previous = state.appLimit(packageName)
                if (previous == minutes) {
                    editing = null
                    return@confirm
                }
                if (!state.setAppLimit(packageName, minutes)) return@confirm
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

    DetailScaffold(if (unrotEdition) stringResource(R.string.shield_row_limits) else "Daily limit", onBack) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                PageIntro(stringResource(R.string.limit_intro))
            }
            // Unrot's first visit: limits drawn from today's real screen time, one tap to add.
            if (unrotEdition && limited.isEmpty() && usageAccess) {
                item { UnrotLimitSuggestions(state) }
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
            if (unrotEdition && limited.isEmpty() && !usageAccess) {
                item { if (unrotEdition) Box(Modifier.fillMaxWidth().padding(vertical = 6.dp), contentAlignment = Alignment.Center) { EmptyArt(Icons.Rounded.Timer, TileSage) } }
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
                            leadingContent = { RowLead(Icons.Rounded.Timer) },
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
                                // + gives more minutes, which loosens the rule, so a protection lock
                                // disables it; − only tightens and always works.
                                canLower = minutes > LimitStep,
                                canRaise = state.lockRemainingMillis <= 0L,
                                onStep = { delta ->
                                    val next = (minutes + delta).coerceIn(LimitStep, 24 * 60)
                                    if (state.setAppLimit(packageName, next)) haptics.tick()
                                },
                                onRemove = remove@{
                                    val previous = state.appLimit(packageName) ?: HisnState.DEFAULT_MINUTES
                                    if (!state.removeAppLimit(packageName)) return@remove
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
                    text = "Limits stay on this phone. ${Edition.NAME} keeps no list of what you opened."
                )
            }
        }
    }
}

/** Minutes one − or + moves a daily limit. */
private const val LimitStep = 5

@Composable
private fun LimitRow(
    packageName: String,
    minutes: Int,
    usedMs: Long?,
    onClick: () -> Unit,
    canLower: Boolean = false,
    canRaise: Boolean = true,
    onStep: ((Int) -> Unit)? = null,
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onStep != null) {
                    IconButton(onClick = { onStep(-LimitStep) }, enabled = canLower) {
                        Icon(Icons.Rounded.Remove, contentDescription = stringResource(R.string.limit_less, label))
                    }
                    IconButton(onClick = { onStep(LimitStep) }, enabled = canRaise) {
                        Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.limit_more, label))
                    }
                }
                IconButton(onClick = onRemove) {
                    Icon(
                        Icons.Rounded.Delete,
                        contentDescription = "Remove the limit for $label"
                    )
                }
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

    var showAll by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(showAll) {
        loading = true
        apps = withContext(Dispatchers.IO) { installedApps(context, showAll) }
        loading = false
    }

    val limited = state.appLimits.keys.toList()
    val visible = remember(apps, query, limited) {
        val needle = query.trim().lowercase()
        apps
            .sortedWith(
                compareByDescending<InstalledApp> { limited.contains(it.packageName) }
                    .thenByDescending { isUsualPick(it.packageName) }
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
            item { ShowAllAppsRow(showAll) { showAll = it } }
            item {
                SectionHeader(
                    title = "${limited.size} ${if (limited.size == 1) "app" else "apps"} limited",
                    supporting = when {
                        loading -> "Reading this phone's apps…"
                        apps.isEmpty() -> "No launchable apps were returned by this phone."
                        else -> "${apps.size} ${if (showAll) "apps" else "launchable apps"} on this phone."
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
                                    onValueChange = change@{ include ->
                                        if (include) {
                                            if (!state.setAppLimit(app.packageName, HisnState.DEFAULT_MINUTES)) return@change
                                            haptics.toggle(true)
                                            snackbar(
                                                HisnMessage(
                                                    text = "${app.label} · ${HisnState.DEFAULT_MINUTES} min a day",
                                                    actionLabel = "Undo",
                                                    onAction = { state.removeAppLimit(app.packageName) }
                                                )
                                            )
                                        } else {
                                            val previous = state.appLimit(app.packageName)
                                            if (!state.removeAppLimit(app.packageName)) return@change
                                            haptics.toggle(false)
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
                ConnectedChoice(
                    options = listOf(15, 30, 60, 120),
                    selected = minutes,
                    label = { "$it" },
                    onSelect = { minutes = it }
                )
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
fun LockScreen(state: HisnState, onBack: () -> Unit) {
    var days by rememberSaveable { mutableIntStateOf(7) }
    val haptics = rememberHisnHaptics()
    val context = LocalContext.current
    val active = state.lockRemainingMillis > 0L

    DetailScaffold(stringResource(R.string.commitment_setup), onBack) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (active) {
                item {
                    HisnCard(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh) {
                        ListItem(
                            headlineContent = {
                                Text(
                                    stringResource(R.string.commitment_remaining, state.lockLabel),
                                    style = MaterialTheme.typography.titleMedium
                                )
                            },
                            supportingContent = {
                                Text(
                                    stringResource(R.string.commitment_locked_note),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            leadingContent = { Icon(Icons.Rounded.Lock, null, tint = MaterialTheme.colorScheme.primary) },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                        )
                    }
                }
            }
            item { PageIntro(stringResource(R.string.commitment_choose_period)) }
            item {
                ConnectedChoice(
                    options = listOf(1, 7, 30, 90),
                    selected = days,
                    label = { context.resources.getQuantityString(R.plurals.days, it, it) },
                    onSelect = { days = it }
                )
            }
            if (!state.hasRestrictions) {
                item {
                    AttentionCard(stringResource(R.string.commitment_needs_rules))
                }
            }
            item {
                Button(
                    onClick = {
                        haptics.confirm()
                        state.startLock(days)
                    },
                    enabled = state.hasRestrictions,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text(
                        stringResource(R.string.commitment_start) + " · " +
                            context.resources.getQuantityString(R.plurals.days, days, days)
                    )
                }
            }
        }
    }
}

internal data class PermissionItem(val key: String, val icon: ImageVector, val title: Int, val body: Int)

internal val PermissionItems = listOf(
    PermissionItem("accessibility", Icons.Rounded.Accessibility, R.string.perm_access_title, R.string.perm_access_body),
    PermissionItem("dns", Icons.Rounded.Language, R.string.perm_dns_title, R.string.perm_dns_body),
    PermissionItem("usage", Icons.Rounded.Timer, R.string.perm_usage_title, R.string.perm_usage_body),
    PermissionItem("overlay", Icons.Rounded.Apps, R.string.perm_overlay_title, R.string.perm_overlay_body),
    PermissionItem("notifications", Icons.Rounded.Notifications, R.string.perm_notify_title, R.string.perm_notify_body),
    PermissionItem("battery", Icons.Rounded.Bolt, R.string.perm_battery_title, R.string.perm_battery_body)
)

@Composable
fun PermissionsScreen(state: HisnState, onBack: () -> Unit, onOpen: (String) -> Unit = {}) {
    if (unrotEdition) { UnrotPermissionsScreen(state, onBack, onOpen); return }
    val status = LocalHisnStatusColors.current
    DetailScaffold(stringResource(R.string.settings_permissions), onBack) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                HisnCard {
                    SettingRow(
                        icon = Icons.Rounded.BatteryChargingFull,
                        title = stringResource(R.string.phone_title),
                        supporting = stringResource(
                            R.string.phone_checks_body,
                            state.phone.maker.ifEmpty { "Android" }
                        ),
                        onClick = { onOpen("phone") }
                    )
                }
            }
            item {
                HisnCard {
                    PermissionItems.forEachIndexed { index, item ->
                        val granted = state.permissions[item.key] == true
                        SettingRow(
                            icon = item.icon,
                            title = stringResource(item.title),
                            supporting = stringResource(item.body),
                            trailing = {
                                Text(
                                    text = stringResource(if (granted) R.string.perm_on else R.string.perm_off),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (granted) status.success else MaterialTheme.colorScheme.error
                                )
                            },
                            onClick = { state.openPermission(item.key) }
                        )
                        if (index < PermissionItems.lastIndex) InsetDivider()
                    }
                }
            }
        }
    }
}

@Composable
fun DiagnosticsScreen(state: HisnState, onBack: () -> Unit) {
    val context = LocalContext.current
    val resources = androidx.compose.ui.platform.LocalResources.current
    val haptics = rememberHisnHaptics()
    val snackbar = LocalHisnSnackbar.current
    // Re-read the service counters whenever the shell's refresh tick lands.
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(1500)
            tick++
        }
    }
    val summary = remember(tick, state.dnsState) {
        resources.getString(
            R.string.dns_summary,
            resources.getString(
                R.string.dns_stats, DnsVpnService.domains, DnsVpnService.blocked.get(),
                DnsVpnService.forwarded.get(), DnsVpnService.rewritten.get(), DnsVpnService.errors.get()
            ),
            resources.getString(
                R.string.dns_packet_diagnostics, DnsVpnService.unsupportedPackets.get(),
                DnsVpnService.unsupportedTcpPackets.get(), DnsVpnService.malformedPackets.get()
            ),
            resources.getString(
                R.string.dns_benign_packets, DnsVpnService.ignoredPackets.get(),
                DnsVpnService.refusedTlsConnections.get(), DnsVpnService.blockedResolvers.get()
            ),
            resources.getString(R.string.dns_recoveries, DnsVpnService.recoveries.get()),
            resources.getString(
                if (DnsVpnService.networkMonitoring) R.string.dns_monitor_active else R.string.dns_monitor_inactive,
                DnsVpnService.networkChanges.get()
            ),
            resources.getString(
                R.string.dns_cancelled_network_requests, DnsVpnService.cancelledNetworkRequests.get()
            ),
            resources.getString(
                R.string.dns_tcp_stats, DnsVpnService.tcpQueries.get(),
                DnsVpnService.tcpReplies.get(), DnsVpnService.udpTruncated.get()
            )
        )
    }
    val checkText = stringResource(
        when (state.check) {
            DnsVpnService.CheckState.CHECKING -> R.string.network_check_running
            DnsVpnService.CheckState.PASSED -> R.string.network_check_passed
            DnsVpnService.CheckState.FAILED -> R.string.network_check_failed
            DnsVpnService.CheckState.STALE -> R.string.network_check_stale
            else -> R.string.network_check_idle
        }
    )
    val canCheck = (state.dnsState == DnsVpnService.State.RUNNING ||
        state.dnsState == DnsVpnService.State.DEGRADED) &&
        state.check != DnsVpnService.CheckState.CHECKING

    DetailScaffold(stringResource(R.string.section_diagnostics), onBack) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { PageIntro(stringResource(R.string.diagnostics_hint)) }
            item {
                HisnCard {
                    SelectionContainer {
                        Text(
                            text = summary,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }
            item {
                TextButton(
                    onClick = {
                        val clipboard = context.getSystemService(android.content.ClipboardManager::class.java)
                        clipboard?.setPrimaryClip(android.content.ClipData.newPlainText("${Edition.NAME}", summary))
                        haptics.confirm()
                        snackbar(HisnMessage(resources.getString(R.string.diagnostics_copied)))
                    }
                ) {
                    Text(stringResource(R.string.copy_diagnostics))
                }
            }
            item {
                HisnCard {
                    Text(
                        text = checkText,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
            item {
                Button(
                    onClick = {
                        haptics.tick()
                        state.confirmNetworkCheck()
                    },
                    enabled = canCheck,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Rounded.BarChart, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.network_check_button))
                }
            }
        }
    }
}

@Composable
fun WordTesterScreen(state: HisnState, onBack: () -> Unit) {
    // Starts from the saved word rules, so the test answers "would my rules catch this?".
    var rules by rememberSaveable { mutableStateOf(state.words.joinToString("\n")) }
    var sample by rememberSaveable { mutableStateOf("") }
    // null = not run, otherwise the string resource of the outcome.
    var outcome by remember { mutableStateOf<Int?>(null) }
    val haptics = rememberHisnHaptics()
    val status = LocalHisnStatusColors.current

    DetailScaffold(stringResource(R.string.tester), onBack) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { PageIntro(stringResource(R.string.tester_help)) }
            item {
                OutlinedTextField(
                    value = rules,
                    onValueChange = { rules = it; outcome = null },
                    label = { Text(stringResource(R.string.rule_hint)) },
                    minLines = 3,
                    maxLines = 6,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                OutlinedTextField(
                    value = sample,
                    onValueChange = { sample = it; outcome = null },
                    label = { Text(stringResource(R.string.sample_hint)) },
                    minLines = 2,
                    maxLines = 5,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                Button(
                    onClick = {
                        outcome = when {
                            sample.isBlank() -> R.string.invalid
                            sample.codePointCount(0, sample.length) > 65536 -> R.string.too_long
                            else -> try {
                                val matcher = app.hisn.core.WordRuleList.compile(rules)
                                if (matcher.matches(sample).isEmpty()) R.string.no_match else R.string.match
                            } catch (invalid: IllegalArgumentException) {
                                R.string.invalid
                            }
                        }
                        when (outcome) {
                            R.string.match -> haptics.tick()
                            R.string.no_match -> haptics.confirm()
                            else -> haptics.reject()
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Rounded.Search, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.test_button))
                }
            }
            item {
                val result = outcome
                HisnCard(
                    containerColor = when (result) {
                        null -> MaterialTheme.colorScheme.surfaceContainerHigh
                        R.string.match -> status.warningContainer
                        R.string.no_match -> status.successContainer
                        else -> MaterialTheme.colorScheme.errorContainer
                    }
                ) {
                    Text(
                        text = stringResource(result ?: R.string.tester_result_hint),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier
                            .padding(16.dp)
                            .semantics { liveRegion = LiveRegionMode.Polite }
                    )
                }
            }
        }
    }
}

/** Explains why a hostname would or would not be blocked, and offers to add it as a rule. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun SiteCheckScreen(state: HisnState, onBack: () -> Unit, onEditRules: () -> Unit = {}) {
    var input by rememberSaveable { mutableStateOf("") }
    var checking by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<SiteCheck?>(null) }
    var confirmAdd by remember { mutableStateOf<String?>(null) }
    val haptics = rememberHisnHaptics()
    val snackbar = LocalHisnSnackbar.current
    val context = LocalContext.current
    val resources = androidx.compose.ui.platform.LocalResources.current
    val focusManager = LocalFocusManager.current

    fun run() {
        if (input.isBlank() || checking) return
        focusManager.clearFocus()
        checking = true
        result = null
        state.checkSite(input) {
            checking = false
            result = it
            if (it.host != null) haptics.confirm() else haptics.tick()
        }
    }

    confirmAdd?.let { host ->
        AlertDialog(
            onDismissRequest = { confirmAdd = null },
            title = { Text(stringResource(R.string.check_site_add_title, host)) },
            text = { Text(stringResource(R.string.check_site_add_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmAdd = null
                    if (state.addDomain(host)) {
                        snackbar(HisnMessage(resources.getString(R.string.check_site_added)))
                        result = null
                        input = ""
                    }
                }) { Text(stringResource(R.string.check_site_add)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmAdd = null }) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        )
    }

    DetailScaffold(stringResource(R.string.check_site_title), onBack) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { PageIntro(stringResource(R.string.check_site_path_note)) }
            item {
                OutlinedTextField(
                    value = input,
                    onValueChange = {
                        input = it
                        result = null
                    },
                    label = { Text(stringResource(R.string.check_site_hint)) },
                    singleLine = true,
                    enabled = !checking,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Uri,
                        imeAction = androidx.compose.ui.text.input.ImeAction.Go,
                        autoCorrectEnabled = false
                    ),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(onGo = { run() }),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                Button(
                    onClick = { run() },
                    enabled = input.isNotBlank() && !checking,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Rounded.Search, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(
                            if (checking) R.string.check_site_checking else R.string.check_site_button
                        )
                    )
                }
            }
            if (unrotEdition && result == null && !checking) {
                item { SectionHeader(stringResource(R.string.site_try)) }
                item {
                    val examples = (listOf("youtube.com", "old.reddit.com") + state.domains.take(1)).distinct()
                    androidx.compose.foundation.layout.FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        examples.forEach { example ->
                            androidx.compose.material3.SuggestionChip(
                                onClick = { input = example; run() },
                                label = { Text(example) }
                            )
                        }
                    }
                }
                item {
                    ReassuranceRow(icon = Icons.Rounded.Lock, text = stringResource(R.string.site_local_note))
                }
            }
            result?.let { shown ->
                item {
                    HisnCard {
                        Column(
                            Modifier.padding(16.dp).semantics(mergeDescendants = true) {
                                liveRegion = LiveRegionMode.Polite
                            },
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // The verdict first, then each fact that decided it, numbered.
                            shown.blocked?.let { blocked ->
                                val status = LocalHisnStatusColors.current
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    androidx.compose.foundation.layout.Box(
                                        Modifier.size(12.dp).background(
                                            if (blocked) status.success else MaterialTheme.colorScheme.outline,
                                            androidx.compose.foundation.shape.CircleShape
                                        )
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        stringResource(if (blocked) R.string.check_site_verdict_blocked else R.string.check_site_verdict_open),
                                        style = MaterialTheme.typography.headlineSmall
                                    )
                                }
                            }
                            val lines = shown.message.lines().filter { it.isNotBlank() }
                            if (shown.blocked == null || lines.size == 1) {
                                Text(shown.message, style = MaterialTheme.typography.bodyLarge)
                            } else {
                                lines.forEachIndexed { index, line ->
                                    Row {
                                        Text("${index + 1}", style = MaterialTheme.typography.titleSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.width(24.dp))
                                        Text(line, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }
                }
                if (unrotEdition) item {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        androidx.compose.material3.OutlinedButton(
                            onClick = { onEditRules() },
                            modifier = Modifier.weight(1f)
                        ) { Text(stringResource(R.string.site_edit_rule)) }
                        androidx.compose.material3.OutlinedButton(
                            onClick = { result = null; input = "" },
                            modifier = Modifier.weight(1f)
                        ) { Text(stringResource(R.string.site_check_another)) }
                    }
                }
                shown.host?.takeIf { !unrotEdition || shown.blocked != true }?.let { host ->
                    item {
                        TextButton(
                            onClick = { confirmAdd = host },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Rounded.Add, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.check_site_add))
                        }
                    }
                }
            }
        }
    }
}

/** The installed blocklist generation and a manual check for a newer signed one. */
@Composable
fun ListUpdateScreen(state: HisnState, onBack: () -> Unit) {
    var summary by remember { mutableStateOf(state.listUpdateSummary()) }
    var checking by remember { mutableStateOf(false) }
    var outcome by remember { mutableStateOf<String?>(null) }
    val haptics = rememberHisnHaptics()

    DetailScaffold(stringResource(R.string.list_update_title), onBack) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                HisnCard {
                    Text(
                        text = summary,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
            item {
                Button(
                    onClick = {
                        haptics.tick()
                        checking = true
                        outcome = null
                        state.checkListUpdate {
                            checking = false
                            outcome = it
                            summary = state.listUpdateSummary()
                        }
                    },
                    enabled = !checking,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        stringResource(
                            if (checking) R.string.list_update_checking else R.string.list_update_check
                        )
                    )
                }
            }
            outcome?.let { text ->
                item {
                    HisnCard(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh) {
                        Text(
                            text = text,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier
                                .padding(16.dp)
                                .semantics { liveRegion = LiveRegionMode.Polite }
                        )
                    }
                }
            }
        }
    }
}

/** Export the saved sites and words to a file, or merge a file into them after a preview. */
@Composable
fun RulesFileScreen(state: HisnState, onBack: () -> Unit) {
    DetailScaffold(stringResource(R.string.rules_file_title), onBack) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { PageIntro(stringResource(R.string.rules_file_summary)) }
            item {
                HisnCard {
                    SettingRow(
                        icon = Icons.Rounded.Language,
                        title = stringResource(R.string.rules_export),
                        supporting = "${tr("Blocked domains")}: ${state.domains.size} · ${tr("Blocked words")}: ${state.words.size}",
                        onClick = { state.exportRules() }
                    )
                    InsetDivider()
                    SettingRow(
                        icon = Icons.Rounded.Add,
                        title = stringResource(R.string.rules_import),
                        supporting = stringResource(R.string.rules_import_title),
                        onClick = { state.importRules() }
                    )
                }
            }
        }
    }
}

internal val PersonalPresets = listOf(
    "faith" to R.string.personal_preset_faith,
    "family" to R.string.personal_preset_family,
    "focus" to R.string.personal_preset_focus,
    "rest" to R.string.personal_preset_rest,
    "self" to R.string.personal_preset_self,
    app.hisn.core.PersonalProfile.CUSTOM to R.string.personal_preset_custom
)

/** Keeps at most [max] code points, so a name of emoji is not cut in the middle of one. */
internal fun limitCodePoints(text: String, max: Int): String =
    if (text.codePointCount(0, text.length) <= max) text
    else text.substring(0, text.offsetByCodePoints(0, max))

/** Name and reason, saved only on this phone. Not a rule, so a protection lock does not cover it. */
@Composable
fun PersonalScreen(state: HisnState, onBack: () -> Unit) {
    val saved = state.personal
    var name by rememberSaveable { mutableStateOf(saved.name) }
    var preset by rememberSaveable { mutableStateOf(saved.preset) }
    var custom by rememberSaveable { mutableStateOf(saved.customReason) }
    var customError by rememberSaveable { mutableStateOf(false) }
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    val haptics = rememberHisnHaptics()
    val snackbar = LocalHisnSnackbar.current
    val context = LocalContext.current
    val resources = androidx.compose.ui.platform.LocalResources.current
    val isCustom = preset == app.hisn.core.PersonalProfile.CUSTOM

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.personal_clear_title)) },
            text = { Text(stringResource(R.string.personal_clear_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    state.clearPersonal()
                    name = ""; preset = ""; custom = ""
                    haptics.confirm()
                    snackbar(HisnMessage(resources.getString(R.string.personal_removed)))
                    onBack()
                }) { Text(stringResource(R.string.personal_clear_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        )
    }

    DetailScaffold(stringResource(R.string.personal_dialog_title), onBack) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { PageIntro(stringResource(R.string.personal_dialog_body)) }
            item {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = limitCodePoints(it, app.hisn.core.PersonalProfile.MAX_NAME_CODE_POINTS)
                    },
                    label = { Text(stringResource(R.string.personal_name_hint)) },
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Words,
                        autoCorrectEnabled = false
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                Text(
                    text = stringResource(R.string.personal_reason_question),
                    style = MaterialTheme.typography.titleMedium
                )
            }
            item {
                HisnCard {
                    PersonalPresets.forEachIndexed { index, (key, label) ->
                        val selected = preset == key
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .toggleable(
                                    value = selected,
                                    role = Role.RadioButton,
                                    onValueChange = {
                                        haptics.tick()
                                        // Tapping the chosen reason again clears it.
                                        preset = if (selected) "" else key
                                        customError = false
                                    }
                                )
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            androidx.compose.material3.RadioButton(selected = selected, onClick = null)
                            Spacer(Modifier.width(12.dp))
                            Text(stringResource(label), style = MaterialTheme.typography.bodyLarge)
                        }
                        if (index < PersonalPresets.lastIndex) InsetDivider(start = 16.dp)
                    }
                }
            }
            if (isCustom) {
                item {
                    OutlinedTextField(
                        value = custom,
                        onValueChange = {
                            custom = limitCodePoints(it, app.hisn.core.PersonalProfile.MAX_REASON_CODE_POINTS)
                            customError = false
                        },
                        label = { Text(stringResource(R.string.personal_reason_hint)) },
                        isError = customError,
                        supportingText = if (customError) {
                            { Text(stringResource(R.string.personal_custom_empty)) }
                        } else {
                            null
                        },
                        minLines = 2,
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            item {
                Button(
                    onClick = {
                        if (isCustom && app.hisn.core.PersonalProfile.cleanReason(custom).isEmpty()) {
                            customError = true
                            haptics.reject()
                        } else {
                            state.savePersonal(name, preset, if (isCustom) custom else "")
                            haptics.confirm()
                            snackbar(HisnMessage(resources.getString(R.string.personal_saved)))
                            onBack()
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.personal_save))
                }
            }
            if (!saved.empty || saved.metric.isNotEmpty()) {
                item {
                    TextButton(onClick = { confirmClear = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            stringResource(R.string.personal_clear),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
            item {
                ReassuranceRow(
                    icon = Icons.Rounded.Lock,
                    text = stringResource(R.string.personal_local_note)
                )
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
                text = "${Edition.NAME} keeps matching on this phone."
            )
        }
    }
}

private fun infoFor(key: String): Pair<String, String> = when (key) {
    "uninstall" -> "Uninstall protection" to
        "On the direct build, ${Edition.NAME} can close the uninstall and disable screens while a protection lock is active. The Play build omits that guard. Android settings and a data wipe remain outside ${Edition.NAME}'s control unless the phone is managed separately."
    "keep" -> "Keep protection running" to
        "Android may pause background services. The watchdog checks every 15 minutes and warns when a layer stops, but it cannot restart the layer by itself. Open ${Edition.NAME} after a reboot or a permission change."
    "guardian" -> "Guardian pairing" to
        "Pairing with a guardian laptop lets a signed policy add restrictions. It cannot remove the lock early, and ${Edition.NAME} does not need an account for it."
    "how" -> "How ${Edition.NAME} works" to
        "${Edition.NAME} combines a DNS filter, whole-app restrictions and on-device text, tag and image checks. Matching happens on the phone. There is no account and no browsing log. The platform lock (Screen Time, Device Owner, MDM) is separate from ${Edition.NAME}'s own rules."
    "bypass" -> "What can bypass ${Edition.NAME}" to
        "DNS filtering covers supported domain lookups, not direct IP connections, cached answers, or apps that run their own encrypted DNS. Text, tag and image checks only cover the apps you select and may act after content appears. Android can stop services or revoke permissions. The protection lock freezes rules inside ${Edition.NAME}, not Android settings or uninstall."
    "privacy" -> "Privacy" to
        "Matching happens on-device. Prompts, frames and page text are not uploaded. ${Edition.NAME} keeps no browsing history. The list updater receives the signed list only. A DNS tunnel still sends allowed lookups to the configured resolver, as the DNS screen explains."
    "language" -> "Language" to
        "${Edition.NAME} follows the language of this phone. English and Arabic are included; on Android 13 and later you can also pick a language for ${Edition.NAME} alone under Settings › Apps › ${Edition.NAME} › Language."
    "gap" -> "Background check" to
        "The watchdog found a layer that stopped. Turn the layer back on from Protection, then run the DNS route check under Settings › Session diagnostics to confirm the route."
    "about" -> "About ${Edition.NAME}" to
        "${Edition.NAME} ${app.hisn.android.BuildConfig.VERSION_NAME} · local-first adult-content protection for Android. A DNS filter, app restrictions and on-device text and image checks run on this phone. No account, no cloud, no browsing log."
    else -> "About ${Edition.NAME}" to
        "${Edition.NAME} keeps matching on this phone. No account, no cloud, no browsing log."
}

@Composable
fun DetailHost(
    detail: String,
    state: HisnState,
    onBack: () -> Unit,
    onNavigate: (String) -> Unit
) {
    when {
        // Unrot's second-pass pages; the Islamic edition keeps the ones below.
        unrotEdition && detail == "apps" -> UnrotYourAppsScreen(state, onBack, onNavigate)
        unrotEdition && detail == "lock" -> UnrotLockScreen(state, onBack)
        unrotEdition && detail == "personal" -> UnrotAboutYouScreen(state, onBack)
        unrotEdition && detail == "diagnostics" -> UnrotHealthScreen(state, onBack, onNavigate)
        unrotEdition && detail == "info:how" -> UnrotHowScreen(onBack, onNavigate)
        unrotEdition && detail == "info:why" -> UnrotWhyScreen(state, onBack, onNavigate)
        detail == "lists" -> UnrotListsScreen(state, onBack, onNavigate)
        unrotEdition && detail == "notes" -> UnrotNotesScreen(onBack)
        // Unrot has no "Check a site"; an old link opens the lists instead.
        unrotEdition && detail == "sitecheck" -> UnrotListsScreen(state, onBack, onNavigate)
        // Unrot: "Your lists" has the list update and the export/import, so the two old pages open it.
        unrotEdition && (detail == "listupdate" || detail == "rulesfile") -> UnrotListsScreen(state, onBack, onNavigate)
        detail == "diagnostics:full" -> DiagnosticsScreen(state, onBack)
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
        detail == "lock" -> LockScreen(state, onBack)
        detail == "phone" -> PhoneSetupScreen(state, onBack)
        detail == "permissions" -> PermissionsScreen(state, onBack, onNavigate)
        detail == "diagnostics" -> DiagnosticsScreen(state, onBack)
        detail == "personal" -> PersonalScreen(state, onBack)
        detail == "progress" -> ProgressScreen(state, onBack, onNavigate)
        detail.startsWith("progress:day:") -> UnrotDayScreen(state, detail.removePrefix("progress:day:").toLongOrNull() ?: java.time.LocalDate.now().toEpochDay(), onBack)
        detail == "rules" -> DetailScaffold(title = stringResource(R.string.rules_all), onBack = onBack) { padding ->
            androidx.compose.foundation.layout.Box(Modifier.padding(padding)) { RulesScreen(state, onNavigate) }
        }
        detail == "more" -> DetailScaffold(title = stringResource(R.string.you_more_title), onBack = onBack) { padding ->
            SettingsScreen(state, onNavigate, part = YouPart.More, padding = padding)
        }
        detail == "sitecheck" -> SiteCheckScreen(state, onBack, onEditRules = { onNavigate("domains") })
        detail == "listupdate" -> ListUpdateScreen(state, onBack)
        detail == "rulesfile" -> RulesFileScreen(state, onBack)
        detail == "wordtester" -> WordTesterScreen(state, onBack)
        detail.startsWith("info:") -> InfoScreen(detail.removePrefix("info:"), onBack)
        else -> InfoScreen("about", onBack)
    }
}
