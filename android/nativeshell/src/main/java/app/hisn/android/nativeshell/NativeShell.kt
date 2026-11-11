package app.hisn.android.nativeshell

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import android.view.WindowManager
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private data class PendingConsent(
    val consent: ShellConsent,
    val onAccept: () -> Unit
)

private enum class Destination(val icon: ImageVector) {
    Protection(Icons.Outlined.Lock),
    Rules(Icons.AutoMirrored.Outlined.List),
    Settings(Icons.Outlined.Settings)
}

/** Labels used before the host answers, or when it is not installed at all. */
private fun fallbackLabels() = ShellLabels(
    appName = "Hisn",
    tagline = "Adult content blocker",
    protection = "Protection",
    rules = "Rules",
    settings = "Settings",
    runCheck = "Run a protection check",
    openClassic = "Open classic Hisn",
    live = "Live · read-only preview",
    liveBody = "Real saved rules and service state. Changes open the classic Hisn screen.",
    attention = "What needs attention",
    layers = "Protection layers",
    locked = "Rules are locked until the protection lock ends.",
    unsupported = "This layer needs Android 14 or later.",
    dnsStart = "Set up DNS filtering",
    settingsControls = "Protection settings",
    settingsAndroid = "Android settings",
    exportRules = "Export",
    importRules = "Import"
)

