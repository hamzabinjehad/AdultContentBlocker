package app.hisn.android.nativeshell

import android.app.Activity
import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.hisn.android.R
import app.hisn.core.PlusGate

/**
 * Unrot Plus on this phone: whether it is on, and the one question every screen asks before it
 * shows a paid thing ([open]). What is free and what is paid is decided in [PlusGate], never here.
 *
 * The purchase itself is the store's job ([PlusStore]); this object only remembers the result,
 * so the app keeps working offline and a direct (non-Play) build simply has no store.
 */
internal object UnrotPlus {
    private const val PREFS = "unrot_plus"
    private const val PAID_UNTIL = "paid_until"
    private const val LIFETIME = "lifetime"
    /** A subscription seen by the store is trusted this long without the store (offline, no Play). */
    private const val GRACE_MS = 3 * 24 * 3_600_000L

    /** Bumped whenever Plus turns on or off, so open screens redraw. */
    val version = mutableIntStateOf(0)

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun active(context: Context): Boolean = runCatching {
        val p = prefs(context)
        PlusGate.active(System.currentTimeMillis(), p.getLong(PAID_UNTIL, 0L), p.getBoolean(LIFETIME, false))
    }.getOrDefault(false)

    fun open(context: Context, feature: PlusGate.Feature, have: Int = 0): Boolean =
        PlusGate.open(feature, active(context), have)

    /** The store's verdict, written after every check and every purchase. */
    fun record(context: Context, subscribed: Boolean, lifetime: Boolean) {
        val p = prefs(context)
        val before = active(context)
        p.edit()
            .putLong(PAID_UNTIL, if (subscribed) System.currentTimeMillis() + GRACE_MS else 0L)
            .putBoolean(LIFETIME, lifetime)
            .apply()
        if (before != active(context)) version.intValue++
    }
}

internal enum class PlusPlan { YEARLY, MONTHLY, LIFETIME }

/** One thing the store can sell, with the price exactly as the store words it. */
internal class PlusOffer(val plan: PlusPlan, val price: String, val trialDays: Int, val handle: Any)

/**
 * The shop behind Plus. The Play build provides one (Google Play Billing, in the general
 * edition's own source set); a direct build and the Islamic edition have none.
 */
internal interface PlusStore {
    /** Offers once the store has answered; empty while loading or when nothing is for sale. */
    val offers: androidx.compose.runtime.State<List<PlusOffer>>
    /** False once the store has said it cannot be reached (no Play, no network, not signed in). */
    val reachable: androidx.compose.runtime.State<Boolean>
    fun connect(context: Context)
    fun buy(activity: Activity, offer: PlusOffer)
    /** Ask the store again what this account owns (also "Restore purchases"). */
    fun refresh(context: Context)
    fun close()
}

internal object PlusStores {
    /** A new store client, or null where this build has no shop. */
    fun create(): PlusStore? = runCatching {
        Class.forName("app.hisn.android.plus.PlayPlusStore").getDeclaredConstructor().newInstance() as PlusStore
    }.getOrNull()
}

/**
 * The Plus page. It says what stays free before it asks for anything, because a person fighting
 * adult content must never feel the help is behind a wall.
 */
