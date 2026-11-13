package app.hisn.android.nativeshell

import androidx.lifecycle.repeatOnLifecycle
import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.core.view.WindowCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
private data class NavItem(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

@Composable
fun NativeShell(state: HisnState, initialTab: Int = 0, tabRequest: Int = 0) {
    var selectedTab by rememberSaveable { mutableIntStateOf(initialTab) }
    var detail by rememberSaveable { mutableStateOf<String?>(null) }
    // A shortcut or notification that reaches the running activity asks for a tab; honour it.
    var appliedRequest by rememberSaveable { mutableIntStateOf(tabRequest) }
    if (appliedRequest != tabRequest) {
        appliedRequest = tabRequest
        selectedTab = initialTab
        detail = null
    }
    val context = LocalContext.current
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val copy = protectionCopy(
        androidx.core.os.ConfigurationCompat.getLocales(configuration)[0] ?: java.util.Locale.getDefault()
    )
    val dark = when (state.themeMode) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }
    val attention = state.needsAttention()
    val reduceMotion = systemReduceMotion(context)
    // Poll only while the screen is visible; a backgrounded shell does no work.
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.RESUMED) {
            while (true) {
                state.refreshAll()
                kotlinx.coroutines.delay(1500)
            }
        }
    }

    val scope = rememberCoroutineScope()
    val haptics = rememberHisnHaptics()
    val snackbarHostState = remember { SnackbarHostState() }
    // 1 = the detail screen covers the app; 0 = the tab shell is fully visible. A real Animatable,
    // not a Boolean, because both predictive back and the open/close taps need to stop halfway.
    val detailProgress = remember { Animatable(if (detail != null) 1f else 0f) }
    var closing by remember { mutableStateOf(false) }
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val dir = if (isRtl) -1f else 1f

    // "limit:apps" is a child of "limit": the overlay stays pushed and only its content changes,
    // which is how Android Settings handles a picker under a settings page.
    val nestedDetail = detail?.startsWith("limit:") == true

    val navigateDetail: (String) -> Unit = { key ->
        if (detail == null) {
            detail = key
            scope.launch {
                detailProgress.animateTo(1f, HisnMotion.spatialSlow())
            }
        } else if (!closing) {
            detail = key
        }
    }
    val closeDetail: () -> Unit = {
        if (detail != null && !closing) {
            closing = true
            scope.launch {
                detailProgress.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(HisnMotion.SlowMs, easing = FastOutSlowInEasing)
                )
                detail = null
                closing = false
            }
        }
    }
    val goBackFromDetail: () -> Unit = {
        if (nestedDetail) {
            haptics.nav()
            detail = "limit"
        } else {
            closeDetail()
        }
    }

    // A nested page pops one level instead of closing the whole overlay. It does not participate
    // in the predictive push: that gesture belongs to the overlay itself.
    BackHandler(enabled = nestedDetail) { goBackFromDetail() }

    // Predictive back: drive the push with the finger, then finish either direction. On devices
    // without a predictive gesture the flow completes immediately and the detail still closes.
    PredictiveBackHandler(enabled = detail != null && !nestedDetail && !closing) { progress ->
        var committed = false
        try {
            progress.collect { event ->
                detailProgress.snapTo((1f - event.progress).coerceIn(0f, 1f))
            }
            committed = true
        } catch (cancelled: CancellationException) {
            // The gesture was cancelled. The handler coroutine is ending, so put the screen back
            // from the composition scope, not from this cancelled one.
            scope.launch { detailProgress.animateTo(1f, HisnMotion.spatial()) }
        } finally {
            if (committed) {
                detailProgress.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(220, easing = FastOutSlowInEasing)
                )
                detail = null
            }
        }
    }

    val showMessage: (HisnMessage) -> Unit = { message ->
        scope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            val result = snackbarHostState.showSnackbar(
                message = message.text,
                actionLabel = message.actionLabel,
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) {
                message.onAction?.invoke()
            }
        }
    }

    CompositionLocalProvider(
        LocalReduceMotion provides reduceMotion,
        LocalHisnSnackbar provides showMessage
    ) {
        HisnTheme(darkTheme = dark, dynamicColor = state.systemColors.on) {
            SystemBars(dark)
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
            ) {
                val wide = maxWidth >= 600.dp
                val widthPx = constraints.maxWidth.toFloat()

                // The shell shrinks and steps aside while a detail page is pushed; the scrim keeps
                // the depth legible in dark mode where the two backgrounds are close.
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            val push = detailProgress.value
                            val scale = 1f - 0.055f * push
                            scaleX = scale
                            scaleY = scale
                            translationX = -dir * widthPx * 0.055f * push
                        }
                ) {
                    HisnScaffold(
                        selectedTab = selectedTab,
                        attention = attention,
                        onSelect = {
                            if (it != selectedTab) haptics.nav()
                            selectedTab = it
                        },
                        copy = copy,
                        state = state,
                        onOpen = navigateDetail
                    )
                }

                if (detail != null) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.16f * detailProgress.value))
                    )
                }

                if (detail != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                val push = detailProgress.value
                                translationX = dir * widthPx * (1f - push)
                                alpha = 0.55f + 0.45f * push
                            }
                    ) {
                        AnimatedContent(
                            targetState = detail,
                            modifier = Modifier.fillMaxSize(),
                            transitionSpec = {
                                val from = initialState
                                val to = targetState
                                val back = from != null && to != null && to.length < from.length
                                val direction = if (isRtl) -1 else 1
                                val sign = if (back) -direction else direction
                                (slideInHorizontally(
                                    animationSpec = tween(HisnMotion.MediumMs + 40, easing = FastOutSlowInEasing)
                                ) { width -> sign * width / 5 } + fadeIn(tween(180))) togetherWith
                                    (slideOutHorizontally(
                                        animationSpec = tween(HisnMotion.MediumMs, easing = FastOutSlowInEasing)
                                    ) { width -> -sign * width / 5 } + fadeOut(tween(110)))
                            },
                            label = "detail route"
                        ) { route ->
                            if (route != null) {
                                DetailHost(
                                    detail = route,
                                    state = state,
                                    onBack = goBackFromDetail,
                                    onNavigate = navigateDetail
                                )
                            }
                        }
                    }
                }

                SnackbarHost(
                    hostState = snackbarHostState,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = if (wide || detail != null) 16.dp else 120.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HisnScaffold(
    selectedTab: Int,
    attention: Boolean,
    onSelect: (Int) -> Unit,
    copy: ProtectionCopy,
    state: HisnState,
    onOpen: (String) -> Unit
) {
    val titles = listOf(copy.appName, copy.navRules, copy.navSettings)
    val tagline = copy.tagline
    val navItems = listOf(
        NavItem(copy.navProtection, Icons.Filled.Shield, Icons.Outlined.Shield),
        NavItem(copy.navRules, Icons.Filled.Tune, Icons.Outlined.Tune),
        NavItem(copy.navSettings, Icons.Filled.Settings, Icons.Outlined.Settings)
    )
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 600.dp
        if (wide) {
            Row(Modifier.fillMaxSize()) {
                NavigationRail(
                    modifier = Modifier
                        .fillMaxHeight()
                        .statusBarsPadding(),
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                ) {
                    Spacer(Modifier.height(12.dp))
                    Icon(
                        imageVector = Icons.Filled.Shield,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .padding(horizontal = 20.dp)
                            .size(28.dp)
                    )
                    Spacer(Modifier.height(12.dp))
                    navItems.forEachIndexed { index, item ->
                        NavigationRailItem(
                            selected = selectedTab == index,
                            onClick = { onSelect(index) },
                            icon = { NavIcon(selectedTab == index, item) },
                            label = { Text(item.label) },
                            colors = NavigationRailItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                selectedTextColor = MaterialTheme.colorScheme.onSurface,
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Column(
                        modifier = Modifier
                            .widthIn(max = 840.dp)
                            .fillMaxHeight()
                    ) {
                        HisnTopBar(
                            selectedTab = selectedTab,
                            titles = titles,
                            tagline = tagline,
                            showLogo = false,
                            scrollBehavior = scrollBehavior
                        )
                        HisnContent(
                            innerPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                            selectedTab = selectedTab,
                            attention = attention,
                            copy = copy,
                            state = state,
                            onOpen = onOpen,
                            scrollBehavior = scrollBehavior
                        )
                    }
                }
            }
        } else {
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                containerColor = MaterialTheme.colorScheme.background,
                topBar = {
                    HisnTopBar(
                        selectedTab = selectedTab,
                        titles = titles,
                        tagline = tagline,
                        showLogo = true,
                        scrollBehavior = scrollBehavior
                    )
                },
                bottomBar = {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        tonalElevation = 0.dp
                    ) {
                        navItems.forEachIndexed { index, item ->
                            NavigationBarItem(
                                selected = selectedTab == index,
                                onClick = { onSelect(index) },
                                icon = { NavIcon(selectedTab == index, item) },
                                label = { Text(item.label) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    selectedTextColor = MaterialTheme.colorScheme.onSurface,
                                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                        }
                    }
                }
            ) { innerPadding ->
                HisnContent(
                    innerPadding = innerPadding,
                    selectedTab = selectedTab,
                    attention = attention,
                    copy = copy,
                    state = state,
                    onOpen = onOpen,
                    scrollBehavior = scrollBehavior
                )
            }
        }
    }
}

@Composable
private fun NavIcon(selected: Boolean, item: NavItem) {
    AnimatedContent(
        targetState = selected,
        transitionSpec = {
            (fadeIn(tween(180)) + scaleIn(initialScale = 0.86f, animationSpec = tween(220))) togetherWith
                (fadeOut(tween(90)) + scaleOut(targetScale = 0.9f, animationSpec = tween(120)))
        },
        label = "nav icon"
    ) { isSelected ->
        Icon(
            imageVector = if (isSelected) item.selectedIcon else item.unselectedIcon,
            contentDescription = null
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HisnTopBar(
    selectedTab: Int,
    titles: List<String>,
    tagline: String,
    showLogo: Boolean,
    scrollBehavior: TopAppBarScrollBehavior
) {
    TopAppBar(
        title = {
            Row(
                modifier = Modifier
                    .widthIn(max = 840.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AnimatedVisibility(
                    visible = showLogo && selectedTab == 0,
                    enter = fadeIn(tween(200)),
                    exit = fadeOut(tween(120))
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Shield,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                    }
                }
                Column {
                    AnimatedContent(
                        targetState = selectedTab,
                        transitionSpec = {
                            (fadeIn(tween(200)) +
                                slideInHorizontally(tween(240)) { height -> height / 8 }) togetherWith
                                (fadeOut(tween(90)) +
                                    slideOutHorizontally(tween(140)) { height -> -height / 10 })
                        },
                        label = "top bar title"
                    ) { tab ->
                        Text(
                            text = titles[tab],
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
                    AnimatedVisibility(
                        visible = selectedTab == 0,
                        enter = fadeIn(tween(220)),
                        exit = fadeOut(tween(100))
                    ) {
                        Text(
                            text = tagline,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        scrollBehavior = scrollBehavior,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
            scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HisnContent(
    innerPadding: androidx.compose.foundation.layout.PaddingValues,
    selectedTab: Int,
    attention: Boolean,
    copy: ProtectionCopy,
    state: HisnState,
    onOpen: (String) -> Unit,
    scrollBehavior: TopAppBarScrollBehavior
) {
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Box(
        modifier = Modifier
            .padding(innerPadding)
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        contentAlignment = Alignment.TopStart
    ) {
        AnimatedContent(
            targetState = selectedTab,
            transitionSpec = {
                // Tab order is start -> end; in RTL that is physically right -> left, so the
                // slide mirrors while the bar and rail keep their native order.
                val direction = if (isRtl) -1 else 1
                val sign = if (targetState > initialState) direction else -direction
                (slideInHorizontally(
                    animationSpec = tween(HisnMotion.MediumMs + 40, easing = FastOutSlowInEasing)
                ) { width -> sign * width / 6 } + fadeIn(tween(180))) togetherWith
                    (slideOutHorizontally(
                        animationSpec = tween(HisnMotion.MediumMs, easing = FastOutSlowInEasing)
                    ) { width -> -sign * width / 8 } + fadeOut(tween(110)))
            },
            modifier = Modifier.fillMaxSize(),
            label = "tab content"
        ) { tab ->
            when (tab) {
                0 -> ProtectionScreen(
                    attention = attention,
                    state = state,
                    copy = copy,
                    onOpen = onOpen
                )
                1 -> RulesScreen(
                    state = state,
                    onOpen = onOpen
                )
                else -> SettingsScreen(
                    state = state,
                    onOpen = onOpen
                )
            }
        }
    }
}

@Composable
private fun SystemBars(darkTheme: Boolean) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }
}
