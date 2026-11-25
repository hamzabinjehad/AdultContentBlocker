package app.hisn.android.nativeshell

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * The Islamic edition's Tools tab, Shield page and Settings page, drawn as the Hisn mockup draws
 * them: grouped white cards with round icons and one line of why. Every row opens the working
 * screen behind it, so nothing the general app can do is lost.
 */

/** The website filter is attached and answering, with no fault the Protection screen would flag. */
internal fun dnsOk(state: HisnState): Boolean {
    val r = state.runtime
    return r.dnsAttached && r.dnsLockdown != true && !r.privateDnsStrict &&
        r.dns != app.hisn.android.DnsVpnService.State.ERROR && r.dns != app.hisn.android.DnsVpnService.State.DEGRADED &&
        state.check != app.hisn.android.DnsVpnService.CheckState.FAILED
}

// ---------------------------------------------------------------- shared rows

@Composable
internal fun GroupTitle(text: String) {
    Text(text, color = LocalLook.current.t.sub, fontSize = 12.5.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 6.dp, end = 6.dp, top = 14.dp, bottom = 6.dp))
}

@Composable
internal fun GroupCard(content: @Composable ColumnScope.() -> Unit) {
    val look = LocalLook.current
    Column(Modifier.fillMaxWidth().hisnCard(look, 18.dp).padding(vertical = 4.dp), content = content)
}

/** One row of a group: round icon, title, a line of why, and a chevron, a switch or a status word. */
@Composable
internal fun ToolRow(
    icon: ImageVector,
    title: String,
    sub: String,
    tint: Color? = null,
    status: String? = null,
    statusColor: Color? = null,
    switch: Boolean? = null,
    onSwitch: ((Boolean) -> Unit)? = null,
    onClick: (() -> Unit)? = null
) {
    val look = LocalLook.current
    Row(
        Modifier.fillMaxWidth().heightIn(min = 60.dp)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(38.dp).clip(CircleShape).background(look.t.soft), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = tint ?: look.t.pri, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = look.t.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            if (sub.isNotEmpty()) Text(sub, color = look.t.sub, fontSize = 12.5.sp, lineHeight = 17.sp)
        }
        Spacer(Modifier.width(8.dp))
        when {
            switch != null -> Switch(
                checked = switch, onCheckedChange = { onSwitch?.invoke(it) },
                colors = SwitchDefaults.colors(checkedTrackColor = look.t.kept, checkedThumbColor = Color.White,
                    uncheckedTrackColor = look.t.soft, uncheckedBorderColor = look.t.line, uncheckedThumbColor = look.t.sub)
            )
            status != null -> Text(status, color = statusColor ?: look.t.sub, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
            onClick != null -> Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = look.t.sub)
        }
    }
}

@Composable
internal fun RowLine() {
    Box(Modifier.fillMaxWidth().padding(start = 64.dp, end = 14.dp).height(1.dp).background(LocalLook.current.t.line))
}

