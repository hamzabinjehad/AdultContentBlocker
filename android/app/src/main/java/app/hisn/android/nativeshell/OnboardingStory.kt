package app.hisn.android.nativeshell

import app.hisn.android.Edition
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.Balance
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.BatteryAlert
import androidx.compose.material.icons.rounded.AllInclusive
import androidx.compose.material.icons.rounded.Loop
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.SmartDisplay
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.hisn.android.R
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

// The story pages before setup. Every number shown comes from the person's own answers; nothing
// here is a statistic about other people, a review, or a countdown.

/** Number of message blocks in the opening chat; each ends with a reply the person taps. */
internal const val ChatBlocks = 3

private data class ChatBlock(val messages: List<Int>, val replies: List<Int>)

private val Chat = listOf(
    ChatBlock(
        listOf(R.string.story_chat_hi, R.string.story_chat_ask),
        listOf(R.string.story_chat_reply_sure, R.string.story_chat_reply_okay)
    ),
    ChatBlock(
        listOf(R.string.story_chat_phone, R.string.story_chat_gone, R.string.story_chat_somewhere),
        listOf(R.string.story_chat_reply_me, R.string.story_chat_reply_sometimes)
    ),
    ChatBlock(
        listOf(R.string.story_chat_willpower, R.string.story_chat_built, R.string.story_chat_wall),
        listOf(R.string.story_chat_reply_show, R.string.story_chat_reply_go)
    )
)

private const val MessageDelayMs = 420L

@Composable
internal fun ChatPage(name: String, picks: List<Int>, onReply: (Int) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val reduceMotion = LocalReduceMotion.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        IconBadge(Icons.Rounded.Shield, colors.primary, colors.onPrimary, size = 40.dp)
        Column {
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() })
            Text(stringResource(R.string.story_chat_subtitle), style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant)
        }
    }
    val current = picks.size.coerceAtMost(ChatBlocks - 1)
    // Messages of the block being read appear one by one; earlier blocks are already there.
    var visible by remember(picks.size) {
        mutableIntStateOf(if (reduceMotion || picks.size >= ChatBlocks) Chat[current].messages.size else 0)
    }
    LaunchedEffect(picks.size) {
        while (visible < Chat[current].messages.size) {
            delay(MessageDelayMs)
            visible++
        }
    }
    Column(
        Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Chat.forEachIndexed { index, block ->
            if (index > current) return@forEachIndexed
            val shown = if (index < current || picks.size >= ChatBlocks) block.messages.size else visible
            block.messages.take(shown).forEachIndexed { line, message ->
                val text = if (index == 0 && line == 0) {
                    if (name.isEmpty()) stringResource(R.string.story_chat_hi_anon) else stringResource(message, name)
                } else stringResource(message)
                AnimatedVisibility(
                    visible = true,
                    enter = if (reduceMotion) fadeIn(tween(0)) else fadeIn(tween(HisnMotion.MediumMs)) +
                        slideInVertically(tween(HisnMotion.MediumMs)) { it / 3 }
                ) { Bubble(text, mine = false) }
            }
            picks.getOrNull(index)?.let { pick -> Bubble(stringResource(block.replies[pick]), mine = true) }
        }
    }
    if (picks.size < ChatBlocks && visible >= Chat[current].messages.size) {
        val requester = remember { BringIntoViewRequester() }
        LaunchedEffect(picks.size) { requester.bringIntoView() }
        Column(
            Modifier.fillMaxWidth().bringIntoViewRequester(requester),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(stringResource(R.string.story_chat_pick), style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant)
            Chat[current].replies.forEachIndexed { index, reply ->
                OutlinedButton(onClick = { onReply(index) }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(reply))
                }
            }
        }
    }
}

@Composable
private fun Bubble(text: String, mine: Boolean) {
    val colors = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
        Text(
            text,
            style = MaterialTheme.typography.bodyLarge,
            color = if (mine) colors.onPrimary else colors.onSurface,
            modifier = Modifier
                .widthIn(max = 300.dp)
                .background(
                    if (mine) colors.primary else LocalHisnStatusColors.current.card,
                    RoundedCornerShape(
                        topStart = 18.dp, topEnd = 18.dp,
                        bottomStart = if (mine) 18.dp else 4.dp, bottomEnd = if (mine) 4.dp else 18.dp
                    )
                )
                .padding(horizontal = 14.dp, vertical = 10.dp)
        )
    }
}

internal val HoursAnswers = listOf(
    "lt2" to R.string.story_hours_lt2,
    "2to4" to R.string.story_hours_2to4,
    "4to6" to R.string.story_hours_4to6,
    "6to8" to R.string.story_hours_6to8,
    "gt8" to R.string.story_hours_gt8
)

