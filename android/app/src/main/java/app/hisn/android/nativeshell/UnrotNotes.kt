package app.hisn.android.nativeshell

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.hisn.android.R
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import org.json.JSONArray
import org.json.JSONObject

/** One note: when it was written (or last changed) and what it says. */
internal data class Note(val at: Long, val text: String)

/**
 * The person's notebook: anything they want to write, kept per day on this phone only. Nothing
 * is uploaded and the app backup is off, so notes never leave the phone.
 */
internal object Notes {
    private const val PREFS = "unrot_notes"
    const val MAX_CHARS = 4000

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun day(context: Context, day: LocalDate): List<Note> = runCatching {
        val array = JSONArray(prefs(context).getString("d:${day.toEpochDay()}", "[]"))
        List(array.length()) { i -> array.getJSONObject(i).let { Note(it.getLong("t"), it.getString("x")) } }
    }.getOrDefault(emptyList())

    private fun write(context: Context, day: LocalDate, notes: List<Note>) {
        val key = "d:${day.toEpochDay()}"
        val edit = prefs(context).edit()
        if (notes.isEmpty()) edit.remove(key) else edit.putString(key, JSONArray().apply {
            notes.forEach { put(JSONObject().put("t", it.at).put("x", it.text)) }
        }.toString())
        edit.apply()
    }

    fun add(context: Context, day: LocalDate, text: String) {
        val clean = text.trim().take(MAX_CHARS)
        if (clean.isEmpty()) return
        write(context, day, day(context, day) + Note(System.currentTimeMillis(), clean))
    }

    fun replace(context: Context, day: LocalDate, old: Note, text: String) {
        val clean = text.trim().take(MAX_CHARS)
        write(context, day, day(context, day).map { if (it == old) Note(System.currentTimeMillis(), clean) else it }
            .filter { it.text.isNotEmpty() })
    }

    fun remove(context: Context, day: LocalDate, note: Note) = write(context, day, day(context, day) - note)
    fun restore(context: Context, day: LocalDate, note: Note) =
        write(context, day, (day(context, day) + note).sortedBy { it.at })

    /** Days in this month that have at least one note. */
    fun daysWithNotes(context: Context, month: YearMonth): Set<Int> {
        val first = month.atDay(1).toEpochDay()
        val last = month.atEndOfMonth().toEpochDay()
        return prefs(context).all.keys.mapNotNull { it.removePrefix("d:").toLongOrNull() }
            .filter { it in first..last }.map { LocalDate.ofEpochDay(it).dayOfMonth }.toSet()
    }
}

/** "My note": a month calendar with a dot on each day that has notes, and that day's notes below. */
@Composable
internal fun UnrotNotesScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val haptics = rememberHisnHaptics()
    val snackbar = LocalHisnSnackbar.current
    val locale = appLocale()
    val today = remember { LocalDate.now() }
    var picked by rememberSaveable { mutableStateOf(today.toEpochDay()) }
    val day = LocalDate.ofEpochDay(picked)
    var month by rememberSaveable { mutableStateOf(YearMonth.from(today).toString()) }
    val shownMonth = YearMonth.parse(month)
    var version by remember { mutableIntStateOf(0) }
    val notes = remember(picked, version) { Notes.day(context, day) }
    val marked = remember(month, version) { Notes.daysWithNotes(context, shownMonth) }
    var draft by rememberSaveable { mutableStateOf("") }
    var editing by remember { mutableStateOf<Note?>(null) }
    val savedText = stringResource(R.string.notes_saved)
    val deletedText = stringResource(R.string.notes_deleted)
    val undoText = stringResource(R.string.notes_undo)

    fun save() {
        val old = editing
        if (old != null) Notes.replace(context, day, old, draft) else Notes.add(context, day, draft)
        if (draft.isNotBlank()) { haptics.confirm(); snackbar(HisnMessage(savedText)) }
        draft = ""; editing = null; version++
    }

    DetailScaffold(stringResource(R.string.notes_title), onBack) { innerPadding ->
        LazyColumn(
            Modifier.padding(innerPadding).fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                MonthCalendar(shownMonth, day, today, marked, locale,
                    onMonth = { month = it.toString() },
                    onPick = { haptics.tap(); picked = it.toEpochDay(); editing = null; draft = "" })
            }
            item {
                val title = if (day == today) stringResource(R.string.notes_today)
                    else day.format(DateTimeFormatter.ofPattern("EEEE d MMMM", locale))
                Text(title, style = MaterialTheme.typography.titleLarge.storySerif(),
                    modifier = Modifier.padding(top = 4.dp).semantics { heading() })
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { if (it.length <= Notes.MAX_CHARS) draft = it },
                        placeholder = { Text(stringResource(R.string.notes_hint)) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
                        shape = MaterialTheme.shapes.large
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.notes_private), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                        if (editing != null) {
                            TextButton(onClick = { editing = null; draft = "" }) { Text(stringResource(R.string.notes_cancel)) }
                        }
                        Button(onClick = ::save, enabled = draft.isNotBlank()) {
                            Text(stringResource(if (editing != null) R.string.notes_update else R.string.notes_save))
                        }
                    }
                }
            }
            if (notes.isEmpty()) {
                item {
                    Text(stringResource(if (day == today) R.string.notes_empty_today else R.string.notes_empty_day),
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            items(notes.reversed(), key = { it.at }) { note ->
                NoteCard(note, locale,
                    onEdit = { haptics.tap(); editing = note; draft = note.text },
                    onDelete = {
                        haptics.tap()
                        Notes.remove(context, day, note); version++
                        snackbar(HisnMessage(deletedText, undoText) { Notes.restore(context, day, note); version++ })
                    })
            }
        }
    }
}

