package app.hisn.android.nativeshell

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A launchable app on this phone. */
data class InstalledApp(
    val packageName: String,
    val label: String
)

/**
 * Reads the apps the phone can launch, so the picker shows the real device instead of a fixed list.
 *
 * `AndroidManifest.xml` declares a `<queries>` element for the same intent; without it Android 11+
 * hides the list from the app.
 */
fun installedApps(context: Context, all: Boolean = false): List<InstalledApp> {
    val manager = context.packageManager
    val launchable = Intent(Intent.ACTION_MAIN, null).addCategory(Intent.CATEGORY_LAUNCHER)
    val resolved = runCatching {
        manager.queryIntentActivities(launchable, PackageManager.MATCH_ALL)
    }.getOrDefault(emptyList())
    val launchableApps = resolved
        .mapNotNull { info ->
            val packageName = info.activityInfo?.packageName ?: return@mapNotNull null
            if (packageName == context.packageName) return@mapNotNull null
            InstalledApp(packageName, info.loadLabel(manager).toString().ifBlank { packageName })
        }
    // "Show all apps": every package Android lets Hisn see, including system apps with no
    // home-screen icon. The direct build may see all of them; the Play build sees fewer.
    val others = if (!all) emptyList() else runCatching {
        manager.getInstalledApplications(0).mapNotNull { info ->
            if (info.packageName == context.packageName) return@mapNotNull null
            InstalledApp(info.packageName, manager.getApplicationLabel(info).toString().ifBlank { info.packageName })
        }
    }.getOrDefault(emptyList())
    return (launchableApps + others)
        .distinctBy { it.packageName }
        .sortedBy { it.label.lowercase() }
}

/** Whether "Show all apps" can really list every app: always before Android 11, else by permission. */
fun canListEveryApp(context: Context): Boolean =
    android.os.Build.VERSION.SDK_INT < 30 ||
        context.checkSelfPermission("android.permission.QUERY_ALL_PACKAGES") == PackageManager.PERMISSION_GRANTED

/** The switch every app list shares; it says plainly when Android limits what "all" means. */
@Composable
fun ShowAllAppsRow(checked: Boolean, onChange: (Boolean) -> Unit) {
    val context = LocalContext.current
    HisnCard {
        SwitchRow(
            icon = androidx.compose.material.icons.Icons.Rounded.Apps,
            title = androidx.compose.ui.res.stringResource(app.hisn.android.R.string.apps_show_all),
            supporting = androidx.compose.ui.res.stringResource(
                if (canListEveryApp(context)) app.hisn.android.R.string.apps_show_all_body
                else app.hisn.android.R.string.apps_show_all_limited
            ),
            checked = checked,
            onCheckedChange = onChange
        )
    }
}

/** Resolves the label the phone shows for a saved package, falling back to the package name. */
fun appLabel(context: Context, packageName: String): String = runCatching {
    val manager = context.packageManager
    manager.getApplicationLabel(manager.getApplicationInfo(packageName, 0)).toString()
}.getOrNull()?.takeIf { it.isNotBlank() } ?: packageName

private fun Drawable.toImageBitmap(): ImageBitmap? {
    if (this is BitmapDrawable && bitmap != null) return bitmap.asImageBitmap()
    val width = intrinsicWidth.coerceAtLeast(1)
    val height = intrinsicHeight.coerceAtLeast(1)
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    setBounds(0, 0, width, height)
    draw(canvas)
    return bitmap.asImageBitmap()
}

/** The app's own icon, loaded off the main thread; a neutral glyph until it arrives. */
@Composable
fun AppIcon(packageName: String, size: Dp = 40.dp) {
    val context = LocalContext.current
    val icon by produceState<ImageBitmap?>(initialValue = null, key1 = packageName) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                context.packageManager.getApplicationIcon(packageName).toImageBitmap()
            }.getOrNull()
        }
    }
    val bitmap = icon
    if (bitmap != null) {
        Image(bitmap = bitmap, contentDescription = null, modifier = Modifier.size(size))
    } else {
        Icon(
            imageVector = Icons.Rounded.Apps,
            contentDescription = null,
            modifier = Modifier.size(size)
        )
    }
}


private val SocialPackages: Set<String> by lazy { app.hisn.core.SocialApps.common().map { it.packageName }.toSet() }

/**
 * Apps people most often want to slow down: short-video feeds and the common social apps. Pickers
 * list them right after the person's own choices, so they never have to scroll past Calendar and
 * Camera to find TikTok when usage access is off.
 */
fun isUsualPick(packageName: String): Boolean =
    app.hisn.core.SetupPlan.hasFeed(packageName) || packageName in SocialPackages
