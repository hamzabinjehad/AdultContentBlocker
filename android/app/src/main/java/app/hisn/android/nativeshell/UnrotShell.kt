package app.hisn.android.nativeshell

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.rounded.Air
import androidx.compose.material.icons.filled.InsertChart
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material.icons.outlined.InsertChart
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.clickable
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.hisn.android.R

/*
 * Unrot's own shell: tabs, top bar and the floating dock. The Islamic edition has IslamicShell;
 * the two editions share the engine and NativeShell's detail routing, never a scaffold, so work on
 * one app's look cannot move the other's.
 */

private data class UnrotNavItem(val label: String, val selected: ImageVector, val unselected: ImageVector)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun UnrotScaffold(
    selectedTab: Int,
    onSelect: (Int) -> Unit,
    copy: ProtectionCopy,
    state: HisnState,
    onOpen: (String) -> Unit
) {
    val titles = listOf(
        stringResource(R.string.tab_today),
        stringResource(R.string.tab_shield),
        stringResource(R.string.tab_rules),
        stringResource(R.string.tab_progress),
        stringResource(R.string.tab_you)
    )
    val navItems = listOf(
        UnrotNavItem(titles[0], Icons.Filled.WbSunny, Icons.Outlined.WbSunny),
        UnrotNavItem(titles[1], Icons.Filled.Shield, Icons.Outlined.Shield),
        UnrotNavItem(titles[2], Icons.Filled.Tune, Icons.Outlined.Tune),
        UnrotNavItem(titles[3], Icons.Filled.InsertChart, Icons.Outlined.InsertChart),
        UnrotNavItem(titles[4], Icons.Filled.Person, Icons.Outlined.Person)
    )
    val tagline = todayLine()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    // "+ Add" on Rules opens one sheet for every kind of rule.
    var addSheet by rememberSaveable { mutableStateOf(false) }
    if (addSheet) UnrotAddSheet(onDismiss = { addSheet = false }, onOpen = onOpen)
    var helpSheet by rememberSaveable { mutableStateOf(false) }
    if (helpSheet) ProgressExplainerSheet(onDismiss = { helpSheet = false })

    val topBar: @Composable () -> Unit = {
        if (selectedTab != 0) UnrotTopBar(
            selectedTab = selectedTab,
            titles = titles,
            tagline = tagline,
            scrollBehavior = scrollBehavior,
            state = state,
            onBreathe = state::openPause,
            onAdd = if (selectedTab == UNROT_TAB_RULES) ({ addSheet = true }) else null,
            onHelp = if (selectedTab == UNROT_TAB_PROGRESS) ({ helpSheet = true }) else null
        )
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (maxWidth >= 600.dp) {
            Row(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                NavigationRail(
                    modifier = Modifier.fillMaxHeight().statusBarsPadding(),
                    containerColor = Color.Transparent
                ) {
                    Spacer(Modifier.height(24.dp))
                    navItems.forEachIndexed { index, item ->
                        NavigationRailItem(
                            selected = selectedTab == index,
                            onClick = { onSelect(index) },
                            icon = { UnrotNavIcon(selectedTab == index, item) },
                            label = { Text(item.label) },
                            colors = NavigationRailItemDefaults.colors(
                                selectedIconColor = Color.White.copy(alpha = .55f).compositeOver(MaterialTheme.colorScheme.primary),
                                selectedTextColor = MaterialTheme.colorScheme.onSurface,
                                indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = .30f).compositeOver(MaterialTheme.colorScheme.background),
                                unselectedIconColor = MaterialTheme.colorScheme.onSurface.copy(alpha = .8f),
                                unselectedTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = .8f)
                            )
                        )
                    }
                }
                Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.TopCenter) {
                    Column(Modifier.widthIn(max = 840.dp).fillMaxHeight()) {
                        topBar()
                        UnrotTabContent(PaddingValues(0.dp), selectedTab, copy, state, onOpen, scrollBehavior, onSelect)
                    }
                }
            }
        } else {
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                containerColor = MaterialTheme.colorScheme.background,
                contentColor = MaterialTheme.colorScheme.onBackground,
                topBar = topBar,
                bottomBar = {
                    // Android's own navigation bar, in the bar colour of the moment, with a gold pill.
                    val scheme = MaterialTheme.colorScheme
                    NavigationBar(
                        containerColor = scheme.surfaceContainerLow,
                        tonalElevation = 0.dp
                    ) {
                        navItems.forEachIndexed { index, item ->
                            NavigationBarItem(
                                selected = selectedTab == index,
                                onClick = { onSelect(index) },
                                icon = { UnrotNavIcon(selectedTab == index, item) },
                                label = {
                                    Text(item.label, fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Medium)
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = Color.White.copy(alpha = .55f).compositeOver(scheme.primary),
                                    selectedTextColor = scheme.onSurface,
                                    indicatorColor = scheme.primary.copy(alpha = .30f).compositeOver(scheme.surfaceContainerLow),
                                    unselectedIconColor = scheme.onSurface.copy(alpha = .8f),
                                    unselectedTextColor = scheme.onSurface.copy(alpha = .8f)
                                )
                            )
                        }
                    }
                }
            ) { innerPadding ->
                UnrotTabContent(innerPadding, selectedTab, copy, state, onOpen, scrollBehavior, onSelect)
            }
        }
    }
}