/** The middle of the range the person picked, in hours a day. */
internal fun hoursPerDay(answer: String): Double = when (answer) {
    "lt2" -> 1.5
    "2to4" -> 3.0
    "4to6" -> 5.0
    "6to8" -> 7.0
    "gt8" -> 9.0
    else -> 0.0
}

@Composable
internal fun HoursPage(chosen: String, onChoose: (String) -> Unit) {
    Heading(R.string.story_hours_question, R.string.story_hours_body)
    Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        HoursAnswers.forEach { (value, label) -> ChoiceRow(label, chosen == value) { onChoose(value) } }
    }
    PrivateAnswersNote()
}

/** A number that counts up from zero once the page is shown, or appears at once under reduced motion. */
@Composable
private fun CountUp(target: Int, style: androidx.compose.ui.text.TextStyle, color: androidx.compose.ui.graphics.Color) {
    val reduceMotion = LocalReduceMotion.current
    var started by rememberSaveable { mutableStateOf(reduceMotion) }
    LaunchedEffect(Unit) { started = true }
    val value by animateIntAsState(
        targetValue = if (started) target else 0,
        animationSpec = if (reduceMotion) tween(0) else tween(900),
        label = "count up"
    )
    Text(
        value.toString(),
        style = style.copy(fontFeatureSettings = "tnum"),
        color = color,
        modifier = Modifier.clearAndSetSemantics { contentDescription = target.toString() }
    )
}

@Composable
internal fun MathPage(name: String, hours: String) {
    val colors = MaterialTheme.colorScheme
    val perDay = hoursPerDay(hours)
    val perMonth = (perDay * 30).roundToInt()
    val daysPerYear = (perDay * 365 / 24).roundToInt()
    Text(
        if (name.isEmpty()) stringResource(R.string.story_math_title) else stringResource(R.string.story_math_title_named, name),
        style = MaterialTheme.typography.headlineMedium.storySerif(),
        modifier = Modifier.fillMaxWidth().semantics { heading() }
    )
    HisnCard {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 20.dp, horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val big = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.Bold)
            val number = colors.primary
            Text(
                if (perDay % 1.0 == 0.0) perDay.toInt().toString() else perDay.toString(),
                style = big.copy(fontFeatureSettings = "tnum"), color = number
            )
            Text(stringResource(R.string.story_math_day), style = MaterialTheme.typography.titleSmall)
            MathArrow()
            CountUp(perMonth, big, number)
            Text(stringResource(R.string.story_math_month), style = MaterialTheme.typography.titleSmall)
            MathArrow()
            CountUp(daysPerYear, big, number)
            Text(stringResource(R.string.story_math_year), style = MaterialTheme.typography.titleSmall)
        }
    }
    Text(
        stringResource(R.string.story_math_body),
        style = MaterialTheme.typography.titleMedium,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
    )
    SupportingText(stringResource(R.string.story_math_note))
}

@Composable
private fun MathArrow() {
    Icon(
        Icons.Rounded.ArrowDownward,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(22.dp)
    )
}

/** Waking hours in a day, the base for the year of dots. */
private const val WakingHours = 16.0

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun YearPage(hours: String) {
    val colors = MaterialTheme.colorScheme
    val share = (hoursPerDay(hours) / WakingHours * 100).roundToInt().coerceIn(0, 100)
    val reduceMotion = LocalReduceMotion.current
    var lit by remember { mutableIntStateOf(if (reduceMotion) share else 0) }
    LaunchedEffect(share) {
        while (lit < share) {
            delay(18)
            lit++
        }
    }
    Heading(R.string.story_year_title, R.string.story_year_body)
    HisnCard {
        FlowRow(
            Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .clearAndSetSemantics {
                    contentDescription = "$share / 100"
                },
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            maxItemsInEachRow = 10
        ) {
            repeat(100) { index ->
                Box(
                    Modifier
                        .size(20.dp)
                        .background(
                            if (index < lit) colors.primary else colors.outlineVariant.copy(alpha = 0.6f),
                            CircleShape
                        )
                )
            }
        }
    }
    Text(
        androidx.compose.ui.res.pluralStringResource(R.plurals.story_year_share, share, share),
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.fillMaxWidth()
    )
}

/** Feelings in the groups people recognise them by; keys stay stable for the "two paths" page. */
internal val FeelingGroups = listOf(
    R.string.story_feel_group_mind to listOf(
        "guilty" to R.string.story_feel_guilty,
        "anxious" to R.string.story_feel_anxious,
        "empty" to R.string.story_feel_empty,
        "foggy" to R.string.story_feel_foggy
    ),
    R.string.story_feel_group_body to listOf(
        "tired" to R.string.story_feel_tired,
        "energy" to R.string.story_feel_energy,
        "distracted" to R.string.story_feel_distracted
    ),
    R.string.story_feel_group_values to listOf(
        "faith" to R.string.story_feel_faith,
        "self" to R.string.story_feel_self
    ),
    R.string.story_feel_group_people to listOf(
        "lonely" to R.string.story_feel_lonely,
        "people" to R.string.story_feel_people
    ),
    R.string.story_feel_group_other to listOf("fine" to R.string.story_feel_fine)
)

