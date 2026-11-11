package app.hisn.prototype

import android.app.Activity
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
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
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import app.hisn.prototype.ui.ArabicProtectionCopy
import app.hisn.prototype.ui.DetailHost
import app.hisn.prototype.ui.EnglishProtectionCopy
import app.hisn.prototype.ui.HisnTheme
import app.hisn.prototype.ui.ProtectionCopy
import app.hisn.prototype.ui.ProtectionScreen
import app.hisn.prototype.ui.RulesScreen
import app.hisn.prototype.ui.SettingsScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val attention = intent.getBooleanExtra("attention", false)
        val forceDark = if (intent.hasExtra("dark")) intent.getBooleanExtra("dark", false) else null
        val initialTab = intent.getIntExtra("screen", 0).coerceIn(0, 2)
        val rtl = intent.getBooleanExtra("rtl", false)
        val arabic = intent.getBooleanExtra("arabic", false)
        setContent {
            HisnApp(
                attention = attention,
                darkTheme = forceDark,
                initialTab = initialTab,
                rtl = rtl,
                arabic = arabic
            )
        }
    }
}

private data class NavItem(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HisnApp(
    attention: Boolean,
    darkTheme: Boolean?,
    initialTab: Int,
    rtl: Boolean,
    arabic: Boolean
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(initialTab) }
    var detail by rememberSaveable { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val state = remember(context) { HisnState(context.applicationContext) }
    val copy = if (arabic) ArabicProtectionCopy else EnglishProtectionCopy
    // "--ez dark true/false" is the design-capture override; otherwise Settings decides.
    val dark = when {
        darkTheme != null -> darkTheme
        state.themeMode == "dark" -> true
        state.themeMode == "light" -> false
        else -> isSystemInDarkTheme()
    }

    // "--ez attention true" is the demo for a stopped layer: make the DNS layer really stopped so
    // the hero and the row agree, instead of drawing an attention state over a running layer.
    LaunchedEffect(attention) {
        if (attention) state.dns.set(false)
    }

    BackHandler(enabled = detail != null) {
        detail = null
    }

    CompositionLocalProvider(
        LocalLayoutDirection provides if (rtl || arabic) LayoutDirection.Rtl else LayoutDirection.Ltr
    ) {
        HisnTheme(darkTheme = dark, dynamicColor = state.systemColors.on) {
            SystemBars(dark)
            if (detail != null) {
                DetailHost(
                    detail = detail!!,
                    state = state,
                    onBack = { detail = null }
                )
            } else {
                HisnScaffold(
                    selectedTab = selectedTab,
                    onSelect = { selectedTab = it },
                    attention = attention,
                    copy = copy,
                    state = state,
                    onOpen = { detail = it },
                    onRunCheck = {
                        Toast.makeText(context, copy.checkPassed, Toast.LENGTH_SHORT).show()
                    },
                    onFix = {
                        Toast.makeText(context, "Protection layers re-armed — prototype", Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HisnScaffold(
    selectedTab: Int,
    onSelect: (Int) -> Unit,
    attention: Boolean,
    copy: ProtectionCopy,
    state: HisnState,
    onOpen: (String) -> Unit,
    onRunCheck: () -> Unit,
    onFix: () -> Unit
) {
    val titles = listOf(copy.appName, copy.navRules, copy.navSettings)
    val tagline = copy.tagline
    val navItems = listOf(
        NavItem(copy.navProtection, Icons.Filled.Shield, Icons.Outlined.Shield),
        NavItem(copy.navRules, Icons.Filled.Tune, Icons.Outlined.Tune),
        NavItem(copy.navSettings, Icons.Filled.Settings, Icons.Outlined.Settings)
    )

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
                            icon = {
                                Icon(
                                    imageVector = if (selectedTab == index) item.selectedIcon else item.unselectedIcon,
                                    contentDescription = null
                                )
                            },
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
                            showLogo = false
                        )
                        HisnContent(
                            innerPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                            selectedTab = selectedTab,
                            attention = attention,
                            copy = copy,
                            state = state,
                            wide = true,
                            onOpen = onOpen,
                            onRunCheck = onRunCheck,
                            onFix = onFix
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
                        showLogo = true
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
                                icon = {
                                    Icon(
                                        imageVector = if (selectedTab == index) item.selectedIcon else item.unselectedIcon,
                                        contentDescription = null
                                    )
                                },
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
                    wide = false,
                    onOpen = onOpen,
                    onRunCheck = onRunCheck,
                    onFix = onFix
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HisnTopBar(
    selectedTab: Int,
    titles: List<String>,
    tagline: String,
    showLogo: Boolean
) {
    TopAppBar(
        title = {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(
                    modifier = Modifier
                        .widthIn(max = 840.dp)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (showLogo && selectedTab == 0) {
                        Icon(
                            imageVector = Icons.Filled.Shield,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                    }
                    Column {
                        Text(
                            text = titles[selectedTab],
                            style = MaterialTheme.typography.titleLarge
                        )
                        if (selectedTab == 0) {
                            Text(
                                text = tagline,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background
        )
    )
}

@Composable
private fun HisnContent(
    innerPadding: androidx.compose.foundation.layout.PaddingValues,
    selectedTab: Int,
    attention: Boolean,
    copy: ProtectionCopy,
    state: HisnState,
    wide: Boolean,
    onOpen: (String) -> Unit,
    onRunCheck: () -> Unit,
    onFix: () -> Unit
) {
    Box(
        modifier = Modifier
            .padding(innerPadding)
            .fillMaxSize(),
        contentAlignment = Alignment.TopStart
    ) {
        Crossfade(
            targetState = selectedTab,
            animationSpec = tween(180),
            modifier = if (wide) {
                Modifier
                    .widthIn(max = 840.dp)
                    .fillMaxHeight()
                    .padding(horizontal = 16.dp)
            } else {
                Modifier.fillMaxSize()
            }
        ) { tab ->
            when (tab) {
                0 -> ProtectionScreen(
                    attention = attention,
                    state = state,
                    copy = copy,
                    onOpen = onOpen,
                    onRunCheck = onRunCheck,
                    onFix = onFix
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