@Composable
internal fun UnrotPlusScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val haptics = rememberHisnHaptics()
    val store = remember { PlusStores.create() }
    DisposableEffect(store) {
        store?.connect(context)
        onDispose { store?.close() }
    }
    val owned = remember(UnrotPlus.version.intValue) { UnrotPlus.active(context) }
    val offers = store?.offers?.value.orEmpty()
    val reachable = store?.reachable?.value ?: false
    var picked by rememberSaveable { mutableStateOf(PlusPlan.YEARLY.name) }
    val offer = offers.firstOrNull { it.plan.name == picked } ?: offers.firstOrNull()

    DetailScaffold(stringResource(R.string.plus_title), onBack) { innerPadding ->
        LazyColumn(
            Modifier.padding(innerPadding).fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 4.dp)) {
                    Image(painterResource(R.drawable.unrot_tree_cherry), null, Modifier.size(92.dp).offset(x = 14.dp))
                    Image(painterResource(R.drawable.unrot_tree_maple), null, Modifier.size(128.dp))
                    Image(painterResource(R.drawable.unrot_tree_pine), null, Modifier.size(92.dp).offset(x = (-14).dp))
                }
            }
            item {
                Text(stringResource(R.string.plus_title), style = MaterialTheme.typography.headlineMedium.storySerif(),
                    textAlign = TextAlign.Center, modifier = Modifier.semantics { heading() })
                Text(stringResource(if (owned) R.string.plus_owned else R.string.plus_lede),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
            }
            item { PlusHeading(R.string.plus_with) }
            item {
                // Only what this version really does. Trees and woods join the list when they ship.
                PlusRows(listOf(
                    Triple(R.drawable.unrot_3d_mobile, R.string.plus_rules, R.string.plus_rules_sub),
                    Triple(R.drawable.unrot_3d_heart, R.string.plus_support, R.string.plus_support_sub),
                    Triple(R.drawable.unrot_3d_leaf, R.string.plus_soon, R.string.plus_soon_sub)
                ))
            }
            item { PlusHeading(R.string.plus_free) }
            item {
                PlusRows(listOf(
                    Triple(R.drawable.unrot_3d_shield, R.string.plus_free_block, 0),
                    Triple(R.drawable.unrot_3d_pause, R.string.plus_free_breathe, 0),
                    Triple(R.drawable.unrot_3d_lock, R.string.plus_free_lock, 0),
                    Triple(R.drawable.unrot_3d_moon, R.string.plus_free_rules, 0)
                ))
            }
            if (!owned) {
                item {
                    if (offers.isEmpty()) {
                        Text(stringResource(if (store == null || !reachable) R.string.plus_no_store else R.string.plus_loading),
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 6.dp)) {
                            offers.forEach { o ->
                                PlanCard(o, selected = o === offer, modifier = Modifier.weight(1f)) { haptics.tap(); picked = o.plan.name }
                            }
                        }
                    }
                }
                item {
                    Button(
                        onClick = {
                            haptics.confirm()
                            val activity = context.findActivity()
                            if (activity != null && offer != null) store?.buy(activity, offer)
                        },
                        enabled = offer != null,
                        modifier = Modifier.fillMaxWidth().height(54.dp)
                    ) {
                        Text(
                            if (offer != null && offer.trialDays > 0) stringResource(R.string.plus_start_trial, offer.trialDays)
                            else stringResource(R.string.plus_start),
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    TextButton(onClick = { haptics.tap(); store?.refresh(context) }, enabled = store != null,
                        modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.plus_restore)) }
                    Text(stringResource(R.string.plus_note), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}

private fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is android.content.ContextWrapper) { if (c is Activity) return c; c = c.baseContext }
    return null
}

@Composable
private fun PlusHeading(text: Int) {
    Text(stringResource(text), style = MaterialTheme.typography.titleLarge.storySerif(),
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp).semantics { heading() })
}

@Composable
private fun PlusRows(rows: List<Triple<Int, Int, Int>>) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        rows.forEach { (art, title, sub) ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    Modifier.size(42.dp).clip(RoundedCornerShape(13.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.34f), RoundedCornerShape(13.dp)),
                    contentAlignment = Alignment.Center
                ) { Image(painterResource(art), null, Modifier.size(30.dp)) }
                Column(Modifier.weight(1f)) {
                    Text(stringResource(title), style = MaterialTheme.typography.bodyLarge)
                    if (sub != 0) Text(stringResource(sub), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun PlanCard(offer: PlusOffer, selected: Boolean, modifier: Modifier, onPick: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(if (selected) 2.dp else 1.dp,
                if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, shape)
            .clickable(role = Role.RadioButton, onClick = onPick)
            .semantics { this.selected = selected }
            .padding(horizontal = 12.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            stringResource(when (offer.plan) {
                PlusPlan.YEARLY -> R.string.plus_yearly
                PlusPlan.MONTHLY -> R.string.plus_monthly
                PlusPlan.LIFETIME -> R.string.plus_lifetime
            }),
            style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold
        )
        Text(offer.price, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (offer.trialDays > 0) {
            Spacer(Modifier.height(2.dp))
            Text(stringResource(R.string.plus_trial_days, offer.trialDays), style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.background(MaterialTheme.colorScheme.primary, CircleShape).padding(horizontal = 8.dp, vertical = 3.dp))
        }
    }
}

/** A row that opens the Plus page: "Unrot Plus" with what it is for, or "Plus is on". */
@Composable
internal fun UnrotPlusRow(onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val owned = remember(UnrotPlus.version.intValue) { UnrotPlus.active(context) }
    val shape = RoundedCornerShape(22.dp)
    Row(
        modifier.fillMaxWidth().clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f), shape)
            .clickable(role = Role.Button, onClick = onOpen)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Image(painterResource(R.drawable.unrot_tree_maple), null, Modifier.size(48.dp))
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.plus_title), style = MaterialTheme.typography.titleMedium.storySerif())
            Text(stringResource(if (owned) R.string.plus_row_owned else R.string.plus_row_sub),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