internal val TriggerAnswers = listOf(
    "night" to R.string.story_trigger_night,
    "bored" to R.string.story_trigger_bored,
    "alone" to R.string.story_trigger_alone,
    "stressed" to R.string.story_trigger_stressed,
    "morning" to R.string.story_trigger_morning
)

@Composable
internal fun FeelingsPage(chosen: Set<String>, onChange: (Set<String>) -> Unit) {
    Heading(R.string.story_feelings_question, R.string.story_select_all)
    FeelingGroups.forEach { (group, answers) ->
        Text(
            stringResource(group),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 4.dp).semantics { heading() }
        )
        MultiChoice(answers, chosen, onChange)
    }
    PrivateAnswersNote()
}

@Composable
internal fun TriggersPage(chosen: Set<String>, onChange: (Set<String>) -> Unit) {
    Heading(R.string.story_triggers_question, R.string.story_select_all)
    MultiChoice(TriggerAnswers, chosen, onChange)
    PrivateAnswersNote()
}

@Composable
internal fun MultiChoice(answers: List<Pair<String, Int>>, chosen: Set<String>, onChange: (Set<String>) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        answers.forEach { (value, label) ->
            val on = value in chosen
            HisnCard(containerColor = if (on) colors.primaryContainer else LocalHisnStatusColors.current.card) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .toggleable(value = on, role = Role.Checkbox) {
                            onChange(if (it) chosen + value else chosen - value)
                        }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        stringResource(label),
                        style = MaterialTheme.typography.titleMedium,
                        color = if (on) colors.onPrimaryContainer else colors.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        if (on) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                        contentDescription = null,
                        tint = if (on) colors.primary else colors.outline
                    )
                }
            }
        }
    }
}

@Composable
internal fun RingIcon(icon: ImageVector, size: Dp = 112.dp) {
    val colors = MaterialTheme.colorScheme
    Box(Modifier.fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier.size(size + 36.dp).border(1.dp, colors.primary.copy(alpha = 0.18f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Box(
                Modifier.size(size + 14.dp).border(1.dp, colors.primary.copy(alpha = 0.35f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                IconBadge(icon, colors.primaryContainer, colors.onPrimaryContainer, size = size)
            }
        }
    }
}

@Composable
internal fun TruthPage(name: String) {
    val colors = MaterialTheme.colorScheme
    RingIcon(Icons.Rounded.Balance)
    Text(
        if (name.isEmpty()) stringResource(R.string.story_truth_title) else stringResource(R.string.story_truth_title_named, name),
        style = MaterialTheme.typography.headlineMedium.storySerif(),
        modifier = Modifier.fillMaxWidth().semantics { heading() }
    )
    SupportingText(stringResource(R.string.story_truth_body))
    HisnCard(containerColor = colors.secondaryContainer) {
        Row(
            Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Rounded.Lightbulb, contentDescription = null, tint = colors.onSecondaryContainer)
            Text(
                stringResource(R.string.story_truth_wall),
                style = MaterialTheme.typography.bodyLarge,
                color = colors.onSecondaryContainer
            )
        }
    }
}

private data class Show(val icon: ImageVector, val title: Int, val body: Int)

private val Shows = mapOf(
    SetupPage.ShowWebsites to Show(Icons.Rounded.Language, R.string.story_show_websites_title, R.string.story_show_websites_body),
    SetupPage.ShowPause to Show(Icons.Rounded.HourglassTop, R.string.story_show_pause_title, R.string.story_show_pause_body),
    SetupPage.ShowShorts to Show(Icons.Rounded.SmartDisplay, R.string.story_show_shorts_title, R.string.story_show_shorts_body),
    SetupPage.ShowPictures to Show(Icons.Rounded.Image, R.string.story_show_pictures_title, R.string.story_show_pictures_body),
    SetupPage.ShowPrivate to Show(Icons.Rounded.Lock, R.string.story_show_private_title, R.string.story_show_private_body)
)

/** One thing Hisn does, with dots for the five of them. */
@Composable
internal fun ShowPage(page: SetupPage, order: List<SetupPage>, onPreview: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val show = Shows.getValue(page)
    val index = order.indexOf(page)
    Text(
        stringResource(R.string.story_show_eyebrow),
        style = MaterialTheme.typography.labelLarge,
        color = colors.primary,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
    )
    RingIcon(show.icon, size = 128.dp)
    Text(
        stringResource(show.title),
        style = MaterialTheme.typography.headlineMedium.storySerif(),
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().semantics { heading() }
    )
    Text(
        stringResource(show.body),
        style = MaterialTheme.typography.bodyLarge,
        color = colors.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
    )
    if (page == SetupPage.ShowPause) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            FilledTonalButton(onClick = onPreview, modifier = Modifier.heightIn(min = 48.dp)) {
                Icon(Icons.Rounded.HourglassTop, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.welcome_preview_pause))
            }
        }
    }
    PageDots(order.size, index)
}