/**
 * Phase 2 shell: real state, read-only policy.
 *
 * The host polls the same two-second cadence as the classic dashboard and returns fully localized
 * strings. The shell never writes a rule; every editor still belongs to the audited classic screen
 * until the next phase. If the app did not install a host, the shell says so instead of showing
 * plausible sample data.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NativeShell(host: NativeShellHost?) {
    var selected by rememberSaveable { mutableIntStateOf(0) }
    var protection by remember { mutableStateOf<ShellProtection?>(null) }
    var rules by remember { mutableStateOf<ShellRules?>(null) }
    var settings by remember { mutableStateOf<ShellSettings?>(null) }
    var consentRequest by remember { mutableStateOf<PendingConsent?>(null) }
    var editor by remember { mutableStateOf<ShellRulesEditor?>(null) }
    var appPicker by remember { mutableStateOf<ShellAppPicker?>(null) }
    var lockDialog by remember { mutableStateOf<ShellLockState?>(null) }
    var limitsDialog by remember { mutableStateOf<ShellLimitsEditor?>(null) }
    var scheduleDialog by remember { mutableStateOf<ShellScheduleEditor?>(null) }
    var uninstallSheet by remember { mutableStateOf<ShellUninstall?>(null) }
    var importPreview by remember { mutableStateOf<ShellImportPreview?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val activity = LocalActivity.current
    val lifecycleOwner: LifecycleOwner? = activity as? LifecycleOwner
    var resumed by remember {
        mutableStateOf(
            lifecycleOwner?.lifecycle?.currentState?.isAtLeast(Lifecycle.State.RESUMED) == true
        )
    }

    // Match the classic dashboard: poll only while the shell is resumed. Returning from a classic
    // editor triggers ON_RESUME and an immediate refresh.
    DisposableEffect(lifecycleOwner) {
        if (lifecycleOwner == null) {
            resumed = true
            onDispose { }
        } else {
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_RESUME -> resumed = true
                    Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> resumed = false
                    else -> Unit
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }
    }

    suspend fun refresh() {
        if (host == null) return
        protection = runCatching { host.protection() }.getOrNull()
        rules = runCatching { host.rules() }.getOrNull()
        settings = runCatching { host.settings() }.getOrNull()
    }

    LaunchedEffect(host, resumed) {
        if (host == null || !resumed) return@LaunchedEffect
        while (isActive) {
            refresh()
            delay(2_000)
        }
    }

    val labels = protection?.labels
        ?: rules?.labels
        ?: settings?.labels
        ?: fallbackLabels()

    SecureEditorWindow(
        editor != null || appPicker != null || limitsDialog != null || scheduleDialog != null
    )

    fun lockedMessage() {
        scope.launch { snackbar.showSnackbar(labels.locked) }
    }

    fun requestLayer(key: String, enabled: Boolean) {
        if (rules?.locked == true) {
            lockedMessage()
            return
        }
        val consent = host?.layerConsent(key)
        if (consent == null) {
            scope.launch { snackbar.showSnackbar(labels.unsupported) }
            return
        }
        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
        consentRequest = PendingConsent(consent) {
            scope.launch {
                val result = host?.setLayer(key, enabled)
                    ?: ShellActionResult(labels.unsupported, ok = false, refresh = false)
                snackbar.showSnackbar(result.message)
                if (result.ok) refresh()
            }
        }
    }

    fun editRules(kind: String) {
        val next = host?.rulesEditor(kind) ?: return
        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
        editor = next
    }

    fun editApps(kind: String) {
        val next = host?.appPicker(kind) ?: return
        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
        appPicker = next
    }

    fun editLock() {
        val next = host?.lockState() ?: return
        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
        lockDialog = next
    }

    fun editLimits() {
        val next = host?.limitsEditor() ?: return
        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
        limitsDialog = next
    }

    fun editSchedule() {
        val next = host?.scheduleEditor() ?: return
        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
        scheduleDialog = next
    }

    fun setToggle(key: String, enabled: Boolean) {
        scope.launch {
            val result = host?.setToggle(key, enabled)
                ?: return@launch
            snackbar.showSnackbar(result.message)
            if (result.ok) refresh()
        }
    }

    fun runSettingAction(key: String) {
        if (key == "uninstall") {
            uninstallSheet = host?.uninstallState()
            return
        }
        if (host?.runSettingAction(key) != true) {
            scope.launch { snackbar.showSnackbar(labels.unsupported) }
        }
    }

    val vpnLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            scope.launch {
                val started = host?.startDns()
                    ?: ShellActionResult(labels.unsupported, ok = false, refresh = false)
                refresh()
                snackbar.showSnackbar(started.message)
            }
        }
    }

    fun launchVpnConsent() {
        val intent = host?.vpnConsentIntent()
        if (intent != null) {
            vpnLauncher.launch(intent)
        } else {
            scope.launch {
                val started = host?.startDns()
                    ?: ShellActionResult(labels.unsupported, ok = false, refresh = false)
                refresh()
                snackbar.showSnackbar(started.message)
            }
        }
    }

    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        // The classic flow proceeds whether or not the notification permission is granted.
        launchVpnConsent()
    }

    val lockNotificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        // The lock is already saved; this only asks for the same notification permission
        // the classic confirmation requests after starting it.
    }

    val exportRulesLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val result = host?.exportRules(uri)
                    ?: ShellActionResult(labels.unsupported, ok = false, refresh = false)
                snackbar.showSnackbar(result.message)
            }
        }
    }

    val importRulesLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val preview = host?.previewImport(uri)
            if (preview == null) {
                scope.launch { snackbar.showSnackbar(labels.unsupported) }
            } else if (preview.error) {
                scope.launch { snackbar.showSnackbar(preview.body) }
            } else {
                importPreview = preview
            }
        }
    }

    fun beginDnsSetup() {
        val current = activity
        if (Build.VERSION.SDK_INT >= 33 && current != null &&
            ContextCompat.checkSelfPermission(
                current,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            launchVpnConsent()
        }
    }

    fun requestDnsSetup() {
        val consent = host?.dnsConsent() ?: return
        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
        consentRequest = PendingConsent(consent) { beginDnsSetup() }
    }

    fun openClassic() {
        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
        host?.openClassic()
    }

    fun runCheck() {
        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
        scope.launch {
            val result = host?.runCheck()
            snackbar.showSnackbar(result?.message ?: labels.liveBody)
            if (result?.refresh == true) refresh()
        }
    }

    BoxWithConstraints {
        val wide = maxWidth >= 600.dp
        Scaffold(
            snackbarHost = { SnackbarHost(snackbar) },
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(labels.appName, style = MaterialTheme.typography.titleLarge)
                            Text(
                                labels.tagline,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = ::openClassic) {
                            Icon(
                                Icons.Outlined.Info,
                                contentDescription = labels.openClassic
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
            },
            bottomBar = {
                if (!wide) {
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                        Destination.entries.forEachIndexed { index, destination ->
                            NavigationBarItem(
                                selected = selected == index,
                                onClick = {
                                    haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                    selected = index
                                },
                                icon = { Icon(destination.icon, contentDescription = null) },
                                label = {
                                    Text(
                                        when (index) {
                                            0 -> labels.protection
                                            1 -> labels.rules
                                            else -> labels.settings
                                        }
                                    )
                                }
                            )
                        }
                    }
                }
            }
        ) { insets ->
            Row(Modifier.fillMaxSize().padding(insets)) {
                if (wide) {
                    NavigationRail(
                        modifier = Modifier.fillMaxHeight(),
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    ) {
                        Destination.entries.forEachIndexed { index, destination ->
                            NavigationRailItem(
                                selected = selected == index,
                                onClick = {
                                    haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                    selected = index
                                },
                                icon = { Icon(destination.icon, contentDescription = null) },
                                label = {
                                    Text(
                                        when (index) {
                                            0 -> labels.protection
                                            1 -> labels.rules
                                            else -> labels.settings
                                        }
                                    )
                                }
                            )
                        }
                    }
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .widthIn(max = 840.dp)
                ) {
                    LiveBanner(labels = labels)
                    ShellContent(
                        selected = selected,
                        host = host,
                        protection = protection,
                        rules = rules,
                        settings = settings,
                        labels = labels,
                        onCheck = ::runCheck,
                        onOpenClassic = ::openClassic,
                        onLayer = ::requestLayer,
                        onEditRules = ::editRules,
                        onEditApps = ::editApps,
                        onEditLock = ::editLock,
                        onEditLimits = ::editLimits,
                        onEditSchedule = ::editSchedule,
                        onExportRules = { exportRulesLauncher.launch("hisn-rules.json") },
                        onImportRules = {
                            importRulesLauncher.launch(arrayOf("application/json", "text/plain"))
                        },
                        onStartDns = ::requestDnsSetup,
                        onToggle = ::setToggle,
                        onAction = ::runSettingAction,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }

    consentRequest?.let { request ->
        AlertDialog(
            onDismissRequest = { consentRequest = null },
            title = { Text(request.consent.title) },
            text = {
                Column(
                    Modifier
                        .heightIn(max = 360.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(request.consent.body, style = MaterialTheme.typography.bodyMedium)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val pending = request
                    consentRequest = null
                    pending.onAccept()
                }) {
                    Text(request.consent.acceptLabel)
                }
            },
            dismissButton = {
                TextButton(onClick = { consentRequest = null }) {
                    Text(request.consent.cancelLabel)
                }
            }
        )
    }

    editor?.let { current ->
        RulesEditorDialog(
            editor = current,
            onPreview = { text -> host?.previewRules(current.kind, text) ?: ShellRulesPreview("") },
            onSave = { text ->
                host?.saveRules(current.kind, text)
                    ?: ShellActionResult(labels.unsupported, ok = false, refresh = false)
            },
            onDismiss = { editor = null },
            onResult = { result ->
                if (result.ok) {
                    editor = null
                    scope.launch { refresh() }
                }
                scope.launch { snackbar.showSnackbar(result.message) }
            }
        )
    }

    appPicker?.let { current ->
        AppPickerDialog(
            picker = current,
            onSave = { selected ->
                host?.saveAppPicker(current.kind, selected)
                    ?: ShellActionResult(labels.unsupported, ok = false, refresh = false)
            },
            onDismiss = { appPicker = null },
            onResult = { result ->
                if (result.ok) {
                    appPicker = null
                    scope.launch { refresh() }
                }
                scope.launch { snackbar.showSnackbar(result.message) }
            }
        )
    }

    lockDialog?.let { current ->
        LockDialog(
            state = current,
            onStart = { days ->
                host?.startLock(days)
                    ?: ShellActionResult(labels.unsupported, ok = false, refresh = false)
            },
            onDismiss = { lockDialog = null },
            onResult = { result ->
                if (result.ok) {
                    lockDialog = null
                    scope.launch { refresh() }
                    val current = activity
                    if (Build.VERSION.SDK_INT >= 33 && current != null &&
                        ContextCompat.checkSelfPermission(
                            current,
                            Manifest.permission.POST_NOTIFICATIONS
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        lockNotificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
                scope.launch { snackbar.showSnackbar(result.message) }
            }
        )
    }

    limitsDialog?.let { current ->
        LimitsDialog(
            editor = current,
            onSave = { limits ->
                host?.saveLimits(limits)
                    ?: ShellActionResult(labels.unsupported, ok = false, refresh = false)
            },
            onOpenUsageAccess = { host?.openUsageAccess() == true },
            onDismiss = { limitsDialog = null },
            onResult = { result ->
                if (result.ok) {
                    limitsDialog = null
                    scope.launch { refresh() }
                }
                scope.launch { snackbar.showSnackbar(result.message) }
            }
        )
    }

    scheduleDialog?.let { current ->
        ScheduleDialog(
            editor = current,
            onSave = { enabled, start, end, days, allApps, apps ->
                host?.saveSchedule(enabled, start, end, days, allApps, apps)
                    ?: ShellActionResult(labels.unsupported, ok = false, refresh = false)
            },
            onDismiss = { scheduleDialog = null },
            onResult = { result ->
                if (result.ok) {
                    scheduleDialog = null
                    scope.launch { refresh() }
                }
                scope.launch { snackbar.showSnackbar(result.message) }
            }
        )
    }

    uninstallSheet?.let { current ->
        UninstallDialog(
            state = current,
            onCopy = {
                if (host?.copyUninstallCommand() == true) {
                    scope.launch { snackbar.showSnackbar(current.copyMessage) }
                } else {
                    scope.launch { snackbar.showSnackbar(labels.unsupported) }
                }
            },
            onAdmin = {
                if (host?.openAdminSetup() != true) {
                    scope.launch { snackbar.showSnackbar(labels.unsupported) }
                }
            },
            onOpenClassic = {
                uninstallSheet = null
                openClassic()
            },
            onDismiss = { uninstallSheet = null }
        )
    }

    importPreview?.let { current ->
        ImportPreviewDialog(
            preview = current,
            onApply = {
                scope.launch {
                    val result = host?.applyPendingImport()
                        ?: ShellActionResult(labels.unsupported, ok = false, refresh = false)
                    importPreview = null
                    snackbar.showSnackbar(result.message)
                    if (result.ok) refresh()
                }
            },
            onDismiss = {
                host?.discardPendingImport()
                importPreview = null
            }
        )
    }
}

@Composable
private fun SecureEditorWindow(active: Boolean) {
    val activity = LocalActivity.current
    DisposableEffect(active) {
        if (active) {
            activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
        onDispose {
            if (active) {
                activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
        }
    }
}

@Composable
private fun ShellContent(
    selected: Int,
    host: NativeShellHost?,
    protection: ShellProtection?,
    rules: ShellRules?,
    settings: ShellSettings?,
    labels: ShellLabels,
    onCheck: () -> Unit,
    onOpenClassic: () -> Unit,
    onLayer: (String, Boolean) -> Unit,
    onEditRules: (String) -> Unit,
    onEditApps: (String) -> Unit,
    onEditLock: () -> Unit,
    onEditLimits: () -> Unit,
    onEditSchedule: () -> Unit,
    onExportRules: () -> Unit,
    onImportRules: () -> Unit,
    onStartDns: () -> Unit,
    onToggle: (String, Boolean) -> Unit,
    onAction: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val layoutDirection = LocalLayoutDirection.current
    Box(modifier = modifier, contentAlignment = Alignment.TopCenter) {
        AnimatedContent(
            targetState = selected,
            transitionSpec = {
                val forward = targetState > initialState
                val mirror = if (layoutDirection == LayoutDirection.Rtl) -1 else 1
                val direction = (if (forward) 1 else -1) * mirror
                (
                    slideInHorizontally(animationSpec = tween(300)) { width -> direction * width / 6 } +
                        fadeIn(animationSpec = tween(180))
                    ) togetherWith (
                    slideOutHorizontally(animationSpec = tween(300)) { width -> -direction * width / 6 } +
                        fadeOut(animationSpec = tween(180))
                    )
            },
            label = "shell-tab"
        ) { index ->
            when {
                host == null -> UnavailableShellScreen(labels = labels, onOpenClassic = onOpenClassic)
                index == 0 && protection != null -> ProtectionShellScreen(
                    state = protection,
                    onCheck = onCheck,
                    onOpenClassic = onOpenClassic,
                    onStartDns = onStartDns
                )
                index == 1 && rules != null -> RulesShellScreen(
                    state = rules,
                    onOpenClassic = onOpenClassic,
                    onLayer = onLayer,
                    onEditRules = onEditRules,
                    onEditApps = onEditApps,
                    onEditLock = onEditLock,
                    onEditLimits = onEditLimits,
                    onEditSchedule = onEditSchedule,
                    onExportRules = onExportRules,
                    onImportRules = onImportRules
                )
                index == 2 && settings != null -> SettingsShellScreen(
                    state = settings,
                    onOpenClassic = onOpenClassic,
                    onToggle = onToggle,
                    onAction = onAction
                )
                else -> LoadingShellScreen()
            }
        }
    }
}
