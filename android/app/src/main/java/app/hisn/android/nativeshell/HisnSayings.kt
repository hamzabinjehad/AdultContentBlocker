package app.hisn.android.nativeshell

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import app.hisn.core.Sayings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The tazkiya library, read once from the hisn flavor's assets (sayings/sayings.tsv). The general
 * edition has no such file and gets null everywhere, so nothing here shows up there.
 */
object HisnSayings {
    @Volatile private var cached: Sayings? = null
    @Volatile private var missing = false

    suspend fun get(context: Context): Sayings? {
        cached?.let { return it }
        if (missing) return null
        return withContext(Dispatchers.IO) {
            cached ?: runCatching {
                context.applicationContext.assets.open("sayings/sayings.tsv").use { input ->
                    Sayings.parse(String(input.readBytes(), Charsets.UTF_8))
                }
            }.getOrNull().also { if (it == null) missing = true else cached = it }
        }
    }

    /** Sayings the reader kept with the heart; saved by index in the shell preferences. */
    val saved = mutableStateOf<Set<Int>>(emptySet())
    private var savedLoaded = false

    fun loadSaved(context: Context) {
        if (savedLoaded) return
        savedLoaded = true
        val raw = context.getSharedPreferences("hisn-native", Context.MODE_PRIVATE).getString("sayings.saved", "") ?: ""
        saved.value = raw.split(',').mapNotNull { it.trim().toIntOrNull() }.toSet()
    }

    fun toggleSaved(context: Context, index: Int) {
        val next = saved.value.toMutableSet().apply { if (!add(index)) remove(index) }
        saved.value = next
        context.getSharedPreferences("hisn-native", Context.MODE_PRIVATE).edit()
            .putString("sayings.saved", next.joinToString(",")).apply()
    }
}

/** The library as Compose state: null while loading and in the general edition. */
@Composable
fun rememberSayings(): State<Sayings?> {
    val context = LocalContext.current
    LaunchedEffect(Unit) { HisnSayings.loadSaved(context) }
    return produceState<Sayings?>(initialValue = null) { value = HisnSayings.get(context) }
}