private val WithoutLines = mapOf(
    "guilty" to R.string.story_without_guilty,
    "tired" to R.string.story_without_tired,
    "anxious" to R.string.story_without_anxious,
    "empty" to R.string.story_without_empty,
    "distracted" to R.string.story_without_distracted,
    "foggy" to R.string.story_without_foggy,
    "energy" to R.string.story_without_energy,
    "faith" to R.string.story_without_faith,
    "self" to R.string.story_without_self,
    "lonely" to R.string.story_without_lonely,
    "people" to R.string.story_without_people
)

private val WithLines = listOf(
    R.string.story_with_closed,
    R.string.story_with_pause,
    R.string.story_with_shorts,
    R.string.story_with_reason
)

/** Two ways the next weeks can go; the "without" side repeats only what the person said they feel. */
@Composable
internal fun PathsPage(feelings: Set<String>) {
    val colors = MaterialTheme.colorScheme
    val status = LocalHisnStatusColors.current
    var withHisn by rememberSaveable { mutableStateOf(false) }
    Heading(R.string.story_paths_title, R.string.story_paths_body)
    Row(
        Modifier
            .fillMaxWidth()
            .background(colors.surfaceVariant.copy(alpha = 0.7f), CircleShape)
            .padding(4.dp)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        listOf(false to R.string.story_paths_without, true to R.string.story_paths_with).forEach { (value, label) ->
            val selected = withHisn == value
            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .background(if (selected) colors.primary else androidx.compose.ui.graphics.Color.Transparent, CircleShape)
                    .selectable(selected = selected, role = Role.Tab) { withHisn = value },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    stringResource(label),
                    style = MaterialTheme.typography.titleSmall,
                    color = if (selected) colors.onPrimary else colors.onSurfaceVariant
                )
            }
        }
    }
    val lines = if (withHisn) WithLines else {
        feelings.mapNotNull { WithoutLines[it] }.ifEmpty { listOf(R.string.story_without_time) } +
            R.string.story_without_somewhere
    }
    HisnCard(containerColor = if (withHisn) status.successContainer else colors.errorContainer.copy(alpha = 0.55f)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            lines.forEach { line ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (withHisn) Icons.Rounded.CheckCircle else Icons.Rounded.Cancel,
                        contentDescription = null,
                        tint = if (withHisn) status.success else colors.error
                    )
                    Text(stringResource(line), style = MaterialTheme.typography.bodyLarge, color = colors.onSurface)
                }
            }
        }
    }
}

@Composable
internal fun RemindersPage() {
    val colors = MaterialTheme.colorScheme
    RingIcon(Icons.Rounded.Notifications)
    Heading(R.string.story_reminders_title, R.string.story_reminders_body)
    Text(stringResource(R.string.story_reminders_example), style = MaterialTheme.typography.labelLarge,
        color = colors.onSurfaceVariant)
    HisnCard {
        Row(
            Modifier.fillMaxWidth().padding(14.dp).semantics(mergeDescendants = true) {},
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            IconBadge(Icons.Rounded.Shield, colors.primary, colors.onPrimary, size = 36.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.app_name), style = MaterialTheme.typography.labelMedium,
                        color = colors.onSurfaceVariant, modifier = Modifier.weight(1f))
                    Text(stringResource(R.string.story_reminders_now), style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant)
                }
                Text(stringResource(R.string.layer_health_title), style = MaterialTheme.typography.titleSmall)
                Text(stringResource(R.string.layer_lost_dns), style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant)
            }
        }
    }
}

