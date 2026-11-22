package app.hisn.android

import android.app.Activity
import android.app.AlertDialog

/**
 * A confirmation that leads with a short, plain summary. The full disclosure is one tap away
 * under Details, and accepting from there works the same, so nothing is hidden but nobody has
 * to read a page of network detail to turn protection on.
 */
internal object Disclosure {
    fun show(activity: Activity, title: Int, short: Int, full: Int, accept: Int, onAccept: () -> Unit) {
        AlertDialog.Builder(activity)
            .setTitle(title)
            .setMessage(short)
            .setNegativeButton(android.R.string.cancel, null)
            .setNeutralButton(R.string.disclosure_details) { _, _ ->
                AlertDialog.Builder(activity)
                    .setTitle(R.string.full_disclosure)
                    .setMessage(full)
                    .setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton(accept) { _, _ -> onAccept() }
                    .show()
            }
            .setPositiveButton(accept) { _, _ -> onAccept() }
            .show()
    }
}