/** A big square tile, as on the mockup's home and tools. */
@Composable
private fun BigTile(icon: String, title: String, sub: String, subColor: Color?, modifier: Modifier, onClick: () -> Unit) {
    val look = LocalLook.current
    Column(
        modifier.hisnCard(look, 20.dp).clickable(role = Role.Button, onClick = onClick).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        HisnIcon(icon, 46.dp)
        Spacer(Modifier.height(4.dp))
        Text(title, color = look.t.ink, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Text(sub, color = subColor ?: look.t.sub, fontSize = 12.5.sp, fontWeight = if (subColor != null) FontWeight.SemiBold else FontWeight.Normal)
    }
}

// ---------------------------------------------------------------- Tools tab

@Composable
internal fun IslamicTools(state: HisnState, onOpen: (String) -> Unit, onLibrary: () -> Unit) {
    val look = LocalLook.current
    val context = LocalContext.current
    val protectedNow = homeProtected(state)
    Column(
        Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp).padding(bottom = IslamicBarHeight + 28.dp)
    ) {
        Row(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(ia("Your tools", "أدواتك"), color = look.t.ink, fontSize = 26.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            IconButton(onClick = { onOpen("hisn:settings") }) {
                Box(Modifier.size(38.dp).clip(CircleShape).background(look.t.card).border(1.dp, look.t.line, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Settings, ia("Settings", "الإعدادات"), tint = look.t.pri, modifier = Modifier.size(20.dp))
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BigTile("shield", ia("Content shield", "درع المحتوى"),
                if (protectedNow) ia("Standing", "قائم") else ia("Needs you", "يحتاجك"),
                if (protectedNow) look.t.kept else look.t.help, Modifier.weight(1f)) { onOpen("hisn:shield") }
            BigTile("padlock", ia("Your rules", "قواعدك"), ia("Apps, sites, words, times", "التطبيقات والمواقع والكلمات والأوقات"),
                null, Modifier.weight(1f)) { onOpen("rules") }
        }

        GroupTitle(ia("For right now", "للحظة الحاضرة"))
        GroupCard {
            ToolRow(Icons.Filled.Air, ia("Breathe with dhikr", "تنفّس مع الذكر"), ia("30 seconds", "30 ثانية")) { state.openPause() }
            RowLine()
            ToolRow(Icons.Filled.Favorite, ia("Help now", "ساعدني الآن"), ia("Your promise, a verse, then what to do instead", "عهدك وآية، ثم ما تفعله بدلًا"), tint = look.t.help) { onOpen("hisn:help") }
            RowLine()
            ToolRow(Icons.Filled.EditNote, ia("My notes", "ملاحظاتي"), ia("On your calendar, a note for each night", "على تقويمك، ملاحظة لكل ليلة")) { onOpen("hisn:calendar") }
            RowLine()
            ToolRow(Icons.Filled.FormatQuote, ia("From the scholars", "من كلام العلماء"), ia("Ibn al-Qayyim, Tazkiyat al-Nufūs", "ابن القيم، تزكية النفوس"), onClick = onLibrary)
        }

        GroupTitle(ia("Your rules", "قواعدك"))
        GroupCard {
            ToolRow(Icons.Filled.HourglassTop, ia("Open Wait", "انتظار الفتح"), ia("A pause with dhikr before the apps you choose", "وقفة مع الذكر قبل التطبيقات التي تختارها")) {
                context.startActivity(android.content.Intent(context, app.hisn.android.OpenWaitSettingsActivity::class.java))
            }
            RowLine()
            ToolRow(Icons.Filled.Apps, ia("Blocked apps", "التطبيقات المحجوبة"), ia("Apps closed fully, and apps checked", "تطبيقات تُغلق كليًا، وأخرى تُفحص")) { onOpen("apps") }
            RowLine()
            ToolRow(Icons.Filled.NightsStay, ia("Quiet nights", "الليالي الهادئة"), ia("Times when the risky apps sleep", "أوقات تنام فيها التطبيقات الخطرة")) { onOpen("schedule") }
            RowLine()
            ToolRow(Icons.Filled.Contrast, ia("Gray screen", "الشاشة الرمادية"), ia("Colours off, so feeds pull less", "بلا ألوان، فتقلّ جاذبية المقاطع"),
                switch = state.gray.on, onSwitch = { state.gray.set(it) })
            RowLine()
            ToolRow(Icons.Filled.Timer, ia("Daily limits", "الحدود اليومية"), ia("Minutes per app", "دقائق لكل تطبيق")) { onOpen("limit") }
            RowLine()
            ToolRow(Icons.Filled.Lock, ia("Protection lock", "قفل الحماية"),
                if (state.lockLabel.isNotEmpty()) state.lockLabel else ia("Rules can't be loosened in a weak minute", "لا تُخفَّف القواعد في لحظة ضعف")) { onOpen("lock") }
            RowLine()
            ToolRow(Icons.Filled.People, ia("Guardian", "الوليّ"), ia("A paired laptop can add rules", "حاسوب مقترن يضيف قواعد")) { state.openGuardian() }
        }

        GroupTitle(ia("This phone", "الهاتف"))
        GroupCard {
            ToolRow(Icons.Filled.BatteryChargingFull, ia("Keep protection running", "إبقاء الحماية تعمل"), ia("Battery and background on this phone", "البطارية والخلفية على هذا الهاتف")) { onOpen("phone") }
            RowLine()
            ToolRow(Icons.Filled.VerifiedUser, ia("Permissions", "الصلاحيات"), ia("What Android has allowed Hisn", "ما سمح به أندرويد لحصن")) { onOpen("permissions") }
            RowLine()
            ToolRow(Icons.Filled.HealthAndSafety, ia("Health check", "فحص السلامة"), ia("Is every layer working?", "هل تعمل كل الطبقات؟")) { onOpen("diagnostics") }
        }

        GroupTitle(ia("Settings", "الإعدادات"))
        GroupCard {
            ToolRow(Icons.Filled.Settings, ia("Settings", "الإعدادات"), ia("Look, prayer times, you", "الطابع، مواقيت الصلاة، أنت")) { onOpen("hisn:settings") }
        }
        Text(ia("Hisn is free and open source.", "حصن مجاني ومفتوح المصدر."),
            color = look.t.sub, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 28.dp))
    }
}

// ---------------------------------------------------------------- Shield page

@Composable
internal fun ShieldPage(state: HisnState, onBack: () -> Unit, onOpen: (String) -> Unit) {
    val look = LocalLook.current
    val sites = dnsOk(state)
    val apps = state.apps.on && state.guardReady
    val layers = listOf(sites, apps, state.tags.on, state.images.on, state.shortVideos.on)
    val working = layers.count { it }
    val standing = sites && apps
    IslamicPage(ia("Content shield", "درع المحتوى"), onBack) {
        Box(Modifier.fillMaxWidth().padding(top = 4.dp), contentAlignment = Alignment.Center) { HisnIcon("shield", 96.dp) }
        Text(ia("Adult content filter", "مرشّح المحتوى الإباحي"), color = look.t.ink, fontSize = 22.sp, fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
        Text(ia("Closes adult sites, words and images in every app and browser on this phone. The checks run here; nothing is uploaded.",
            "يحجب المواقع والكلمات والصور الإباحية في كل تطبيق ومتصفح على هاتفك. يجري الفحص هنا، ولا يُرفع شيء."),
            color = look.t.sub, fontSize = 13.5.sp, lineHeight = 20.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
        Row(
            Modifier.fillMaxWidth().hisnCard(look, 18.dp)
                .border(1.5.dp, if (standing) look.t.kept else look.t.help.copy(alpha = 0.6f), RoundedCornerShape(18.dp)).padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(if (standing) Icons.Filled.CheckCircle else Icons.Filled.Warning, null, tint = if (standing) look.t.kept else look.t.help, modifier = Modifier.size(30.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(if (standing) ia("The shield is standing", "الدرع يعمل") else ia("The shield needs you", "الدرع يحتاجك"),
                    color = if (standing) look.t.kept else look.t.help, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text(ia("$working of 5 layers working", "${nums(working)} من ${nums(5)} طبقات تعمل"), color = look.t.sub, fontSize = 12.5.sp)
            }
            if (!standing) Text(ia("Fix", "أصلحه"), color = look.t.priInk, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.clip(CircleShape).background(look.t.pri).clickable { onOpen("permissions") }.padding(horizontal = 14.dp, vertical = 7.dp))
        }

        GroupTitle(ia("The layers", "الطبقات"))
        GroupCard {
            val on = ia("On", "يعمل"); val off = ia("Off", "متوقف")
            ToolRow(Icons.Filled.Language, ia("Websites", "المواقع"),
                if (sites) ia("DNS filter · ${state.dnsDomains} sites · SafeSearch", "مرشّح DNS · ${nums(state.dnsDomains)} موقع · البحث الآمن")
                else ia("The local filter is not answering", "المرشّح المحلي لا يعمل"),
                status = if (sites) on else off, statusColor = if (sites) look.t.kept else look.t.help) {
                if (sites) onOpen("domains") else state.openPermission("dns")
            }
            RowLine()
            ToolRow(Icons.Filled.Shield, ia("App protection", "حماية التطبيقات"), ia("Reads words and closes what you chose", "يقرأ الكلمات ويغلق ما اخترت"),
                switch = state.apps.on, onSwitch = { state.apps.set(it) })
            RowLine()
            ToolRow(Icons.Filled.TextFields, ia("Words in apps", "الكلمات في التطبيقات"), ia("Adult words in searches and pages", "الكلمات الإباحية في البحث والصفحات"),
                switch = state.tags.on, onSwitch = { state.tags.set(it) })
            RowLine()
            ToolRow(Icons.Filled.Image, ia("Images and video", "الصور والفيديو"), ia("Checked on this phone, never uploaded", "تُفحص على هاتفك ولا تُرفع"),
                switch = state.images.on, onSwitch = { state.images.set(it) })
            RowLine()
            ToolRow(Icons.Filled.Apps, ia("Shorts and Reels", "المقاطع القصيرة"), ia("Closed; the rest of each app keeps working", "تُغلق، ويبقى باقي التطبيق يعمل"),
                switch = state.shortVideos.on, onSwitch = { state.shortVideos.set(it) })
            RowLine()
            ToolRow(Icons.Filled.Lock, ia("Protection lock", "قفل الحماية"),
                if (state.lockLabel.isNotEmpty()) state.lockLabel else ia("Off · make the rules read-only", "متوقف · اجعل القواعد للقراءة فقط")) { onOpen("lock") }
        }

        GroupTitle(ia("Your lists", "قوائمك"))
        GroupCard {
            ToolRow(Icons.AutoMirrored.Filled.List, ia("Your lists", "قوائمك"), ia("Your own sites and words", "مواقعك وكلماتك الخاصة")) { onOpen("lists") }
            RowLine()
            ToolRow(Icons.Filled.Search, ia("Check a site", "افحص موقعًا"), ia("Does Hisn block it?", "هل يحجبه حصن؟")) { onOpen("sitecheck") }
            RowLine()
            ToolRow(Icons.Filled.Tune, ia("Words tester", "اختبار الكلمات"), ia("Try a phrase against the word list", "جرّب عبارة على قائمة الكلمات")) { onOpen("wordtester") }
            RowLine()
            ToolRow(Icons.Filled.HealthAndSafety, ia("Health check", "فحص السلامة"), ia("Is every layer working?", "هل تعمل كل الطبقات؟")) { onOpen("diagnostics") }
        }
        Spacer(Modifier.height(10.dp))
    }
}

// ---------------------------------------------------------------- Settings page

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun HisnSettingsPage(state: HisnState, onBack: () -> Unit, onOpen: (String) -> Unit) {
    val look = LocalLook.current
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("hisn-native", Context.MODE_PRIVATE)
    var hanafi by remember { mutableStateOf(HisnPrayers.asr(context) == app.hisn.core.PrayerTimes.Asr.HANAFI) }
    val goals = remember { prefs.getString("onboard.goals", "").orEmpty().split(',').filter { it.isNotEmpty() } }
    IslamicPage(ia("Settings", "الإعدادات"), onBack) {
        GroupTitle(ia("You", "أنت"))
        GroupCard {
            val name = state.personal.name.trim()
            ToolRow(Icons.Filled.Person, name.ifEmpty { ia("Your name", "اسمك") },
                ia("Your reason, your name and your counts, on this phone", "سببك واسمك وأرقامك، على هذا الهاتف")) { onOpen("personal") }
            if (goals.isNotEmpty()) {
                FlowRow(Modifier.padding(start = 64.dp, end = 14.dp, bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    goals.mapNotNull { k -> GoalNames[k] }.forEach { (en, ar) ->
                        Text(ia(en, ar), color = look.t.pri, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.clip(CircleShape).background(look.t.soft).border(1.dp, look.t.pri.copy(alpha = 0.4f), CircleShape).padding(horizontal = 10.dp, vertical = 5.dp))
                    }
                }
            }
        }

        GroupTitle(ia("Look", "الطابع"))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HisnLook.entries.forEach { l -> LookChip(l, l == look, Modifier.weight(1f)) { HisnLooks.set(context, l) } }
        }

        GroupTitle(ia("Prayer", "الصلاة"))
        GroupCard {
            val p = HisnPrayers.place(context)
            ToolRow(Icons.Filled.NightsStay, ia("Prayer times", "مواقيت الصلاة"),
                (if (isArabicUi()) p.ar else p.en) + ia(" · ", "، ") + methodName(p.method) + ia(" · worked out on this phone", "، تُحسب على هاتفك")) { onOpen("hisn:city") }
            RowLine()
            ToolRow(Icons.Filled.Timer, ia("Ḥanafī ʿAṣr", "عصر الحنفية"), ia("Later: when a shadow is twice its length", "متأخر: حين يصير ظلّ الشيء مثليه"),
                switch = hanafi, onSwitch = { hanafi = it; prefs.edit().putBoolean("prayer.hanafi", it).apply() })
        }

        GroupTitle(ia("More", "المزيد"))
        GroupCard {
            ToolRow(Icons.Filled.Language, ia("Language", "اللغة"), ia("Arabic or English", "العربية أو الإنجليزية")) { onOpen("info:language") }
            RowLine()
            ToolRow(Icons.Filled.Tune, ia("All settings", "كل الإعدادات"), ia("Notifications, backups, privacy, about", "التنبيهات والنسخ والخصوصية وعن التطبيق")) { onOpen("hisn:moresettings") }
        }

        Column(Modifier.fillMaxWidth().padding(top = 16.dp).hisnCard(look, 18.dp).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            HisnIcon("hands", 44.dp)
            Text(ia("Give ṣadaqa", "تصدّق"), color = look.t.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text(ia("Hisn is free and open source. If it helps you, give to someone near you.", "حصن مجاني ومفتوح المصدر. وإن نفعك فتصدّق على من حولك."),
                color = look.t.sub, fontSize = 12.5.sp, textAlign = TextAlign.Center)
        }
        Spacer(Modifier.height(12.dp))
    }
}

private val GoalNames = mapOf(
    "pray" to ("Pray on time" to "الصلاة في وقتها"), "gaze" to ("Lower my gaze" to "غضّ بصري"),
    "quran" to ("Qur'an every day" to "القرآن كل يوم"), "near" to ("Nearer to Allah" to "القرب من الله"),
    "marriage" to ("A stronger marriage" to "زواج أقوى"), "family" to ("Better with family" to "علاقة أطيب مع الأهل"),
    "focus" to ("Focus" to "التركيز"), "energy" to ("More energy" to "طاقة أكبر"),
    "sleep" to ("Sleep before midnight" to "النوم قبل منتصف الليل"), "fast" to ("Fast Mondays and Thursdays" to "صيام الاثنين والخميس")
)

/** Every remaining setting of the shared app, under the Islamic header. */
@Composable
internal fun HisnMoreSettings(state: HisnState, onBack: () -> Unit, onOpen: (String) -> Unit) {
    val look = LocalLook.current
    Column(Modifier.fillMaxSize().background(look.t.sky).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, ia("Back", "رجوع"), tint = look.t.ink) }
            Text(ia("All settings", "كل الإعدادات"), color = look.t.ink, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        }
        Box(Modifier.weight(1f)) { SettingsScreen(state = state, onOpen = onOpen) }
    }
}