/** A short, honest "putting it together": each line names something the plan really does with the answers. */
@Composable
internal fun BuildingPage(focus: Boolean, triggers: Boolean, reason: Boolean) {
    val colors = MaterialTheme.colorScheme
    val status = LocalHisnStatusColors.current
    val steps = buildList {
        add(if (focus) R.string.story_building_order else R.string.story_building_order_default)
        add(R.string.story_building_phone)
        if (triggers) add(R.string.story_building_tips)
        if (reason) add(R.string.story_building_reason)
    }
    val reduceMotion = LocalReduceMotion.current
    var ticked by rememberSaveable { mutableIntStateOf(if (reduceMotion) steps.size else 0) }
    LaunchedEffect(Unit) {
        while (ticked < steps.size) {
            delay(550)
            ticked++
        }
    }
    val progress by animateIntAsState(
        targetValue = ticked * 100 / steps.size,
        animationSpec = if (reduceMotion) tween(0) else tween(500),
        label = "plan progress"
    )
    Box(Modifier.fillMaxWidth().padding(top = 12.dp), contentAlignment = Alignment.Center) {
        androidx.compose.material3.CircularProgressIndicator(
            progress = { progress / 100f },
            modifier = Modifier.size(132.dp),
            strokeWidth = 10.dp,
            trackColor = colors.outlineVariant.copy(alpha = 0.5f)
        )
        Text(
            stringResource(R.string.story_percent, progress),
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum")
        )
    }
    Text(
        stringResource(if (ticked >= steps.size) R.string.story_building_done else R.string.story_building_title),
        style = MaterialTheme.typography.headlineMedium.storySerif(),
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().semantics { heading(); liveRegion = LiveRegionMode.Polite }
    )
    HisnCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            steps.forEachIndexed { index, step ->
                val done = index < ticked
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (done) {
                        Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = status.success)
                    } else {
                        Icon(Icons.Rounded.RadioButtonUnchecked, contentDescription = null, tint = colors.outline)
                    }
                    Text(
                        stringResource(step),
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (done) colors.onSurface else colors.onSurfaceVariant
                    )
                }
            }
        }
    }
}

private val Faqs = listOf(
    R.string.story_faq_know_q to R.string.story_faq_know_a,
    R.string.story_faq_read_q to R.string.story_faq_read_a,
    R.string.story_faq_off_q to R.string.story_faq_off_a,
    R.string.story_faq_wrong_q to R.string.story_faq_wrong_a,
    R.string.story_faq_tried_q to R.string.story_faq_tried_a
)

/** Questions people ask before they trust a blocker, each opened on request. */
@Composable
internal fun FaqPage() {
    val colors = MaterialTheme.colorScheme
    var open by rememberSaveable { mutableIntStateOf(-1) }
    Heading(R.string.story_faq_title, R.string.story_faq_body)
    HisnCard {
        Faqs.forEachIndexed { index, (question, answer) ->
            val expanded = open == index
            Column(
                Modifier
                    .fillMaxWidth()
                    .toggleable(value = expanded, role = Role.Button) { open = if (it) index else -1 }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(question), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Icon(
                        if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                        contentDescription = null, tint = colors.onSurfaceVariant
                    )
                }
                if (expanded) {
                    Text(stringResource(answer), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                }
            }
            if (index < Faqs.lastIndex) InsetDivider(start = 16.dp)
        }
    }
}

internal enum class FirstResponse(val label: Int, val instruction: Int, val icon: ImageVector) {
    Breathe(R.string.story_response_breathe, R.string.story_response_breathe_body, Icons.Rounded.HourglassTop),
    StepAway(R.string.story_response_step_away, R.string.story_response_step_away_body, Icons.Rounded.PhoneAndroid),
    ReachOut(R.string.story_response_reach_out, R.string.story_response_reach_out_body, Icons.Rounded.Forum)
}

@Composable
internal fun FirstMovePage(selected: FirstResponse?, onChoose: (FirstResponse) -> Unit) {
    Heading(R.string.story_first_move_title, R.string.story_first_move_body)
    Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FirstResponse.entries.forEach { response ->
            ChoiceRow(response.label, response == selected, response.icon) { onChoose(response) }
        }
    }
    PrivateAnswersNote()
}

@Composable
internal fun RehearsePage(chosen: FirstResponse?, onPreview: () -> Unit) {
    val response = chosen ?: FirstResponse.Breathe
    RingIcon(response.icon, size = 80.dp)
    Heading(R.string.story_rehearse_title, R.string.story_rehearse_body)
    FirstResponseCard(response, suggested = chosen == null)
    if (response == FirstResponse.Breathe) {
        FilledTonalButton(
            onClick = onPreview,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
        ) { Text(stringResource(R.string.welcome_preview_pause)) }
    }
}

@Composable
internal fun FirstResponseCard(response: FirstResponse, suggested: Boolean = false) {
    val colors = MaterialTheme.colorScheme
    HisnCard(containerColor = colors.secondaryContainer) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                stringResource(if (suggested) R.string.story_response_example else R.string.story_response_yours),
                style = MaterialTheme.typography.labelLarge,
                color = colors.onSecondaryContainer
            )
            Text(stringResource(response.label), style = MaterialTheme.typography.titleMedium, color = colors.onSecondaryContainer)
            Text(stringResource(response.instruction), style = MaterialTheme.typography.bodyMedium, color = colors.onSecondaryContainer)
        }
    }
}

private val RestartSteps = listOf(
    R.string.story_restart_close to R.string.story_restart_close_body,
    R.string.story_restart_check to R.string.story_restart_check_body,
    R.string.story_restart_rule to R.string.story_restart_rule_body
)