@Composable
private fun UnrotNavIcon(selected: Boolean, item: UnrotNavItem) {
    AnimatedContent(
        targetState = selected,
        transitionSpec = {
            (fadeIn(tween(180)) + scaleIn(initialScale = 0.86f, animationSpec = tween(220))) togetherWith
                (fadeOut(tween(90)) + scaleOut(targetScale = 0.9f, animationSpec = tween(120)))
        },
        label = "unrot nav icon"
    ) { isSelected ->
        Icon(if (isSelected) item.selected else item.unselected, contentDescription = null)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UnrotTopBar(
    selectedTab: Int,
    titles: List<String>,
    tagline: String,
    scrollBehavior: TopAppBarScrollBehavior,
    state: HisnState,
    onBreathe: () -> Unit,
    onAdd: (() -> Unit)?,
    onHelp: (() -> Unit)?
) {
    val bg = MaterialTheme.colorScheme.background
    TopAppBar(
        actions = {
            if (onHelp != null) {
                IconButton(onClick = onHelp) {
                    Icon(Icons.AutoMirrored.Rounded.HelpOutline,
                        contentDescription = stringResource(R.string.explain_progress_title))
                }
            }
            // Breathe is one tap away on every tab: a round button in the bar.
            RoundBarButton(onClick = onBreathe, label = stringResource(R.string.ut_breathe), gold = false) {
                Icon(Icons.Rounded.Air, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            }
            if (onAdd != null) {
                Spacer(Modifier.width(10.dp))
                RoundBarButton(onClick = onAdd, label = stringResource(R.string.ut_new_rule), gold = true) {
                    Icon(Icons.Rounded.Add, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(22.dp))
                }
            }
            Spacer(Modifier.width(12.dp))
        },
        title = {
            Row(
                modifier = Modifier.widthIn(max = 840.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    AnimatedContent(
                        targetState = selectedTab,
                        transitionSpec = {
                            (fadeIn(tween(200)) + slideInHorizontally(tween(240)) { it / 8 }) togetherWith
                                (fadeOut(tween(90)) + slideOutHorizontally(tween(140)) { -it / 10 })
                        },
                        label = "unrot top bar title"
                    ) { tab ->
                        Text(titles[tab], style = MaterialTheme.typography.headlineLarge.copy(fontSize = 30.sp, lineHeight = 34.sp))
                    }
                    AnimatedVisibility(
                        visible = selectedTab == 0,
                        enter = fadeIn(tween(220)),
                        exit = fadeOut(tween(100))
                    ) {
                        Text(tagline, style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        },
        scrollBehavior = scrollBehavior,
        // No hard band once the page scrolls; the page fades out under the title instead.
        modifier = Modifier.drawBehind {
            val f = scrollBehavior.state.overlappedFraction.coerceIn(0f, 1f)
            if (f > 0f) drawRect(
                Brush.verticalGradient(0f to bg.copy(alpha = f), 0.72f to bg.copy(alpha = f), 1f to Color.Transparent),
                size = size.copy(height = size.height + 28.dp.toPx())
            )
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent,
            scrolledContainerColor = Color.Transparent
        )
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UnrotTabContent(
    innerPadding: PaddingValues,
    selectedTab: Int,
    copy: ProtectionCopy,
    state: HisnState,
    onOpen: (String) -> Unit,
    scrollBehavior: TopAppBarScrollBehavior,
    onSelectTab: (Int) -> Unit
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
                // Tab order is start -> end; in RTL the slide mirrors.
                val direction = if (isRtl) -1 else 1
                val sign = if (targetState > initialState) direction else -direction
                (slideInHorizontally(tween(HisnMotion.MediumMs + 40, easing = FastOutSlowInEasing)) { sign * it / 6 } +
                    fadeIn(tween(180))) togetherWith
                    (slideOutHorizontally(tween(HisnMotion.MediumMs, easing = FastOutSlowInEasing)) { -sign * it / 8 } +
                        fadeOut(tween(110)))
            },
            modifier = Modifier.fillMaxSize(),
            label = "unrot tab content"
        ) { tab ->
            when (tab) {
                0 -> UnrotTodayScreen(state = state, onOpen = onOpen, onSelectTab = onSelectTab, innerPadding = PaddingValues(0.dp))
                // Shield is the adult protection alone; app rules have their own tab.
                1 -> ProtectionScreen(state = state, copy = copy, onOpen = onOpen, mode = HomeMode.Shield, part = SHIELD_ADULT)
                UNROT_TAB_RULES -> ProtectionScreen(state = state, copy = copy, onOpen = onOpen, mode = HomeMode.Shield, part = SHIELD_FEEDS)
                UNROT_TAB_PROGRESS -> ProgressContent(state = state, onOpen = onOpen)
                else -> SettingsScreen(state = state, onOpen = onOpen)
            }
        }
    }
}

/** A 38 dp round button in the top bar: Breathe on a card, or the gold + for a new rule. */
@Composable
private fun RoundBarButton(onClick: () -> Unit, label: String, gold: Boolean, icon: @Composable () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Box(
        Modifier.size(48.dp).clip(CircleShape)
            .clickable(role = androidx.compose.ui.semantics.Role.Button, onClickLabel = label, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier.size(38.dp).clip(CircleShape).background(
                if (gold) androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color.White.copy(alpha = .22f).compositeOver(scheme.primary), scheme.primary))
                else androidx.compose.ui.graphics.Brush.verticalGradient(listOf(scheme.surfaceContainer, scheme.surfaceContainer))
            ),
            contentAlignment = Alignment.Center
        ) { icon() }
    }
}
