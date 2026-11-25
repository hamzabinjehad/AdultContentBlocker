package app.hisn.android.nativeshell

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.hisn.core.Sayings

private data class MomentUi(val moment: Sayings.Moment, val icon: String, val en: String, val ar: String)

private val MOMENTS = listOf(
    MomentUi(Sayings.Moment.TEMPTED, "shield", "When tempted", "عند الإغراء"),
    MomentUi(Sayings.Moment.AFTER_SLIP, "ibrik", "After a slip", "بعد الزلّة"),
    MomentUi(Sayings.Moment.ALONE, "lantern", "When alone", "في الخلوة"),
    MomentUi(Sayings.Moment.WEAK, "hourglass", "Feeling weak", "عند الفتور"),
    MomentUi(Sayings.Moment.TIME, "phonedown", "Lost time", "ضياع الوقت"),
    MomentUi(Sayings.Moment.HEAVY, "hands", "Heavy heart", "ضيق الصدر")
)

private val AUTHOR_EN_SHORT = mapOf("ابن رجب" to "Ibn Rajab", "ابن قدامة المقدسي" to "Ibn Qudama")

@Composable
internal fun IslamicLibrary(onOpen: (String) -> Unit) {
    val look = LocalLook.current
    val sayings by rememberSayings()
    var savedOnly by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var topic by rememberSaveable { mutableStateOf("") }
    var author by rememberSaveable { mutableStateOf("") }
    var moment by rememberSaveable { mutableIntStateOf(-1) }
    val saved by HisnSayings.saved

    val lib = sayings
    val list = remember(lib, query, topic, author, moment, savedOnly, saved) {
        when {
            lib == null -> emptyList()
            savedOnly -> lib.all.filter { it.index in saved }
            else -> lib.filter(topic, author, if (moment >= 0) MOMENTS[moment].moment else null, query)
        }
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = IslamicBarHeight + 40.dp)) {
        item {
            Column(Modifier.statusBarsPadding().padding(horizontal = 18.dp, vertical = 10.dp)) {
                Text(ia("Purifying the soul", "تزكية النفس"), color = look.t.ink, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    ia("Ibn al-Qayyim, Ibn al-Jawzī, Ibn Rajab, Ibn Qudāma and others on the heart, the gaze and coming back. Each with its book and page, as written.",
                        "من كلام ابن القيم وابن الجوزي وابن رجب وابن قدامة وغيرهم في القلب والنظر والرجوع إلى الله، مع الكتاب والصفحة، بنصّه كما هو."),
                    color = look.t.sub, fontSize = 13.sp, lineHeight = 19.sp, modifier = Modifier.padding(top = 4.dp)
                )
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(look.t.soft).padding(4.dp)) {
                    listOf(false to ia("Library", "المكتبة"), true to ia("Saved", "المحفوظة") + if (saved.isNotEmpty()) " (" + nums(saved.size) + ")" else "").forEach { (s, label) ->
                        Text(
                            label, color = if (savedOnly == s) look.t.ink else look.t.sub, fontSize = 13.5.sp,
                            fontWeight = if (savedOnly == s) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier.weight(1f).clip(RoundedCornerShape(10.dp))
                                .background(if (savedOnly == s) look.t.card else Color.Transparent)
                                .clickable { savedOnly = s }.padding(vertical = 9.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
        }
        if (!savedOnly) {
            item {
                Text(ia("For the moment you are in", "للحظة التي أنت فيها"), color = look.t.sub, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 18.dp))
                Spacer(Modifier.height(8.dp))
                for (row in 0 until 2) {
                    Row(Modifier.padding(horizontal = 18.dp).padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (col in 0 until 3) {
                            val i = row * 3 + col
                            val m = MOMENTS[i]
                            val on = moment == i
                            Column(
                                Modifier.weight(1f).hisnCard(look, 16.dp, if (on) look.t.chip else look.t.card)
                                    .then(if (on) Modifier.border(2.dp, look.t.pri, RoundedCornerShape(16.dp)) else Modifier)
                                    .clickable { moment = if (on) -1 else i; topic = ""; author = "" }
                                    .padding(vertical = 10.dp, horizontal = 4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                HisnIcon(m.icon, 34.dp)
                                Text(ia(m.en, m.ar), color = look.t.ink, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                Text(nums(lib?.moment(m.moment)?.size ?: 0), color = look.t.sub, fontSize = 10.5.sp)
                            }
                        }
                    }
                }
            }
            item {
                Row(
                    Modifier.padding(horizontal = 18.dp, vertical = 6.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp))
                        .background(look.t.card).border(1.5.dp, look.t.line, RoundedCornerShape(16.dp)).padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.Search, null, tint = look.t.sub, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Box(Modifier.weight(1f)) {
                        if (query.isEmpty()) Text(ia("Search: heart, gaze, patience…", "ابحث: القلب، النظر، الصبر…"), color = look.t.sub, fontSize = 15.sp)
                        BasicTextField(
                            value = query, onValueChange = { query = it }, singleLine = true,
                            textStyle = TextStyle(color = look.t.ink, fontSize = 15.sp, textDirection = TextDirection.Content),
                            cursorBrush = SolidColor(look.t.pri), modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
            item {
                if (lib != null) {
                    ChipRow(
                        listOf("" to ia("Every scholar", "كل العلماء")) + lib.authors(4).map {
                            it to (if (isArabicUi()) it else (AUTHOR_EN_SHORT[it] ?: lib.all.first { s -> s.book.authorAr == it }.book.authorEn)) + " " + nums(lib.count(it))
                        },
                        author, outlined = true
                    ) { author = it; moment = -1 }
                    ChipRow(listOf("" to ia("All", "الكل")) + lib.topics.take(11).filter { it.key != "akhira" && it.key != "salah" }.map { it.key to ia(it.en, it.ar) }, topic) { topic = it }
                }
            }
        }
        item {
            Text(
                ia("${list.size} sayings", "${nums(list.size)} قولًا"), color = look.t.sub, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp)
            )
            if (savedOnly && list.isEmpty()) {
                Text(
                    ia("Tap the heart on any saying to keep it here.", "اضغط القلب على أي قول ليبقى هنا."), color = look.t.sub, fontSize = 14.sp,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 30.dp)
                )
            }
        }
        items(list, key = { it.index }) { s ->
            Column(Modifier.padding(horizontal = 18.dp, vertical = 6.dp).fillMaxWidth().hisnCard(look).padding(16.dp)) {
                SayingBody(s, flat = false)
            }
        }
    }
}

@Composable
private fun ChipRow(items: List<Pair<String, String>>, selected: String, outlined: Boolean = false, onPick: (String) -> Unit) {
    val look = LocalLook.current
    Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { (key, label) ->
            val on = key == selected
            Text(
                label, color = look.t.ink, fontSize = 13.5.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1,
                modifier = Modifier.clip(RoundedCornerShape(50))
                    .background(if (on) look.t.chip else if (outlined) Color.Transparent else look.t.card)
                    .border(1.5.dp, if (on) look.t.pri else if (outlined) look.t.line else Color.Transparent, RoundedCornerShape(50))
                    .clickable { onPick(key) }.padding(horizontal = 14.dp, vertical = 9.dp)
            )
        }
    }
}

/** One saying with its source and the save, share and open-the-page actions. */
@Composable
internal fun SayingBody(s: Sayings.Saying, flat: Boolean) {
    val look = LocalLook.current
    val context = LocalContext.current
    val saved by HisnSayings.saved
    var expanded by remember(s.index) { mutableStateOf(false) }
    val long = s.text.length > 300
    val author = if (isArabicUi()) (if (s.book.authorAr == "ابن رجب الحنبلي") "ابن رجب" else s.book.authorAr) else s.book.authorEn
    val source = "$author · ${s.book.title}، ${ia("p.", "ص")} ${nums(s.pageLabel())}"

    Row {
        if (!flat) Box(Modifier.width(3.dp).height(1.dp))
        Column(Modifier.weight(1f)) {
            Text(
                rtlLine("«${s.text}»"), color = look.t.ink, fontSize = 17.5.sp, lineHeight = 33.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Serif,
                style = TextStyle(textDirection = TextDirection.Rtl),
                maxLines = if (long && !expanded) 6 else Int.MAX_VALUE, overflow = TextOverflow.Ellipsis
            )
            if (long && !expanded) {
                Text(ia("Read it all", "اقرأ كاملًا"), color = look.t.pri, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 4.dp).clickable { expanded = true })
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (isArabicUi()) rtlLine("— $source") else "— $source", color = look.t.sub, fontSize = 12.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                IconButton(onClick = { HisnSayings.toggleSaved(context, s.index) }, modifier = Modifier.size(34.dp)) {
                    val on = s.index in saved
                    Icon(if (on) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, ia("Save", "حفظ"), tint = if (on) Color(0xFFD2526B) else look.t.sub, modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = {
                    val text = "«${s.text}»\n— $source"
                    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), null))
                }, modifier = Modifier.size(34.dp)) { Icon(Icons.Filled.Share, ia("Share", "مشاركة"), tint = look.t.sub, modifier = Modifier.size(18.dp)) }
                IconButton(onClick = {
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(s.link))) }
                }, modifier = Modifier.size(34.dp)) { Icon(Icons.AutoMirrored.Filled.OpenInNew, ia("Open in the book", "افتح في الكتاب"), tint = look.t.sub, modifier = Modifier.size(18.dp)) }
            }
            if (s.source == "reader") {
                val hearts = if (s.likes > 0) "♥ " + nums(s.likes) + ia(" · ", "، ") else ""
                Text(hearts + ia("chosen by Turath readers, checked against the book", "اختارها قرّاء «تراث»، ورُوجعت على الكتاب"), color = look.t.sub, fontSize = 11.sp)
            }
        }
    }
}