@Composable
internal fun RestartPage() {
    val colors = MaterialTheme.colorScheme
    Heading(R.string.story_restart_title, R.string.story_restart_body)
    HisnCard {
        RestartSteps.forEachIndexed { index, (title, body) ->
            Row(
                Modifier.fillMaxWidth().padding(16.dp).semantics(mergeDescendants = true) {},
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top
            ) {
                Box(
                    Modifier.size(32.dp).background(colors.primaryContainer, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        java.text.NumberFormat.getIntegerInstance().format(index + 1),
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.onPrimaryContainer
                    )
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
                    SupportingText(stringResource(body))
                }
            }
            if (index < RestartSteps.lastIndex) InsetDivider(start = 60.dp)
        }
    }
}

/** Day one, dated today; the home screen counts the days from here. */
@Composable
internal fun DayOnePage(name: String) {
    val colors = MaterialTheme.colorScheme
    val today = remember { java.text.DateFormat.getDateInstance(java.text.DateFormat.LONG).format(java.util.Date()) }
    Box(Modifier.fillMaxWidth().padding(top = 16.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier.size(168.dp).border(1.dp, colors.primary.copy(alpha = 0.2f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Box(
                Modifier.size(140.dp).background(colors.primary, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.story_day_label), style = MaterialTheme.typography.labelLarge, color = colors.onPrimary)
                    Text("1", style = MaterialTheme.typography.displayLarge.copy(fontWeight = FontWeight.Bold), color = colors.onPrimary)
                }
            }
        }
    }
    Text(
        if (name.isEmpty()) stringResource(R.string.story_day_title) else stringResource(R.string.story_day_title_named, name),
        style = MaterialTheme.typography.headlineMedium.storySerif(),
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().semantics { heading() }
    )
    Text(
        today,
        style = MaterialTheme.typography.titleMedium,
        color = colors.primary,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
    )
    Text(
        stringResource(R.string.story_day_body),
        style = MaterialTheme.typography.bodyLarge,
        color = colors.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
    )
}

/**
 * "Why it's hard", in order. The two brain pages come right after the habit page: first what
 * endless feeds do, then what adult content does. The adult page is only shown to people who
 * picked it, or who skipped the question (the plan treats that as adult too).
 */
internal fun learnPages(targets: Set<String>): List<SetupPage> = buildList {
    add(SetupPage.LearnHabit)
    add(SetupPage.LearnBrainFeeds)
    if (targets.isEmpty() || "adult" in targets) add(SetupPage.LearnBrainAdult)
    add(SetupPage.LearnLoop)
    add(SetupPage.LearnFeeds)
    add(SetupPage.LearnWillpower)
    add(SetupPage.LearnChange)
}

private val Learns = mapOf(
    SetupPage.LearnHabit to Show(Icons.Rounded.Psychology, R.string.story_learn_habit_title, R.string.story_learn_habit_body),
    SetupPage.LearnBrainFeeds to Show(Icons.Rounded.Psychology, R.string.story_learn_brain_feeds_title, R.string.story_learn_brain_feeds_body),
    SetupPage.LearnBrainAdult to Show(Icons.Rounded.Psychology, R.string.story_learn_brain_adult_title, R.string.story_learn_brain_adult_body),
    SetupPage.LearnLoop to Show(Icons.Rounded.Loop, R.string.story_learn_loop_title, R.string.story_learn_loop_body),
    SetupPage.LearnFeeds to Show(Icons.Rounded.AllInclusive, R.string.story_learn_feeds_title, R.string.story_learn_feeds_body),
    SetupPage.LearnWillpower to Show(Icons.Rounded.BatteryAlert, R.string.story_learn_willpower_title, R.string.story_learn_willpower_body),
    SetupPage.LearnChange to Show(Icons.AutoMirrored.Rounded.TrendingUp, R.string.story_learn_change_title, R.string.story_learn_change_body)
)

private val LoopSteps = listOf(
    R.string.story_loop_feeling, R.string.story_loop_urge, R.string.story_loop_scroll, R.string.story_loop_regret
)