@Composable
private fun NoteCard(note: Note, locale: java.util.Locale, onEdit: () -> Unit, onDelete: () -> Unit) {
    val shape = MaterialTheme.shapes.large
    val time = remember(note.at) {
        Instant.ofEpochMilli(note.at).atZone(ZoneId.systemDefault()).toLocalTime()
            .format(DateTimeFormatter.ofPattern("HH:mm", locale))
    }
    Column(
        Modifier.fillMaxWidth().unrotFrame(shape).background(LocalHisnStatusColors.current.card, shape)
            .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 4.dp)
    ) {
        Text(time, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(note.text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 4.dp, end = 8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onEdit) { Text(stringResource(R.string.notes_edit)) }
            TextButton(onClick = onDelete) { Text(stringResource(R.string.notes_delete)) }
        }
    }
}

@Composable
private fun MonthCalendar(
    month: YearMonth, picked: LocalDate, today: LocalDate, marked: Set<Int>, locale: java.util.Locale,
    onMonth: (YearMonth) -> Unit, onPick: (LocalDate) -> Unit
) {
    val shape = MaterialTheme.shapes.large
    val firstDay: DayOfWeek = WeekFields.of(locale).firstDayOfWeek
    val days = (0 until 7).map { firstDay.plus(it.toLong()) }
    val lead = (month.atDay(1).dayOfWeek.value - firstDay.value + 7) % 7
    val cells = lead + month.lengthOfMonth()
    val ink = MaterialTheme.colorScheme.onSurface
    Column(
        Modifier.fillMaxWidth().unrotFrame(shape).background(LocalHisnStatusColors.current.card, shape).padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onMonth(month.minusMonths(1)) }) {
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, contentDescription = stringResource(R.string.notes_prev_month))
            }
            Text(month.atDay(1).format(DateTimeFormatter.ofPattern("MMMM yyyy", locale)),
                style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            IconButton(onClick = { onMonth(month.plusMonths(1)) }, enabled = month < YearMonth.from(today)) {
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = stringResource(R.string.notes_next_month))
            }
        }
        Row(Modifier.fillMaxWidth()) {
            days.forEach {
                Text(it.getDisplayName(TextStyle.NARROW, locale), style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            }
        }
        for (row in 0 until (cells + 6) / 7) {
            Row(Modifier.fillMaxWidth()) {
                for (col in 0 until 7) {
                    val n = row * 7 + col - lead + 1
                    Box(Modifier.weight(1f).aspectRatio(1f), contentAlignment = Alignment.Center) {
                        if (n in 1..month.lengthOfMonth()) {
                            val date = month.atDay(n)
                            val isPicked = date == picked
                            val future = date.isAfter(today)
                            val label = date.format(DateTimeFormatter.ofPattern("d MMMM", locale)) +
                                if (n in marked) ", " + stringResource(R.string.notes_has_notes) else ""
                            Column(
                                Modifier.size(40.dp)
                                    .background(if (isPicked) SetupButter else androidx.compose.ui.graphics.Color.Transparent, CircleShape)
                                    .then(if (date == today && !isPicked) Modifier.border(1.5.dp, ink.copy(alpha = 0.5f), CircleShape) else Modifier)
                                    .clickable(enabled = !future, role = Role.Button) { onPick(date) }
                                    .semantics { contentDescription = label; selected = isPicked },
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text("$n", style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (isPicked || date == today) FontWeight.Bold else FontWeight.Normal,
                                    color = when {
                                        isPicked -> UnrotInk
                                        future -> ink.copy(alpha = 0.3f)
                                        else -> ink
                                    })
                                Spacer(Modifier.size(2.dp))
                                Box(Modifier.size(5.dp).background(
                                    if (n in marked) (if (isPicked) UnrotInk else androidx.compose.ui.graphics.Color(0xFFE0A532)) else androidx.compose.ui.graphics.Color.Transparent,
                                    CircleShape))
                            }
                        }
                    }
                }
            }
        }
    }
}
