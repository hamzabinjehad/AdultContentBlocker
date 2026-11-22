package app.hisn.android

/**
 * Which edition this build is. The blocking engine never reads this: only the UI does, to pick
 * the Islamic or the normal look, copy and content.
 *
 *  - hisn: the Islamic edition (Islamic UI, and later Islamic texts, links and community).
 *  - general: the non-religious brainrot blocker with the normal UI.
 *
 * Set by the `edition` product flavor in app/build.gradle, which also holds each edition's name.
 * Kotlin copy writes the name as `${Edition.NAME}` / `${Edition.NAME_AR}`, never as a literal;
 * string resources say "Hisn" / "حصن" and the build rewrites them for the general edition.
 */
object Edition {
    const val HISN = "hisn"
    const val GENERAL = "general"

    const val current: String = BuildConfig.EDITION

    /** The app's name in English and Arabic copy. */
    const val NAME: String = BuildConfig.APP_NAME
    const val NAME_AR: String = BuildConfig.APP_NAME_AR

    @JvmField
    val islamic: Boolean = current == HISN
}