/** One page of "Why it’s hard": how the habit works, never blame. */
@Composable
internal fun LearnPage(page: SetupPage, pages: List<SetupPage>) {
    val colors = MaterialTheme.colorScheme
    val learn = Learns.getValue(page)
    Text(
        stringResource(R.string.story_learn_eyebrow),
        style = MaterialTheme.typography.labelLarge,
        color = colors.primary,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
    )
    when (page) {
        SetupPage.LearnBrainFeeds -> RewardChart(RewardPattern.RANDOM)
        SetupPage.LearnBrainAdult -> RewardChart(RewardPattern.NOVELTY)
        else -> RingIcon(learn.icon, size = 112.dp)
    }
    Text(
        stringResource(learn.title),
        style = MaterialTheme.typography.headlineMedium.storySerif(),
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().semantics { heading() }
    )
    Text(
        stringResource(learn.body),
        style = MaterialTheme.typography.bodyLarge,
        color = colors.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
    )
    if (page == SetupPage.LearnLoop) {
        HisnCard {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                LoopSteps.forEachIndexed { index, step ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(
                            Modifier.size(28.dp).background(colors.primaryContainer, CircleShape),
                            contentAlignment = Alignment.Center
                        ) { Text("${index + 1}", style = MaterialTheme.typography.labelLarge, color = colors.onPrimaryContainer) }
                        Text(stringResource(step), style = MaterialTheme.typography.bodyLarge)
                    }
                    if (index < LoopSteps.lastIndex) {
                        Icon(
                            Icons.Rounded.ArrowDownward, contentDescription = null,
                            tint = colors.outline, modifier = Modifier.padding(start = 4.dp).size(20.dp)
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Rounded.Replay, contentDescription = null, tint = colors.error, modifier = Modifier.size(20.dp))
                    Text(stringResource(R.string.story_loop_again), style = MaterialTheme.typography.bodyMedium, color = colors.error)
                }
            }
        }
    }
    when (page) {
        SetupPage.LearnBrainFeeds -> BrainFacts(BrainFeedsFacts)
        SetupPage.LearnBrainAdult -> BrainFacts(BrainAdultFacts)
        else -> Unit
    }
    PageDots(pages.size, pages.indexOf(page).coerceAtLeast(0))
}

private val BrainFeedsFacts = listOf(
    R.string.story_brain_feeds_fact1_title to R.string.story_brain_feeds_fact1_body,
    R.string.story_brain_feeds_fact2_title to R.string.story_brain_feeds_fact2_body,
    R.string.story_brain_feeds_fact3_title to R.string.story_brain_feeds_fact3_body
)

private val BrainAdultFacts = listOf(
    R.string.story_brain_adult_fact1_title to R.string.story_brain_adult_fact1_body,
    R.string.story_brain_adult_fact2_title to R.string.story_brain_adult_fact2_body,
    R.string.story_brain_adult_fact3_title to R.string.story_brain_adult_fact3_body
)

/** Three short facts under a brain page, plus one line saying this is a simplification. */
@Composable
private fun BrainFacts(facts: List<Pair<Int, Int>>) {
    val colors = MaterialTheme.colorScheme
    HisnCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            facts.forEachIndexed { index, (title, body) ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(
                        Modifier.size(28.dp).background(colors.primaryContainer, CircleShape),
                        contentAlignment = Alignment.Center
                    ) { Text("${index + 1}", style = MaterialTheme.typography.labelLarge, color = colors.onPrimaryContainer) }
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(title), style = MaterialTheme.typography.titleSmall)
                        Text(stringResource(body), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                    }
                }
            }
        }
    }
    Text(
        stringResource(R.string.story_brain_note),
        style = MaterialTheme.typography.bodySmall,
        color = colors.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
    )
}

internal enum class RewardPattern { RANDOM, NOVELTY }

/**
 * A small bar chart of how strongly the reward system answers, one bar per clip or image.
 * RANDOM: feeds pay out at random, so a big one can come any time. NOVELTY: the same thing
 * again answers less and less; only something new answers strongly. Bars grow in once, and
 * stand still when Android's "remove animations" is on.
 */
@Composable
internal fun RewardChart(pattern: RewardPattern) {
    val colors = MaterialTheme.colorScheme
    val reduceMotion = LocalReduceMotion.current
    val heights = when (pattern) {
        RewardPattern.RANDOM -> listOf(0.25f, 0.9f, 0.2f, 0.35f, 0.15f, 0.7f, 0.3f, 1f, 0.2f)
        RewardPattern.NOVELTY -> listOf(1f, 0.62f, 0.4f, 0.26f, 0.95f, 0.58f, 0.36f, 0.24f, 0.9f)
    }
    val strong = when (pattern) {
        RewardPattern.RANDOM -> setOf(1, 5, 7)
        RewardPattern.NOVELTY -> setOf(0, 4, 8)
    }
    val grow = remember { androidx.compose.animation.core.Animatable(if (reduceMotion) 1f else 0f) }
    LaunchedEffect(pattern) {
        if (!reduceMotion) grow.animateTo(1f, tween(900))
    }
    val description = stringResource(
        if (pattern == RewardPattern.RANDOM) R.string.story_brain_chart_random else R.string.story_brain_chart_novelty
    )
    Column(
        Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { contentDescription = description },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().height(112.dp).padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            heights.forEachIndexed { index, h ->
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight((h * grow.value).coerceAtLeast(0.04f))
                        .background(
                            if (index in strong) colors.primary else colors.outlineVariant,
                            RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp, bottomStart = 2.dp, bottomEnd = 2.dp)
                        )
                )
            }
        }
        Text(
            description,
            style = MaterialTheme.typography.labelMedium,
            color = colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().clearAndSetSemantics { }
        )
    }
}

@Composable
internal fun PageDots(count: Int, index: Int) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().clearAndSetSemantics { },
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)
    ) {
        repeat(count) { dot ->
            Box(
                Modifier
                    .size(width = if (dot == index) 22.dp else 8.dp, height = 8.dp)
                    .background(if (dot == index) colors.primary else colors.outlineVariant, CircleShape)
            )
        }
    }
}

internal val GoalAnswers = listOf(
    "focus" to R.string.story_goal_focus,
    "sleep" to R.string.story_goal_sleep,
    "faith" to R.string.story_goal_faith,
    "family" to R.string.story_goal_family,
    "time" to R.string.story_goal_time,
    "respect" to R.string.story_goal_respect
)

@Composable
internal fun GoalsPage(chosen: Set<String>, onChange: (Set<String>) -> Unit) {
    Heading(R.string.story_goals_question, R.string.story_select_all)
    MultiChoice(GoalAnswers, chosen, onChange)
    PrivateAnswersNote()
}

/** The goals the person chose, as small labels on the plan. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun GoalChips(goals: Set<String>) {
    if (goals.isEmpty()) return
    val colors = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.story_goals_working), style = MaterialTheme.typography.labelLarge, color = colors.primary)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            GoalAnswers.filter { it.first in goals }.forEach { (_, label) ->
                Text(
                    stringResource(label),
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.onPrimaryContainer,
                    modifier = Modifier
                        .background(colors.primaryContainer, CircleShape)
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                )
            }
        }
    }
}

private data class CompareRow(val label: Int, val plainDns: Boolean)

private val CompareRows = listOf(
    CompareRow(R.string.story_compare_sites, true),
    CompareRow(R.string.story_compare_apps, false),
    CompareRow(R.string.story_compare_shorts, false),
    CompareRow(R.string.story_compare_pictures, false),
    CompareRow(R.string.story_compare_pause, false),
    CompareRow(R.string.story_compare_lock, false),
    CompareRow(R.string.story_compare_private, true)
)

/** What Hisn adds over a DNS filter on its own; both columns describe real behaviour. */
@Composable
internal fun ComparePage() {
    val colors = MaterialTheme.colorScheme
    val status = LocalHisnStatusColors.current
    Heading(R.string.story_compare_title, R.string.story_compare_body)
    HisnCard {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 12.dp)) {
            Spacer(Modifier.weight(1f))
            Text(stringResource(R.string.story_compare_dns), style = MaterialTheme.typography.labelMedium,
                color = colors.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.width(76.dp))
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.labelMedium,
                color = colors.onPrimary, textAlign = TextAlign.Center,
                modifier = Modifier.width(76.dp).background(colors.primary, RoundedCornerShape(8.dp)).padding(vertical = 4.dp))
        }
        CompareRows.forEachIndexed { index, row ->
            val yes = stringResource(R.string.story_compare_yes)
            val no = stringResource(R.string.story_compare_no)
            val label = stringResource(row.label)
            val dns = stringResource(R.string.story_compare_dns)
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .padding(start = 16.dp, end = 8.dp)
                    .clearAndSetSemantics {
                        contentDescription = "$label. $dns: ${if (row.plainDns) yes else no}. ${Edition.NAME}: $yes."
                    },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f).padding(vertical = 10.dp))
                Box(Modifier.width(76.dp), contentAlignment = Alignment.Center) {
                    Icon(
                        if (row.plainDns) Icons.Rounded.CheckCircle else Icons.Rounded.Cancel, contentDescription = null,
                        tint = if (row.plainDns) status.success else colors.outline
                    )
                }
                Box(Modifier.width(76.dp), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = status.success)
                }
            }
            if (index < CompareRows.lastIndex) InsetDivider(start = 16.dp)
        }
        Spacer(Modifier.size(8.dp))
    }
}

@Composable
internal fun HoldPage(directBuild: Boolean) {
    val colors = MaterialTheme.colorScheme
    RingIcon(Icons.Rounded.Lock)
    Heading(R.string.story_hold_title, R.string.story_hold_body)
    HisnCard {
        val rows = buildList {
            add(Triple(Icons.Rounded.HourglassTop, R.string.story_hold_wait_title, R.string.story_hold_wait_body))
            add(Triple(Icons.Rounded.Lock, R.string.story_hold_lock_title, R.string.story_hold_lock_body))
            if (directBuild) add(Triple(Icons.Rounded.Shield, R.string.welcome_lock_card_title, R.string.story_hold_guard_body))
        }
        rows.forEachIndexed { index, (icon, title, body) ->
            Row(
                Modifier.fillMaxWidth().padding(16.dp).semantics(mergeDescendants = true) {},
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top
            ) {
                IconBadge(icon, colors.primaryContainer, colors.onPrimaryContainer, size = 36.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
                    SupportingText(stringResource(body))
                }
            }
            if (index < rows.lastIndex) InsetDivider()
        }
    }
}
